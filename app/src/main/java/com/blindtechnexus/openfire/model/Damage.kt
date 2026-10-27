package com.blindtechnexus.openfire.model

import kotlin.math.roundToInt

/**
 * The single health-reduction table of the whole game.
 *
 * Every weapon - the player's and Robert's - takes its damage from this one
 * table, so a hit always deducts exactly the documented amount and never a
 * "100 percent" or "150 percent" surprise. Damage is a percentage of the
 * victim's maximum health:
 *
 *  | weapon          | 0-40% | 40-70% | 70-90% | 90-100% |
 *  |-----------------|-------|--------|--------|---------|
 *  | knife           |   0   |    5   |    8   |    10   |
 *  | shotgun         |   0   |    5   |    8   |    12   |
 *  | assault rifle   |   0   |    7   |   10   |    14   |
 *  | pistol          |   0   |    7   |   11   |    15   |
 *  | sniper rifle    |   0   |    8   |   12   |    16   |
 *  | sword           |   0   |    8   |   12   |    16   |
 *  | AWM             |  10 * |   12   |   18   |    24   |
 *  | MP40            |   0   |    4   |    6   |     8   |
 *  | machine gun     |   0   |    5   |    8   |    11   |
 *  | grenade         |  100  |  100   |  100   |   100   |
 *  | atom bomb       |  100  |  100   |  100   |   100   |
 *  | timer bomb      |  100  |  100   |  100   |   100   |
 *  | missile         |  100  |  100   |  100   |   100   |
 *
 *  * The AWM is a long-range precision rifle: inside the 20% to 40%
 *    proximity band - where every other weapon misses completely - it still
 *    lands and takes [AWM_LONG_RANGE_PERCENT] percent of the victim's maximum
 *    health, never less than [AWM_LONG_RANGE_MIN_HP] health points.
 *
 * Normal weapons are capped at [MAX_WEAPON_PERCENT] percent of maximum
 * health per hit. Explosives (bombs and the missile) keep their full
 * [EXPLOSIVE_PERCENT] percent capacity: they destroy their target outright.
 */
object DamageTable {
    /** Hard cap for every non-explosive weapon. */
    const val MAX_WEAPON_PERCENT = 40

    /** Full capacity of bombs and missiles. */
    const val EXPLOSIVE_PERCENT = 100

    /** The AWM's long-range precision band, in proximity percent. */
    const val AWM_LONG_RANGE_MIN_PROXIMITY = 20f
    const val AWM_LONG_RANGE_MAX_PROXIMITY = 40f

    /** AWM long-range hit: percent of maximum health, and its HP floor. */
    const val AWM_LONG_RANGE_PERCENT = 10
    const val AWM_LONG_RANGE_MIN_HP = 10

    /**
     * While the quick menu is genuinely being navigated every incoming hit is
     * reduced to this percentage of maximum health, so browsing items stays
     * safe without becoming an invulnerability cheat.
     */
    const val MENU_PROTECTION_PERCENT = 2

    /** How long the menu protection stays alive after the last menu swipe. */
    const val MENU_PROTECTION_MS = 6_000L

    /** One row per weapon, indexed by [HitZone.ordinal]. */
    private val table: Map<Weapon, IntArray> = mapOf(
        Weapon.KNIFE to intArrayOf(0, 5, 8, 10),
        Weapon.SHORT_GUN to intArrayOf(0, 5, 8, 12),
        Weapon.ASSAULT_GUN to intArrayOf(0, 7, 10, 14),
        Weapon.PISTOL to intArrayOf(0, 7, 11, 15),
        Weapon.SNIPER to intArrayOf(0, 8, 12, 16),
        Weapon.SWORD to intArrayOf(0, 8, 12, 16),
        Weapon.AWM to intArrayOf(0, 12, 18, 24),
        Weapon.MP40 to intArrayOf(0, 4, 6, 8),
        Weapon.MACHINE_GUN to intArrayOf(0, 5, 8, 11),
        Weapon.GRENADE to intArrayOf(
            EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT
        ),
        Weapon.ATOM_BOMB to intArrayOf(
            EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT
        ),
        Weapon.TIMER_BOMB to intArrayOf(
            EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT
        ),
        Weapon.MISSILE to intArrayOf(
            EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT, EXPLOSIVE_PERCENT
        )
    )

    /** True for the weapons that ignore the 40 percent cap. */
    fun isExplosive(weapon: Weapon): Boolean =
        weapon.missileLauncher || weapon.bomb

    /** Applies the cap that belongs to this weapon class. */
    fun capFor(weapon: Weapon): Int =
        if (isExplosive(weapon)) EXPLOSIVE_PERCENT else MAX_WEAPON_PERCENT

    /** The exact percentage of maximum health this weapon takes in this zone. */
    fun percentOf(weapon: Weapon, zone: HitZone): Int {
        val row = table[weapon] ?: return 0
        val value = row.getOrElse(zone.ordinal) { 0 }
        return value.coerceIn(0, capFor(weapon))
    }

    /** The exact percentage for a proximity value (0..100). */
    fun percentAt(weapon: Weapon, proximityPercent: Float): Int =
        percentOf(weapon, Combat.zoneFor(proximityPercent))

    /** True inside the AWM's special long-range band. */
    fun isAwmLongRangeBand(weapon: Weapon, proximityPercent: Float): Boolean =
        weapon == Weapon.AWM &&
            proximityPercent >= AWM_LONG_RANGE_MIN_PROXIMITY &&
            proximityPercent < AWM_LONG_RANGE_MAX_PROXIMITY

    /**
     * The AWM's long-range precision damage: exactly
     * [AWM_LONG_RANGE_PERCENT] percent of maximum health and never less than
     * [AWM_LONG_RANGE_MIN_HP] health points.
     */
    fun awmLongRangeDamage(victimMaxHp: Int): Int {
        if (victimMaxHp <= 0) return 0
        val percentDamage = (victimMaxHp * AWM_LONG_RANGE_PERCENT / 100.0).roundToInt()
        return maxOf(AWM_LONG_RANGE_MIN_HP, percentDamage).coerceAtMost(victimMaxHp)
    }

    /**
     * The damage of one hit, in health points. A connected hit always takes
     * at least 1 health, and the value is never larger than the victim's
     * remaining maximum health.
     */
    fun damage(weapon: Weapon, proximityPercent: Float, victimMaxHp: Int): Int {
        if (victimMaxHp <= 0) return 0
        if (isAwmLongRangeBand(weapon, proximityPercent)) {
            return awmLongRangeDamage(victimMaxHp)
        }
        val percent = percentAt(weapon, proximityPercent)
        if (percent <= 0) return 0
        val value = (victimMaxHp * percent / 100.0).roundToInt()
        return value.coerceIn(1, victimMaxHp)
    }

    /** Menu-protection damage: a fixed percentage of maximum health. */
    fun menuProtectedDamage(victimMaxHp: Int): Int {
        if (victimMaxHp <= 0) return 0
        val value = (victimMaxHp * MENU_PROTECTION_PERCENT / 100.0).roundToInt()
        return value.coerceIn(1, victimMaxHp)
    }

    /**
     * Every weapon's row as human readable percentages, in the order
     * miss / light / strong / heavy. Used by the shop and the guide.
     */
    fun zonesText(weapon: Weapon): String =
        "%d, %d, %d, %d".format(
            percentOf(weapon, HitZone.MISS),
            percentOf(weapon, HitZone.LIGHT),
            percentOf(weapon, HitZone.STRONG),
            percentOf(weapon, HitZone.HEAVY)
        )

    /** True when the whole table respects the documented caps. */
    fun isValid(): Boolean = table.values.all { row -> row.size == HitZone.entries.size }
}
