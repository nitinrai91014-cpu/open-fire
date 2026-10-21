package com.blindtechnexus.openfire.model

import kotlin.math.roundToInt

/**
 * The four proximity zones that decide the outcome of a shot.
 *
 * The arena is exactly [Combat.MAX_RANGE] metres across. Proximity is the
 * closeness of the target as a percentage of that arena range (100% = point
 * blank, 0% = the far edge of the arena).
 */
enum class HitZone {
    /** 0% - 40% proximity: the shot misses completely. */
    MISS,
    /** 40% - 70% proximity: injury, 10% of max health. */
    LIGHT,
    /** 70% - 90% proximity: strong hit, 15% of max health. */
    STRONG,
    /** 90% - 100% proximity: point-blank hit, 20% of max health. */
    HEAVY
}

/**
 * Standardised combat rules shared by the player and the devil.
 *
 * Zone rules (proximity = how close the target is, in percent):
 *  - 0%  to 40%  -> miss (no damage at all)
 *  - 40% to 70%  -> injury, 10% of max health
 *  - 70% to 90%  -> strong hit, 15% of max health
 *  - 90% to 100% -> point-blank hit, 20% of max health
 *
 * There is no preset where a combatant loses after a fixed number of hits:
 * a combatant is defeated only when their health actually reaches zero
 * (after their one second chance has been used).
 *
 * Three consecutive enemy hits received inside the 90%-100% band damage the
 * gun in the player's hands. A damaged gun cannot fire until it is repaired
 * by the Recovery action, which costs [GUN_RECOVERY_COST] coins.
 */
object Combat {
    /** Full combat area width in metres (the arena is a 50 m range). */
    const val MAX_RANGE = 50f

    const val MISS_ZONE_MAX = 40f
    const val LIGHT_ZONE_MAX = 70f
    const val STRONG_ZONE_MAX = 90f
    const val HEAVY_ZONE_MIN = 90f

    const val LIGHT_DAMAGE_FRACTION = 0.10f
    const val STRONG_DAMAGE_FRACTION = 0.15f
    const val HEAVY_DAMAGE_FRACTION = 0.20f

    /** Point-blank hits in a row needed to damage the gun in hand. */
    const val HITS_TO_DAMAGE_GUN = 3

    /** Coins required to repair every damaged gun. */
    const val GUN_RECOVERY_COST = 300

    /** Fraction of maximum health granted by the single second chance. */
    const val SECOND_CHANCE_FRACTION = 0.5f

    /** Converts a distance (50 = far edge, 0 = point-blank) into 0..100% proximity. */
    fun proximityOf(distance: Float): Float =
        ((1f - distance / MAX_RANGE) * 100f).coerceIn(0f, 100f)

    /** Returns the hit zone for the given proximity percentage. */
    fun zoneFor(proximityPercent: Float): HitZone = when {
        proximityPercent >= STRONG_ZONE_MAX -> HitZone.HEAVY
        proximityPercent >= LIGHT_ZONE_MAX -> HitZone.STRONG
        proximityPercent >= MISS_ZONE_MAX -> HitZone.LIGHT
        else -> HitZone.MISS
    }

    /**
     * Exact zone damage for a combatant with [maxHp] max health.
     * Misses deal 0, injuries exactly 10%, strong hits exactly 15% and
     * point-blank hits exactly 20% (rounded, with a minimum of 1 so a
     * connected hit is never fully ignored).
     */
    fun damageFor(maxHp: Int, proximityPercent: Float): Int = when (zoneFor(proximityPercent)) {
        HitZone.MISS -> 0
        HitZone.LIGHT -> (maxHp * LIGHT_DAMAGE_FRACTION).roundToInt().coerceAtLeast(1)
        HitZone.STRONG -> (maxHp * STRONG_DAMAGE_FRACTION).roundToInt().coerceAtLeast(1)
        HitZone.HEAVY -> (maxHp * HEAVY_DAMAGE_FRACTION).roundToInt().coerceAtLeast(1)
    }

    /** Health granted by the one-per-fight second chance. */
    fun secondChanceHealth(maxHp: Int): Int =
        (maxHp * SECOND_CHANCE_FRACTION).roundToInt().coerceAtLeast(1)

    /**
     * Advances the "consecutive point-blank hits received" streak.
     * A point-blank hit (90%-100% proximity) increments it; every other
     * outcome (miss, injury, strong hit) resets it to zero.
     */
    fun nextHeavyHitStreak(currentStreak: Int, proximityPercent: Float): Int =
        if (zoneFor(proximityPercent) == HitZone.HEAVY) currentStreak + 1 else 0

    /** True when [streak] consecutive point-blank hits damage the gun. */
    fun gunBreaks(streak: Int): Boolean = streak >= HITS_TO_DAMAGE_GUN
}
