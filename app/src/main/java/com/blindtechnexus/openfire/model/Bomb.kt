package com.blindtechnexus.openfire.model

import kotlin.math.roundToInt

/**
 * The real, measured length of every sound that drives a game timer.
 *
 * These values are read straight from the shipped assets in
 * `app/src/main/assets/sounds`, so a flight, a countdown or an explosion
 * always ends exactly when its sound ends instead of guessing.
 */
object SoundDurations {
    /** missile-sound.mp3 */
    const val MISSILE_MS = 14_800L

    /** explosion-sound.mp3: the tail every blast waits for. */
    const val EXPLOSION_MS = 6_100L

    /** atom-bom-sound.mp3 */
    const val ATOM_BOMB_MS = 7_100L

    /** grenade-sound.mp3 */
    const val GRENADE_MS = 9_100L

    /** timer-sound.mp3 (looped until the chosen duration elapses) */
    const val TIMER_LOOP_MS = 4_300L

    /** mp-40-gun.mp3 (looped during a continuous burst) */
    const val MP40_MS = 1_300L

    /** machine-gun.mp3 (looped during a continuous burst) */
    const val MACHINE_GUN_MS = 1_600L

    /** AWM-sound.mp3 */
    const val AWM_MS = 2_600L

    /** level-complete.mp3 */
    const val LEVEL_COMPLETE_MS = 2_600L

    /** round-complete.mp3 */
    const val ROUND_COMPLETE_MS = 28_200L

    /** glue-wall-crack.mp3, reused as the boundary wall impact */
    const val WALL_MS = 2_800L

    /** walk.mp3 */
    const val WALK_MS = 9_800L
}

/** The three bombs of round three and later. */
enum class BombKind(
    val id: String,
    val nameKey: String,
    val weapon: Weapon,
    /** Sound played from the throw until the blast. */
    val flightSoundKey: String,
    /** Fixed flight length; the timer bomb is driven by its picker instead. */
    val flightMs: Long
) {
    GRENADE(
        id = "grenade",
        nameKey = "gunGrenade",
        weapon = Weapon.GRENADE,
        flightSoundKey = "grenade",
        flightMs = SoundDurations.GRENADE_MS
    ),
    ATOM(
        id = "atomBomb",
        nameKey = "gunAtomBomb",
        weapon = Weapon.ATOM_BOMB,
        flightSoundKey = "atomBomb",
        flightMs = SoundDurations.ATOM_BOMB_MS
    ),
    TIMER(
        id = "timerBomb",
        nameKey = "gunTimerBomb",
        weapon = Weapon.TIMER_BOMB,
        flightSoundKey = "timerLoop",
        flightMs = 0L
    );

    /** True for the bomb whose duration is chosen by the player. */
    val needsTimerSelection: Boolean
        get() = this == TIMER

    /** The blast sound of this bomb: the timer bomb explodes like the rest. */
    val blastSoundKey: String
        get() = "explosion"

    /** Whether this bomb tracks its target like a missile (all bombs now do). */
    val tracking: Boolean
        get() = true

    companion object {
        fun fromId(id: String?): BombKind? = entries.firstOrNull { it.id == id }

        fun fromWeapon(weapon: Weapon?): BombKind? =
            entries.firstOrNull { it.weapon == weapon }
    }
}

/**
 * Pure bomb rules shared by the battle engine, the shop and Robert.
 *
 * Bombs now track the enemy like the missile and kill at any cost — when a
 * bomb is released it tracks the enemy, falls onto them, and detonates. A
 * target is always destroyed unless an anti-bomb explosive intercepts it.
 *
 *  - the grenade and the atom bomb unlock in round three and fly for exactly
 *    as long as their own sound (grenade-sound, atom-bom-sound); tracking ensures
 *    the bomb follows the moving target and explodes on top of them;
 *  - the timer bomb unlocks in round three too. Swiping up asks
 *    \"Select your timer\": swiping left or right walks through 15, 30, 40, 50
 *    and 60 seconds and a tap locks the choice in. The timer sound then loops
 *    for the whole countdown, stops, and the explosion plays;
 *  - every bomb now kills its enemy target at any cost, exactly like the missile.
 *    A target without an anti-bomb explosive cannot escape by running — the bomb
 *    tracks them. The 50m race is kept for the timer bomb thrower safety but
 *    the enemy is always hit;
 *  - the timer bomb is the exception: its thrower must stay at least
 *    [SAFE_DISTANCE] metres away from the destination, or both fighters are
 *    destroyed together and the thrower loses;
 *  - from round three Robert carries anti-bomb explosives per the new limits:
 *    once in round 3, three times in round 4, and unlimited in round 5;
 *  - bomb power: each Bomb Power upgrade costs 1500 coins and allows each bomb
 *    to be used once (1 Bomb Power = 1 use of any bomb type). Similar to Missile
 *    Power, it stacks and must be bought again once used;
 *  - an intercepted, disposed or destroyed bomb always plays the explosion
 *    sound, exactly like an intercepted missile does.
 */
object BombRules {
    /** Shop price of one anti-bomb explosive defence. */
    const val ANTI_BOMB_PRICE = 2600

    /** Shop price of one Bomb Power (allows one bomb use). */
    const val BOMB_POWER_PRICE = 1500

    /** First round that carries bombs and anti-bomb explosives. */
    const val FIRST_BOMB_ROUND = 3

    /** A fighter inside this distance from the destination is destroyed. */
    const val BLAST_RADIUS = 50f

    /** Distance the timer bomb thrower must keep to survive its own bomb. */
    const val SAFE_DISTANCE = 50f

    /** The timer durations the picker offers, in seconds (up to one minute). */
    val TIMER_SECONDS: List<Int> = listOf(15, 30, 40, 50, 60)

    /** The picker starts on 30 seconds. */
    const val DEFAULT_TIMER_INDEX = 1

    /** The longest timer the game allows: one minute. */
    const val MAX_TIMER_SECONDS = 60

    /**
     * Sprint speed of a fighter running out of a blast radius, in metres per
     * second. Kept for legacy race calculations but bombs now track their target
     * so the escape is no longer guaranteed.
     */
    const val BOMB_ESCAPE_SPEED_MPS = 6f

    /** How often the bomb escape run recalculates its step. */
    const val BOMB_FLEE_TICK_MS = 150L

    /** Metres covered per bomb escape tick. */
    const val BOMB_FLEE_STEP_M = 0.9f

    /** How often a tracking bomb updates its destination (ms). */
    const val TRACKING_UPDATE_MS = 80L

    /** Anti-bomb explosives Robert carries per round: 1 in R3, 3 in R4, unlimited in R5. */
    fun antiBombsForRound(round: Int): Int =
        when (round.coerceIn(1, Progression.ROUNDS_PER_MATCH)) {
            3 -> 1
            4 -> 3
            5 -> 99 // unlimited — as many as he chooses
            else -> 0
        }

    /** Bombs Robert may throw per game: 1 in R3, 3 in R4, unlimited in R5. */
    fun enemyBombsForRound(round: Int): Int =
        when (round.coerceIn(1, Progression.ROUNDS_PER_MATCH)) {
            3 -> 1
            4 -> 3
            5 -> 99 // unlimited — strategic use, not stat-based
            else -> 0
        }

    /** True when bombs are part of this round at all. */
    fun bombsUnlocked(round: Int): Boolean =
        round.coerceIn(1, Progression.ROUNDS_PER_MATCH) >= FIRST_BOMB_ROUND

    /** Flight time of a thrown bomb, in milliseconds. */
    fun flightMs(kind: BombKind, timerSeconds: Int = 0): Long = when (kind) {
        BombKind.TIMER -> {
            val seconds = if (timerSeconds <= 0) {
                TIMER_SECONDS[DEFAULT_TIMER_INDEX]
            } else {
                timerSeconds.coerceIn(TIMER_SECONDS.first(), MAX_TIMER_SECONDS)
            }
            seconds * 1_000L
        }
        else -> kind.flightMs
    }

    /** The timer duration in seconds for a picker index (wraps around). */
    fun timerSecondsAt(index: Int): Int {
        val size = TIMER_SECONDS.size
        val safe = ((index % size) + size) % size
        return TIMER_SECONDS[safe]
    }

    /** Wraps a picker index into the valid range. */
    fun wrapTimerIndex(index: Int): Int {
        val size = TIMER_SECONDS.size
        return ((index % size) + size) % size
    }

    /** Steps the picker one duration left or right. */
    fun stepTimerIndex(index: Int, direction: Int): Int = wrapTimerIndex(index + direction)

    /** True when a fighter this close to the destination is destroyed — now tracking ensures hit. */
    fun blastKills(distanceFromBomb: Float): Boolean =
        distanceFromBomb < BLAST_RADIUS

    /**
     * Bombs now track their target like missiles: they always kill the enemy at any cost.
     * This returns true unless intercepted, guaranteeing the blast destroys the target.
     */
    fun trackingKillGuaranteed(): Boolean = true

    /**
     * True when the timer bomb also destroys its own thrower: the thrower did
     * not keep the [SAFE_DISTANCE] metre safety distance.
     */
    fun timerBombKillsThrower(throwerDistanceFromBomb: Float): Boolean =
        throwerDistanceFromBomb < SAFE_DISTANCE

    /** True while a target must sprint away from the bomb destination. */
    fun mustEscape(distanceFromBomb: Float): Boolean =
        distanceFromBomb < SAFE_DISTANCE

    /** The distance a fleeing target is trying to reach. */
    fun escapeDistance(distanceFromBomb: Float): Float =
        (distanceFromBomb + SAFE_DISTANCE).coerceIn(0f, Arena.SIZE)

    /**
     * True when Robert fires his anti-bomb explosive. From round three he has
     * limited charges per the round table (1 in R3, 3 in R4, unlimited R5).
     */
    fun willIntercept(
        round: Int,
        antiBombsLeft: Int,
        bombInFlight: Boolean
    ): Boolean =
        bombInFlight &&
            bombsUnlocked(round) &&
            antiBombsLeft > 0

    /**
     * True when Robert throws one of his bombs. Like his missile it is a
     * desperation weapon but in round 5 it becomes strategic: he throws
     * even at moderate health to force movement and control space.
     */
    fun shouldEnemyThrow(
        round: Int,
        bombsLeft: Int,
        robertHp: Int,
        robertMaxHp: Int,
        playerHp: Int,
        playerMaxHp: Int,
        robertShots: Int,
        bombInFlight: Boolean,
        missileInFlight: Boolean
    ): Boolean {
        if (bombInFlight || missileInFlight || bombsLeft <= 0) return false
        if (!bombsUnlocked(round)) return false
        if (robertMaxHp <= 0 || playerMaxHp <= 0 || robertHp <= 0 || playerHp <= 0) return false
        if (robertShots < ENEMY_THROW_MIN_SHOTS) return false
        // Round 5 strategic logic: throw bombs more liberally to control battle
        if (round == 5) {
            val robertFraction = robertHp.toFloat() / robertMaxHp.toFloat()
            // In round 5, strategic play — throw when at disadvantage or even, not just desperation
            return robertFraction <= 0.65f
        }
        val robertFraction = robertHp.toFloat() / robertMaxHp.toFloat()
        val playerFraction = playerHp.toFloat() / playerMaxHp.toFloat()
        return robertFraction <= ENEMY_THROW_MAX_HP_FRACTION && playerFraction > robertFraction
    }

    /** Robert's health fraction at (or below) which he throws a bomb (non-strategy rounds). */
    const val ENEMY_THROW_MAX_HP_FRACTION = 0.35f

    /** Shots Robert must have fired before he considers a bomb. */
    const val ENEMY_THROW_MIN_SHOTS = 3

    /**
     * Sprint speed out of a blast radius in metres per second. The round is
     * accepted so callers stay uniform with the other speed helpers, but the
     * escape pace itself never changes: see [BOMB_ESCAPE_SPEED_MPS].
     */
    @Suppress("UNUSED_PARAMETER")
    fun escapeSpeedMps(round: Int): Float = BOMB_ESCAPE_SPEED_MPS

    /** Metres covered in one escape tick. */
    fun escapeStep(round: Int): Float =
        escapeSpeedMps(round) * (BOMB_FLEE_TICK_MS / 1000f)

    /** Seconds a clean escape out of the blast radius takes. */
    fun escapeSeconds(): Float = SAFE_DISTANCE / BOMB_ESCAPE_SPEED_MPS

    /** Whether the player can throw a bomb with their current bomb powers. */
    fun canThrowWithBombPower(bombPowers: Int, bombInFlight: Boolean, missileInFlight: Boolean): Boolean =
        bombPowers > 0 && !bombInFlight && !missileInFlight

    /** Whether the bomb kind requires bomb power (all bombs do from round 3). */
    fun requiresBombPower(round: Int): Boolean = bombsUnlocked(round)
}
