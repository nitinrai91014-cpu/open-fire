package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.ExtensionState
import com.blindtechnexus.openfire.model.Inventory
import com.blindtechnexus.openfire.model.MissileRules
import com.blindtechnexus.openfire.model.Shop
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks every shop price, purchase, removal and consumption rule. */
class ShopTest {

    @Test
    fun defaultInventoryOwnsOnlyTheFreeWeapons() {
        val inventory = Inventory()
        assertTrue(inventory.ownsWeapon(Weapon.SHORT_GUN))
        assertTrue(inventory.ownsWeapon(Weapon.ASSAULT_GUN))
        assertFalse(inventory.ownsWeapon(Weapon.PISTOL))
        assertFalse(inventory.ownsWeapon(Weapon.SNIPER))
        assertFalse(inventory.ownsWeapon(Weapon.KNIFE))
        assertFalse(inventory.ownsWeapon(Weapon.SWORD))
        assertFalse(inventory.ownsWeapon(Weapon.MISSILE))
        assertEquals(0, inventory.missilePowers)
        assertFalse(inventory.hasMissilePower)
        assertEquals(2, inventory.ownedWeapons().size)
    }

    @Test
    fun weaponPricesMatchTheShop() {
        assertEquals(500, Weapon.KNIFE.price)
        assertEquals(750, Weapon.PISTOL.price)
        assertEquals(950, Weapon.SWORD.price)
        assertEquals(1200, Weapon.SNIPER.price)
    }

    @Test
    fun buyingAWeaponDeductsCoinsAndAddsIt() {
        val result = Shop.buyWeapon(Inventory(), 1000, Weapon.PISTOL)
        assertTrue(result.success)
        assertEquals(250, result.coins)
        assertTrue(result.inventory.ownsWeapon(Weapon.PISTOL))
    }

    @Test
    fun buyingWithoutEnoughCoinsFailsWithoutChanges() {
        val result = Shop.buyWeapon(Inventory(), 199, Weapon.PISTOL)
        assertFalse(result.success)
        assertEquals(199, result.coins)
        assertEquals("notEnoughCoins", result.reasonKey)
        assertFalse(result.inventory.ownsWeapon(Weapon.PISTOL))
    }

    @Test
    fun aWeaponCannotBeBoughtTwice() {
        val owned = Shop.buyWeapon(Inventory(), 2000, Weapon.SNIPER).inventory
        val again = Shop.buyWeapon(owned, 2000, Weapon.SNIPER)
        assertFalse(again.success)
        assertEquals("alreadyOwned", again.reasonKey)
        assertEquals(2000, again.coins)
    }

    @Test
    fun boughtWeaponsCanBeRemovedButFreeWeaponsCannot() {
        val owned = Shop.buyWeapon(Inventory(), 1000, Weapon.SWORD).inventory
        val removed = Shop.removeWeapon(owned, 1000, Weapon.SWORD)
        assertTrue(removed.success)
        assertFalse(removed.inventory.ownsWeapon(Weapon.SWORD))

        val free = Shop.removeWeapon(Inventory(), 1000, Weapon.SHORT_GUN)
        assertFalse(free.success)
        assertEquals("cannotRemoveWeapon", free.reasonKey)
    }

    @Test
    fun removingTheRememberedWeaponFallsBackToTheDefault() {
        val owned = Shop.buyWeapon(Inventory(), 1000, Weapon.KNIFE).inventory
            .copy(lastWeaponId = Weapon.KNIFE.id)
        val removed = Shop.removeWeapon(owned, 0, Weapon.KNIFE).inventory
        assertEquals(Weapon.SHORT_GUN.id, removed.lastWeaponId)
        assertEquals(Weapon.SHORT_GUN, removed.startingWeapon())
    }

    @Test
    fun defencesAreRepeatableAndDecreaseOnUse() {
        var inventory = Inventory()
        var coins = 2000
        repeat(3) {
            val bought = Shop.buyDefence(inventory, coins, DefenceItem.MEDKIT)
            assertTrue(bought.success)
            inventory = bought.inventory
            coins = bought.coins
        }
        assertEquals(3, inventory.medikits)
        assertEquals(500, coins)

        inventory = Shop.consume(inventory, DefenceItem.MEDKIT)
        assertEquals(2, inventory.medikits)
        assertTrue(inventory.count(DefenceItem.MEDKIT) >= 0)
    }

    @Test
    fun antiMissileDefenceCostsThreeThousandAndUnlocksAfterRoundTwo() {
        assertEquals(3000, DefenceItem.ANTI_MISSILE.price)
        assertEquals(2, DefenceItem.ANTI_MISSILE.requiredCompletedRounds)

        val locked = Shop.buyDefence(
            Inventory(),
            5000,
            DefenceItem.ANTI_MISSILE,
            completedRounds = 1
        )
        assertFalse(locked.success)
        assertEquals("antiMissileLocked", locked.reasonKey)
        assertEquals(5000, locked.coins)
        assertEquals(0, locked.inventory.antiMissiles)

        val unlocked = Shop.buyDefence(
            Inventory(),
            5000,
            DefenceItem.ANTI_MISSILE,
            completedRounds = 2
        )
        assertTrue(unlocked.success)
        assertEquals(2000, unlocked.coins)
        assertEquals(1, unlocked.inventory.antiMissiles)

        val consumed = Shop.consume(unlocked.inventory, DefenceItem.ANTI_MISSILE)
        assertEquals(0, consumed.antiMissiles)

        val removed = Shop.removeDefence(unlocked.inventory, 2000, DefenceItem.ANTI_MISSILE)
        assertTrue(removed.success)
        assertEquals(0, removed.inventory.antiMissiles)
    }

    @Test
    fun aDefenceCanNeverGoNegative() {
        val inventory = Shop.consume(Inventory(), DefenceItem.COVER)
        assertEquals(0, inventory.covers)
        assertEquals(0, Shop.consume(Inventory(), DefenceItem.ANTI_MISSILE).antiMissiles)
    }

    @Test
    fun extensionsCanBeBoughtDisabledEnabledAndRemoved() {
        val bought = Shop.buyExtension(Inventory(), 500, Extension.AUTO_RELOAD)
        assertTrue(bought.success)
        assertEquals(350, bought.coins)
        assertTrue(bought.inventory.extensionEnabled(Extension.AUTO_RELOAD))

        val disabled = Shop.toggleExtension(bought.inventory, 350, Extension.AUTO_RELOAD)
        assertTrue(disabled.success)
        assertEquals(
            ExtensionState.DISABLED,
            disabled.inventory.extensionState(Extension.AUTO_RELOAD)
        )
        assertFalse(disabled.inventory.extensionEnabled(Extension.AUTO_RELOAD))
        assertEquals(350, disabled.coins)

        val enabled = Shop.toggleExtension(disabled.inventory, 350, Extension.AUTO_RELOAD)
        assertTrue(enabled.inventory.extensionEnabled(Extension.AUTO_RELOAD))

        val removed = Shop.removeExtension(enabled.inventory, 0, Extension.AUTO_RELOAD)
        assertTrue(removed.success)
        assertEquals(
            ExtensionState.NOT_OWNED,
            removed.inventory.extensionState(Extension.AUTO_RELOAD)
        )
    }

    @Test
    fun buyingAnExtensionWithoutEnoughCoinsFails() {
        val result = Shop.buyExtension(Inventory(), 299, Extension.AUTO_COVER)
        assertFalse(result.success)
        assertEquals("notEnoughCoins", result.reasonKey)
        assertEquals(299, result.coins)
    }

    @Test
    fun extensionPricesAreTripledAndMatchTheShop() {
        assertEquals(150, Extension.AUTO_RELOAD.price)
        assertEquals(240, Extension.AUTO_MEDKIT.price)
        assertEquals(300, Extension.AUTO_COVER.price)
        assertEquals(300, Extension.AUTO_GLUE_WALL.price)
    }

    @Test
    fun missileAndMissilePowerHaveTheRequestedPrices() {
        assertEquals(3500, Weapon.MISSILE.price)
        assertEquals(3500, MissileRules.MISSILE_PRICE)
        assertEquals(1800, MissileRules.MISSILE_POWER_PRICE)
        assertEquals(2, Weapon.MISSILE.requiredCompletedRounds)
        assertTrue(Weapon.MISSILE.missileLauncher)
        assertEquals(0, Weapon.MISSILE.magazine)
        assertFalse(Weapon.MISSILE.needsReload)
        // Switching to the missile uses the shared gun-switch sound.
        assertEquals("gunReload", Weapon.MISSILE.switchSoundKey)
        // Missile power is a consumable, never a permanent extension.
        assertFalse(Extension.entries.any { it.id == "missilePowers" })
    }

    @Test
    fun missileIsLockedUntilRoundTwoIsCompleted() {
        val locked = Shop.buyWeapon(Inventory(), 5000, Weapon.MISSILE, completedRounds = 1)
        assertFalse(locked.success)
        assertEquals("missileLocked", locked.reasonKey)
        assertEquals(5000, locked.coins)
        assertFalse(locked.inventory.ownsWeapon(Weapon.MISSILE))

        val unlocked = Shop.buyWeapon(Inventory(), 5000, Weapon.MISSILE, completedRounds = 2)
        assertTrue(unlocked.success)
        assertEquals(1500, unlocked.coins)
        assertTrue(unlocked.inventory.ownsWeapon(Weapon.MISSILE))
        // The missile is a real battle weapon in the switch inventory.
        assertEquals(3, unlocked.inventory.ownedWeapons().size)
        assertTrue(unlocked.inventory.ownedWeapons().contains(Weapon.MISSILE))

        val again = Shop.buyWeapon(unlocked.inventory, 5000, Weapon.MISSILE, completedRounds = 2)
        assertFalse(again.success)
        assertEquals("alreadyOwned", again.reasonKey)
    }

    @Test
    fun missilePowersStackLikeDefencesAndAreConsumedOnePerLaunch() {
        val locked = Shop.buyMissilePower(Inventory(), 6000, completedRounds = 1)
        assertFalse(locked.success)
        assertEquals("missileLocked", locked.reasonKey)
        assertEquals(6000, locked.coins)
        assertEquals(0, locked.inventory.missilePowers)

        var inventory = Inventory()
        var coins = 6000
        repeat(3) {
            val bought = Shop.buyMissilePower(inventory, coins, completedRounds = 2)
            assertTrue(bought.success)
            inventory = bought.inventory
            coins = bought.coins
        }
        assertEquals(3, inventory.missilePowers)
        assertTrue(inventory.hasMissilePower)
        assertEquals(600, coins)

        // Exactly one power is consumed per use; the rest stay stacked.
        inventory = Shop.consumeMissilePower(inventory)
        assertEquals(2, inventory.missilePowers)
        inventory = Shop.consumeMissilePower(inventory)
        inventory = Shop.consumeMissilePower(inventory)
        assertEquals(0, inventory.missilePowers)
        assertFalse(inventory.hasMissilePower)
        // The count can never go negative.
        assertEquals(0, Shop.consumeMissilePower(inventory).missilePowers)

        val broke = Shop.buyMissilePower(Inventory(), 1799, completedRounds = 2)
        assertFalse(broke.success)
        assertEquals("notEnoughCoins", broke.reasonKey)
        assertEquals(1799, broke.coins)
    }

    @Test
    fun theRememberedWeaponIsOnlyUsedWhenItIsStillOwned() {
        assertNull(Inventory(lastWeaponId = Weapon.SWORD.id).rememberedWeapon())
        val owned = Shop.buyWeapon(Inventory(), 500, Weapon.SWORD).inventory
            .copy(lastWeaponId = Weapon.SWORD.id)
        assertEquals(Weapon.SWORD, owned.rememberedWeapon())
        assertEquals(Weapon.SWORD, owned.startingWeapon())
    }
}
