package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.BattleMenuItem
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.FireRange
import com.blindtechnexus.openfire.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the quick menu contract.
 *
 * The old "play" item was replaced by Recovery, which repairs every damaged
 * weapon for [Combat.GUN_RECOVERY_COST] coins.
 */
class BattleMenuTest {

    @Test
    fun theMenuRunsMedikitCoverGlueWallExitAndRecover() {
        assertEquals(
            listOf(
                BattleMenuItem.MEDKIT,
                BattleMenuItem.COVER,
                BattleMenuItem.GLUE_WALL,
                BattleMenuItem.EXIT,
                BattleMenuItem.RECOVER
            ),
            BattleMenuItem.entries
        )
    }

    @Test
    fun theOldPlayItemIsGone() {
        assertFalse(BattleMenuItem.entries.any { it.name == "PLAY" })
        assertEquals("recoverWeapon", BattleMenuItem.RECOVER.nameKey)
    }

    @Test
    fun recoverIsTheSafeDefaultSelection() {
        assertEquals(BattleMenuItem.RECOVER.ordinal, BattleMenuItem.RECOVER_INDEX)
        assertEquals(BattleMenuItem.RECOVER_INDEX, GameState().menuIndex)
        assertEquals(BattleMenuItem.RECOVER, GameState().menuItem)
        // Nothing is damaged in a fresh battle, so the default tap is free.
        assertFalse(GameState().anyWeaponDamaged)
        // Recovery is the cheap 100 coin action the menu item offers.
        assertEquals(100, Combat.GUN_RECOVERY_COST)
    }

    @Test
    fun cyclingWrapsAroundBothEnds() {
        assertEquals(BattleMenuItem.MEDKIT, BattleMenuItem.at(0))
        assertEquals(BattleMenuItem.RECOVER, BattleMenuItem.at(4))
        assertEquals(BattleMenuItem.MEDKIT, BattleMenuItem.at(5))
        assertEquals(BattleMenuItem.RECOVER, BattleMenuItem.at(-1))
        assertEquals(BattleMenuItem.EXIT, BattleMenuItem.at(-2))
        assertEquals(BattleMenuItem.GLUE_WALL, BattleMenuItem.at(-3))
    }

    @Test
    fun onlyTheThreeDefencesCarryAnItem() {
        assertEquals(DefenceItem.MEDKIT, BattleMenuItem.MEDKIT.defence)
        assertEquals(DefenceItem.COVER, BattleMenuItem.COVER.defence)
        assertEquals(DefenceItem.GLUE_WALL, BattleMenuItem.GLUE_WALL.defence)
        assertEquals(null, BattleMenuItem.EXIT.defence)
        assertEquals(null, BattleMenuItem.RECOVER.defence)
    }

    @Test
    fun aFreshBattleStartsInTheMiddleBandWithoutDeception() {
        val state = GameState()
        assertEquals(FireRange.MID.id, state.robertFireRange)
        assertEquals(FireRange.MID, state.robertRange)
        assertEquals(0, state.robertDeceptionShots)
        assertEquals("advance", state.robertState)
        assertFalse(state.robertFleeing)
        assertFalse(state.activeWeaponDamaged)
        // Head-on by default, with no missile activity in the air.
        assertEquals(0f, state.robertLateral, 0.0001f)
        assertFalse(state.missileInFlight)
        assertFalse(state.missileFromPlayer)
        assertFalse(state.missileInterceptPlanned)
        assertEquals(0, state.robertMissilePowers)
        assertEquals(0, state.robertAntiMissiles)
        assertEquals(0, state.robertExchangeShots)
    }

    @Test
    fun aDamagedWeaponIsReportedForTheActiveSlot() {
        val state = GameState().copy(
            weapons = listOf("shortGun", "assaultGun"),
            damaged = listOf(false, true),
            weaponIndex = 1
        )
        assertTrue(state.activeWeaponDamaged)
        assertTrue(state.anyWeaponDamaged)

        val switched = state.copy(weaponIndex = 0)
        assertFalse(switched.activeWeaponDamaged)
        assertTrue(switched.anyWeaponDamaged)

        val recovered = state.copy(damaged = listOf(false, false))
        assertFalse(recovered.anyWeaponDamaged)
    }
}
