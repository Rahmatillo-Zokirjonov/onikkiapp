package com.onikki.app.domain.vocab

/** Lives on the learning path: a wrong answer in a lesson costs one; they come back with time or practice. */
data class HeartState(val hearts: Int, /** Epoch millis the refill clock last moved. */ val updatedAt: Long)

object Hearts {
    const val MAX = 5
    const val REFILL_MS = 5L * 60 * 60 * 1000

    /** Adds one heart per [REFILL_MS] since [HeartState.updatedAt]; a full bar keeps the clock at now. */
    fun refill(state: HeartState, now: Long): HeartState {
        if (state.hearts >= MAX) return HeartState(MAX, now)
        val gained = ((now - state.updatedAt) / REFILL_MS).toInt().coerceAtLeast(0)
        if (gained == 0) return state
        val hearts = (state.hearts + gained).coerceAtMost(MAX)
        return HeartState(hearts, if (hearts >= MAX) now else state.updatedAt + gained * REFILL_MS)
    }

    fun lose(state: HeartState, now: Long): HeartState {
        val current = refill(state, now)
        // Losing from a full bar starts the refill clock.
        return HeartState((current.hearts - 1).coerceAtLeast(0), if (current.hearts >= MAX) now else current.updatedAt)
    }

    fun gain(state: HeartState, now: Long): HeartState {
        val current = refill(state, now)
        val hearts = (current.hearts + 1).coerceAtMost(MAX)
        return HeartState(hearts, if (hearts >= MAX) now else current.updatedAt)
    }

    /** Millis until the next heart, or null when full. */
    fun nextIn(state: HeartState, now: Long): Long? {
        val current = refill(state, now)
        if (current.hearts >= MAX) return null
        return (current.updatedAt + REFILL_MS - now).coerceAtLeast(0)
    }
}

/** Correct answers in a row inside a session. */
object Combo {
    fun next(current: Int, verdict: Verdict): Int = if (verdict == Verdict.WRONG) 0 else current + 1

    /** Only worth celebrating from three in a row, and then on every multiple of five. */
    fun shouldShow(combo: Int): Boolean = combo == 3 || (combo >= 5 && combo % 5 == 0)
}
