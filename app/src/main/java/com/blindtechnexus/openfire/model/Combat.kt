package com.blindtechnexus.openfire.model

import kotlin.math.roundToInt

/**
 * The four proximity zones that decide how much of a weapon's power lands.
 *
 * The arena is exactly [Combat.MAX_RANGE] metres across. Proximity is the
 * closeness of the target as a percentage of that range (100% = point blank,
 * 0% = the far edge of the arena).
 */
enum class HitZone {
    /** 0% - 40% proximity: the shot misses completely. */
    MISS,
    /** 40% - 70% proximity: half of the weapon's power. */
    LIGHT,
    /** 70% - 90% proximity: 80% of the weapon's power. */
    STRONG,
    /** 90% - 100% proximity: the full weapon's power. */
    HEAVY
}

/**
 * Standard combat rules shared by the player and the Robert.
 *
 * Zone rules (proximity = how close the target is, in percent):
 *  - 0%  to 40%  -> miss (no damage at all)
 *  - 40% to 70%  -> 50% of the weapon power
 *  - 70% to 90%  -> 80% of the weapon power
 *  - 90% to 100% -> 100% of the weapon power
 *
 * Weapon powers (full power at 90%-100%): assault gun 10, sniper rifle 9,
 * knife 8, sword 12, pistol 15, short gun 6 (10 in the Robert's hands).
 *
 * There is no preset where a combatant loses after a fixed number of hits:
 * a combatant is defeated only when their health actually reaches zero.
 *
 * Three consecutive enemy hits received inside the 90%-100% band damage the
 * weapon in the player's hands. A damaged weapon cannot fire until it is
 * repaired by the Recovery action, which costs [GUN_RECOVERY_COST] coins.
 */
object Combat {
    /** Full combat area width in metres (the arena is a 50 m range). */
    const val MAX_RANGE = 50f

    const val MISS_ZONE_MAX = 40f
    const val LIGHT_ZONE_MAX = 70f
    const val STRONG_ZONE_MAX = 90f
    const val HEAVY_ZONE_MIN = 90f

    /** Fraction of the weapon power that lands in each zone. */
    const val LIGHT_DAMAGE_FRACTION = 0.50f
    const val STRONG_DAMAGE_FRACTION = 0.80f
    const val HEAVY_DAMAGE_FRACTION = 1.00f

    /** Point-blank hits in a row needed to damage the weapon in hand. */
    const val HITS_TO_DAMAGE_GUN = 3

    /** Coins required to repair every damaged weapon. */
    const val GUN_RECOVERY_COST = 300

    /** Fraction of maximum health granted by the single second chance. */
    const val SECOND_CHANCE_FRACTION = 0.5f

    /** A cover blocks this many hits; the next (17th) hit breaks it. */
    const val COVER_MAX_HITS = 16

    /** Sniper hits at 90%-100% proximity needed to break a glue wall. */
    const val GLUE_WALL_BREAK_HITS = 3

    /** Only a sniper shot at or above this proximity can damage a glue wall. */
    const val GLUE_WALL_BREAK_PROXIMITY = 90f

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
     * Damage of a weapon with the given [power] at the given proximity.
     * Misses deal 0, injuries exactly half of the power, strong hits 80% and
     * point-blank hits the full power (rounded, with a minimum of 1 so a
     * connected hit is never fully ignored).
     */
    fun damageFor(power: Int, proximityPercent: Float): Int = when (zoneFor(proximityPercent)) {
        HitZone.MISS -> 0
        HitZone.LIGHT -> (power * LIGHT_DAMAGE_FRACTION).roundToInt().coerceAtLeast(1)
        HitZone.STRONG -> (power * STRONG_DAMAGE_FRACTION).roundToInt().coerceAtLeast(1)
        HitZone.HEAVY -> (power * HEAVY_DAMAGE_FRACTION).roundToInt().coerceAtLeast(1)
    }

    /** Damage dealt by a specific weapon at the given proximity. */
    fun weaponDamage(weapon: Weapon, proximityPercent: Float, enemyPower: Int? = null): Int =
        damageFor(enemyPower ?: weapon.power, proximityPercent)

    /** True when this shot can damage a glue wall (sniper rifle, point blank). */
    fun canBreakGlueWall(weapon: Weapon, proximityPercent: Float): Boolean =
        weapon == Weapon.SNIPER && proximityPercent >= GLUE_WALL_BREAK_PROXIMITY

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

    /** True when [streak] consecutive point-blank hits damage the weapon. */
    fun gunBreaks(streak: Int): Boolean = streak >= HITS_TO_DAMAGE_GUN
}
