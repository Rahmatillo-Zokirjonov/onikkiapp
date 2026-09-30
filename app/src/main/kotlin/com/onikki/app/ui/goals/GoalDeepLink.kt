package com.onikki.app.ui.goals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Open this goal" from outside Reja (the Bosh sahifa card): Reja switches to its Maqsadlar tab,
 * then GoalsRoute opens the goal and consumes the request. [ALL] just opens the list.
 */
object GoalDeepLink {
    const val ALL = 0L
    private val pending = MutableStateFlow<Long?>(null)
    val requested: StateFlow<Long?> = pending

    fun request(goalId: Long) {
        pending.value = goalId
    }

    fun consume() {
        pending.value = null
    }
}
