package com.blindtechnexus.openfire.model

import kotlin.random.Random

/**
 * Robert's health, survival items, weapons and fleeing behaviour scale with
 * the current round (and, in round one, with the current level). His
 * maximum health also depends on the current level.
 *
 *  - Round 1, levels 1-2: 2 medkits and 1 cover (no glue walls).
 *    Round 1, levels 3-5: 2 medkits and 2 covers (no glue walls).
 *    Weapons: assault rifle and shotgun; the sniper rifle is present but
 *    locked until the glue-wall / low-health condition is met.
 *  - Round 2 (all levels): 3 medkits, 1 glue wall and 2 covers. The knife
 *    and the sword join his weapons.
 *  - Round 3 (all levels): 3 medkits, 2 glue walls and 4 covers. The
 *    pistol joins his weapons and the sniper rifle unlocks.
 *  - Round 4 (all levels): 5 medkits, 3 glue walls and 5 covers. Every
 *    weapon is available, including one missile power per game, and he
 *    carries one anti-missile interceptor.
 *  - Round 5 (all levels): 5 medkits, 4 glue walls and 7 covers. Every
 *    weapon is available, including one missile power per game, and he
 *    carries two anti-missile interceptors.
 *
 * The missile itself never enters his random weapon rotation: it is fired
 * only through [MissileRules.shouldEnemyLaunch], exactly once per game.
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
    val fleeAfterHits: Int,
    /** Missile powers he may spend this game (round four and later). */
    val missilePowers: Int = 0,
    /** Anti-missile interceptors he carries this game (see [MissileRules]). */
    val antiMissiles: Int = 0
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

    /** Every standard battle weapon (the missile is handled separately). */
    val ALL_STANDARD_WEAPONS: List<Weapon> =
        Weapon.entries.filter { !it.missileLauncher }

    fun profile(round: Int, level: Int = 1): RobertProfile {
        val safeRound = round.coerceIn(1, Progression.ROUNDS_PER_MATCH)
        val safeLevel = level.coerceIn(1, Progression.LEVELS_PER_ROUND)
        val maxHp = Progression.hpFor(safeRound, safeLevel)
        val weapons = when (safeRound) {
            1 -> listOf(Weapon.ASSAULT_GUN, Weapon.SHORT_GUN, Weapon.SNIPER)
            // Round two adds the knife and the sword.
            2 -> listOf(
                Weapon.ASSAULT_GUN,
                Weapon.SHORT_GUN,
                Weapon.SNIPER,
                Weapon.KNIFE,
                Weapon.SWORD
            )
            // Round three adds the pistol: from here on he owns them all.
            3 -> listOf(
                Weapon.SHORT_GUN,
                Weapon.ASSAULT_GUN,
                Weapon.PISTOL,
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
            1 -> if (safeLevel <= 2) {
                // Levels 1 and 2: two medkits and one cover, no glue walls.
                RobertProfile(1, maxHp, 2, 0, 1, weapons, true, fleeAfterHits(1))
            } else {
                // Levels 3, 4 and 5: two medkits and two covers, no glue walls.
                RobertProfile(1, maxHp, 2, 0, 2, weapons, true, fleeAfterHits(1))
            }
            2 -> RobertProfile(2, maxHp, 3, 1, 2, weapons, true, fleeAfterHits(2))
            3 -> RobertProfile(3, maxHp, 3, 2, 4, weapons, false, fleeAfterHits(3))
            4 -> RobertProfile(
                4, maxHp, 5, 3, 5, weapons, false, fleeAfterHits(4),
                missilePowers = MissileRules.enemyMissilePowersForRound(4),
                antiMissiles = MissileRules.antiMissilesForRound(4)
            )
            else -> RobertProfile(
                5, maxHp, 5, 4, 7, weapons, false, fleeAfterHits(5),
                missilePowers = MissileRules.enemyMissilePowersForRound(5),
                antiMissiles = MissileRules.antiMissilesForRound(5)
            )
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

    /**
     * True when a one-sided exchange must make Robert run as fast as
     * possible: he is at a disadvantage, has sustained two or three hits in
     * a row, and has only managed to answer with one or two shots while
     * inside combat range (proximity at least 40%, so the hits hurt him).
     */
    fun shouldFleeFromExchange(
        hitsTakenInRow: Int,
        shotsFiredInExchange: Int,
        proximityPercent: Float,
        atDisadvantage: Boolean = true
    ): Boolean =
        atDisadvantage &&
            hitsTakenInRow >= 2 &&
            shotsFiredInExchange <= 2 &&
            Combat.zoneFor(proximityPercent) != HitZone.MISS

    /** Which defence Robert uses next: medkit first, then cover, then glue wall. */
    fun nextDefence(medikits: Int, covers: Int, glueWalls: Int): DefenceItem? = when {
        medikits > 0 -> DefenceItem.MEDKIT
        covers > 0 -> DefenceItem.COVER
        glueWalls > 0 -> DefenceItem.GLUE_WALL
        else -> null
    }

    // ------------------------------------------------------------------
    // Situational decisions: who is winning, where to stand, how fast
    // to fire. Robert never fires on a fixed clock any more.
    // ------------------------------------------------------------------

    /** Health-fraction gap that counts as a moderate / big advantage. */
    const val MODERATE_ADVANTAGE_GAP = 0.10f
    const val BIG_ADVANTAGE_GAP = 0.25f

    /** Who currently holds the upper hand in a fight. */
    enum class CombatSituation {
        /** The player's weapon is damaged: Robert presses at point blank. */
        PRESSURE,
        /** Robert is clearly healthier: he fires continuously, close in. */
        ADVANTAGE,
        /** Roughly even: a varied, human-like pace. */
        EVEN,
        /** Robert is losing: sparse fire while he repositions or flees. */
        DISADVANTAGE
    }

    /** Robert's health advantage: his fraction minus the player's (-1..1). */
    fun advantageGap(
        robertHp: Int,
        robertMaxHp: Int,
        playerHp: Int,
        playerMaxHp: Int
    ): Float {
        if (robertMaxHp <= 0 || playerMaxHp <= 0) return 0f
        val robertFraction = robertHp.toFloat() / robertMaxHp.toFloat()
        val playerFraction = playerHp.toFloat() / playerMaxHp.toFloat()
        return (robertFraction - playerFraction).coerceIn(-1f, 1f)
    }

    fun assessSituation(
        robertHp: Int,
        robertMaxHp: Int,
        playerHp: Int,
        playerMaxHp: Int,
        playerWeaponDamaged: Boolean
    ): CombatSituation {
        if (playerWeaponDamaged) return CombatSituation.PRESSURE
        val gap = advantageGap(robertHp, robertMaxHp, playerHp, playerMaxHp)
        return when {
            gap > MODERATE_ADVANTAGE_GAP -> CombatSituation.ADVANTAGE
            gap < -MODERATE_ADVANTAGE_GAP -> CombatSituation.DISADVANTAGE
            else -> CombatSituation.EVEN
        }
    }

    /**
     * The band Robert holds for the current situation. A forced point blank
     * (damaged weapon or glue wall) always wins. Otherwise a big advantage
     * pushes him into the 90%-100% band, a moderate advantage into the
     * 70%-90% band, and a disadvantage pulls him back to the 40%-70% band.
     * An active 0%-40% deception trip is always allowed to finish.
     */
    fun situationalRange(
        stored: FireRange,
        playerWeaponDamaged: Boolean,
        playerGlueWallActive: Boolean,
        robertHp: Int,
        robertMaxHp: Int,
        playerHp: Int,
        playerMaxHp: Int,
        deceptionShotsLeft: Int
    ): FireRange {
        if (playerWeaponDamaged || playerGlueWallActive) return FireRange.CLOSE
        if (stored == FireRange.FAR && deceptionShotsLeft > 0) return FireRange.FAR
        val gap = advantageGap(robertHp, robertMaxHp, playerHp, playerMaxHp)
        return when {
            gap >= BIG_ADVANTAGE_GAP -> FireRange.CLOSE
            gap > MODERATE_ADVANTAGE_GAP -> FireRange.STRONG
            gap < -MODERATE_ADVANTAGE_GAP -> FireRange.MID
            else -> stored
        }
    }

    /** The inclusive delay window (ms) before Robert's next shot. */
    fun fireIntervalWindow(situation: CombatSituation, fleeing: Boolean): LongRange = when {
        fleeing -> 2_200L..3_200L
        situation == CombatSituation.PRESSURE -> 620L..1_000L
        situation == CombatSituation.ADVANTAGE -> 750L..1_150L
        situation == CombatSituation.EVEN -> 1_400L..2_200L
        else -> 2_000L..3_000L
    }

    /** A jittered delay before Robert's next shot, for the situation. */
    fun fireIntervalMs(situation: CombatSituation, fleeing: Boolean, random: Random): Long {
        val window = fireIntervalWindow(situation, fleeing)
        return if (window.last <= window.first) {
            window.first
        } else {
            window.first + (random.nextFloat() * (window.last - window.first)).toLong()
        }
    }
}
