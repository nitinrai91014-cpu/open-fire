package com.blindtechnexus.openfire

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blindtechnexus.openfire.data.PreferencesManager
import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        val guideEn = Strings.getGuide("en")
        assertTrue(guideEn.isNotEmpty())
        val guideHi = Strings.getGuide("hi")
        assertTrue(guideHi.isNotEmpty())
        val guideNe = Strings.getGuide("ne")
        assertTrue(guideNe.isNotEmpty())
    }

    @Test
    fun testPreferencesSaveAndLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        val settings = GameSettings(
            name = "TestWarrior",
            lang = "hi",
            playerMaxHp = 250,
            devilMaxHp = 260
        )
        prefs.saveSettings(settings)

        val loaded = prefs.loadSettings()
        assertEquals("TestWarrior", loaded.name)
        assertEquals("hi", loaded.lang)
        assertEquals(250, loaded.playerMaxHp)
        assertEquals(260, loaded.devilMaxHp)
    }

    @Test
    fun testStatsLevelCalculation() {
        val stats1 = GameStats(wins = 0)
        assertEquals(0, stats1.level)
        assertEquals(10, stats1.needWinsForNext)

        val stats2 = GameStats(wins = 12)
        assertEquals(1, stats2.level)
        assertEquals(8, stats2.needWinsForNext)
    }
}
