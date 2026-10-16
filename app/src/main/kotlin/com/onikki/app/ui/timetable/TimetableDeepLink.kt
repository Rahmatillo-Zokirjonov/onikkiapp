package com.onikki.app.ui.timetable

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** "Open Jadval" from the Bosh sahifa card: Reja switches to its Jadval tab and consumes the request. */
object TimetableDeepLink {
    private val pending = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = pending

    fun request() {
        pending.value = true
    }

    fun consume() {
        pending.value = false
    }
}
