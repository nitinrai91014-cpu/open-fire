package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.FireRange
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.model.RobertRules
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the zone multipliers, the weapon power table and the defence rules.
 *
 *  - 0%   to 40%  -> miss
 *  - 40%  to 70%  -> 50% of the power
 *  - 70%  to 90%  -> 80% of the power
 *  - 90%  to 100% -> full power
 *
 * Weapon power is a percentage of the victim's maximum health: knife 15,
 * sword 20, assault gun 18, pistol 22, sniper rifle 25 and short gun 10
 * (18 at 90%-100% proximity). The Robert uses exactly the same table.
 */
class CombatTest {

    @Test
    fun zoneBoundaries() {
        assertEquals(HitZone.MISS, Combat.zoneFor(0f))
        assertEquals(HitZone.MISS, Combat.zoneFor(39.9f))
        assertEquals(HitZone.LIGHT, Combat.zoneFor(40f))
        assertEquals(HitZone.LIGHT, Combat.zoneFor(69.9f))
        assertEquals(HitZone.STRONG, Combat.zoneFor(70f))
        assertEquals(HitZone.STRONG, Combat.zoneFor(89.9f))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(90f))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(100f))
    }

    @Test
    fun missesDealNoDamage() {
        assertEquals(0, Combat.damageFor(20, 0f, 300))
        assertEquals(0, Combat.damageFor(20, 39.9f, 300))
    }

    @Test
    fun lightHitsDealHalfThePower() {
        // 300 health at 20% is 60 damage, so half of it is 30.
        assertEquals(30, Combat.damageFor(20, 40f, 300))
        assertEquals(30, Combat.damageFor(20, 69.9f, 300))
        assertEquals(15, Combat.damageFor(10, 50f, 300))
        // 300 health at 25% is 75 damage, half of it rounds up to 38.
        assertEquals(38, Combat.damageFor(25, 60f, 300))
    }

    @Test
    fun strongHitsDealEightyPercentOfThePower() {
        assertEquals(48, Combat.damageFor(20, 70f, 300))
        assertEquals(48, Combat.damageFor(20, 89.9f, 300))
        assertEquals(24, Combat.damageFor(10, 80f, 300))
        assertEquals(60, Combat.damageFor(25, 75f, 300))
    }

    @Test
    fun pointBlankHitsDealTheFullPower() {
        assertEquals(60, Combat.damageFor(20, 90f, 300))
        assertEquals(60, Combat.damageFor(20, 100f, 300))
        assertEquals(75, Combat.damageFor(25, 95f, 300))
        assertEquals(30, Combat.damageFor(10, 92f, 300))
    }

    @Test
    fun damageScalesWithTheRoundHealth() {
        // The same weapon and the same proximity scale with the fighter's health.
        assertEquals(60, Combat.damageFor(20, 100f, 300))
        assertEquals(80, Combat.damageFor(20, 100f, 400))
        assertEquals(150, Combat.damageFor(20, 100f, 750))
    }

    @Test
    fun connectedHitsNeverDealZeroAndDeadFightersTakeNothing() {
        assertEquals(1, Combat.damageFor(1, 50f, 100))
        assertEquals(1, Combat.damageFor(1, 75f, 100))
        assertEquals(0, Combat.damageFor(20, 100f, 0))
    }

    @Test
    fun weaponPowersMatchTheRequestedTable() {
        assertEquals(15, Weapon.KNIFE.powerPercent)
        assertEquals(20, Weapon.SWORD.powerPercent)
        assertEquals(18, Weapon.ASSAULT_GUN.powerPercent)
        assertEquals(22, Weapon.PISTOL.powerPercent)
        assertEquals(25, Weapon.SNIPER.powerPercent)
        assertEquals(10, Weapon.SHORT_GUN.powerPercent)
    }

    @Test
    fun theShortGunIsOnlyStrongAtPointBlank() {
        assertEquals(10, Combat.powerPercentOf(Weapon.SHORT_GUN, 50f))
        assertEquals(10, Combat.powerPercentOf(Weapon.SHORT_GUN, 89.9f))
        assertEquals(18, Combat.powerPercentOf(Weapon.SHORT_GUN, 90f))
        assertEquals(18, Combat.powerPercentOf(Weapon.SHORT_GUN, 100f))
        // Every other weapon keeps its own value at point blank.
        assertEquals(25, Combat.powerPercentOf(Weapon.SNIPER, 100f))
        assertEquals(15, Combat.powerPercentOf(Weapon.KNIFE, 100f))
    }

    @Test
    fun theEnemyUsesExactlyTheSamePowerTable() {
        for (weapon in Weapon.entries) {
            for (proximity in listOf(10f, 50f, 80f, 95f)) {
                assertEquals(
                    "Robert must use the player's power for ${weapon.id}",
                    Combat.powerPercentOf(weapon, proximity),
                    RobertRules.powerFor(weapon, proximity)
                )
            }
        }
        assertEquals(18, RobertRules.powerFor(Weapon.SHORT_GUN, 95f))
        assertEquals(10, RobertRules.powerFor(Weapon.SHORT_GUN, 50f))
    }

    @Test
    fun weaponDamageCombinesTheTableAndTheZone() {
        assertEquals(45, Combat.weaponDamage(Weapon.KNIFE, 100f, 300))
        assertEquals(60, Combat.weaponDamage(Weapon.SWORD, 100f, 300))
        assertEquals(75, Combat.weaponDamage(Weapon.SNIPER, 100f, 300))
        assertEquals(66, Combat.weaponDamage(Weapon.PISTOL, 100f, 300))
        assertEquals(54, Combat.weaponDamage(Weapon.ASSAULT_GUN, 100f, 300))
        // Short gun: 18% at point blank, but only 10% in the strong band.
        assertEquals(54, Combat.weaponDamage(Weapon.SHORT_GUN, 100f, 300))
        assertEquals(24, Combat.weaponDamage(Weapon.SHORT_GUN, 80f, 300))
        assertEquals(15, Combat.weaponDamage(Weapon.SHORT_GUN, 50f, 300))
        assertEquals(0, Combat.weaponDamage(Weapon.SWORD, 20f, 300))
    }

    @Test
    fun proximityUsesTheFiftyMeterArena() {
        assertEquals(0f, Combat.proximityOf(50f), 0.001f)
        assertEquals(40f, Combat.proximityOf(30f), 0.001f)
        assertEquals(70f, Combat.proximityOf(15f), 0.001f)
        assertEquals(90f, Combat.proximityOf(5f), 0.001f)
        assertEquals(100f, Combat.proximityOf(0f), 0.001f)
        assertEquals(0f, Combat.proximityOf(80f), 0.001f)
        assertEquals(100f, Combat.proximityOf(-20f), 0.001f)
    }

    @Test
    fun distanceAndProximityAreInverseOperations() {
        assertEquals(40f, Combat.distanceFor(20f), 0.001f)
        assertEquals(22.5f, Combat.distanceFor(55f), 0.001f)
        assertEquals(2.5f, Combat.distanceFor(95f), 0.001f)
        assertEquals(0f, Combat.distanceFor(100f), 0.001f)
        assertEquals(50f, Combat.distanceFor(0f), 0.001f)
        for (proximity in listOf(0f, 15f, 40f, 55f, 70f, 95f, 100f)) {
            assertEquals(proximity, Combat.proximityOf(Combat.distanceFor(proximity)), 0.001f)
        }
    }

    @Test
    fun theFiringBandsMatchTheRequestedRanges() {
        assertEquals(0f, FireRange.FAR.minProximity, 0.001f)
        assertEquals(40f, FireRange.FAR.maxProximity, 0.001f)
        assertEquals(40f, FireRange.MID.minProximity, 0.001f)
        assertEquals(70f, FireRange.MID.maxProximity, 0.001f)
        assertEquals(90f, FireRange.CLOSE.minProximity, 0.001f)
        assertEquals(100f, FireRange.CLOSE.maxProximity, 0.001f)

        // Every band always produces the zone its name promises.
        assertEquals(HitZone.MISS, Combat.zoneFor(FireRange.FAR.midProximity))
        assertEquals(HitZone.LIGHT, Combat.zoneFor(FireRange.MID.midProximity))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(FireRange.CLOSE.midProximity))

        assertEquals(40f, FireRange.FAR.targetDistance, 0.001f)
        assertEquals(22.5f, FireRange.MID.targetDistance, 0.001f)
        assertEquals(2.5f, FireRange.CLOSE.targetDistance, 0.001f)
    }

    @Test
    fun anUnknownBandFallsBackToTheMiddleBand() {
        assertEquals(FireRange.FAR, FireRange.fromId("far"))
        assertEquals(FireRange.MID, FireRange.fromId("mid"))
        assertEquals(FireRange.CLOSE, FireRange.fromId("close"))
        assertEquals(FireRange.MID, FireRange.fromId(null))
        assertEquals(FireRange.MID, FireRange.fromId("nowhere"))
    }

    @Test
    fun secondChanceRestoresExactlyHalfHealth() {
        assertEquals(150, Combat.secondChanceHealth(300))
        assertEquals(200, Combat.secondChanceHealth(400))
        assertEquals(375, Combat.secondChanceHealth(750))
        assertEquals(1, Combat.secondChanceHealth(1))
    }

    @Test
    fun onlySniperShotsAtPointBlankCanBreakAGlueWall() {
        assertTrue(Combat.canBreakGlueWall(Weapon.SNIPER, 90f))
        assertTrue(Combat.canBreakGlueWall(Weapon.SNIPER, 100f))
        assertFalse(Combat.canBreakGlueWall(Weapon.SNIPER, 89.9f))
        assertFalse(Combat.canBreakGlueWall(Weapon.ASSAULT_GUN, 100f))
        assertFalse(Combat.canBreakGlueWall(Weapon.PISTOL, 100f))
        assertEquals(3, Combat.GLUE_WALL_BREAK_HITS)
    }

    @Test
    fun coverStopsElevenHitsAndBreaksOnTheTwelfth() {
        assertEquals(11, Combat.COVER_MAX_HITS)
        var hits = 0
        var broken = false
        repeat(Combat.COVER_MAX_HITS + 1) {
            hits++
            if (hits > Combat.COVER_MAX_HITS) broken = true
        }
        assertEquals(12, hits)
        assertTrue("The twelfth hit must break the cover", broken)
    }

    @Test
    fun recoveryCostsOneHundredCoins() {
        assertEquals(100, Combat.GUN_RECOVERY_COST)
    }

    @Test
    fun threeConsecutivePointBlankHitsDamageTheWeapon() {
        var streak = 0
        streak = Combat.nextHeavyHitStreak(streak, 95f)
        assertEquals(1, streak)
        assertFalse(Combat.gunBreaks(streak))
        streak = Combat.nextHeavyHitStreak(streak, 90f)
        streak = Combat.nextHeavyHitStreak(streak, 100f)
        assertTrue(Combat.gunBreaks(streak))
        assertEquals(0, Combat.nextHeavyHitStreak(streak, 80f))
    }

    @Test
    fun defencePricesAreFixed() {
        assertEquals(200, DefenceItem.MEDKIT.price)
        assertEquals(400, DefenceItem.GLUE_WALL.price)
        assertEquals(350, DefenceItem.COVER.price)
    }
}
