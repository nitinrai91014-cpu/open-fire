package com.blindtechnexus.openfire.model

/**
 * Pure missile rules, shared by the shop, the battle engine and Robert.
 *
 * A missile is a guided, guaranteed kill:
 *  - it unlocks after round two has been completed and costs
 *    [MISSILE_PRICE] coins, appearing in the normal weapon inventory;
 *  - every launch consumes exactly one missile power ([MISSILE_POWER_PRICE]
 *    coins each). Powers stack like defences and must be bought again once
 *    used;
 *  - once launched it tracks its target with 100% accuracy and reduces the
 *    target directly to zero health, no matter whether the target runs,
 *    hides behind a glue wall or hides behind a cover. Even the player's
 *    second chance cannot save against a direct missile hit;
 *  - the target still tries to run as far away as possible inside the 100 m
 *    arena while the missile sound plays. If the target can run, the sound
 *    loops and is cut the moment the missile impacts; if the target has
 *    nowhere left to run, the sound plays once and the missile impacts as
 *    it ends. The explosion (and the hit sound of the destroyed fighter)
 *    always plays at impact;
 *  - from round four Robert owns an anti-missile system, but he may only
 *    fire it in an end-game situation: his health below
 *    [ANTI_MISSILE_MAX_HP] while a player missile is in the air. Round
 *    four holds one interceptor, round five holds two;
 *  - from round four Robert also carries one missile power per game. He
 *    spends it only as a desperation weapon while he is clearly losing.
 */
object MissileRules {
    /** Shop price of the missile launcher itself. */
    const val MISSILE_PRICE = 2000

    /** Shop price of one consumable missile power. */
    const val MISSILE_POWER_PRICE = 600

    /**
     * Length of `missile-sound.mp3` (about 14 seconds). A direct hit lands
     * exactly when this sound finishes.
     */
    const val SOUND_DURATION_MS = 14_800L

    /** Extra chase time for every metre the target can still run away. */
    const val CHASE_MS_PER_METER = 60L

    /** Hard cap so a fleeing chase can never drag on forever. */
    const val MAX_FLIGHT_MS = 22_000L

    /** Robert may fire an interceptor only while below this much health. */
    const val ANTI_MISSILE_MAX_HP = 100

    /** First round that carries missile and anti-missile equipment. */
    const val FIRST_MISSILE_ROUND = 4

    /** Robert's health fraction at (or below) which he is "clearly losing". */
    const val ENEMY_LAUNCH_MAX_HP_FRACTION = 0.40f

    /** Shots Robert must have fired before he considers his missile. */
    const val ENEMY_LAUNCH_MIN_SHOTS = 3

    /** True when the target still has room to run away inside the arena. */
    fun targetWillFlee(distance: Float): Boolean =
        distance < Combat.MAX_RANGE - 1f

    /**
     * How long a launched missile stays in the air. A target with nowhere
     * to run is hit after exactly one play of the missile sound. A fleeing
     * target is chased: the sound loops and the flight grows with the room
     * the target has to run, capped by [MAX_FLIGHT_MS]. The missile always
     * catches its target either way.
     */
    fun flightMs(distanceAtLaunch: Float): Long {
        val distance = distanceAtLaunch.coerceIn(0f, Combat.MAX_RANGE)
        if (!targetWillFlee(distance)) return SOUND_DURATION_MS
        val roomToRun = Combat.MAX_RANGE - distance
        val extra = (roomToRun * CHASE_MS_PER_METER).toLong()
        return (SOUND_DURATION_MS + extra).coerceIn(SOUND_DURATION_MS, MAX_FLIGHT_MS)
    }

    /** True while [flightMs] says the missile sound must loop (a chase). */
    fun soundLoops(distanceAtLaunch: Float): Boolean = targetWillFlee(distanceAtLaunch)

    /** True when a launch is allowed right now. */
    fun canLaunch(ownsMissile: Boolean, missilePowers: Int, missileInFlight: Boolean): Boolean =
        ownsMissile && missilePowers > 0 && !missileInFlight

    /** Interceptors Robert carries in a round: 0, then 1 in round 4, 2 in round 5. */
    fun antiMissilesForRound(round: Int): Int = when (round.coerceIn(1, Progression.ROUNDS_PER_MATCH)) {
        4 -> 1
        5 -> 2
        else -> 0
    }

    /** Missile powers Robert carries in a round: one per game from round four. */
    fun enemyMissilePowersForRound(round: Int): Int =
        if (round.coerceIn(1, Progression.ROUNDS_PER_MATCH) >= FIRST_MISSILE_ROUND) 1 else 0

    /**
     * True when Robert fires an interceptor against a player missile. All
     * end-game conditions must hold at once: round four or later, an
     * interceptor still unused, a missile actually in the air, and his
     * health below [ANTI_MISSILE_MAX_HP]. A healthy Robert never wastes
     * one, and each interceptor can only ever be used once.
     */
    fun willIntercept(
        round: Int,
        robertHp: Int,
        antiMissilesLeft: Int,
        missileInFlight: Boolean
    ): Boolean =
        missileInFlight &&
            round.coerceIn(1, Progression.ROUNDS_PER_MATCH) >= FIRST_MISSILE_ROUND &&
            antiMissilesLeft > 0 &&
            robertHp in 1 until ANTI_MISSILE_MAX_HP

    /**
     * True when Robert spends his single missile power. It is a desperation
     * weapon only: round four or later, still unused, the fight properly
     * underway, his own health badly down and the player healthier than he
     * is. A Robert who is winning never launches it.
     */
    fun shouldEnemyLaunch(
        round: Int,
        missilePowers: Int,
        robertHp: Int,
        robertMaxHp: Int,
        playerHp: Int,
        playerMaxHp: Int,
        robertShots: Int,
        missileInFlight: Boolean
    ): Boolean {
        if (missileInFlight || missilePowers <= 0) return false
        if (round.coerceIn(1, Progression.ROUNDS_PER_MATCH) < FIRST_MISSILE_ROUND) return false
        if (robertMaxHp <= 0 || playerMaxHp <= 0 || robertHp <= 0 || playerHp <= 0) return false
        if (robertShots < ENEMY_LAUNCH_MIN_SHOTS) return false
        val robertFraction = robertHp.toFloat() / robertMaxHp.toFloat()
        val playerFraction = playerHp.toFloat() / playerMaxHp.toFloat()
        return robertFraction <= ENEMY_LAUNCH_MAX_HP_FRACTION && playerFraction > robertFraction
    }
}
