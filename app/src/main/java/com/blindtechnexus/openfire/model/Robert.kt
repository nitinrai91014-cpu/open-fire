package com.blindtechnexus.openfire.model

/**
 * The Robert is the enemy of Open Fire. Its health, its survival items, its
 * weapons and its fleeing behaviour all scale with the current round:
 *
 *  Round 1: 1 medkit, 1 cover. Short gun + sniper rifle (sniper locked).
 *  Round 2: 1 glue wall, 2 medkits, 1 cover. Adds the assault gun.
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

    /** The Robert's short gun is stronger than the player's. */
    const val ROBERT_SHORT_GUN_POWER = 10

    /** The Robert's sniper is usable under this health while it is locked. */
    const val LOCKED_SNIPER_MAX_HP = 100

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
            1 -> listOf(Weapon.SHORT_GUN, Weapon.SNIPER)
            2 -> listOf(Weapon.SHORT_GUN, Weapon.ASSAULT_GUN, Weapon.SNIPER)
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
            1 -> RobertProfile(1, hpForRound(1), 1, 0, 1, weapons, true, fleeAfterHits(1))
            2 -> RobertProfile(2, hpForRound(2), 2, 1, 1, weapons, true, fleeAfterHits(2))
            3 -> RobertProfile(3, hpForRound(3), 4, 2, 3, weapons, false, fleeAfterHits(3))
            4 -> RobertProfile(4, hpForRound(4), 5, 3, 3, weapons, false, fleeAfterHits(4))
            else -> RobertProfile(5, hpForRound(5), 6, 5, 7, weapons, false, fleeAfterHits(5))
        }
    }

    /** Power of a Robert weapon (its short gun hits for 10 instead of 6). */
    fun powerFor(weapon: Weapon): Int =
        if (weapon == Weapon.SHORT_GUN) ROBERT_SHORT_GUN_POWER else weapon.power

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
