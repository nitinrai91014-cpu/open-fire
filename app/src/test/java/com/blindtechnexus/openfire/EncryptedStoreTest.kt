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
 * The profile must live in /.openfire/data, be unreadable as plain text and
 * be protected against any manual edit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedStoreTest {

    private fun store(context: Context, name: String = ".openfire/testdata"): EncryptedStore =
        EncryptedStore(context, name)

    @Test
    fun dataIsStoredInTheOpenFireFolder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encrypted = store(context)
        assertEquals(".openfire/testdata", encrypted.relativeDirectoryForTest())

        val dir = encrypted.storageDirectory()
        assertTrue(dir.path.contains(".openfire"))
        assertTrue(dir.path.endsWith("data") || dir.path.contains(".openfire"))
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
        val encrypted = store(context, ".openfire/tamper")
        encrypted.write("profile", mapOf("coins" to "10"))

        val file = File(encrypted.storageDirectory(), "profile.dat")
        val lines = file.readText().split("\n").toMutableList()
        assertTrue("Expected an encrypted payload", lines.size >= 4)
        // Flip a character in the encrypted body: the GCM tag must reject it.
        val body = lines[3]
        val changedFirst = if (body.first() == 'A') 'B' else 'A'
        lines[3] = changedFirst + body.substring(1)
        file.writeText(lines.joinToString("\n"))

        val reread = store(context, ".openfire/tamper").read("profile")
        assertTrue("Tampered data must be discarded", reread.isEmpty())
    }

    @Test
    fun deletingAFileRemovesItFromStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encrypted = store(context, ".openfire/delete")
        encrypted.write("inventory", mapOf("medkits" to "3"))
        assertTrue(File(encrypted.storageDirectory(), "inventory.dat").exists())
        encrypted.delete("inventory")
        assertFalse(File(encrypted.storageDirectory(), "inventory.dat").exists())
    }
}
