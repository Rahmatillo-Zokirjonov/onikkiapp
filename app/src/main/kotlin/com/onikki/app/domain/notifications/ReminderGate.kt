package com.onikki.app.domain.notifications

import android.content.Context

/**
 * "Ilovaga kirishdan oldin" reminders that have fired but not been seen: the accessibility service
 * shows the oldest before the next app the user opens. Kept in SharedPreferences (read off the main
 * path, tiny, shared by the receiver and the service).
 */
object ReminderGate {
    private const val PREFS = "reminder_gate"
    private const val KEY = "pending"

    fun add(context: Context, noteId: Long) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet(KEY, p.getStringSet(KEY, emptySet()).orEmpty() + noteId.toString()).apply()
    }

    fun hasPending(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().isNotEmpty()

    /** Takes one waiting reminder off the queue (it is shown once). */
    fun take(context: Context): Long? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = p.getStringSet(KEY, emptySet()).orEmpty()
        val id = set.mapNotNull { it.toLongOrNull() }.minOrNull() ?: return null
        p.edit().putStringSet(KEY, set - id.toString()).apply()
        return id
    }
}
