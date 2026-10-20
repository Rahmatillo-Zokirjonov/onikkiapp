package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.SpeakingAttempt
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.domain.speaking.Pronunciation
import com.onikki.app.domain.speaking.PronunciationResult
import com.onikki.app.domain.speaking.SpeakItem
import com.onikki.app.domain.speaking.SpeakingDeck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

data class SpeakingHomeState(
    val level: String = "A1",
    val readToday: Int = 0,
    /** Average best score of the last 20 read sentences. */
    val recentAverage: Int? = null
)

data class ReadSession(
    val level: String,
    val items: List<SpeakItem>,
    val index: Int = 0,
    /** Result of the latest try on the current sentence. */
    val result: PronunciationResult? = null,
    val tries: Int = 0,
    /** Best percent per finished sentence. */
    val best: List<Int> = emptyList(),
    val xp: Int = 0,
    /** Words not heard, across the round (to practise). */
    val missed: List<String> = emptyList(),
    val done: Boolean = false
) {
    val current: SpeakItem? get() = items.getOrNull(index)
    val average: Int get() = if (best.isEmpty()) 0 else best.sum() / best.size
}

class SpeakingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val dao = db.speakingDao()
    private val prefs = LearnPrefs(application)
    private val random = Random(System.nanoTime())
    private val level = MutableStateFlow<String?>(prefs.listenLevel())
    private val suggested = MutableStateFlow("A1")
    private var bestByKey: Map<String, Int> = emptyMap()

    fun setSuggested(s: String) { suggested.value = s }

    val home: StateFlow<SpeakingHomeState> = combine(level, suggested, dao.observeAll()) { picked, sug, all ->
        val read = all.filter { it.kind == KIND_READ }
        bestByKey = read.groupBy { it.itemKey }.mapValues { (_, a) -> a.maxOf { it.percent } }
        SpeakingHomeState(
            level = picked ?: sug,
            readToday = read.count { it.day == LocalDate.now() },
            recentAverage = read.takeLast(20).takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { it.percent } / r.size }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpeakingHomeState())

    private val _read = MutableStateFlow<ReadSession?>(null)
    val read: StateFlow<ReadSession?> = _read

    fun setLevel(l: String) {
        prefs.setListenLevel(l)
        level.value = l
    }

    fun startRead() {
        viewModelScope.launch {
            val lvl = home.value.level
            val words = db.vocabWordDao().getAll()
            val items = SpeakingDeck.round(lvl, words, bestByKey, random)
            if (items.isNotEmpty()) _read.value = ReadSession(lvl, items)
        }
    }

    /** The recognizer's alternatives for the current sentence. */
    fun heard(candidates: List<String>) = _read.update { s ->
        val item = s?.current ?: return@update s
        val r = Pronunciation.score(item.english, candidates)
        // Keep the better of this and earlier tries.
        val keep = if ((s.result?.percent ?: -1) > r.percent) s.result else r
        s.copy(result = keep, tries = s.tries + 1)
    }

    /** Next sentence: the best try is saved. */
    fun next() {
        val s = _read.value ?: return
        val item = s.current ?: return
        val percent = s.result?.percent ?: 0
        val xp = Pronunciation.xp(percent)
        if (s.tries > 0) {
            viewModelScope.launch {
                dao.insert(SpeakingAttempt(kind = KIND_READ, itemKey = item.key, level = s.level, at = System.currentTimeMillis(), day = LocalDate.now(), percent = percent, xp = xp))
            }
        }
        val best = if (s.tries > 0) s.best + percent else s.best
        val missed = (s.missed + s.result?.missed.orEmpty().map { it.trim { c -> !c.isLetterOrDigit() && c != '\'' } }).filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        _read.value = if (s.index + 1 >= s.items.size) s.copy(best = best, xp = s.xp + xp, missed = missed, done = true, result = null)
        else s.copy(index = s.index + 1, result = null, tries = 0, best = best, xp = s.xp + xp, missed = missed)
    }

    fun closeRead() { _read.value = null }

    companion object {
        const val KIND_READ = "READ"
        const val KIND_ANSWER = "ANSWER"
    }
}
