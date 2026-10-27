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

        assertEquals("Version ${BuildConfig.VERSION_NAME}", Strings.get("version", "en"))
        assertEquals("Changelog", Strings.get("changelog", "en"))
        assertEquals("Join Telegram Channel", Strings.get("joinTelegram", "en"))
        assertEquals("Contact on WhatsApp", Strings.get("contactWhatsApp", "en"))

        assertTrue(Strings.getGuide("en").isNotEmpty())
        assertTrue(Strings.getGuide("hi").isNotEmpty())
        assertTrue(Strings.getGuide("ne").isNotEmpty())
    }

    @Test
    fun testHpForRoundAndLevelScaling() {
        assertEquals(200, Progression.hpFor(1, 1))
        assertEquals(600, Progression.hpFor(1, 5))
        assertEquals(700, Progression.hpFor(2, 1))
        assertEquals(900, Progression.hpFor(2, 3))
        assertEquals(2600, Progression.hpFor(5, 5))
    }

    @Test
    fun testPistolMagazineCapacity() {
        val pistol = Weapon.PISTOL
        assertEquals(6, pistol.magazine)
        assertEquals(13, pistol.powerPercent)
        // Direct damage follows the same proximity multipliers as all firearms.
        assertEquals(0, Combat.weaponDamage(pistol, 20f, 750))
        assertEquals(7, Combat.weaponDamage(pistol, 50f, 750))
        assertEquals(10, Combat.weaponDamage(pistol, 80f, 750))
        assertEquals(13, Combat.weaponDamage(pistol, 95f, 750))
    }

    @Test
    fun testPreferencesSaveAndLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        prefs.saveSettings(
            GameSettings(
                name = "TestWarrior",
                lang = "hi",
                keepScreenOn = true
            )
        )
        val loaded = prefs.loadSettings()
        assertEquals("TestWarrior", loaded.name)
        assertEquals("hi", loaded.lang)
        assertTrue(loaded.keepScreenOn)

        val stats = GameStats(
            games = 5,
            wins = 3,
            kills = 3,
            coins = 240,
            round = 2,
            level = 4,
            levelWins = 4,
            completedRounds = 2
        )
        prefs.saveStats(stats)
        val loadedStats = prefs.loadStats()
        assertEquals(5, loadedStats.games)
        assertEquals(3, loadedStats.wins)
        assertEquals(2, loadedStats.round)
        assertEquals(4, loadedStats.level)
        assertEquals(240, loadedStats.coins)
        assertEquals(4, loadedStats.levelWins)
        assertEquals(2, loadedStats.completedRounds)
        // Five wins complete a level, so four wins leave exactly one to go.
        assertEquals(1, loadedStats.needWinsForNext)
    }

    @Test
    fun testInventoryRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        val inventory = Inventory(
            weaponIds = setOf(
                Weapon.SHORT_GUN.id,
                Weapon.ASSAULT_GUN.id,
                Weapon.SWORD.id,
                Weapon.MISSILE.id
            ),
            medikits = 7,
            glueWalls = 2,
            covers = 5,
            antiMissiles = 2,
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
        assertEquals(2, loaded.antiMissiles)
        assertTrue(loaded.extensionEnabled(Extension.AUTO_RELOAD))
        assertEquals(ExtensionState.DISABLED, loaded.extensionState(Extension.AUTO_COVER))
        assertEquals(Weapon.SWORD.id, loaded.lastWeaponId)
        assertTrue(loaded.ownsWeapon(Weapon.MISSILE))
        assertEquals(4, loaded.ownedWeapons().size)
        assertEquals(Weapon.SWORD, loaded.startingWeapon())
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
            Inventory(
                medikits = 4,
                glueWalls = 3,
                covers = 2,
                antiMissiles = 1,
                extensions = mapOf(Extension.AUTO_RELOAD.id to ExtensionState.ENABLED),
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
        assertEquals(0, inventory.antiMissiles)
        assertEquals(Weapon.defaults(), inventory.ownedWeapons())
        assertEquals(Weapon.SHORT_GUN, inventory.startingWeapon())
        assertFalse(inventory.extensionOwned(Extension.AUTO_RELOAD))
    }

    @Test
    fun testRemovedWeaponIdsAreIgnoredWhenLoadingOldInventories() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        prefs.saveInventory(
            Inventory(
                weaponIds = setOf(Weapon.ATOMIC_BOMB.id, "grenade", "atomBomb", "unknownWeapon"),
                lastWeaponId = "timerBomb"
            )
        )

        val loaded = PreferencesManager(context).loadInventory()
        assertEquals(
            (Weapon.defaults() + Weapon.ATOMIC_BOMB).map { it.id }.toSet(),
            loaded.weaponIds
        )
        assertEquals(Weapon.SHORT_GUN, loaded.startingWeapon())
    }

    @Test
    fun testGameDefaultsUseTheOneHundredMeterArena() {
        assertEquals(Combat.MAX_RANGE, GameState().distance, 0.001f)
        assertEquals(100f, Combat.MAX_RANGE, 0.001f)
    }
}
