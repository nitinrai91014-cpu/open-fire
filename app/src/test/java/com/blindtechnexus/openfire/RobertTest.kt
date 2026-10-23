package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.FireRange
import com.blindtechnexus.openfire.model.Progression
import com.blindtechnexus.openfire.model.RobertRules
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Locks Robert's level health, loadouts, special sniper rule and round speed. */
class RobertTest {

    @Test
    fun profileHealthMatchesRoundAndLevel() {
        assertEquals(200, RobertRules.profile(1, 1).maxHp)
        assertEquals(600, RobertRules.profile(1, 5).maxHp)
        assertEquals(700, RobertRules.profile(2, 1).maxHp)
        assertEquals(900, RobertRules.profile(2, 3).maxHp)
        assertEquals(2600, RobertRules.profile(5, 5).maxHp)
        assertEquals(Progression.hpFor(3, 4), RobertRules.profile(3, 4).maxHp)
    }

    @Test
    fun roundOneLoadoutHasAssaultShotgunAndConditionallyLockedSniper() {
        val profile = RobertRules.profile(1, 1)
        assertEquals(1, profile.medikits)
        assertEquals(0, profile.glueWalls)
        assertEquals(1, profile.covers)
        assertEquals(listOf(Weapon.ASSAULT_GUN, Weapon.SHORT_GUN, Weapon.SNIPER), profile.weapons)
        assertTrue(profile.sniperLocked)
        assertEquals(2, profile.fleeAfterHits)
    }

    @Test
    fun laterRoundLoadoutsRemainIntact() {
        val roundTwo = RobertRules.profile(2, 1)
        assertEquals(2, roundTwo.medikits)
        assertEquals(1, roundTwo.glueWalls)
        assertTrue(roundTwo.sniperLocked)

        val roundThree = RobertRules.profile(3, 1)
        assertEquals(4, roundThree.medikits)
        assertTrue(roundThree.weapons.contains(Weapon.KNIFE))
        assertFalse(roundThree.sniperLocked)
        assertEquals(3, roundThree.fleeAfterHits)

        val roundFour = RobertRules.profile(4, 1)
        assertTrue(roundFour.weapons.contains(Weapon.SWORD))
        assertEquals(4, roundFour.fleeAfterHits)

        val roundFive = RobertRules.profile(5, 1)
        assertEquals(Weapon.entries.toSet(), roundFive.weapons.toSet())
        assertEquals(5, roundFive.fleeAfterHits)
    }

    @Test
    fun lockedSniperNeedsAGlueWallAndHealthUnderOneHundred() {
        val profile = RobertRules.profile(1, 1)
        assertFalse(RobertRules.sniperAllowed(profile, false, 50))
        assertFalse(RobertRules.sniperAllowed(profile, true, 150))
        assertTrue(RobertRules.sniperAllowed(profile, true, 99))
        assertTrue(RobertRules.sniperAllowed(profile, true, 1))

        val withoutWall = RobertRules.allowedWeapons(profile, false, 200)
        assertFalse(withoutWall.contains(Weapon.SNIPER))
        val situationMet = RobertRules.allowedWeapons(profile, true, 90)
        assertTrue(situationMet.contains(Weapon.SNIPER))
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

    @Test
    fun movementGetsFasterInEveryRound() {
        var previous = 0f
        for (round in 1..5) {
            val speed = RobertRules.movementSpeedMultiplier(round)
            assertTrue("Round $round must be faster", speed > previous)
            previous = speed
        }
        assertEquals(1.2f, RobertRules.movementSpeedMultiplier(1), 0.0001f)
        assertEquals(2.0f, RobertRules.movementSpeedMultiplier(5), 0.0001f)
    }

    @Test
    fun heRandomlyPrefersTheMidOrTheCloseBand() {
        val random = Random(20261022)
        val picked = mutableSetOf<FireRange>()
        repeat(200) { picked.add(RobertRules.preferredRange(random)) }
        assertEquals(setOf(FireRange.MID, FireRange.CLOSE), picked)
        assertFalse(picked.contains(FireRange.FAR))
    }

    @Test
    fun deceptionTripsLastOneOrTwoShotsAndHappenSometimes() {
        val random = Random(99)
        val shots = mutableSetOf<Int>()
        repeat(200) { shots.add(RobertRules.deceptionShots(random)) }
        assertEquals(setOf(1, 2), shots)

        var deceptive = 0
        repeat(1000) {
            if (RobertRules.shouldStartDeception(random)) deceptive++
        }
        assertTrue(deceptive > 100)
        assertTrue(deceptive < 400)
    }

    @Test
    fun damagedWeaponOrGlueWallAlwaysPullsHimToPointBlank() {
        assertEquals(FireRange.CLOSE, RobertRules.fireRangeFor(FireRange.MID, true, false))
        assertEquals(FireRange.CLOSE, RobertRules.fireRangeFor(FireRange.FAR, true, false))
        assertEquals(FireRange.CLOSE, RobertRules.fireRangeFor(FireRange.MID, false, true))
        assertEquals(FireRange.MID, RobertRules.fireRangeFor(FireRange.MID, false, false))

        val proximity = Combat.proximityOf(FireRange.CLOSE.targetDistance)
        assertTrue(proximity >= Combat.HEAVY_ZONE_MIN)
        assertTrue(proximity <= Combat.FULL_PROXIMITY)
    }
}
