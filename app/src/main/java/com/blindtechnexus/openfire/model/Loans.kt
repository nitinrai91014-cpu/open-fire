package com.blindtechnexus.openfire.model

/** Loan principal and coin balance live together in the encrypted profile. */
object Loans {
    const val ELIGIBILITY_LIMIT = 500
    val AMOUNTS = listOf(500, 1_000, 1_500)

    data class IssueResult(val stats: GameStats, val reasonKey: String? = null) {
        val success: Boolean get() = reasonKey == null
    }

    data class Repayment(val deducted: Int, val credited: Int, val remaining: Int)

    /** Validate again at the point of issue, not just when rendering the shop. */
    fun issue(stats: GameStats, amount: Int): IssueResult = when {
        amount !in AMOUNTS -> IssueResult(stats, "loanInvalidAmount")
        stats.loanBalance > 0 -> IssueResult(stats, "loanAlreadyActive")
        stats.coins >= ELIGIBILITY_LIMIT -> IssueResult(stats, "loanNotEligible")
        stats.coins < 0 -> IssueResult(stats, "loanNotEligible")
        else -> IssueResult(stats.copy(coins = stats.coins + amount, loanBalance = amount))
    }

    /** Only winnings repay the principal; no interest, wallet deduction or negative balance. */
    fun repay(balance: Int, winnings: Int): Repayment {
        val safeBalance = balance.coerceAtLeast(0)
        val safeWinnings = winnings.coerceAtLeast(0)
        val deducted = minOf(safeBalance, safeWinnings)
        return Repayment(deducted, safeWinnings - deducted, safeBalance - deducted)
    }
}
