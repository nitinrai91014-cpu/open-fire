package com.blindtechnexus.openfire.model

import kotlin.math.roundToInt

/**
 * The three proximity zones that decide the outcome of a shot.
 */
enum class HitZone {
    /** 0% - 40% proximity: the shot misses completely. */
    MISS,
    /** 40% - 70% proximity: light hit, 10% of max health. */
    LIGHT,
    /** 70% - 100% proximity: proper hit, 20% of max health. */
    HEAVY
}

/**
 * Standardised combat rules shared by the player and the devil.
 *
 * Zone rules (proximity = how close the target is, in percent):
 *  - 0% to 40%   -> miss (no damage at all)
 *  - 40% to 70%  -> the target is injured for 10% of its max health
 *  - 70% to 100% -> a proper hit for 20% of its max health
 *
 * There is no preset where a combatant loses after a fixed number of hits:
 * a combatant is defeated only when their health actually reaches zero.
 */
object Combat {
    const val LIGHT_ZONE_MIN = 40f
    const val HEAVY_ZONE_MIN = 70f
    const val LIGHT_DAMAGE_FRACTION = 0.10f
    const val HEAVY_DAMAGE_FRACTION = 0.20f

    /** Converts a distance (100 = far away, 0 = point-blank) into 0..100% proximity. */
    fun proximityOf(distance: Float): Float = (100f - distance).coerceIn(0f, 100f)

    /** Returns the hit zone for the given proximity percentage. */
    fun zoneFor(proximityPercent: Float): HitZone = when {
        proximityPercent >= HEAVY_ZONE_MIN -> HitZone.HEAVY
        proximityPercent >= LIGHT_ZONE_MIN -> HitZone.LIGHT
        else -> HitZone.MISS
    }

    /**
     * Exact zone damage for a combatant with [maxHp] max health.
     * Misses deal 0, light hits deal exactly 10%, proper hits exactly 20%
     * (rounded, with a minimum of 1 so a hit is never fully ignored).
     */
    fun damageFor(maxHp: Int, proximityPercent: Float): Int = when (zoneFor(proximityPercent)) {
        HitZone.MISS -> 0
        HitZone.LIGHT -> (maxHp * LIGHT_DAMAGE_FRACTION).roundToInt().coerceAtLeast(1)
        HitZone.HEAVY -> (maxHp * HEAVY_DAMAGE_FRACTION).roundToInt().coerceAtLeast(1)
    }
}
