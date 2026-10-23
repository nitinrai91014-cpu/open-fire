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
    fun roundOneLevelsOneAndTwoHaveOneCoverAndOneGlueWall() {
        for (level in listOf(1, 2)) {
            val profile = RobertRules.profile(1, level)
            assertEquals("level $level medikits", 0, profile.medikits)
            assertEquals("level $level glueWalls", 1, profile.glueWalls)
            assertEquals("level $level covers", 1, profile.covers)
            assertEquals(
                listOf(Weapon.ASSAULT_GUN, Weapon.SHORT_GUN, Weapon.SNIPER),
                profile.weapons
            )
            assertTrue(profile.sniperLocked)
            assertEquals(2, profile.fleeAfterHits)
            assertEquals(0, profile.missilePowers)
            assertEquals(0, profile.antiMissiles)
        }
    }

    @Test
    fun roundOneLevelsThreeToFiveHaveTwoMedkitsAndOneCover() {
        for (level in listOf(3, 4, 5)) {
            val profile = RobertRules.profile(1, level)
            assertEquals("level $level medikits", 2, profile.medikits)
            assertEquals("level $level glueWalls", 0, profile.glueWalls)
            assertEquals("level $level covers", 1, profile.covers)
            assertEquals(
                listOf(Weapon.ASSAULT_GUN, Weapon.SHORT_GUN, Weapon.SNIPER),
                profile.weapons
            )
            assertTrue(profile.sniperLocked)
        }
    }

    @Test
    fun roundTwoAddsKnifeAndSwordWithTheNewDefences() {
        val roundTwo = RobertRules.profile(2, 1)
        assertEquals(3, roundTwo.medikits)
        assertEquals(1, roundTwo.glueWalls)
        assertEquals(2, roundTwo.covers)
        assertTrue(roundTwo.weapons.contains(Weapon.KNIFE))
        assertTrue(roundTwo.weapons.contains(Weapon.SWORD))
        assertFalse(roundTwo.weapons.contains(Weapon.PISTOL))
        assertFalse(roundTwo.weapons.contains(Weapon.MISSILE))
        assertTrue(roundTwo.sniperLocked)
        assertEquals(0, roundTwo.missilePowers)
        assertEquals(0, roundTwo.antiMissiles)
        // The loadout is the same throughout the round.
        assertEquals(roundTwo.weapons, RobertRules.profile(2, 5).weapons)
        assertEquals(3, RobertRules.profile(2, 5).medikits)
    }

    @Test
    fun roundThreeAddsThePistolThroughoutTheRound() {
        val roundThree = RobertRules.profile(3, 1)
        assertEquals(3, roundThree.medikits)
        assertEquals(2, roundThree.glueWalls)
        assertEquals(4, roundThree.covers)
        assertTrue(roundThree.weapons.contains(Weapon.PISTOL))
        assertTrue(roundThree.weapons.contains(Weapon.KNIFE))
        assertEquals(RobertRules.ALL_STANDARD_WEAPONS.toSet(), roundThree.weapons.toSet())
        assertFalse(roundThree.sniperLocked)
        assertEquals(3, roundThree.fleeAfterHits)
        assertEquals(0, roundThree.missilePowers)
        assertEquals(4, RobertRules.profile(3, 5).covers)
    }

    @Test
    fun roundFourHasEveryWeaponOneMissileAndOneInterceptor() {
        val roundFour = RobertRules.profile(4, 1)
        assertEquals(5, roundFour.medikits)
        assertEquals(3, roundFour.glueWalls)
        assertEquals(5, roundFour.covers)
        assertEquals(RobertRules.ALL_STANDARD_WEAPONS.toSet(), roundFour.weapons.toSet())
        // The missile is a once-per-game special, not a random rotation weapon.
        assertFalse(roundFour.weapons.contains(Weapon.MISSILE))
        assertEquals(1, roundFour.missilePowers)
        assertEquals(1, roundFour.antiMissiles)
        assertEquals(4, roundFour.fleeAfterHits)
    }

    @Test
    fun roundFiveIsStrongerWithTwoInterceptors() {
        val roundFive = RobertRules.profile(5, 1)
        assertEquals(5, roundFive.medikits)
        assertEquals(4, roundFive.glueWalls)
        assertEquals(7, roundFive.covers)
        assertEquals(RobertRules.ALL_STANDARD_WEAPONS.toSet(), roundFive.weapons.toSet())
        assertFalse(roundFive.weapons.contains(Weapon.MISSILE))
        assertEquals(1, roundFive.missilePowers)
        assertEquals(2, roundFive.antiMissiles)
        assertEquals(5, roundFive.fleeAfterHits)
    }

    @Test
    fun situationalRangePushesAnAdvantagedRobertClose() {
        fun range(robertHp: Int, playerHp: Int) = RobertRules.situationalRange(
            stored = FireRange.MID,
            playerWeaponDamaged = false,
            playerGlueWallActive = false,
            robertHp = robertHp,
            robertMaxHp = 1000,
            playerHp = playerHp,
            playerMaxHp = 1000,
            deceptionShotsLeft = 0
        )
        // Big advantage: the 90%-100% band. Moderate: the 70%-90% band.
        val bigProx = Combat.proximityOf(range(900, 400).targetDistance)
        assertTrue(bigProx >= 90f && bigProx <= 100f)
        val moderateProx = Combat.proximityOf(range(800, 650).targetDistance)
        assertTrue(moderateProx >= 70f && moderateProx <= 90f)
        // Even fight keeps the stored band; losing pulls back to 40%-70%.
        assertEquals(FireRange.MID, range(600, 600))
        val losingProx = Combat.proximityOf(range(300, 800).targetDistance)
        assertTrue(losingProx >= 40f && losingProx <= 70f)
        // Forced point blank always wins, deception always finishes.
        assertEquals(
            FireRange.CLOSE,
            RobertRules.situationalRange(FireRange.FAR, true, false, 100, 1000, 900, 1000, 2)
        )
        assertEquals(
            FireRange.FAR,
            RobertRules.situationalRange(FireRange.FAR, false, false, 900, 1000, 100, 1000, 1)
        )
    }

    @Test
    fun situationalFireIntervalsAreNeverAFixedClock() {
        val random = Random(7)
        fun samples(situation: RobertRules.CombatSituation, fleeing: Boolean) =
            (1..60).map { RobertRules.fireIntervalMs(situation, fleeing, random) }

        val advantage = samples(RobertRules.CombatSituation.ADVANTAGE, false)
        assertTrue(advantage.all { it in 750L..1_150L })
        assertTrue("advantage fire must vary", advantage.distinct().size > 10)

        val pressure = samples(RobertRules.CombatSituation.PRESSURE, false)
        assertTrue(pressure.all { it in 620L..1_000L })

        val even = samples(RobertRules.CombatSituation.EVEN, false)
        assertTrue(even.all { it in 1_400L..2_200L })
        assertTrue("even fire must vary", even.distinct().size > 10)

        val disadvantage = samples(RobertRules.CombatSituation.DISADVANTAGE, false)
        assertTrue(disadvantage.all { it in 2_000L..3_000L })

        // Advantage always fires faster than even, even faster than losing.
        assertTrue(advantage.max() < even.min())
        assertTrue(even.max() <= disadvantage.max())
        // While fleeing he barely fires at all.
        assertTrue(samples(RobertRules.CombatSituation.ADVANTAGE, true).all { it >= 2_200L })
    }

    @Test
    fun aOneSidedExchangeMakesHimRunAway() {
        // Two or three sustained hits against one or two answering shots.
        assertTrue(RobertRules.shouldFleeFromExchange(2, 1, 55f))
        assertTrue(RobertRules.shouldFleeFromExchange(3, 2, 95f))
        assertTrue(RobertRules.shouldFleeFromExchange(2, 0, 40f))
        // Not after a single hit, not when he is answering properly, and
        // never for hits that could not hurt him (the 0%-40% miss band).
        assertFalse(RobertRules.shouldFleeFromExchange(1, 1, 95f))
        assertFalse(RobertRules.shouldFleeFromExchange(2, 3, 95f))
        assertFalse(RobertRules.shouldFleeFromExchange(3, 1, 20f))
    }

    @Test
    fun situationAssessmentFollowsTheHealthGap() {
        assertEquals(
            RobertRules.CombatSituation.PRESSURE,
            RobertRules.assessSituation(100, 1000, 900, 1000, playerWeaponDamaged = true)
        )
        assertEquals(
            RobertRules.CombatSituation.ADVANTAGE,
            RobertRules.assessSituation(900, 1000, 500, 1000, playerWeaponDamaged = false)
        )
        assertEquals(
            RobertRules.CombatSituation.EVEN,
            RobertRules.assessSituation(600, 1000, 550, 1000, playerWeaponDamaged = false)
        )
        assertEquals(
            RobertRules.CombatSituation.DISADVANTAGE,
            RobertRules.assessSituation(300, 1000, 800, 1000, playerWeaponDamaged = false)
        )
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
