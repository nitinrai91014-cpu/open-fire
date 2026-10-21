package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.Revive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the second-chance rules and the devil's three survival items.
 */
class ReviveTest {

    @Test
    fun firstZeroHealthGrantsHalfHealthAndAllThreeItems() {
        val result = Revive.devilAtZero(maxHp = 200, itemsLeft = 0, secondChanceUsed = false)
        assertEquals(Revive.Outcome.SECOND_CHANCE, result.outcome)
        assertEquals(100, result.hp)
        assertEquals(3, result.itemsLeft)
        assertTrue(result.secondChanceUsed)
        assertEquals(-1, result.itemIndex)
    }

    @Test
    fun eachLaterZeroConsumesExactlyOneItem() {
        val gloo = Revive.devilAtZero(200, 3, true)
        assertEquals(Revive.Outcome.USED_ITEM, gloo.outcome)
        assertEquals(0, gloo.itemIndex) // Gloo Wall
        assertEquals(30, gloo.hp)
        assertEquals(2, gloo.itemsLeft)

        val cover = Revive.devilAtZero(200, 2, true)
        assertEquals(1, cover.itemIndex) // cover
        assertEquals(30, cover.hp)
        assertEquals(1, cover.itemsLeft)

        val medkit = Revive.devilAtZero(200, 1, true)
        assertEquals(2, medkit.itemIndex) // medkit
        assertEquals(30, medkit.hp)
        assertEquals(0, medkit.itemsLeft)
    }

    @Test
    fun theDevilDiesOnlyAfterTheThreeItemsAreExhausted() {
        val dead = Revive.devilAtZero(200, 0, true)
        assertEquals(Revive.Outcome.DEFEATED, dead.outcome)
        assertEquals(0, dead.hp)
        assertEquals(0, dead.itemsLeft)
    }

    @Test
    fun itemHealingScalesWithMaximumHealth() {
        assertEquals(60, Revive.devilAtZero(400, 3, true).hp)
        assertEquals(120, Revive.devilAtZero(800, 3, true).hp)
        assertEquals(400, Revive.devilAtZero(800, 0, false).hp)
    }

    @Test
    fun fleeOnlyWhenBelowTheHalfHealthPoolAndAvailable() {
        assertTrue(Revive.shouldDevilFlee(hp = 99, maxHp = 200, fleeAvailable = true))
        assertTrue(Revive.shouldDevilFlee(hp = 1, maxHp = 200, fleeAvailable = true))
        assertFalse(Revive.shouldDevilFlee(hp = 100, maxHp = 200, fleeAvailable = true))
        assertFalse(Revive.shouldDevilFlee(hp = 250, maxHp = 200, fleeAvailable = true))
        assertFalse(Revive.shouldDevilFlee(hp = 50, maxHp = 200, fleeAvailable = false))
        assertFalse(Revive.shouldDevilFlee(hp = 0, maxHp = 200, fleeAvailable = true))
    }
}
