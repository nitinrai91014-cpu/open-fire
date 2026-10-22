package com.blindtechnexus.openfire

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blindtechnexus.openfire.data.PreferencesManager
import com.blindtechnexus.openfire.data.Strings
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.ExtensionState
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.GameState
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.Inventory
import com.blindtechnexus.openfire.model.Progression
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals("Version 2.1", Strings.get("version", "en"))
        assertEquals("Changelog", Strings.get("changelog", "en"))
        assertEquals("Join Telegram Channel", Strings.get("joinTelegram", "en"))
        assertEquals("Contact on WhatsApp", Strings.get("contactWhatsApp", "en"))

        assertTrue(Strings.getGuide("en").isNotEmpty())
        assertTrue(Strings.getGuide("hi").isNotEmpty())
        assertTrue(Strings.getGuide("ne").isNotEmpty())
    }

    @Test
    fun testHpForRoundScaling() {
        assertEquals(300, Progression.hpForRound(1))
        assertEquals(400, Progression.hpForRound(2))
        assertEquals(500, Progression.hpForRound(3))
        assertEquals(600, Progression.hpForRound(4))
        assertEquals(750, Progression.hpForRound(5))
    }

    @Test
    fun testPistolMagazineCapacity() {
        val pistol = Weapon.entries.find { it.id == "pistol" }
        org.junit.Assert.assertNotNull(pistol)
        assertEquals(6, pistol?.magazine)
        assertEquals(13, pistol?.powerPercent)
    }

    @Test
    fun testPreferencesSaveAndLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        prefs.saveSettings(GameSettings(name = "TestWarrior", lang = "hi"))
        val loaded = prefs.loadSettings()
        assertEquals("TestWarrior", loaded.name)
        assertEquals("hi", loaded.lang)

        val stats = GameStats(
            games = 5,
            wins = 3,
            kills = 3,
            coins = 240,
            round = 2,
            level = 4,
            levelWins = 4
        )
        prefs.saveStats(stats)
        val loadedStats = prefs.loadStats()
        assertEquals(5, loadedStats.games)
        assertEquals(3, loadedStats.wins)
        assertEquals(2, loadedStats.round)
        assertEquals(4, loadedStats.level)
        assertEquals(240, loadedStats.coins)
        assertEquals(4, loadedStats.levelWins)
        assertEquals(6, loadedStats.needWinsForNext)
    }

    @Test
    fun testInventoryRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        val inventory = Inventory(
            weaponIds = setOf(Weapon.SHORT_GUN.id, Weapon.ASSAULT_GUN.id, Weapon.SWORD.id),
            medikits = 7,
            glueWalls = 2,
            covers = 5,
            extensions = mapOf(
                Extension.AUTO_RELOAD.id to ExtensionState.ENABLED,
                Extension.AUTO_COVER.id to ExtensionState.DISABLED
            ),
            lastWeaponId = Weapon.SWORD.id
        )
        prefs.saveInventory(inventory)

        val loaded = PreferencesManager(context).loadInventory()
        assertTrue(loaded.ownsWeapon(Weapon.SWORD))
        assertEquals(7, loaded.medikits)
        assertEquals(2, loaded.glueWalls)
        assertEquals(5, loaded.covers)
        assertTrue(loaded.extensionEnabled(Extension.AUTO_RELOAD))
        assertEquals(ExtensionState.DISABLED, loaded.extensionState(Extension.AUTO_COVER))
        assertEquals(Weapon.SWORD.id, loaded.lastWeaponId)
    }

    @Test
    fun testClearStatsWipesEverythingIncludingTheShop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        prefs.saveStats(
            GameStats(
                games = 42,
                wins = 31,
                kills = 20,
                coins = 999,
                round = 5,
                level = 7,
                levelWins = 6
            )
        )
        prefs.saveInventory(
            Inventory(medikits = 4, glueWalls = 3, covers = 2).copy(
                lastWeaponId = Weapon.SWORD.id
            )
        )
        prefs.grantDefence(DefenceItem.MEDKIT, 2)

        prefs.clearStats()

        val cleared = prefs.loadStats()
        assertEquals(0, cleared.games)
        assertEquals(0, cleared.wins)
        assertEquals(0, cleared.kills)
        assertEquals(0, cleared.coins)
        assertEquals(0, cleared.levelWins)
        assertEquals(1, cleared.level)
        assertEquals(1, cleared.round)

        val inventory = prefs.loadInventory()
        assertEquals(0, inventory.medikits)
        assertEquals(0, inventory.glueWalls)
        assertEquals(0, inventory.covers)
        assertFalse(inventory.extensionOwned(Extension.AUTO_RELOAD))
    }

    @Test
    fun testGameDefaultsUseTheOneHundredMeterArena() {
        assertEquals(Combat.MAX_RANGE, GameState().distance, 0.001f)
        assertEquals(100f, Combat.MAX_RANGE, 0.001f)
    }
}
