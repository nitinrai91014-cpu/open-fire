package com.blindtechnexus.openfire.model

/** React to actual damage, not just trigger pulls or hits absorbed by cover. */
object RetreatRules {
    const val HEAVY_HIT_HP = 30
    const val PRESSURE_WINDOW_MS = 3_500L
    const val SUSTAINED_HITS = 2

    fun underHeavyFire(recentHits: Int, lastDamage: Int, elapsedMs: Long): Boolean =
        elapsedMs in 0..PRESSURE_WINDOW_MS &&
            (recentHits >= SUSTAINED_HITS || lastDamage >= HEAVY_HIT_HP)
}
