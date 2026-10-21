package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.HitZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the standardised four-zone combat rules inside the 50 m arena:
 *  - 0%   to 40%  -> miss
 *  - 40%  to 70%  -> injury, exactly 10% of max health
 *  - 70%  to 90%  -> strong hit, exactly 15% of max health
 *  - 90%  to 100% -> point-blank hit, exactly 20% of max health
 * and that defeat happens only when health actually reaches zero.
 */
class CombatTest {

    @Test
    fun zoneBoundaries() {
        assertEquals(HitZone.MISS, Combat.zoneFor(0f))
        assertEquals(HitZone.MISS, Combat.zoneFor(20f))
        assertEquals(HitZone.MISS, Combat.zoneFor(39.9f))
        assertEquals(HitZone.LIGHT, Combat.zoneFor(40f))
        assertEquals(HitZone.LIGHT, Combat.zoneFor(55.5f))
        assertEquals(HitZone.LIGHT, Combat.zoneFor(69.9f))
        assertEquals(HitZone.STRONG, Combat.zoneFor(70f))
        assertEquals(HitZone.STRONG, Combat.zoneFor(80f))
        assertEquals(HitZone.STRONG, Combat.zoneFor(89.9f))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(90f))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(95f))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(100f))
    }

    @Test
    fun missesDealNoDamage() {
        assertEquals(0, Combat.damageFor(200, 0f))
        assertEquals(0, Combat.damageFor(200, 39.9f))
    }

    @Test
    fun lightHitsDealExactlyTenPercent() {
        assertEquals(20, Combat.damageFor(200, 40f))
        assertEquals(20, Combat.damageFor(200, 69.9f))
        assertEquals(30, Combat.damageFor(300, 50f))
        assertEquals(50, Combat.damageFor(500, 45f))
    }

    @Test
    fun strongHitsDealExactlyFifteenPercent() {
        assertEquals(30, Combat.damageFor(200, 70f))
        assertEquals(30, Combat.damageFor(200, 89.9f))
        assertEquals(45, Combat.damageFor(300, 80f))
        assertEquals(75, Combat.damageFor(500, 75f))
        assertEquals(120, Combat.damageFor(800, 85f))
    }

    @Test
    fun pointBlankHitsDealExactlyTwentyPercent() {
        assertEquals(40, Combat.damageFor(200, 90f))
        assertEquals(40, Combat.damageFor(200, 100f))
        assertEquals(60, Combat.damageFor(300, 95f))
        assertEquals(100, Combat.damageFor(500, 92f))
        assertEquals(160, Combat.damageFor(800, 100f))
    }

    @Test
    fun smallHealthPoolsStillTakeDamage() {
        // Rounded damage never drops to zero for a connected hit.
        assertEquals(1, Combat.damageFor(5, 50f))
        assertEquals(1, Combat.damageFor(3, 90f))
    }

    @Test
    fun proximityUsesTheFiftyMeterArena() {
        assertEquals(0f, Combat.proximityOf(Combat.MAX_RANGE), 0.001f)
        assertEquals(0f, Combat.proximityOf(50f), 0.001f)
        assertEquals(20f, Combat.proximityOf(40f), 0.001f)
        assertEquals(40f, Combat.proximityOf(30f), 0.001f)
        assertEquals(70f, Combat.proximityOf(15f), 0.001f)
        assertEquals(90f, Combat.proximityOf(5f), 0.001f)
        assertEquals(100f, Combat.proximityOf(0f), 0.001f)
        // Out-of-range distances clamp into 0..100%
        assertEquals(0f, Combat.proximityOf(80f), 0.001f)
        assertEquals(100f, Combat.proximityOf(-20f), 0.001f)
    }

    @Test
    fun secondChanceRestoresExactlyHalfHealth() {
        assertEquals(100, Combat.secondChanceHealth(200))
        assertEquals(150, Combat.secondChanceHealth(300))
        assertEquals(250, Combat.secondChanceHealth(500))
        assertEquals(400, Combat.secondChanceHealth(800))
        // Tiny pools still get at least one point.
        assertEquals(1, Combat.secondChanceHealth(1))
    }

    @Test
    fun threeConsecutivePointBlankHitsDamageTheGun() {
        var streak = 0
        streak = Combat.nextHeavyHitStreak(streak, 95f)
        assertEquals(1, streak)
        assertFalse(Combat.gunBreaks(streak))
        streak = Combat.nextHeavyHitStreak(streak, 90f)
        assertEquals(2, streak)
        assertFalse(Combat.gunBreaks(streak))
        streak = Combat.nextHeavyHitStreak(streak, 100f)
        assertEquals(3, streak)
        assertTrue(Combat.gunBreaks(streak))
    }

    @Test
    fun lowerZoneHitsAndMissesResetThePointBlankStreak() {
        assertEquals(0, Combat.nextHeavyHitStreak(2, 80f))
        assertEquals(0, Combat.nextHeavyHitStreak(2, 50f))
        assertEquals(0, Combat.nextHeavyHitStreak(2, 10f))
    }

    @Test
    fun gunRecoveryCostsExactlyThreeHundredCoins() {
        assertEquals(300, Combat.GUN_RECOVERY_COST)
        assertEquals(3, Combat.HITS_TO_DAMAGE_GUN)
    }

    @Test
    fun deathOnlyWhenHealthReachesZero() {
        // A combatant with 200 HP struck by perfect 20% hits dies on the hit
        // that brings them to exactly zero — never earlier, never by preset.
        var hp = 200
        var hits = 0
        while (hp > 0) {
            hp = (hp - Combat.damageFor(200, 100f)).coerceAtLeast(0)
            hits++
        }
        assertEquals(0, hp)
        assertEquals(5, hits)

        // Strong hits: 200 / 30 = 7 hits (not 2!).
        hp = 200
        hits = 0
        while (hp > 0) {
            hp = (hp - Combat.damageFor(200, 80f)).coerceAtLeast(0)
            hits++
        }
        assertEquals(0, hp)
        assertEquals(7, hits)

        // Light hits take longer: 200 / 20 = 10 hits exactly.
        hp = 200
        hits = 0
        while (hp > 0) {
            hp = (hp - Combat.damageFor(200, 50f)).coerceAtLeast(0)
            hits++
        }
        assertEquals(0, hp)
        assertEquals(10, hits)
    }
}
