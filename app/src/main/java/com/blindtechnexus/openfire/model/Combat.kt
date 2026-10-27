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
 * The three bands the Robert randomly prefers to fight from.
 *
 *  - [FAR]   (0% to 40% proximity): every shot misses. He holds it for only
 *            one or two shots at a time, purely to confuse the real player.
 *  - [MID]   (40% to 70% proximity): half of the weapon power.
 *  - [CLOSE] (90% to 100% proximity): the full weapon power.
 *
 * The 70% to 90% band is never held on purpose: it is only crossed while the
 * Robert walks from one band to the other.
 */
enum class FireRange(
    val id: String,
    val minProximity: Float,
    val maxProximity: Float
) {
    FAR("far", Combat.MISS_ZONE_MIN, Combat.MISS_ZONE_MAX),
    MID("mid", Combat.MISS_ZONE_MAX, Combat.LIGHT_ZONE_MAX),
    CLOSE("close", Combat.HEAVY_ZONE_MIN, Combat.FULL_PROXIMITY);

    /** The proximity the Robert aims at while he holds this band. */
    val midProximity: Float
        get() = (minProximity + maxProximity) / 2f

    /** The arena distance that matches [midProximity]. */
    val targetDistance: Float
        get() = Combat.distanceFor(midProximity)

    companion object {
        fun fromId(id: String?): FireRange =
            FireRange.entries.firstOrNull { it.id == id } ?: MID
    }
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
 * Weapon power table, as a percentage of the victim's maximum health at
 * 90%-100% proximity. The player and the Robert use exactly the same table:
 *
 *  - knife         8%
 *  - sword         12%
 *  - assault gun   14%
 *  - pistol        13%
 *  - sniper rifle  16%
 *  - short gun     8%, but 10% at 90%-100% proximity
 *
 * There is no preset where a combatant loses after a fixed number of hits:
 * a combatant is defeated only when their health actually reaches zero.
 *
 * Three consecutive enemy hits received inside the 90%-100% band damage the
 * weapon in the player's hands. A damaged weapon cannot fire until it is
 * repaired by the Recovery action, which costs [GUN_RECOVERY_COST] coins.
 */
object Combat {
    /** Full combat area width in metres (the arena is a 100 m range). */
    const val MAX_RANGE = 100f

    const val MISS_ZONE_MIN = 0f
    const val MISS_ZONE_MAX = 40f
    const val LIGHT_ZONE_MAX = 70f
    const val STRONG_ZONE_MAX = 90f
    const val HEAVY_ZONE_MIN = 90f
    const val FULL_PROXIMITY = 100f

    /** Fraction of the weapon power that lands in each zone. */
    const val LIGHT_DAMAGE_FRACTION = 0.50f
    const val STRONG_DAMAGE_FRACTION = 0.80f
    const val HEAVY_DAMAGE_FRACTION = 1.00f

    /** Point-blank hits in a row needed to damage the weapon in hand. */
    const val HITS_TO_DAMAGE_GUN = 3

    /** Coins required to repair every damaged weapon. */
    const val GUN_RECOVERY_COST = 100

    /** Fraction of maximum health granted by the single second chance. */
    const val SECOND_CHANCE_FRACTION = 0.5f

    /** A cover blocks this many hits; the next (12th) hit breaks it. */
    const val COVER_MAX_HITS = 11

    /** Sniper hits at 90%-100% proximity needed to break a glue wall. */
    const val GLUE_WALL_BREAK_HITS = 3

    /** Only a sniper shot at or above this proximity can damage a glue wall. */
    const val GLUE_WALL_BREAK_PROXIMITY = 90f

    /**
     * The short gun is the one exception of the power table: it hits with
     * 10% under 90%-100% proximity instead of its base 8%.
     */
    const val SHORT_GUN_CLOSE_POWER_PERCENT = 10

    /** Converts a distance (50 = far edge, 0 = point-blank) into 0..100% proximity. */
    fun proximityOf(distance: Float): Float =
        ((1f - distance / MAX_RANGE) * 100f).coerceIn(MISS_ZONE_MIN, FULL_PROXIMITY)

    /** Converts 0..100% proximity back into an arena distance in metres. */
    fun distanceFor(proximityPercent: Float): Float =
        ((1f - proximityPercent.coerceIn(MISS_ZONE_MIN, FULL_PROXIMITY) / 100f) * MAX_RANGE)
            .coerceIn(0f, MAX_RANGE)

    /** Returns the hit zone for the given proximity percentage. */
    fun zoneFor(proximityPercent: Float): HitZone = when {
        proximityPercent >= STRONG_ZONE_MAX -> HitZone.HEAVY
        proximityPercent >= LIGHT_ZONE_MAX -> HitZone.STRONG
        proximityPercent >= MISS_ZONE_MAX -> HitZone.LIGHT
        else -> HitZone.MISS
    }

    /**
     * The power percentage a weapon really uses at this proximity. Every
     * weapon uses its own table value except the short gun, which is boosted
     * to [SHORT_GUN_CLOSE_POWER_PERCENT] at point blank.
     */
    fun powerPercentOf(weapon: Weapon, proximityPercent: Float): Int =
        if (weapon == Weapon.SHORT_GUN && zoneFor(proximityPercent) == HitZone.HEAVY) {
            SHORT_GUN_CLOSE_POWER_PERCENT
        } else {
            weapon.powerPercent
        }

    /**
     * Damage of a weapon with the given [powerPercent] at the given proximity,
     * applied to a fighter with [maxHp] maximum health.
     *
     * Misses deal 0, light hits exactly half of the stated power, strong hits 80% and
     * point-blank hits the full stated power (rounded, with a minimum of 1 so a
     * connected hit is never fully ignored).
     *
     * Weapons deduct health according to their stated power (e.g. 14 HP for assault gun)
     * rather than scaling wildly against large round health values.
     */
    fun damageFor(powerPercent: Int, proximityPercent: Float, maxHp: Int = 100): Int {
        if (maxHp <= 0 || powerPercent <= 0) return 0
        val fraction = when (zoneFor(proximityPercent)) {
            HitZone.MISS -> return 0
            HitZone.LIGHT -> LIGHT_DAMAGE_FRACTION
            HitZone.STRONG -> STRONG_DAMAGE_FRACTION
            HitZone.HEAVY -> HEAVY_DAMAGE_FRACTION
        }
        return (powerPercent * fraction).roundToInt().coerceAtLeast(1)
    }

    /** Damage dealt by a specific weapon at the given proximity. */
    fun weaponDamage(weapon: Weapon, proximityPercent: Float, victimMaxHp: Int = 100): Int =
        damageFor(powerPercentOf(weapon, proximityPercent), proximityPercent, victimMaxHp)

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
