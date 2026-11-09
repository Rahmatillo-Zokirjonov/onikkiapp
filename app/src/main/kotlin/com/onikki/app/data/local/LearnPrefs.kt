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

    /** So'z zanjiri: start day (epoch day), null = not started. */
    fun chainStart(): java.time.LocalDate? = prefs.getLong("chain_start", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(java.time.LocalDate::ofEpochDay)

    fun setChainStart(date: java.time.LocalDate) {
        prefs.edit().putLong("chain_start", date.toEpochDay()).apply()
    }

    fun chainDone(): Set<Int> = prefs.getStringSet("chain_done", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun setChainDone(days: Set<Int>) {
        prefs.edit().putStringSet("chain_done", days.map { it.toString() }.toSet()).apply()
    }

    /** Best score of the 60-second verb sprint. */
    /** How many words the chain should have by its end date. */
    fun chainGoal(): Int = prefs.getInt("chain_goal", com.onikki.app.domain.vocab.WordChain.DEFAULT_GOAL)

    fun setChainGoal(goal: Int) {
        prefs.edit().putInt("chain_goal", goal).apply()
    }

    /** Chain words the user put in place (position → word), stored as "pos\tenglish\tuzbek\texample". */
    fun chainOverrides(): Map<Int, com.onikki.app.domain.vocab.ImportedWord> =
        prefs.getStringSet("chain_overrides", emptySet()).orEmpty().mapNotNull { line ->
            val p = line.split('\t')
            val pos = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val en = p.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            pos to com.onikki.app.domain.vocab.ImportedWord(en, p.getOrNull(2).orEmpty(), p.getOrNull(3)?.takeIf { it.isNotBlank() }, null)
        }.toMap()

    fun setChainOverrides(map: Map<Int, com.onikki.app.domain.vocab.ImportedWord>) {
        val clean = { s: String? -> s.orEmpty().replace('\t', ' ').replace('\n', ' ') }
        prefs.edit().putStringSet("chain_overrides", map.map { (pos, w) -> "$pos\t${clean(w.english)}\t${clean(w.uzbek)}\t${clean(w.example)}" }.toSet()).apply()
    }

    /** Chain words taken out (normalized English); they never come back. */
    fun chainRemoved(): Set<String> = prefs.getStringSet("chain_removed", emptySet()).orEmpty()

    fun setChainRemoved(words: Set<String>) {
        prefs.edit().putStringSet("chain_removed", words).apply()
    }

    /** Weak links of the sequence practice: "from>to" → misses. */
    fun chainWeak(): Map<String, Int> = prefs.getStringSet("chain_weak", emptySet()).orEmpty().mapNotNull { line ->
        val i = line.lastIndexOf('\t')
        if (i <= 0) null else line.substring(i + 1).toIntOrNull()?.let { line.substring(0, i) to it }
    }.toMap()

    fun setChainWeak(map: Map<String, Int>) {
        prefs.edit().putStringSet("chain_weak", map.map { "${it.key}\t${it.value}" }.toSet()).apply()
    }

    fun sprintBest(): Int = prefs.getInt("sprint_best", 0)

    fun setSprintBest(score: Int) {
        prefs.edit().putInt("sprint_best", score).apply()
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
