package com.blindtechnexus.openfire.model

import kotlin.random.Random

/**
 * Robert's health, survival items, weapons and fleeing behaviour scale with
 * the current round. His maximum health also depends on the current level.
 *
 * In round one he carries the assault rifle and shotgun. His sniper rifle is
 * present but locked until the existing glue-wall / low-health condition is
 * met. Later rounds retain the established loadout progression.
 */
data class RobertProfile(
    val round: Int,
    val maxHp: Int,
    val medikits: Int,
    val glueWalls: Int,
    val covers: Int,
    val weapons: List<Weapon>,
    /** True while the sniper rifle may only be used under the special rule. */
    val sniperLocked: Boolean,
    /** Consecutive hits that make Robert run far away. */
    val fleeAfterHits: Int
)

object RobertRules {
    /** Robert starts using his defences once his health drops below this. */
    const val DEFENCE_THRESHOLD_HP = 60

    /** Robert's sniper is usable under this health while it is locked. */
    const val LOCKED_SNIPER_MAX_HP = 100

    /** Chance that a relocation is a deception trip into the miss band. */
    const val DECEPTION_CHANCE = 0.22f

    /** Consecutive hits needed before Robert runs far away. */
    fun fleeAfterHits(round: Int): Int = when (round) {
        3 -> 3
        4 -> 4
        5 -> 5
        else -> 2
    }

    /**
     * Round-by-round movement multiplier. Round one is 20% faster than the
     * old movement, and every following round adds another 20% of base speed.
     */
    fun movementSpeedMultiplier(round: Int): Float =
        1.2f + (round.coerceIn(1, Progression.ROUNDS_PER_MATCH) - 1) * 0.2f

    fun profile(round: Int, level: Int = 1): RobertProfile {
        val safeRound = round.coerceIn(1, Progression.ROUNDS_PER_MATCH)
        val safeLevel = level.coerceIn(1, Progression.LEVELS_PER_ROUND)
        val maxHp = Progression.hpFor(safeRound, safeLevel)
        val weapons = when (safeRound) {
            1 -> listOf(Weapon.ASSAULT_GUN, Weapon.SHORT_GUN, Weapon.SNIPER)
            2 -> listOf(Weapon.ASSAULT_GUN, Weapon.SHORT_GUN, Weapon.SNIPER)
            3 -> listOf(Weapon.SHORT_GUN, Weapon.ASSAULT_GUN, Weapon.SNIPER, Weapon.KNIFE)
            4 -> listOf(
                Weapon.SHORT_GUN,
                Weapon.ASSAULT_GUN,
                Weapon.SNIPER,
                Weapon.KNIFE,
                Weapon.SWORD
            )
            else -> listOf(
                Weapon.SHORT_GUN,
                Weapon.ASSAULT_GUN,
                Weapon.PISTOL,
                Weapon.SNIPER,
                Weapon.KNIFE,
                Weapon.SWORD
            )
        }
        return when (safeRound) {
            1 -> RobertProfile(1, maxHp, 1, 0, 1, weapons, true, fleeAfterHits(1))
            2 -> RobertProfile(2, maxHp, 2, 1, 1, weapons, true, fleeAfterHits(2))
            3 -> RobertProfile(3, maxHp, 4, 2, 3, weapons, false, fleeAfterHits(3))
            4 -> RobertProfile(4, maxHp, 5, 3, 3, weapons, false, fleeAfterHits(4))
            else -> RobertProfile(5, maxHp, 6, 5, 7, weapons, false, fleeAfterHits(5))
        }
    }

    /**
     * Robert's custom assault rifle and shotgun powers by proximity zone.
     * Values are percentages of the victim's maximum health:
     *
     * Assault rifle: miss / 13 / 15 / 16.
     * Shotgun:       miss / 10 / 13 / 14.
     *
     * Every other weapon uses the standard player power table.
     */
    fun powerFor(weapon: Weapon, proximityPercent: Float): Int = when (weapon) {
        Weapon.ASSAULT_GUN -> when (Combat.zoneFor(proximityPercent)) {
            HitZone.MISS -> 0
            HitZone.LIGHT -> 13
            HitZone.STRONG -> 15
            HitZone.HEAVY -> 16
        }
        Weapon.SHORT_GUN -> when (Combat.zoneFor(proximityPercent)) {
            HitZone.MISS -> 0
            HitZone.LIGHT -> 10
            HitZone.STRONG -> 13
            HitZone.HEAVY -> 14
        }
        else -> Combat.powerPercentOf(weapon, proximityPercent)
    }

    /** True for the two weapons whose zone powers must not be scaled again. */
    private fun hasCustomZonePowers(weapon: Weapon): Boolean =
        weapon == Weapon.ASSAULT_GUN || weapon == Weapon.SHORT_GUN

    /** Actual Robert damage, including his two custom per-zone power tables. */
    fun weaponDamage(weapon: Weapon, proximityPercent: Float, victimMaxHp: Int): Int =
        if (hasCustomZonePowers(weapon)) {
            Combat.damageForDirectPower(
                powerFor(weapon, proximityPercent),
                proximityPercent,
                victimMaxHp
            )
        } else {
            Combat.weaponDamage(weapon, proximityPercent, victimMaxHp)
        }

    // ------------------------------------------------------------------
    // Where Robert prefers to fire from
    // ------------------------------------------------------------------

    /** The two bands Robert really fights from: 40%-70% or 90%-100%. */
    fun preferredRange(random: Random): FireRange =
        if (random.nextBoolean()) FireRange.MID else FireRange.CLOSE

    /** True when the next relocation should be a confusing 0%-40% trip. */
    fun shouldStartDeception(random: Random): Boolean =
        random.nextFloat() < DECEPTION_CHANCE

    /** How many useless shots he fires from the 0%-40% band: one or two. */
    fun deceptionShots(random: Random): Int = 1 + random.nextInt(2)

    /** The band Robert holds after applying forced point-blank situations. */
    fun fireRangeFor(
        stored: FireRange,
        playerWeaponDamaged: Boolean,
        playerGlueWallActive: Boolean
    ): FireRange = when {
        playerWeaponDamaged -> FireRange.CLOSE
        playerGlueWallActive -> FireRange.CLOSE
        else -> stored
    }

    /**
     * The sniper rifle is locked in rounds one and two. While locked it can
     * only be used when the player has raised a glue wall AND Robert's health
     * is below 100.
     */
    fun sniperAllowed(profile: RobertProfile, playerGlueWallActive: Boolean, robertHp: Int): Boolean =
        !profile.sniperLocked ||
            (playerGlueWallActive && robertHp in 1 until LOCKED_SNIPER_MAX_HP)

    /** Every weapon Robert may use right now. */
    fun allowedWeapons(
        profile: RobertProfile,
        playerGlueWallActive: Boolean,
        robertHp: Int
    ): List<Weapon> = profile.weapons.filter { weapon ->
        weapon != Weapon.SNIPER || sniperAllowed(profile, playerGlueWallActive, robertHp)
    }

    /** True when Robert should spend a defence (health below the trigger). */
    fun shouldUseDefence(robertHp: Int, defenceArmed: Boolean): Boolean =
        defenceArmed && robertHp in 1 until DEFENCE_THRESHOLD_HP

    /** True when Robert has taken enough hits in a row to run far away. */
    fun shouldFlee(consecutiveHits: Int, fleeAfterHits: Int): Boolean =
        fleeAfterHits > 0 && consecutiveHits >= fleeAfterHits

    /** Which defence Robert uses next: medkit first, then cover, then glue wall. */
    fun nextDefence(medikits: Int, covers: Int, glueWalls: Int): DefenceItem? = when {
        medikits > 0 -> DefenceItem.MEDKIT
        covers > 0 -> DefenceItem.COVER
        glueWalls > 0 -> DefenceItem.GLUE_WALL
        else -> null
    }
}
