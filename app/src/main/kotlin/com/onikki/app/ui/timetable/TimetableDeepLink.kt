package com.onikki.app.ui.timetable

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Open Jadval" (Bosh sahifa card) or "open the homework list" (block screen): the nav host goes to Reja,
 * Reja switches to its Jadval tab, and the Jadval screen consumes the request (opening Vazifalar if asked).
 */
object TimetableDeepLink {
    enum class Target { TIMETABLE, HOMEWORK }

    private val pending = MutableStateFlow<Target?>(null)
    val requested: StateFlow<Target?> = pending

    fun request(target: Target = Target.TIMETABLE) {
        pending.value = target
    }

    fun consume() {
        pending.value = null
    }
}
