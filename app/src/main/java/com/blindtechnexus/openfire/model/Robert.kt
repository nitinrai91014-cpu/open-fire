package com.blindtechnexus.openfire.model

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Robert's health, survival items, weapons, bombs and fleeing behaviour scale
 * with the current round (and, in round one, with the current level). His
 * maximum health also depends on the current level.
 *
 *  - Round 1, levels 1-2: 2 medkits and 1 cover (no glue walls).
 *    Round 1, levels 3-5: 2 medkits and 2 covers (no glue walls).
 *    Weapons: assault rifle and shotgun; the sniper rifle is present but
 *    locked until the glue-wall / low-health condition is met.
 *  - Round 2 (all levels): 3 medkits, 1 glue wall and 2 covers. The knife,
 *    the sword and the MP40 submachine gun join his weapons.
 *  - Round 3 (all levels): 3 medkits, 2 glue walls and 4 covers. The pistol
 *    and the AWM join his weapons, the sniper rifle unlocks, and he receives
 *    one bomb (grenade or atom bomb) plus one anti-bomb explosive per game.
 *  - Round 4 (all levels): 5 medkits, 3 glue walls and 5 covers. Every weapon
 *    is available, including the machine gun, one missile power per game, one
 *    anti-missile interceptor, two bombs and one anti-bomb explosive.
 *  - Round 5 (all levels): 5 medkits, 4 glue walls and 7 covers. As round
 *    four, with two anti-missile interceptors.
 *
 * The missile and the bombs never enter his random weapon rotation: they are
 * fired only through [MissileRules.shouldEnemyLaunch] and
 * [BombRules.shouldEnemyThrow] as desperation weapons.
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
    val antiMissiles: Int = 0,
    /** Bombs he may throw this game (round three and later). */
    val bombs: Int = 0,
    /** Anti-bomb explosives he may deploy this game (round three and later). */
    val antiBombs: Int = 0
)

object RobertRules {
    /**
     * Legacy absolute defence trigger. The real trigger is round aware: see
     * [defenceThresholdHp]. Robert starts far earlier now, which is what makes
     * him actually use the medkits, covers and glue walls he carries.
     */
    const val DEFENCE_THRESHOLD_HP = 60

    /** Robert's sniper is usable under this health while it is locked. */
    const val LOCKED_SNIPER_MAX_HP = 100

    /** Chance that a relocation is a deception trip into the miss band. */
    const val DECEPTION_CHANCE = 0.22f

    /** Fast retreat: metres gained per flee tick, and the tick length. */
    const val FLEE_STEP_M = 6f
    const val FLEE_TICK_MS = 160L

    /** How often Robert strafes sideways instead of walking straight. */
    const val STRAFE_CHANCE = 0.20f

    /** Metres covered by one sideways strafe step. */
    const val STRAFE_STEP_M = 6f

    /** Ticks Robert spends turning back after he detected a wall. */
    const val TURN_BACK_TICKS = 3

    /** Metres covered per turn-back tick. */
    const val TURN_BACK_STEP_M = 8f

    /** Player shots inside one burst window that count as continuous fire. */
    const val COUNTER_FIRE_BURST_SHOTS = 3

    /** Window in which player shots are counted as one continuous burst. */
    const val PLAYER_BURST_WINDOW_MS = 2_500L

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

    /**
     * Robert's fast running speed in metres per second. The player's fast
     * three-finger speed level is defined to match exactly this value, so a
     * sprinting player and a fleeing enemy cover ground at the same rate.
     */
    fun enemyFastSpeedMps(round: Int): Float =
        FLEE_STEP_M * movementSpeedMultiplier(round) / (FLEE_TICK_MS / 1000f)

    /** Metres Robert covers in one flee tick. */
    fun fleeStep(round: Int): Float = FLEE_STEP_M * movementSpeedMultiplier(round)

    /**
     * The health Robert must fall under before he starts spending his
     * defences. Rounds one and two trigger below 60% of maximum health or
     * below 100 health (whichever is higher); round three triggers below 200;
     * rounds four and five trigger below 300.
     */
    fun defenceThresholdHp(round: Int, maxHp: Int): Int {
        val safeRound = round.coerceIn(1, Progression.ROUNDS_PER_MATCH)
        val safeMax = maxHp.coerceAtLeast(1)
        return when (safeRound) {
            1, 2 -> maxOf(DEFENCE_THRESHOLD_HP + 40, (safeMax * 0.60f).roundToInt())
            3 -> maxOf(200, DEFENCE_THRESHOLD_HP)
            else -> maxOf(300, DEFENCE_THRESHOLD_HP)
        }
    }

    /** Every standard battle weapon (bombs and the missile are separate). */
    val ALL_STANDARD_WEAPONS: List<Weapon> =
        Weapon.entries.filter { !it.missileLauncher && !it.bomb }

    /**
     * Robert's weapon rotation for a round. He receives the MP40 in round two,
     * the AWM in round three and the machine gun in round four; his sniper
     * rifle stays locked during the first two rounds, and his bombs arrive
     * with round three (see [BombRules.enemyBombsForRound]).
     */
    fun weaponsForRound(round: Int): List<Weapon> {
        val safeRound = round.coerceIn(1, Progression.ROUNDS_PER_MATCH)
        return when (safeRound) {
            1 -> listOf(Weapon.ASSAULT_GUN, Weapon.SHORT_GUN, Weapon.SNIPER)
            // Round two adds the knife, the sword and his own MP40.
            2 -> listOf(
                Weapon.ASSAULT_GUN,
                Weapon.SHORT_GUN,
                Weapon.SNIPER,
                Weapon.KNIFE,
                Weapon.SWORD,
                Weapon.MP40
            )
            // Round three adds the pistol and his AWM.
            3 -> listOf(
                Weapon.SHORT_GUN,
                Weapon.ASSAULT_GUN,
                Weapon.PISTOL,
                Weapon.SNIPER,
                Weapon.KNIFE,
                Weapon.SWORD,
                Weapon.MP40,
                Weapon.AWM
            )
            // Round four adds the machine gun: from here he owns them all.
            else -> ALL_STANDARD_WEAPONS
        }
    }

    fun profile(round: Int, level: Int = 1): RobertProfile {
        val safeRound = round.coerceIn(1, Progression.ROUNDS_PER_MATCH)
        val safeLevel = level.coerceIn(1, Progression.LEVELS_PER_ROUND)
        val maxHp = Progression.hpFor(safeRound, safeLevel)
        val weapons = weaponsForRound(safeRound)
        return when (safeRound) {
            1 -> if (safeLevel <= 2) {
                // Levels 1 and 2: two medkits and one cover, no glue walls.
                RobertProfile(1, maxHp, 2, 0, 1, weapons, true, fleeAfterHits(1))
            } else {
                // Levels 3, 4 and 5: two medkits and two covers, no glue walls.
                RobertProfile(1, maxHp, 2, 0, 2, weapons, true, fleeAfterHits(1))
            }
            2 -> RobertProfile(2, maxHp, 3, 1, 2, weapons, true, fleeAfterHits(2))
            3 -> RobertProfile(
                3, maxHp, 3, 2, 4, weapons, false, fleeAfterHits(3),
                bombs = BombRules.enemyBombsForRound(3),
                antiBombs = BombRules.antiBombsForRound(3)
            )
            4 -> RobertProfile(
                4, maxHp, 5, 3, 5, weapons, false, fleeAfterHits(4),
                missilePowers = MissileRules.enemyMissilePowersForRound(4),
                antiMissiles = MissileRules.antiMissilesForRound(4),
                bombs = BombRules.enemyBombsForRound(4),
                antiBombs = BombRules.antiBombsForRound(4)
            )
            else -> RobertProfile(
                5, maxHp, 5, 4, 7, weapons, false, fleeAfterHits(5),
                missilePowers = MissileRules.enemyMissilePowersForRound(5),
                antiMissiles = MissileRules.antiMissilesForRound(5),
                bombs = BombRules.enemyBombsForRound(5),
                antiBombs = BombRules.antiBombsForRound(5)
            )
        }
    }

    /**
     * Robert reads his damage from the same table as the player. His weapons
     * are the standard ones, so both sides always deduct the exact documented
     * percentage of maximum health.
     */
    fun powerFor(weapon: Weapon, proximityPercent: Float): Int =
        DamageTable.percentAt(weapon, proximityPercent)

    /** Actual Robert damage: the single shared damage table. */
    fun weaponDamage(weapon: Weapon, proximityPercent: Float, victimMaxHp: Int): Int =
        DamageTable.damage(weapon, proximityPercent, victimMaxHp)

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

    /**
     * The band Robert holds after applying forced point-blank situations. A
     * raised glue wall only pulls him in while he really owns a usable sniper
     * rifle to break it; without one he keeps his distance instead.
     */
    fun fireRangeFor(
        stored: FireRange,
        playerWeaponDamaged: Boolean,
        playerGlueWallActive: Boolean,
        robertHasSniper: Boolean = true
    ): FireRange = when {
        playerWeaponDamaged -> FireRange.CLOSE
        playerGlueWallActive && robertHasSniper -> FireRange.CLOSE
        playerGlueWallActive -> FireRange.FAR
        else -> stored
    }

    /**
     * True when the player's glue wall is up and Robert has no way to break
     * it. He then refuses to approach: he runs away and keeps away until the
     * glue wall is deactivated or broken, and the player has to hunt him down.
     */
    fun shouldAvoidGlueWall(playerGlueWallActive: Boolean, robertHasSniper: Boolean): Boolean =
        playerGlueWallActive && !robertHasSniper

    /**
     * True when Robert owns a sniper rifle he can actually use right now:
     * it must be in the allowed list for this round and situation, and it must
     * not be damaged.
     */
    fun hasUsableSniper(allowedWeapons: List<Weapon>, sniperDamaged: Boolean): Boolean =
        allowedWeapons.contains(Weapon.SNIPER) && !sniperDamaged

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

    /**
     * True when Robert should spend a defence. [thresholdHp] is the round
     * aware trigger from [defenceThresholdHp]; the default keeps the legacy
     * absolute rule for callers that do not know the round yet.
     */
    fun shouldUseDefence(
        robertHp: Int,
        defenceArmed: Boolean,
        thresholdHp: Int = DEFENCE_THRESHOLD_HP
    ): Boolean = defenceArmed && robertHp in 1 until thresholdHp

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

    /**
     * True when Robert must retreat because the player is firing continuously
     * and really taking his health down. While the player's own health is
     * dropping instead, Robert never runs: he keeps firing back.
     */
    fun shouldRetreatFromSustainedFire(
        playerBurstShots: Int,
        robertHealthDropping: Boolean,
        playerHealthDropping: Boolean
    ): Boolean =
        isContinuousPlayerFire(playerBurstShots) &&
            robertHealthDropping &&
            !playerHealthDropping

    /** True when the player is firing continuously (a real burst). */
    fun isContinuousPlayerFire(playerBurstShots: Int): Boolean =
        playerBurstShots >= COUNTER_FIRE_BURST_SHOTS

    /**
     * True when Robert answers with continuous fire: the player is firing
     * continuously, or the player's health is dropping, or Robert already
     * holds the advantage / presses a damaged weapon.
     */
    fun shouldFireContinuously(
        playerBurstShots: Int,
        playerHealthDropping: Boolean,
        situation: CombatSituation
    ): Boolean =
        playerHealthDropping ||
            isContinuousPlayerFire(playerBurstShots) ||
            situation == CombatSituation.PRESSURE ||
            situation == CombatSituation.ADVANTAGE

    /** True when this tick should be a sideways strafe instead of a straight walk. */
    fun shouldStrafe(random: Random): Boolean = random.nextFloat() < STRAFE_CHANCE

    /** Which way Robert strafes: +1 to his right, -1 to his left. */
    fun strafeDirection(random: Random): Int = if (random.nextBoolean()) 1 else -1

    /** Legacy defence order: medkit first, then cover, then glue wall. */
    fun nextDefence(medikits: Int, covers: Int, glueWalls: Int): DefenceItem? = when {
        medikits > 0 -> DefenceItem.MEDKIT
        covers > 0 -> DefenceItem.COVER
        glueWalls > 0 -> DefenceItem.GLUE_WALL
        else -> null
    }

    /**
     * Situational defence choice. Robert now really uses everything he
     * carries, picked for the moment instead of always in the same order:
     *
     *  - under close fire (70% proximity or more) he raises a barrier first:
     *    a cover when he has one, otherwise a glue wall;
     *  - below half health he heals with a medkit;
     *  - otherwise he falls back to cover, glue wall and medkit in turn.
     */
    fun chooseDefence(
        medikits: Int,
        covers: Int,
        glueWalls: Int,
        robertHp: Int,
        robertMaxHp: Int,
        proximityPercent: Float
    ): DefenceItem? {
        val fraction = if (robertMaxHp > 0) robertHp.toFloat() / robertMaxHp.toFloat() else 0f
        if (proximityPercent >= Combat.STRONG_ZONE_MAX) {
            if (covers > 0) return DefenceItem.COVER
            if (glueWalls > 0) return DefenceItem.GLUE_WALL
        }
        if (fraction <= 0.5f && medikits > 0) return DefenceItem.MEDKIT
        if (covers > 0) return DefenceItem.COVER
        if (glueWalls > 0) return DefenceItem.GLUE_WALL
        if (medikits > 0) return DefenceItem.MEDKIT
        return null
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
     * (damaged weapon, or a glue wall he can break with a sniper) always wins.
     * A glue wall he cannot break pushes him out to the far band instead, so
     * he never walks into a wall he cannot shoot through. Otherwise a big
     * advantage pushes him into the 90%-100% band, a moderate advantage into
     * the 70%-90% band, and a disadvantage pulls him back to 40%-70%.
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
        deceptionShotsLeft: Int,
        robertHasSniper: Boolean = true
    ): FireRange {
        if (playerWeaponDamaged) return FireRange.CLOSE
        if (playerGlueWallActive) return if (robertHasSniper) FireRange.CLOSE else FireRange.FAR
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

    /** The window used while Robert answers continuous fire shot for shot. */
    fun continuousFireWindow(): LongRange = 380L..620L

    /** A jittered delay before Robert's next shot, for the situation. */
    fun fireIntervalMs(
        situation: CombatSituation,
        fleeing: Boolean,
        random: Random,
        continuous: Boolean = false
    ): Long {
        val window = if (continuous && !fleeing) {
            continuousFireWindow()
        } else {
            fireIntervalWindow(situation, fleeing)
        }
        return if (window.last <= window.first) {
            window.first
        } else {
            window.first + (random.nextFloat() * (window.last - window.first)).toLong()
        }
    }
}
