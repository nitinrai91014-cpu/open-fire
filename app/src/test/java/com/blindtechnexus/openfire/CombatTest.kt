package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.HitZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the standardised three-zone combat rules:
 *  - 0%   to 40%  -> miss
 *  - 40%  to 70%  -> light hit, exactly 10% of max health
 *  - 70%  to 100% -> proper hit, exactly 20% of max health
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
        assertEquals(HitZone.HEAVY, Combat.zoneFor(70f))
        assertEquals(HitZone.HEAVY, Combat.zoneFor(85f))
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
    fun properHitsDealExactlyTwentyPercent() {
        assertEquals(40, Combat.damageFor(200, 70f))
        assertEquals(40, Combat.damageFor(200, 100f))
        assertEquals(60, Combat.damageFor(300, 90f))
        assertEquals(100, Combat.damageFor(500, 75f))
        assertEquals(160, Combat.damageFor(800, 100f))
    }

    @Test
    fun smallHealthPoolsStillTakeDamage() {
        // Rounded damage never drops to zero for a connected hit.
        assertEquals(1, Combat.damageFor(5, 50f))
        assertEquals(1, Combat.damageFor(3, 90f))
    }

    @Test
    fun proximityMapsDistanceToPercent() {
        assertEquals(0f, Combat.proximityOf(100f), 0.001f)
        assertEquals(40f, Combat.proximityOf(60f), 0.001f)
        assertEquals(70f, Combat.proximityOf(30f), 0.001f)
        assertEquals(100f, Combat.proximityOf(0f), 0.001f)
        // Out-of-range distances clamp into 0..100%
        assertEquals(0f, Combat.proximityOf(130f), 0.001f)
        assertEquals(100f, Combat.proximityOf(-20f), 0.001f)
    }

    @Test
    fun deathOnlyWhenHealthReachesZero() {
        // A combatant with 200 HP struck by perfect 20% hits dies on the hit
        // that brings them to exactly zero — never earlier, never by preset.
        var hp = 200
        var hits = 0
        while (hp > 0) {
            val damage = Combat.damageFor(200, 100f)
            hp = (hp - damage).coerceAtLeast(0)
            hits++
        }
        assertEquals(0, hp)
        assertEquals(5, hits)

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
