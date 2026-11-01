package com.blindtechnexus.openfire

import com.blindtechnexus.openfire.model.BattleClock
import com.blindtechnexus.openfire.model.FireCadence
import com.blindtechnexus.openfire.model.GameStats
import com.blindtechnexus.openfire.model.Loans
import com.blindtechnexus.openfire.model.Progression
import com.blindtechnexus.openfire.model.RetreatRules
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoansAndBattleClockTest {
    @Test
    fun loansAreAvailableOnlyBelowThresholdAndWhenDebtFree() {
        for (amount in Loans.AMOUNTS) {
            val issued = Loans.issue(GameStats(coins = 499), amount)
            assertTrue(issued.success)
            assertEquals(499 + amount, issued.stats.coins)
            assertEquals(amount, issued.stats.loanBalance)
            assertFalse(Loans.issue(issued.stats.copy(coins = 0), amount).success)
            assertFalse(Loans.issue(GameStats(coins = 500), amount).success)
        }
        assertFalse(Loans.issue(GameStats(coins = -1), 500).success)
        assertFalse(Loans.issue(GameStats(coins = 0), 501).success)
        assertFalse(Loans.issue(GameStats(coins = 0, loanBalance = 1), 1500).success)
        assertEquals(0, Progression.normalize(GameStats(loanBalance = -5)).loanBalance)
    }

    @Test
    fun winningsRepayOnlyDebtAndCarryTheRemainder() {
        val first = Loans.repay(1500, 1100)
        assertEquals(1100, first.deducted)
        assertEquals(0, first.credited)
        assertEquals(400, first.remaining)
        val second = Loans.repay(first.remaining, 650)
        assertEquals(400, second.deducted)
        assertEquals(250, second.credited)
        assertEquals(0, second.remaining)
        assertTrue(Loans.issue(GameStats(coins = 250, loanBalance = second.remaining), 500).success)
        assertEquals(Loans.Repayment(0, 200, 0), Loans.repay(0, 200))
        assertEquals(Loans.Repayment(0, 0, 1500), Loans.repay(1500, 0))
        assertEquals(Loans.Repayment(0, 0, 0), Loans.repay(-1, -1))
    }

    @Test
    fun clockFreezesAcrossRepeatedPausesAndPreservesRemainingDelay() = runBlocking {
        var monotonic = 1_000L
        val clock = BattleClock { monotonic }
        var completed = false
        val job = launch { clock.awaitDelay(200); completed = true }
        yield()
        monotonic += 80
        clock.pause()
        val frozen = clock.nowMs()
        monotonic += 5_000
        delay(30)
        assertEquals(frozen, clock.nowMs())
        assertFalse(completed)
        clock.resume()
        yield()
        assertEquals(frozen, clock.nowMs())
        monotonic += 50
        clock.pause()
        monotonic += 10_000
        delay(30)
        assertFalse(completed)
        clock.resume()
        monotonic += 70
        withTimeout(500) { job.join() }
        assertTrue(completed)
        assertEquals(1_200L, clock.nowMs())
    }

    @Test
    fun pausedDelaysCanStillBeCancelled() = runBlocking {
        val clock = BattleClock { 0L }
        clock.pause()
        val job = launch { clock.awaitDelay(100) }
        yield()
        withTimeout(500) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }

    @Test
    fun sustainedAndHeavyDamageTriggerRapidRetreatButMissesDoNot() {
        assertFalse(RetreatRules.underHeavyFire(0, 0, 0))
        assertFalse(RetreatRules.underHeavyFire(1, 14, 1_000))
        assertTrue(RetreatRules.underHeavyFire(2, 14, 1_000))
        assertTrue(RetreatRules.underHeavyFire(1, 30, 0))
        assertTrue(RetreatRules.underHeavyFire(2, 14, RetreatRules.PRESSURE_WINDOW_MS))
        assertFalse(RetreatRules.underHeavyFire(5, 60, RetreatRules.PRESSURE_WINDOW_MS + 1))
        assertEquals(1_500L, FireCadence.NORMAL_MS)
        assertEquals(1_000L, FireCadence.CONTINUOUS_MS)
    }
}
