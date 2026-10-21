package com.blindtechnexus.openfire.model

/**
 * Strict progression ladder:
 *  - 10 wins  = 1 level
 *  - 30 levels = 1 round
 *
 * The wins collected inside the current level are tracked explicitly
 * ([GameStats.levelWins]) instead of being inferred from the lifetime win
 * count, so clearing stats or migrating old saves can never desynchronise the
 * level ladder from the wins counter.
 */
object Progression {
    const val WINS_PER_LEVEL = 10
    const val LEVELS_PER_ROUND = 30

    data class WinResult(
        val stats: GameStats,
        val leveledUp: Boolean,
        val roundCompleted: Boolean
    )

    /** Applies one won game to [stats] and resolves level / round promotion. */
    fun applyWin(stats: GameStats): WinResult {
        var level = stats.level.coerceIn(1, LEVELS_PER_ROUND)
        var round = stats.round.coerceAtLeast(1)
        var levelWins = stats.levelWins.coerceIn(0, WINS_PER_LEVEL - 1)
        var leveledUp = false
        var roundCompleted = false

        levelWins += 1
        if (levelWins >= WINS_PER_LEVEL) {
            levelWins = 0
            leveledUp = true
            if (level >= LEVELS_PER_ROUND) {
                level = 1
                round += 1
                roundCompleted = true
            } else {
                level += 1
            }
        }

        return WinResult(
            stats = stats.copy(level = level, round = round, levelWins = levelWins),
            leveledUp = leveledUp,
            roundCompleted = roundCompleted
        )
    }

    /** Keeps loaded stats inside the valid ladder ranges. */
    fun normalize(stats: GameStats): GameStats = stats.copy(
        games = stats.games.coerceAtLeast(0),
        wins = stats.wins.coerceAtLeast(0),
        kills = stats.kills.coerceAtLeast(0),
        coins = stats.coins.coerceAtLeast(0),
        round = stats.round.coerceAtLeast(1),
        level = stats.level.coerceIn(1, LEVELS_PER_ROUND),
        levelWins = ((stats.levelWins % WINS_PER_LEVEL) + WINS_PER_LEVEL) % WINS_PER_LEVEL
    )
}
