package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.Progression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the required ladder: 10 wins = 1 level, 30 levels = 1 round.
 */
class ProgressionTest {

    @Test
    fun tenWinsPromoteExactlyOneLevel() {
        var stats = GameStats(wins = 0, level = 1, round = 1, levelWins = 0)

        repeat(9) { index ->
            val promotion = Progression.applyWin(stats)
            stats = promotion.stats.copy(wins = index + 1)
            assertFalse("Level must not change before the 10th win", promotion.leveledUp)
            assertEquals(1, stats.level)
            assertEquals(index + 1, stats.levelWins)
        }

        assertEquals(1, stats.needWinsForNext)

        stats = stats.copy(wins = 10)
        val tenth = Progression.applyWin(stats)
        assertEquals(2, tenth.stats.level)
        assertEquals(0, tenth.stats.levelWins)
        assertEquals(1, tenth.stats.round)
        assertTrue(tenth.leveledUp)
        assertFalse(tenth.roundCompleted)
        assertEquals(10, tenth.stats.needWinsForNext)
    }

    @Test
    fun everyWinIncrementsLevelWinsNotLevel() {
        var stats = GameStats(level = 7, round = 2, levelWins = 0)
        val result = Progression.applyWin(stats)
        stats = result.stats
        assertEquals(7, stats.level)
        assertEquals(1, stats.levelWins)
        assertEquals(2, stats.round)
        assertFalse(result.leveledUp)
    }

    @Test
    fun levelThirtyCompletionStartsTheNextRoundAtLevelOne() {
        val stats = GameStats(wins = 300, level = 30, round = 2, levelWins = 9)
        val result = Progression.applyWin(stats)
        assertTrue(result.leveledUp)
        assertTrue(result.roundCompleted)
        assertEquals(1, result.stats.level)
        assertEquals(3, result.stats.round)
        assertEquals(0, result.stats.levelWins)
    }

    @Test
    fun levelTwentyNinePromotesToThirtyWithoutFinishingTheRound() {
        val stats = GameStats(level = 29, round = 4, levelWins = 9)
        val result = Progression.applyWin(stats)
        assertTrue(result.leveledUp)
        assertFalse(result.roundCompleted)
        assertEquals(30, result.stats.level)
        assertEquals(4, result.stats.round)
    }

    @Test
    fun clearedStatsAreAllZeroWithLevelAndRoundOne() {
        val cleared = GameStats.cleared()
        assertEquals(0, cleared.games)
        assertEquals(0, cleared.wins)
        assertEquals(0, cleared.kills)
        assertEquals(0, cleared.coins)
        assertEquals(0, cleared.levelWins)
        assertEquals(1, cleared.level)
        assertEquals(1, cleared.round)
        assertEquals(10, cleared.needWinsForNext)
    }

    @Test
    fun normalizeRepairsOutOfRangeValues() {
        val weird = GameStats(
            games = -5,
            wins = -3,
            kills = -2,
            coins = -100,
            round = 0,
            level = 99,
            levelWins = 77
        )
        val fixed = Progression.normalize(weird)
        assertEquals(0, fixed.games)
        assertEquals(0, fixed.wins)
        assertEquals(0, fixed.kills)
        assertEquals(0, fixed.coins)
        assertEquals(1, fixed.round)
        assertEquals(30, fixed.level)
        assertEquals(7, fixed.levelWins)
    }
}
