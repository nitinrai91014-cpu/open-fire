package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.ExtensionState
import com.blindtechnexus.openfire.model.Inventory
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
        assertEquals(2, inventory.ownedWeapons().size)
    }

    @Test
    fun weaponPricesMatchTheShop() {
        assertEquals(200, Weapon.PISTOL.price)
        assertEquals(300, Weapon.SNIPER.price)
        assertEquals(250, Weapon.KNIFE.price)
        assertEquals(340, Weapon.SWORD.price)
    }

    @Test
    fun buyingAWeaponDeductsCoinsAndAddsIt() {
        val result = Shop.buyWeapon(Inventory(), 1000, Weapon.PISTOL)
        assertTrue(result.success)
        assertEquals(800, result.coins)
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
        val owned = Shop.buyWeapon(Inventory(), 1000, Weapon.SNIPER).inventory
        val again = Shop.buyWeapon(owned, 1000, Weapon.SNIPER)
        assertFalse(again.success)
        assertEquals("alreadyOwned", again.reasonKey)
        assertEquals(1000, again.coins)
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
        assertEquals(3, inventory.medkits)
        assertEquals(1400, coins)

        inventory = Shop.consume(inventory, DefenceItem.MEDKIT)
        assertEquals(2, inventory.medkits)
        assertTrue(inventory.count(DefenceItem.MEDKIT) >= 0)
    }

    @Test
    fun aDefenceCanNeverGoNegative() {
        val inventory = Shop.consume(Inventory(), DefenceItem.COVER)
        assertEquals(0, inventory.covers)
    }

    @Test
    fun extensionsCanBeBoughtDisabledEnabledAndRemoved() {
        val bought = Shop.buyExtension(Inventory(), 500, Extension.AUTO_RELOAD)
        assertTrue(bought.success)
        assertEquals(450, bought.coins)
        assertTrue(bought.inventory.extensionEnabled(Extension.AUTO_RELOAD))

        val disabled = Shop.toggleExtension(bought.inventory, 450, Extension.AUTO_RELOAD)
        assertTrue(disabled.success)
        assertEquals(
            ExtensionState.DISABLED,
            disabled.inventory.extensionState(Extension.AUTO_RELOAD)
        )
        assertFalse(disabled.inventory.extensionEnabled(Extension.AUTO_RELOAD))
        assertEquals(450, disabled.coins)

        val enabled = Shop.toggleExtension(disabled.inventory, 450, Extension.AUTO_RELOAD)
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
        val result = Shop.buyExtension(Inventory(), 99, Extension.AUTO_COVER)
        assertFalse(result.success)
        assertEquals("notEnoughCoins", result.reasonKey)
        assertEquals(99, result.coins)
    }

    @Test
    fun extensionPricesMatchTheShop() {
        assertEquals(50, Extension.AUTO_RELOAD.price)
        assertEquals(80, Extension.AUTO_MEDKIT.price)
        assertEquals(100, Extension.AUTO_COVER.price)
        assertEquals(100, Extension.AUTO_GLUE_WALL.price)
    }

    @Test
    fun theRememberedWeaponIsOnlyUsedWhenItIsStillOwned() {
        assertNull(Inventory(lastWeaponId = Weapon.SWORD.id).rememberedWeapon())
        val owned = Shop.buyWeapon(Inventory(), 400, Weapon.SWORD).inventory
            .copy(lastWeaponId = Weapon.SWORD.id)
        assertEquals(Weapon.SWORD, owned.rememberedWeapon())
        assertEquals(Weapon.SWORD, owned.startingWeapon())
    }
}
