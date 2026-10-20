package com.onikki.app.data.local

import android.content.Context
import com.onikki.app.domain.vocab.HeartState
import com.onikki.app.domain.vocab.Hearts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LearnSettings(
    /** New words a day before "Davom etish" asks to review instead (still allowed). */
    val newPerDay: Int = 15,
    val sound: Boolean = true,
    val vibration: Boolean = true
) {
    companion object {
        val NEW_PER_DAY_OPTIONS = listOf(5, 10, 15, 20, 30)
    }
}

/**
 * Hearts and the learning settings, in SharedPreferences: tiny, read on every answer, and shared by
 * the lesson screen and the app blocker without a database round trip.
 */
class LearnPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("learn", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<LearnSettings> = _settings

    fun hearts(now: Long = System.currentTimeMillis()): HeartState {
        val stored = HeartState(prefs.getInt("hearts", Hearts.MAX), prefs.getLong("hearts_at", now))
        return Hearts.refill(stored, now).also { if (it != stored) saveHearts(it) }
    }

    fun saveHearts(state: HeartState) {
        prefs.edit().putInt("hearts", state.hearts).putLong("hearts_at", state.updatedAt).apply()
    }

    fun update(transform: (LearnSettings) -> LearnSettings) {
        val next = transform(_settings.value)
        prefs.edit().putInt("new_per_day", next.newPerDay).putBoolean("sound", next.sound).putBoolean("vibration", next.vibration).apply()
        _settings.value = next
    }

    /** The listening level the user picked; null = follow the suggestion. */
    fun listenLevel(): String? = prefs.getString("listen_level", null)

    fun setListenLevel(level: String) {
        prefs.edit().putString("listen_level", level).apply()
    }

    fun grammarLevel(): String? = prefs.getString("grammar_level", null)

    fun setGrammarLevel(level: String) {
        prefs.edit().putString("grammar_level", level).apply()
    }

    private fun readSettings() = LearnSettings(
        newPerDay = prefs.getInt("new_per_day", 15),
        sound = prefs.getBoolean("sound", true),
        vibration = prefs.getBoolean("vibration", true)
    )
}
