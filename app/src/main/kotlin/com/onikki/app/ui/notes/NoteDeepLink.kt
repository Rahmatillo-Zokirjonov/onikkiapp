package com.onikki.app.ui.notes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A note to open, requested from outside the Compose tree (a reminder notification or the alert screen).
 * MainActivity sets it; the nav host switches to the Qaydlar tab and NotesRoute opens and consumes it.
 */
object NoteDeepLink {
    private val pending = MutableStateFlow<Long?>(null)
    val requested: StateFlow<Long?> = pending

    fun request(noteId: Long) {
        pending.value = noteId
    }

    fun consume() {
        pending.value = null
    }
}
