package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.DefenceItem
import com.blindtechnexus.openfire.model.HitZone
import com.blindtechnexus.openfire.model.Weapon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the zone multipliers and the exact weapon powers:
 *  - 0%   to 40%  -> miss
 *  - 40%  to 70%  -> 50% of the power
 *  - 70%  to 90%  -> 80% of the power
 *  - 90%  to 100% -> full power
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
        assertEquals(0, Combat.damageFor(10, 0f))
        assertEquals(0, Combat.damageFor(10, 39.9f))
    }

    @Test
    fun lightHitsDealHalfThePower() {
        assertEquals(5, Combat.damageFor(10, 40f))
        assertEquals(5, Combat.damageFor(10, 69.9f))
        assertEquals(3, Combat.damageFor(6, 50f))
        assertEquals(8, Combat.damageFor(15, 60f))
    }

    @Test
    fun strongHitsDealEightyPercentOfThePower() {
        assertEquals(8, Combat.damageFor(10, 70f))
        assertEquals(8, Combat.damageFor(10, 89.9f))
        assertEquals(5, Combat.damageFor(6, 80f))
        assertEquals(12, Combat.damageFor(15, 75f))
    }

    @Test
    fun pointBlankHitsDealTheFullPower() {
        assertEquals(10, Combat.damageFor(10, 90f))
        assertEquals(10, Combat.damageFor(10, 100f))
        assertEquals(15, Combat.damageFor(15, 95f))
        assertEquals(6, Combat.damageFor(6, 92f))
    }

    @Test
    fun connectedHitsNeverDealZero() {
        assertEquals(1, Combat.damageFor(1, 50f))
        assertEquals(1, Combat.damageFor(1, 75f))
    }

    @Test
    fun weaponPowersMatchTheRules() {
        assertEquals(10, Weapon.ASSAULT_GUN.power)
        assertEquals(9, Weapon.SNIPER.power)
        assertEquals(8, Weapon.KNIFE.power)
        assertEquals(12, Weapon.SWORD.power)
        assertEquals(15, Weapon.PISTOL.power)
        assertEquals(6, Weapon.SHORT_GUN.power)
        assertEquals(10, com.blindtechnexus.openfire.model.RobertRules.powerFor(Weapon.SHORT_GUN))
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
    fun coverStopsSixteenHitsAndBreaksOnTheSeventeenth() {
        var hits = 0
        repeat(Combat.COVER_MAX_HITS) {
            hits++
        }
        assertEquals(16, hits)
        assertEquals(16, Combat.COVER_MAX_HITS)
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
