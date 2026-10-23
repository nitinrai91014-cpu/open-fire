package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.Progression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks 10 wins per level, five levels per round and five rounds per match. */
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

        val tenth = Progression.applyWin(stats.copy(wins = 10))
        assertEquals(2, tenth.stats.level)
        assertEquals(0, tenth.stats.levelWins)
        assertEquals(1, tenth.stats.round)
        assertTrue(tenth.leveledUp)
        assertFalse(tenth.roundCompleted)
    }

    @Test
    fun fiveLevelsCompleteExactlyOneRound() {
        val stats = GameStats(level = 5, round = 2, levelWins = 9, completedRounds = 1)
        val result = Progression.applyWin(stats)
        assertTrue(result.leveledUp)
        assertTrue(result.roundCompleted)
        assertFalse(result.matchWon)
        assertEquals(1, result.stats.level)
        assertEquals(3, result.stats.round)
        assertEquals(0, result.stats.levelWins)
        assertEquals(2, result.stats.completedRounds)
        assertTrue(Progression.missileUnlocked(result.stats))
    }

    @Test
    fun fifthRoundCompletionWinsMatchWithoutResettingLifetimeUnlocks() {
        val stats = GameStats(level = 5, round = 5, levelWins = 9, completedRounds = 4)
        val result = Progression.applyWin(stats)
        assertTrue(result.leveledUp)
        assertTrue(result.roundCompleted)
        assertTrue(result.matchWon)
        assertEquals(1, result.stats.round)
        assertEquals(1, result.stats.level)
        assertEquals(5, result.stats.completedRounds)
        assertTrue(Progression.missileUnlocked(result.stats))
    }

    @Test
    fun clearedStatsAreAllZeroWithLevelAndRoundOne() {
        val cleared = GameStats.cleared()
        assertEquals(0, cleared.games)
        assertEquals(0, cleared.wins)
        assertEquals(0, cleared.kills)
        assertEquals(0, cleared.coins)
        assertEquals(0, cleared.levelWins)
        assertEquals(0, cleared.completedRounds)
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
            levelWins = 77,
            completedRounds = -4
        )
        val fixed = Progression.normalize(weird)
        assertEquals(0, fixed.games)
        assertEquals(0, fixed.wins)
        assertEquals(0, fixed.kills)
        assertEquals(0, fixed.coins)
        assertEquals(1, fixed.round)
        assertEquals(5, fixed.level)
        assertEquals(7, fixed.levelWins)
        assertEquals(0, fixed.completedRounds)
    }

    @Test
    fun healthScalesByBothRoundAndLevel() {
        assertEquals(200, Progression.hpFor(1, 1))
        assertEquals(300, Progression.hpFor(1, 2))
        assertEquals(400, Progression.hpFor(1, 3))
        assertEquals(500, Progression.hpFor(1, 4))
        assertEquals(600, Progression.hpFor(1, 5))
        assertEquals(700, Progression.hpFor(2, 1))
        assertEquals(800, Progression.hpFor(2, 2))
        assertEquals(900, Progression.hpFor(2, 3))
        assertEquals(2600, Progression.hpFor(5, 5))

        var expected = 200
        for (round in 1..5) {
            for (level in 1..5) {
                assertEquals("Round $round level $level", expected, Progression.hpFor(round, level))
                expected += 100
            }
        }
        assertTrue(Progression.isFinalRound(5))
        assertFalse(Progression.isFinalRound(4))
    }

    @Test
    fun missileAndAntiMissileRequireTwoCompletedRounds() {
        assertEquals(5, Progression.LEVELS_PER_ROUND)
        assertFalse(Progression.missileUnlocked(GameStats(completedRounds = 0)))
        assertFalse(Progression.missileUnlocked(GameStats(completedRounds = 1)))
        assertTrue(Progression.missileUnlocked(GameStats(completedRounds = 2)))
        assertFalse(Progression.antiMissileUnlocked(GameStats(completedRounds = 0)))
        assertFalse(Progression.antiMissileUnlocked(GameStats(completedRounds = 1)))
        assertTrue(Progression.antiMissileUnlocked(GameStats(completedRounds = 2)))
    }
}
