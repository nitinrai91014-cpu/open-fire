package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.RobertRules
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the round-by-round Robert rules. */
class RobertTest {

    @Test
    fun healthLadderMatchesTheRounds() {
        assertEquals(300, RobertRules.hpForRound(1))
        assertEquals(400, RobertRules.hpForRound(2))
        assertEquals(500, RobertRules.hpForRound(3))
        assertEquals(600, RobertRules.hpForRound(4))
        assertEquals(750, RobertRules.hpForRound(5))
        assertEquals(300, RobertRules.hpForRound(9))
    }

    @Test
    fun roundOneLoadout() {
        val profile = RobertRules.profile(1)
        assertEquals(1, profile.medikits)
        assertEquals(0, profile.glueWalls)
        assertEquals(1, profile.covers)
        assertEquals(listOf(Weapon.SHORT_GUN, Weapon.SNIPER), profile.weapons)
        assertTrue(profile.sniperLocked)
        assertEquals(2, profile.fleeAfterHits)
    }

    @Test
    fun roundTwoLoadout() {
        val profile = RobertRules.profile(2)
        assertEquals(2, profile.medikits)
        assertEquals(1, profile.glueWalls)
        assertEquals(1, profile.covers)
        assertTrue(profile.weapons.contains(Weapon.ASSAULT_GUN))
        assertTrue(profile.sniperLocked)
    }

    @Test
    fun roundThreeAddsTheKnifeAndUnlocksTheSniper() {
        val profile = RobertRules.profile(3)
        assertEquals(4, profile.medikits)
        assertEquals(2, profile.glueWalls)
        assertEquals(3, profile.covers)
        assertTrue(profile.weapons.contains(Weapon.KNIFE))
        assertFalse(profile.sniperLocked)
        assertEquals(3, profile.fleeAfterHits)
    }

    @Test
    fun roundFourAddsTheSword() {
        val profile = RobertRules.profile(4)
        assertEquals(5, profile.medikits)
        assertEquals(3, profile.glueWalls)
        assertEquals(3, profile.covers)
        assertTrue(profile.weapons.contains(Weapon.SWORD))
        assertEquals(4, profile.fleeAfterHits)
    }

    @Test
    fun roundFiveIsTheGiantWithEveryWeapon() {
        val profile = RobertRules.profile(5)
        assertEquals(6, profile.medikits)
        assertEquals(5, profile.glueWalls)
        assertEquals(7, profile.covers)
        assertEquals(Weapon.entries.toSet(), profile.weapons.toSet())
        assertEquals(5, profile.fleeAfterHits)
    }

    @Test
    fun lockedSniperNeedsAGlueWallAndHealthUnderOneHundred() {
        val profile = RobertRules.profile(1)
        assertFalse(RobertRules.sniperAllowed(profile, playerGlueWallActive = false, robertHp = 50))
        assertFalse(RobertRules.sniperAllowed(profile, playerGlueWallActive = true, robertHp = 150))
        assertTrue(RobertRules.sniperAllowed(profile, playerGlueWallActive = true, robertHp = 99))
        assertTrue(RobertRules.sniperAllowed(profile, playerGlueWallActive = true, robertHp = 1))
    }

    @Test
    fun theSniperIsAlwaysAllowedFromRoundThree() {
        val profile = RobertRules.profile(3)
        assertTrue(RobertRules.sniperAllowed(profile, playerGlueWallActive = false, robertHp = 500))
    }

    @Test
    fun allowedWeaponsRespectTheLockedSniper() {
        val locked = RobertRules.profile(1)
        val withoutWall = RobertRules.allowedWeapons(locked, false, 300)
        assertFalse(withoutWall.contains(Weapon.SNIPER))
        val withWall = RobertRules.allowedWeapons(locked, true, 90)
        assertTrue(withWall.contains(Weapon.SNIPER))
    }

    @Test
    fun defencesStartBelowSixtyHealthAndAlternate() {
        assertFalse(RobertRules.shouldUseDefence(60, true))
        assertTrue(RobertRules.shouldUseDefence(59, true))
        assertFalse(RobertRules.shouldUseDefence(59, false))
        assertEquals(DefenceItem.MEDKIT, RobertRules.nextDefence(1, 1, 1))
        assertEquals(DefenceItem.COVER, RobertRules.nextDefence(0, 1, 1))
        assertEquals(DefenceItem.GLUE_WALL, RobertRules.nextDefence(0, 0, 1))
        assertEquals(null, RobertRules.nextDefence(0, 0, 0))
    }

    @Test
    fun fleeingNeedsContinuousHits() {
        assertFalse(RobertRules.shouldFlee(1, 2))
        assertTrue(RobertRules.shouldFlee(2, 2))
        assertTrue(RobertRules.shouldFlee(3, 3))
        assertFalse(RobertRules.shouldFlee(2, 3))
    }
}
