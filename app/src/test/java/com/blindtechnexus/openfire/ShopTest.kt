package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.Extension
import com.blindtechnexus.openfire.model.ExtensionState
import com.blindtechnexus.openfire.model.Inventory
import com.blindtechnexus.openfire.model.Rewards
import com.blindtechnexus.openfire.model.Shop
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks purchases, unlocks, removals and consumption for the current catalog. */
class ShopTest {

    @Test
    fun defaultInventoryOwnsOnlyTheFreeWeapons() {
        val inventory = Inventory()
        assertEquals(Weapon.defaults(), inventory.ownedWeapons())
        assertEquals(setOf(Weapon.SHORT_GUN, Weapon.ASSAULT_GUN), inventory.ownedWeapons().toSet())
        for (weapon in Weapon.forSale()) {
            assertFalse(inventory.ownsWeapon(weapon))
        }
        for (item in DefenceItem.entries) {
            assertEquals(0, inventory.count(item))
        }
    }

    @Test
    fun weaponPricesMatchTheCurrentShop() {
        assertEquals(300, Weapon.KNIFE.price)
        assertEquals(405, Weapon.SWORD.price)
        assertEquals(499, Weapon.PISTOL.price)
        assertEquals(549, Weapon.SNIPER.price)
        assertEquals(600, Weapon.ATOMIC_BOMB.price)
        assertEquals(500, Weapon.MISSILE.price)
        assertTrue(Weapon.forSale().all { it.price > 0 })
        assertTrue(Weapon.defaults().all { it.price == 0 })
    }

    @Test
    fun buyingAWeaponDeductsCoinsAndAddsIt() {
        val result = Shop.buyWeapon(Inventory(), 1000, Weapon.PISTOL)
        assertTrue(result.success)
        assertEquals(501, result.coins)
        assertTrue(result.inventory.ownsWeapon(Weapon.PISTOL))
    }

    @Test
    fun buyingWithoutEnoughCoinsFailsWithoutChanges() {
        for (weapon in Weapon.forSale()) {
            val inventory = Inventory()
            val coins = weapon.price - 1
            val result = Shop.buyWeapon(inventory, coins, weapon)
            assertFalse(weapon.id, result.success)
            assertEquals(coins, result.coins)
            assertEquals("notEnoughCoins", result.reasonKey)
            assertEquals(inventory, result.inventory)
        }
    }

    @Test
    fun aWeaponCannotBeBoughtTwice() {
        val owned = Shop.buyWeapon(Inventory(), 2000, Weapon.SNIPER).inventory
        val again = Shop.buyWeapon(owned, 2000, Weapon.SNIPER)
        assertFalse(again.success)
        assertEquals("alreadyOwned", again.reasonKey)
        assertEquals(2000, again.coins)
        assertEquals(owned, again.inventory)
    }

    @Test
    fun boughtWeaponsCanBeRemovedButFreeOrUnownedWeaponsCannot() {
        val owned = Shop.buyWeapon(Inventory(), 1000, Weapon.SWORD).inventory
        val removed = Shop.removeWeapon(owned, 1000, Weapon.SWORD)
        assertTrue(removed.success)
        assertEquals(1000, removed.coins)
        assertFalse(removed.inventory.ownsWeapon(Weapon.SWORD))

        for (weapon in listOf(Weapon.SHORT_GUN, Weapon.ASSAULT_GUN, Weapon.PISTOL)) {
            val inventory = Inventory()
            val refused = Shop.removeWeapon(inventory, 1000, weapon)
            assertFalse(refused.success)
            assertEquals("cannotRemoveWeapon", refused.reasonKey)
            assertEquals(inventory, refused.inventory)
        }
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
    fun theRememberedWeaponIsOnlyUsedWhenItIsStillOwned() {
        assertNull(Inventory(lastWeaponId = Weapon.SWORD.id).rememberedWeapon())
        assertNull(Inventory(lastWeaponId = "removedWeapon").rememberedWeapon())
        val owned = Shop.buyWeapon(Inventory(), 1000, Weapon.SWORD).inventory
            .copy(lastWeaponId = Weapon.SWORD.id)
        assertEquals(Weapon.SWORD, owned.rememberedWeapon())
        assertEquals(Weapon.SWORD, owned.startingWeapon())
    }

    @Test
    fun defencesAreRepeatableAndDecreaseOnUse() {
        for (item in DefenceItem.entries) {
            var inventory = Inventory()
            var coins = item.price * 3
            repeat(3) {
                val bought = Shop.buyDefence(inventory, coins, item)
                assertTrue(item.id, bought.success)
                inventory = bought.inventory
                coins = bought.coins
            }
            assertEquals(3, inventory.count(item))
            assertEquals(0, coins)
            assertEquals(2, Shop.consume(inventory, item).count(item))
        }
    }

    @Test
    fun defencePricesAndAntiMissileUnlockMatchTheCurrentShop() {
        assertEquals(200, DefenceItem.MEDKIT.price)
        assertEquals(400, DefenceItem.GLUE_WALL.price)
        assertEquals(350, DefenceItem.COVER.price)
        assertEquals(350, DefenceItem.ANTI_MISSILE.price)
        assertEquals(3, DefenceItem.ANTI_MISSILE.unlockAfterRound)

        val locked = Shop.buyDefence(Inventory(), 1000, DefenceItem.ANTI_MISSILE, currentRound = 3)
        assertFalse(locked.success)
        assertEquals("unlockAfterRound3", locked.reasonKey)
        assertEquals(1000, locked.coins)
        assertEquals(0, locked.inventory.antiMissiles)

        val unlocked = Shop.buyDefence(Inventory(), 1000, DefenceItem.ANTI_MISSILE, currentRound = 4)
        assertTrue(unlocked.success)
        assertEquals(650, unlocked.coins)
        assertEquals(1, unlocked.inventory.antiMissiles)
    }

    @Test
    fun aDefenceCanNeverGoNegativeOrBeBoughtWithoutEnoughCoins() {
        for (item in DefenceItem.entries) {
            assertEquals(0, Shop.consume(Inventory(), item).count(item))
            assertEquals(0, Inventory().withCount(item, -5).count(item))
            val refused = Shop.buyDefence(Inventory(), item.price - 1, item)
            assertFalse(refused.success)
            assertEquals("notEnoughCoins", refused.reasonKey)
            assertEquals(item.price - 1, refused.coins)
            assertEquals(Inventory(), refused.inventory)
        }
    }

    @Test
    fun extensionsCanBeBoughtDisabledEnabledAndRemoved() {
        for (extension in Extension.entries) {
            val bought = Shop.buyExtension(Inventory(), 500, extension)
            assertTrue(bought.success)
            val remaining = 500 - extension.price
            assertEquals(remaining, bought.coins)
            assertTrue(bought.inventory.extensionEnabled(extension))

            val disabled = Shop.toggleExtension(bought.inventory, remaining, extension)
            assertTrue(disabled.success)
            assertEquals(ExtensionState.DISABLED, disabled.inventory.extensionState(extension))
            assertFalse(disabled.inventory.extensionEnabled(extension))
            assertEquals(remaining, disabled.coins)

            val enabled = Shop.toggleExtension(disabled.inventory, remaining, extension)
            assertTrue(enabled.inventory.extensionEnabled(extension))
            assertEquals(remaining, enabled.coins)
            val removed = Shop.removeExtension(enabled.inventory, remaining, extension)
            assertTrue(removed.success)
            assertEquals(ExtensionState.NOT_OWNED, removed.inventory.extensionState(extension))
            assertEquals(remaining, removed.coins)
        }
    }

    @Test
    fun extensionTransactionsRejectInsufficientFundsDuplicatesAndUnownedItems() {
        for (extension in Extension.entries) {
            val result = Shop.buyExtension(Inventory(), extension.price - 1, extension)
            assertFalse(result.success)
            assertEquals("notEnoughCoins", result.reasonKey)
            assertEquals(Inventory(), result.inventory)
            assertEquals(extension.price - 1, result.coins)

            val owned = Shop.buyExtension(Inventory(), 500, extension).inventory
            val duplicate = Shop.buyExtension(owned, 500, extension)
            assertFalse(duplicate.success)
            assertEquals("alreadyOwned", duplicate.reasonKey)
            assertEquals(owned, duplicate.inventory)
            assertEquals(500, duplicate.coins)

            val unownedToggle = Shop.toggleExtension(Inventory(), 500, extension)
            val unownedRemoval = Shop.removeExtension(Inventory(), 500, extension)
            for (refused in listOf(unownedToggle, unownedRemoval)) {
                assertFalse(refused.success)
                assertEquals("notOwned", refused.reasonKey)
                assertEquals(500, refused.coins)
                assertEquals(Inventory(), refused.inventory)
            }
        }
    }

    @Test
    fun extensionPricesMatchTheCurrentCatalog() {
        assertEquals(50, Extension.AUTO_RELOAD.price)
        assertEquals(80, Extension.AUTO_MEDKIT.price)
        assertEquals(100, Extension.AUTO_COVER.price)
        assertEquals(100, Extension.AUTO_GLUE_WALL.price)
    }

    @Test
    fun everyRoundGatedItemUnlocksOnlyAfterItsRequiredRound() {
        for (weapon in Weapon.forSale().filter { it.unlockAfterRound > 0 }) {
            val locked = Shop.buyWeapon(Inventory(), 2000, weapon, currentRound = weapon.unlockAfterRound)
            assertFalse(weapon.id, locked.success)
            assertEquals(Shop.unlockReasonKey(weapon.unlockAfterRound), locked.reasonKey)
            assertEquals(2000, locked.coins)
            assertEquals(Inventory(), locked.inventory)
            val unlocked = Shop.buyWeapon(Inventory(), 2000, weapon, currentRound = weapon.unlockAfterRound + 1)
            assertTrue(weapon.id, unlocked.success)
            assertTrue(unlocked.inventory.ownsWeapon(weapon))
        }
        for (item in DefenceItem.entries.filter { it.unlockAfterRound > 0 }) {
            val locked = Shop.buyDefence(Inventory(), 2000, item, currentRound = item.unlockAfterRound)
            assertFalse(item.id, locked.success)
            assertEquals(Shop.unlockReasonKey(item.unlockAfterRound), locked.reasonKey)
            assertEquals(2000, locked.coins)
            assertEquals(Inventory(), locked.inventory)
            val unlocked = Shop.buyDefence(Inventory(), 2000, item, currentRound = item.unlockAfterRound + 1)
            assertTrue(item.id, unlocked.success)
            assertEquals(1, unlocked.inventory.count(item))
        }
    }

    @Test
    fun rewardBundlesContainOnlySupportedDefencesAndCanBeGrantedWithoutCharge() {
        for (bundle in Rewards.LEVEL_REWARDS + Rewards.ROUND_REWARD) {
            val inventory = bundle.parts().fold(Inventory()) { current, (item, amount) ->
                Shop.grant(current, item, amount)
            }
            for (item in DefenceItem.entries) {
                assertEquals(bundle.count(item), inventory.count(item))
            }
        }
        val round = Rewards.ROUND_REWARD
        assertEquals(2, round.glueWalls)
        assertEquals(2, round.medikits)
        assertEquals(3, round.covers)
        assertEquals(7, round.total)
        assertTrue(Rewards.levelRewardsRespectTheGlueWallLimit())
        assertEquals(5, Rewards.LEVEL_REWARDS.size)
        assertEquals(Rewards.ROUND_REWARD, Rewards.forPromotion(false, true, kotlin.random.Random))
        assertTrue(Rewards.forPromotion(false, false, kotlin.random.Random).isEmpty)
        assertEquals(0, Shop.grant(Inventory(), DefenceItem.MEDKIT, -1).medikits)
    }
}
