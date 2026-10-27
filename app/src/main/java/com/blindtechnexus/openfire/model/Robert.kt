package com.blindtechnexus.openfire.model

import kotlin.random.Random

/**
 * The Robert is the enemy of Open Fire. Its health, its survival items, its
 * weapons and its fleeing behaviour all scale with the current round:
 *
 *  Round 1: 1 medkit, 1 cover. Short gun + assault gun + sniper rifle (sniper locked).
 *  Round 2: 1 glue wall, 2 medkits, 1 cover. Assault gun and short gun.
 *  Round 3: 2 glue walls, 4 medkits, 3 covers. Sniper unlocked, knife added.
 *  Round 4: 3 glue walls, 5 medkits, 3 covers. Sword added.
 *  Round 5: 5 glue walls, 6 medkits, 7 covers. Every weapon.
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
    /** Consecutive hits that make the Robert run far away. */
    val fleeAfterHits: Int
)

object RobertRules {
    /** The Robert starts using its defences once its health drops below this. */
    const val DEFENCE_THRESHOLD_HP = 60

    /** The Robert's sniper is usable under this health while it is locked. */
    const val LOCKED_SNIPER_MAX_HP = 100

    /**
     * Chance that a relocation is not a real attack position but a deception
     * trip into the 0%-40% band, where every shot misses on purpose.
     */
    const val DECEPTION_CHANCE = 0.22f

    /** Round health ladder. Any round outside 1..5 falls back to round 1. */
    fun hpForRound(round: Int): Int = when (round) {
        2 -> 400
        3 -> 500
        4 -> 600
        5 -> 750
        else -> 300
    }

    /** Consecutive hits needed before the Robert runs far away. */
    fun fleeAfterHits(round: Int): Int = when (round) {
        3 -> 3
        4 -> 4
        5 -> 5
        else -> 2
    }

    fun profile(round: Int): RobertProfile {
        val safeRound = round.coerceIn(1, 5)
        val weapons = when (safeRound) {
            1 -> listOf(Weapon.SHORT_GUN, Weapon.ASSAULT_GUN, Weapon.SNIPER)
            2 -> listOf(Weapon.SHORT_GUN, Weapon.ASSAULT_GUN, Weapon.SNIPER)
            3 -> listOf(Weapon.SHORT_GUN, Weapon.ASSAULT_GUN, Weapon.SNIPER, Weapon.KNIFE)
            4 -> listOf(
                Weapon.SHORT_GUN,
                Weapon.ASSAULT_GUN,
                Weapon.SNIPER,
                Weapon.KNIFE,
                Weapon.SWORD
            )
            else -> Weapon.entries
        }
        return when (safeRound) {
            1 -> RobertProfile(1, hpForRound(1), 1, 0, 1, weapons, true, fleeAfterHits(1))
            2 -> RobertProfile(2, hpForRound(2), 2, 1, 1, weapons, true, fleeAfterHits(2))
            3 -> RobertProfile(3, hpForRound(3), 4, 2, 3, weapons, false, fleeAfterHits(3))
            4 -> RobertProfile(4, hpForRound(4), 5, 3, 3, weapons, false, fleeAfterHits(4))
            else -> RobertProfile(5, hpForRound(5), 6, 5, 7, weapons, false, fleeAfterHits(5))
        }
    }

    /**
     * Power of a Robert weapon. The Robert uses exactly the same power table
     * as the player, including the short gun boost to 18% at 90%-100%
     * proximity.
     */
    fun powerFor(weapon: Weapon, proximityPercent: Float): Int =
        Combat.powerPercentOf(weapon, proximityPercent)

    // ------------------------------------------------------------------
    // Where the Robert prefers to fire from
    // ------------------------------------------------------------------

    /**
     * The two bands the Robert really fights from: 40%-70% or 90%-100%.
     * He picks one of them at random every time he relocates.
     */
    fun preferredRange(random: Random): FireRange =
        if (random.nextBoolean()) FireRange.MID else FireRange.CLOSE

    /** True when the next relocation should be a confusing 0%-40% trip. */
    fun shouldStartDeception(random: Random): Boolean =
        random.nextFloat() < DECEPTION_CHANCE

    /** How many useless shots he fires from the 0%-40% band: one or two. */
    fun deceptionShots(random: Random): Int = 1 + random.nextInt(2)

    /**
     * The band the Robert holds right now.
     *
     * Two situations always pull him to point blank (90%-100%):
     *  - the weapon in the player's hands is damaged, so he closes in and
     *    keeps firing until a working weapon is used or Recovery is paid;
     *  - the player raised a glue wall, which only breaks from 90%-100%.
     */
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
     * only be used when the player has raised a glue wall AND the Robert's
     * health is below 100.
     */
    fun sniperAllowed(profile: RobertProfile, playerGlueWallActive: Boolean, robertHp: Int): Boolean =
        !profile.sniperLocked ||
            (playerGlueWallActive && robertHp in 1 until LOCKED_SNIPER_MAX_HP)

    /** Every weapon the Robert may use right now. */
    fun allowedWeapons(
        profile: RobertProfile,
        playerGlueWallActive: Boolean,
        robertHp: Int
    ): List<Weapon> = profile.weapons.filter { weapon ->
        weapon != Weapon.SNIPER || sniperAllowed(profile, playerGlueWallActive, robertHp)
    }

    /** True when the Robert should spend a defence (health below the trigger). */
    fun shouldUseDefence(robertHp: Int, defenceArmed: Boolean): Boolean =
        defenceArmed && robertHp in 1 until DEFENCE_THRESHOLD_HP

    /** Fire every second under pressure, otherwise every 1.5 to 2 seconds. */
    fun fireInterval(
        playerWeaponDamaged: Boolean,
        enemyHasDefence: Boolean,
        playerHasDefence: Boolean,
        random: Random
    ): Long = if (playerWeaponDamaged || (enemyHasDefence && !playerHasDefence)) {
        1000L
    } else {
        1500L + random.nextLong(0, 501L)
    }

    /**
     * AI retreat logic: if the enemy is losing in a fight against the player
     * (the player gains the upper hand / lands more hits), the enemy retreats very quickly.
     * For example, if the enemy takes two hits while the player takes only one, the enemy
     * flees immediately. If the enemy lands more hits, they stay and fight; if the player
     * suddenly lands more hits, the enemy flees.
     */
    fun shouldFleeFight(robertHitsTaken: Int, robertHitsLanded: Int): Boolean =
        robertHitsTaken > robertHitsLanded && robertHitsTaken >= 2

    /** True when the Robert has taken enough hits in a row to run far away. */
    fun shouldFlee(consecutiveHits: Int, fleeAfterHits: Int): Boolean =
        fleeAfterHits > 0 && consecutiveHits >= fleeAfterHits

    /**
     * Which defence the Robert uses next: medkit first, then cover, then glue
     * wall. Returns null when nothing is left.
     */
    fun nextDefence(medikits: Int, covers: Int, glueWalls: Int): DefenceItem? = when {
        medikits > 0 -> DefenceItem.MEDKIT
        covers > 0 -> DefenceItem.COVER
        glueWalls > 0 -> DefenceItem.GLUE_WALL
        else -> null
    }
}
