package com.blindtechnexus.openfire.model

/**
 * Strict progression ladder:
 *  - 10 wins   = 1 level
 *  - 10 levels = 1 round
 *  - 5 rounds  = the whole match
 *
 * The wins collected inside the current level are tracked explicitly
 * ([GameStats.levelWins]) instead of being inferred from the lifetime win
 * count, so clearing stats or migrating old saves can never desynchronise the
 * level ladder from the wins counter.
 *
 * Every round the enemy and the player receive the round health:
 * 300, 400, 500, 600 and 750 health for rounds one to five.
 */
object Progression {
    const val WINS_PER_LEVEL = 10
    const val LEVELS_PER_ROUND = 10
    const val ROUNDS_PER_MATCH = 5

    data class WinResult(
        val stats: GameStats,
        val leveledUp: Boolean,
        val roundCompleted: Boolean,
        /** True when the fifth round was completed: the match is won. */
        val matchWon: Boolean = false
    )

    /** Applies one won game to [stats] and resolves level / round / match promotion. */
    fun applyWin(stats: GameStats): WinResult {
        var level = stats.level.coerceIn(1, LEVELS_PER_ROUND)
        var round = stats.round.coerceIn(1, ROUNDS_PER_MATCH)
        var levelWins = stats.levelWins.coerceIn(0, WINS_PER_LEVEL - 1)
        var leveledUp = false
        var roundCompleted = false
        var matchWon = false

        levelWins += 1
        if (levelWins >= WINS_PER_LEVEL) {
            levelWins = 0
            leveledUp = true
            if (level >= LEVELS_PER_ROUND) {
                level = 1
                if (round >= ROUNDS_PER_MATCH) {
                    roundCompleted = true
                    matchWon = true
                    round = 1
                } else {
                    round += 1
                    roundCompleted = true
                }
            } else {
                level += 1
            }
        }

        return WinResult(
            stats = stats.copy(level = level, round = round, levelWins = levelWins),
            leveledUp = leveledUp,
            roundCompleted = roundCompleted,
            matchWon = matchWon
        )
    }

    /** Health both fighters receive in the given round. */
    fun hpForRound(round: Int): Int = RobertRules.hpForRound(round)

    /** True when the given round is the last one of a match. */
    fun isFinalRound(round: Int): Boolean = round >= ROUNDS_PER_MATCH

    /** Keeps loaded stats inside the valid ladder ranges. */
    fun normalize(stats: GameStats): GameStats = stats.copy(
        games = stats.games.coerceAtLeast(0),
        wins = stats.wins.coerceAtLeast(0),
        kills = stats.kills.coerceAtLeast(0),
        coins = stats.coins.coerceAtLeast(0),
        round = stats.round.coerceIn(1, ROUNDS_PER_MATCH),
        level = stats.level.coerceIn(1, LEVELS_PER_ROUND),
        levelWins = ((stats.levelWins % WINS_PER_LEVEL) + WINS_PER_LEVEL) % WINS_PER_LEVEL
    )
}
