package com.blindtechnexus.openfire.model

import kotlin.math.roundToInt

/**
 * Pure rules for the one-per-fight second chance.
 *
 * Player: the first time health reaches zero it is restored to exactly half
 * of the maximum. The next zero is a defeat.
 *
 * Devil: the first zero restores half of its maximum health and activates its
 * three survival items — a Gloo Wall, a cover and a medkit. Every later zero
 * consumes one of those items to keep fighting. Only when all three have been
 * used does the next zero health defeat it for good.
 */
object Revive {
    const val DEVIL_ITEM_COUNT = 3

    /** Fraction of maximum health restored by one devil survival item. */
    const val DEVIL_ITEM_HEAL_FRACTION = 0.15f

    enum class Outcome {
        /** The devil's first zero: half health + all three items. */
        SECOND_CHANCE,

        /** A later zero survived by consuming one item. */
        USED_ITEM,

        /** No second chance and no items left: final defeat. */
        DEFEATED
    }

    data class DevilResult(
        val outcome: Outcome,
        val hp: Int,
        val itemsLeft: Int,
        /** 0 = Gloo Wall, 1 = cover, 2 = medkit (only valid for [Outcome.USED_ITEM]). */
        val itemIndex: Int,
        val secondChanceUsed: Boolean
    )

    /** Resolves what happens when the devil's health reaches zero. */
    fun devilAtZero(maxHp: Int, itemsLeft: Int, secondChanceUsed: Boolean): DevilResult {
        if (!secondChanceUsed) {
            val restored = Combat.secondChanceHealth(maxHp)
            return DevilResult(
                outcome = Outcome.SECOND_CHANCE,
                hp = restored,
                itemsLeft = DEVIL_ITEM_COUNT,
                itemIndex = -1,
                secondChanceUsed = true
            )
        }

        if (itemsLeft > 0) {
            val remaining = itemsLeft - 1
            val index = (DEVIL_ITEM_COUNT - 1 - remaining).coerceIn(0, DEVIL_ITEM_COUNT - 1)
            val restored = (maxHp * DEVIL_ITEM_HEAL_FRACTION).roundToInt().coerceAtLeast(1)
            return DevilResult(
                outcome = Outcome.USED_ITEM,
                hp = restored,
                itemsLeft = remaining,
                itemIndex = index,
                secondChanceUsed = true
            )
        }

        return DevilResult(
            outcome = Outcome.DEFEATED,
            hp = 0,
            itemsLeft = 0,
            itemIndex = -1,
            secondChanceUsed = true
        )
    }

    /**
     * The devil flees when its health falls below the half-health pool it
     * received from its second chance.
     */
    fun shouldDevilFlee(hp: Int, maxHp: Int, fleeAvailable: Boolean): Boolean =
        fleeAvailable && hp > 0 && hp < maxHp / 2
}
