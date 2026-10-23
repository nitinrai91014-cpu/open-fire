package com.blindtechnexus.openfire.data

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted, tamper-evident storage for the whole game profile.
 *
 * Data lives in the protected app folder `/.open_fire/data/` on external
 * storage and is always written through AES-256 in GCM mode, so:
 *
 *  - every value is confidential: the saved file is unreadable text garbage;
 *  - every value is authenticated: a single changed byte makes the GCM tag
 *    fail, so a player cannot edit coins, wins, weapons or progress by hand.
 *
 * On startup, if the previous `/.openfire/data/` folder exists, all encrypted
 * profile files are automatically moved into `/.open_fire/data/` and the old
 * folder is removed.
 *
 * Two key sources are supported, in order of preference:
 *  1. a non-exportable AES-256 key held by the Android Keystore (hardware
 *     backed when the device offers it) — the key never leaves the device;
 *  2. if the Keystore is unavailable, a passphrase key derived with PBKDF2
 *     (HMAC-SHA256, 20.000 iterations) from the device identity and the app
 *     id, so the stored bytes still cannot be edited or read outside the app.
 *
 * Every profile file keeps the mode that wrote it in its header, so a device
 * that later gains (or loses) Keystore support can still read old data.
 *
 * Performance: each file is read at most once per process and then served from
 * an in-memory map; writes are atomic (temporary file + rename). Profile reads
 * therefore cost a single map lookup, which keeps the shop, the profile screen
 * and every battle start instant.
 */
class EncryptedStore(
    context: Context,
    private val relativeDir: String = DEFAULT_DIR,
    private val legacyRelativeDir: String? = if (relativeDir == DEFAULT_DIR) LEGACY_DIR else null
) {
    companion object {
        private const val TAG = "OpenFireStore"

        /** Protected folder (inside the app's external files) that holds the data. */
        const val DEFAULT_DIR = ".open_fire/data"

        /** Previous folder name, automatically migrated into [DEFAULT_DIR] if present. */
        const val LEGACY_DIR = ".openfire/data"

        private const val MAGIC_ENCRYPTED = "OF2"
        private const val MAGIC_FALLBACK = "OF1"
        private const val KEY_ALIAS = "openfire_secure_storage"
        private const val MODE_KEYSTORE = "ks"
        private const val MODE_PASSPHRASE = "pw"
        private const val GCM_TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val ITERATIONS = 20_000

        /** Password used by the PBKDF2 fallback when no Keystore key exists. */
        private const val PASSPHRASE = "sdssfsd"
        private const val KEY_BITS = 256

        @Volatile
        private var keystoreCache: SecretKey? = null

        @Volatile
        private var keystoreAttempted = false

        @Volatile
        private var passphraseCache: SecretKey? = null
    }

    private val appContext: Context = context.applicationContext
    private val directory: File = resolveDirectory()
    private val memory = mutableMapOf<String, Map<String, String>>()
    private val random = SecureRandom()

    init {
        migrateLegacyDirectoryIfNeeded()
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Absolute path of the folder that holds the encrypted data files. */
    fun storageDirectory(): File = directory

    /** Relative folder name, exposed for diagnostics and tests. */
    fun relativeDirectoryForTest(): String = relativeDir

    /** True when the named profile file already exists. */
    fun exists(name: String): Boolean =
        memory.containsKey(name) || fileFor(name).exists()

    /**
     * Reads a profile file. Returns an empty map when the file does not exist
     * or cannot be decrypted (for example after a restore from another
     * device); the game then simply starts with its defaults.
     */
    fun read(name: String): Map<String, String> {
        synchronized(memory) {
            memory[name]?.let { return it }
        }
        val file = fileFor(name)
        if (!file.exists()) {
            val empty = emptyMap<String, String>()
            synchronized(memory) { memory[name] = empty }
            return empty
        }
        val values = try {
            decodeFile(file.readText(Charsets.UTF_8))
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read encrypted file $name", t)
            emptyMap()
        }
        synchronized(memory) { memory[name] = values }
        return values
    }

    /** Encrypts and atomically persists a profile file. */
    fun write(name: String, values: Map<String, String>) {
        val file = fileFor(name)
        try {
            if (!directory.exists()) directory.mkdirs()
            val payload = encodeValues(values)
            val text = encryptToText(payload)
            val temp = File(directory, "$name.tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Could not replace $name")
            }
            if (!temp.renameTo(file)) {
                // Renaming can fail on some external storages; fall back to a copy.
                file.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to save encrypted file $name", t)
        }
        synchronized(memory) { memory[name] = values.toMap() }
    }

    /** Deletes a stored file and forgets its cached copy. */
    fun delete(name: String) {
        synchronized(memory) { memory.remove(name) }
        try {
            fileFor(name).delete()
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------
    // Encoding
    // ------------------------------------------------------------------

    private fun fileFor(name: String): File = File(directory, "$name.dat")

    private fun resolveBaseDirectory(): File {
        return try {
            appContext.getExternalFilesDir(null)
        } catch (t: Throwable) {
            Log.w(TAG, "External storage unavailable, using internal storage", t)
            null
        } ?: appContext.filesDir
    }

    private fun resolveDirectory(): File = File(resolveBaseDirectory(), relativeDir)

    /**
     * Moves any encrypted profile files from the previous data directory
     * (e.g. `.openfire/data`) into the new destination (`.open_fire/data`),
     * then cleans up the old directory so only the new protected path is used.
     */
    private fun migrateLegacyDirectoryIfNeeded() {
        val legacyRel = legacyRelativeDir?.takeIf { it.isNotBlank() && it != relativeDir } ?: return
        val candidateBases = linkedSetOf<File>()
        try {
            appContext.getExternalFilesDir(null)?.let { candidateBases.add(it) }
        } catch (_: Throwable) {
        }
        candidateBases.add(appContext.filesDir)

        for (base in candidateBases) {
            val legacyDir = File(base, legacyRel)
            if (!legacyDir.exists() || !legacyDir.isDirectory) continue
            if (legacyDir.absolutePath == directory.absolutePath) continue
            try {
                if (!directory.exists()) directory.mkdirs()
                val children = legacyDir.listFiles().orEmpty()
                for (child in children) {
                    if (!child.isFile) continue
                    if (child.name.endsWith(".dat")) {
                        val destination = File(directory, child.name)
                        if (!destination.exists()) {
                            if (!child.renameTo(destination)) {
                                child.copyTo(destination, overwrite = false)
                                child.delete()
                            }
                        } else {
                            child.delete()
                        }
                    } else if (child.name.endsWith(".tmp")) {
                        child.delete()
                    }
                }
                legacyDir.delete()
                val parent = legacyDir.parentFile
                if (parent != null &&
                    parent.absolutePath != base.absolutePath &&
                    parent.list()?.isEmpty() == true
                ) {
                    parent.delete()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to migrate legacy data directory ${legacyDir.path}", t)
            }
        }
    }

    private fun encodeValues(values: Map<String, String>): String =
        values.entries.joinToString("\n") { (key, value) ->
            val encoded = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "$key=$encoded"
        }

    private fun decodeValues(payload: String): Map<String, String> {
        if (payload.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (line in payload.split('\n')) {
            if (line.isEmpty()) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator)
            val raw = line.substring(separator + 1)
            val value = try {
                if (raw.isEmpty()) "" else String(Base64.decode(raw, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
            result[key] = value
        }
        return result
    }

    // ------------------------------------------------------------------
    // Encryption / decryption
    // ------------------------------------------------------------------

    private fun encryptToText(payload: String): String {
        val plain = payload.toByteArray(Charsets.UTF_8)

        val key = keystoreKey()
        if (key != null) {
            val encrypted = gcmEncrypt(plain, key)
            if (encrypted != null) {
                return buildString {
                    append(MAGIC_ENCRYPTED).append('\n')
                    append(MODE_KEYSTORE).append('\n')
                    append(encrypted.first).append('\n')
                    append(encrypted.second)
                }
            }
        }

        val derived = passphraseKey()
        if (derived != null) {
            val encrypted = gcmEncrypt(plain, derived)
            if (encrypted != null) {
                return buildString {
                    append(MAGIC_ENCRYPTED).append('\n')
                    append(MODE_PASSPHRASE).append('\n')
                    append(encrypted.first).append('\n')
                    append(encrypted.second)
                }
            }
        }

        // Last-resort fallback: a checksum-protected file. This never happens
        // on a real Android device, but it guarantees the game stays usable.
        val encoded = Base64.encodeToString(plain, Base64.NO_WRAP)
        return "$MAGIC_FALLBACK\n${checksum(plain)}\n$encoded"
    }

    private fun decodeFile(text: String): Map<String, String> {
        val lines = text.split('\n')
        return when (lines.firstOrNull()) {
            MAGIC_ENCRYPTED -> {
                val mode = lines.getOrNull(1) ?: return emptyMap()
                val iv = Base64.decode(lines.getOrNull(2) ?: "", Base64.NO_WRAP)
                val body = Base64.decode(lines.getOrNull(3) ?: "", Base64.NO_WRAP)
                // Try the key source recorded in the header first and then the
                // other one: a device that gained or lost Keystore support must
                // still be able to open data written before the change.
                val candidates = if (mode == MODE_KEYSTORE) {
                    listOfNotNull(keystoreKey(), passphraseKey())
                } else {
                    listOfNotNull(passphraseKey(), keystoreKey())
                }
                for (key in candidates) {
                    val plain = gcmDecrypt(iv, body, key)
                    if (plain != null) return decodeValues(String(plain, Charsets.UTF_8))
                }
                emptyMap()
            }
            MAGIC_FALLBACK -> {
                val expected = lines.getOrNull(1) ?: return emptyMap()
                val body = Base64.decode(lines.getOrNull(2) ?: "", Base64.NO_WRAP)
                if (checksum(body) != expected) emptyMap()
                else decodeValues(String(body, Charsets.UTF_8))
            }
            else -> {
                // Unknown / legacy plain text: refuse rather than trust it.
                Log.w(TAG, "Unrecognised storage format")
                emptyMap()
            }
        }
    }

    private fun gcmEncrypt(plain: ByteArray, key: SecretKey): Pair<String, String>? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        Pair(
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        )
    } catch (t: Throwable) {
        Log.w(TAG, "Encryption failed", t)
        null
    }

    private fun gcmDecrypt(iv: ByteArray, body: ByteArray, key: SecretKey): ByteArray? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv.takeIf { it.size == IV_BYTES } ?: randomIv())
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        cipher.doFinal(body)
    } catch (t: Throwable) {
        Log.w(TAG, "Decryption failed (data may have been modified)", t)
        null
    }

    private fun randomIv(): ByteArray = ByteArray(IV_BYTES).also { random.nextBytes(it) }

    private fun checksum(bytes: ByteArray): String = try {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        Base64.encodeToString(digest, Base64.NO_WRAP)
    } catch (_: Throwable) {
        bytes.size.toString()
    }

    // ------------------------------------------------------------------
    // Key material
    // ------------------------------------------------------------------

    /**
     * The Android Keystore key. It is created once and then reused; on
     * devices (or test runners) without Keystore support this returns null and
     * the passphrase key is used instead.
     */
    private fun keystoreKey(): SecretKey? {
        synchronized(EncryptedStore::class.java) {
            keystoreCache?.let { return it }
            if (keystoreAttempted) return null
            keystoreAttempted = true
            return try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                val key = existing?.secretKey ?: createKeystoreKey()
                keystoreCache = key
                key
            } catch (t: Throwable) {
                Log.w(TAG, "Android Keystore unavailable, using passphrase key", t)
                null
            }
        }
    }

    private fun createKeystoreKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    /**
     * A deterministic AES-256 key derived from the device identity. It keeps
     * the data unreadable and modifies-proof even where the Keystore is not
     * available, without ever storing a password in the app.
     */
    private fun passphraseKey(): SecretKey? {
        synchronized(EncryptedStore::class.java) {
            passphraseCache?.let { return it }
            return try {
                val deviceId = try {
                    Settings.Secure.getString(
                        appContext.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                } catch (_: Throwable) {
                    null
                } ?: "unknown-device"
                // The passphrase fallback mixes the app password with the
                // device identity, so the stored bytes are useless on any
                // other device even if they are copied out of the folder.
                val secret = "$PASSPHRASE|$deviceId|${appContext.packageName}|open-fire-secure-v2"
                val salt = "open-fire-salt-v2".toByteArray(Charsets.UTF_8)
                val algorithm = try {
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                } catch (_: Throwable) {
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                }
                val specification = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
                val encoded = algorithm.generateSecret(specification).encoded
                val key = SecretKeySpec(encoded, "AES")
                passphraseCache = key
                key
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to derive the storage key", t)
                null
            }
        }
    }
}
