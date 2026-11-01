package com.blindtechnexus.openfire.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A monotonic game clock that does not advance while a battle is paused. */
class BattleClock(private val monotonicMs: () -> Long) {
    private val pausedState = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = pausedState.asStateFlow()
    private var pausedAt = 0L
    private var totalPaused = 0L

    @Synchronized
    fun nowMs(): Long = (if (pausedState.value) pausedAt else monotonicMs()) - totalPaused

    @Synchronized
    fun pause() {
        if (pausedState.value) return
        pausedAt = monotonicMs()
        pausedState.value = true
    }

    @Synchronized
    fun resume() {
        if (!pausedState.value) return
        totalPaused += (monotonicMs() - pausedAt).coerceAtLeast(0L)
        pausedState.value = false
    }

    /** Wait for game time, preserving the exact remaining delay across pauses. */
    suspend fun awaitDelay(durationMs: Long) {
        val deadline = nowMs() + durationMs.coerceAtLeast(0L)
        while (true) {
            pausedState.first { !it }
            val remaining = deadline - nowMs()
            if (remaining <= 0L) return
            // Wake immediately on pause, otherwise the timeout is the deadline.
            withTimeoutOrNull(remaining) { pausedState.first { it } }
        }
    }
}
