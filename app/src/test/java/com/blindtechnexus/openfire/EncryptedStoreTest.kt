package com.blindtechnexus.openfire

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blindtechnexus.openfire.data.EncryptedStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The profile must live in /.open_fire/data, automatically migrate any legacy
 * /.openfire/data files, be unreadable as plain text, and reject tampering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedStoreTest {

    private fun store(context: Context, name: String = ".open_fire/testdata"): EncryptedStore =
        EncryptedStore(context, name)

    @Test
    fun defaultDataIsStoredInTheOpenFireFolder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val defaultStore = EncryptedStore(context)
        assertEquals(EncryptedStore.DEFAULT_DIR, defaultStore.relativeDirectoryForTest())
        assertEquals(".open_fire/data", defaultStore.relativeDirectoryForTest())

        val dir = defaultStore.storageDirectory()
        assertTrue(dir.path.contains(".open_fire"))
        assertTrue(dir.path.endsWith("data") || dir.path.contains(".open_fire"))
    }

    @Test
    fun legacyOpenfireDataDirectoryMigratesAutomaticallyToNewDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacyStore = EncryptedStore(
            context = context,
            relativeDir = ".openfire/migration_legacy",
            legacyRelativeDir = null
        )
        legacyStore.write("stats", mapOf("coins" to "4321", "round" to "3"))
        legacyStore.write("settings", mapOf("name" to "LegacyHero"))
        val legacyDir = legacyStore.storageDirectory()
        assertTrue(File(legacyDir, "stats.dat").exists())
        assertTrue(File(legacyDir, "settings.dat").exists())

        val migratedStore = EncryptedStore(
            context = context,
            relativeDir = ".open_fire/migration_target",
            legacyRelativeDir = ".openfire/migration_legacy"
        )
        val newDir = migratedStore.storageDirectory()
        assertTrue("Migrated stats.dat must exist in new directory", File(newDir, "stats.dat").exists())
        assertTrue("Migrated settings.dat must exist in new directory", File(newDir, "settings.dat").exists())
        assertFalse("Legacy stats.dat must be moved out", File(legacyDir, "stats.dat").exists())
        assertFalse("Legacy settings.dat must be moved out", File(legacyDir, "settings.dat").exists())

        assertEquals("4321", migratedStore.read("stats")["coins"])
        assertEquals("3", migratedStore.read("stats")["round"])
        assertEquals("LegacyHero", migratedStore.read("settings")["name"])
    }

    @Test
    fun valuesRoundTripThroughTheEncryptedFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = store(context)
        first.write("profile", mapOf("coins" to "1234", "name" to "SecretWarrior"))

        val second = store(context)
        val read = second.read("profile")
        assertEquals("1234", read["coins"])
        assertEquals("SecretWarrior", read["name"])
    }

    @Test
    fun theFileOnDiskIsNotHumanReadable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encrypted = store(context)
        encrypted.write("settings", mapOf("name" to "PlainNameVisibleNowhere"))
        val file = File(encrypted.storageDirectory(), "settings.dat")
        assertTrue("Encrypted file must exist", file.exists())
        val text = file.readText()
        assertFalse("Plain value must never be stored", text.contains("PlainNameVisibleNowhere"))
        assertFalse("Header must be the encrypted format", text.startsWith("{") || text.startsWith("name="))
        assertTrue(text.startsWith("OF"))
    }

    @Test
    fun tamperingWithTheFileIsRejectedInsteadOfAccepted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encrypted = store(context, ".open_fire/tamper")
        encrypted.write("profile", mapOf("coins" to "10"))

        val file = File(encrypted.storageDirectory(), "profile.dat")
        val lines = file.readText().split("\n").toMutableList()
        assertTrue("Expected an encrypted payload", lines.size >= 4)
        // Flip a character in the encrypted body: the GCM tag must reject it.
        val body = lines[3]
        val changedFirst = if (body.first() == 'A') 'B' else 'A'
        lines[3] = changedFirst + body.substring(1)
        file.writeText(lines.joinToString("\n"))

        val reread = store(context, ".open_fire/tamper").read("profile")
        assertTrue("Tampered data must be discarded", reread.isEmpty())
    }

    @Test
    fun deletingAFileRemovesItFromStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encrypted = store(context, ".open_fire/delete")
        encrypted.write("inventory", mapOf("medkits" to "3"))
        assertTrue(File(encrypted.storageDirectory(), "inventory.dat").exists())
        encrypted.delete("inventory")
        assertFalse(File(encrypted.storageDirectory(), "inventory.dat").exists())
    }
}
