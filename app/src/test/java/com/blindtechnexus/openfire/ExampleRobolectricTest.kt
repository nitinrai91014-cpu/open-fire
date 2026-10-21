package com.blindtechnexus.openfire

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blindtechnexus.openfire.data.PreferencesManager
import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.DEFAULT_GUNS
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.getHpForLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Open Fire", appName)
    }

    @Test
    fun testStringsTranslations() {
        assertEquals("Open Fire", Strings.get("title", "en"))
        assertEquals("ओपन फायर", Strings.get("title", "hi"))
        assertEquals("ओपन फायर", Strings.get("title", "ne"))

        assertEquals("Version 1.2", Strings.get("version", "en"))
        assertEquals("Changelog", Strings.get("changelog", "en"))
        assertEquals("Join Telegram Channel", Strings.get("joinTelegram", "en"))
        assertEquals("Contact on WhatsApp", Strings.get("contactWhatsApp", "en"))

        val guideEn = Strings.getGuide("en")
        assertTrue(guideEn.isNotEmpty())
        val guideHi = Strings.getGuide("hi")
        assertTrue(guideHi.isNotEmpty())
        val guideNe = Strings.getGuide("ne")
        assertTrue(guideNe.isNotEmpty())
    }

    @Test
    fun testHpForLevelScaling() {
        assertEquals(200, getHpForLevel(1))
        assertEquals(200, getHpForLevel(10))
        assertEquals(300, getHpForLevel(11))
        assertEquals(300, getHpForLevel(20))
        assertEquals(500, getHpForLevel(21))
        assertEquals(500, getHpForLevel(25))
        assertEquals(800, getHpForLevel(26))
        assertEquals(800, getHpForLevel(30))
    }

    @Test
    fun testPistolMagazineCapacity() {
        val pistol = DEFAULT_GUNS.find { it.nameKey == "gunPistol" }
        org.junit.Assert.assertNotNull(pistol)
        assertEquals(6, pistol?.mag)
    }

    @Test
    fun testPreferencesSaveAndLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        val settings = GameSettings(
            name = "TestWarrior",
            lang = "hi"
        )
        prefs.saveSettings(settings)

        val loaded = prefs.loadSettings()
        assertEquals("TestWarrior", loaded.name)
        assertEquals("hi", loaded.lang)

        val stats = GameStats(
            games = 5,
            wins = 3,
            kills = 3,
            coins = 240,
            round = 2,
            level = 14
        )
        prefs.saveStats(stats)

        val loadedStats = prefs.loadStats()
        assertEquals(5, loadedStats.games)
        assertEquals(3, loadedStats.wins)
        assertEquals(2, loadedStats.round)
        assertEquals(14, loadedStats.level)
        assertEquals(240, loadedStats.coins)
    }
}
