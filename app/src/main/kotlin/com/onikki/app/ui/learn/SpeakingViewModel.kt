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
    /** Best oral-answer score per prompt id. */
    val answerBest: Map<String, Int> = emptyMap(),
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

enum class AnswerPhase { PREP, RECORDING, REVIEW, CHECKING, RESULT }

data class AnswerSession(
    val prompt: com.onikki.app.domain.speaking.OralPrompt,
    val phase: AnswerPhase = AnswerPhase.PREP,
    val transcript: String = "",
    val seconds: Int = 0,
    val startedAt: Long = 0,
    val result: com.onikki.app.domain.speaking.OralResult? = null,
    /** Set when the AI couldn't be used and the offline check stood in. */
    val aiNote: String? = null
)

class SpeakingViewModel(application: Application) : AndroidViewModel(application) {
    private val ai = com.onikki.app.data.repository.AiRepository(application, (application as OnIkkiApplication).database)
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
            answerBest = all.filter { it.kind == KIND_ANSWER }.groupBy { it.itemKey }.mapValues { (_, a) -> a.maxOf { it.percent } },
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

    // ------------------------------------------------------------ oral answers

    private val _answer = MutableStateFlow<AnswerSession?>(null)
    val answer: StateFlow<AnswerSession?> = _answer

    fun openAnswer(p: com.onikki.app.domain.speaking.OralPrompt) { _answer.value = AnswerSession(p) }

    /** A random question of the level, not the one just done. */
    fun randomAnswer() {
        val list = com.onikki.app.domain.speaking.OralPrompts.forLevel(home.value.level)
        val pick = list.filter { it.id != _answer.value?.prompt?.id }.randomOrNull(random) ?: return
        _answer.value = AnswerSession(pick)
    }

    fun recordingStarted() = _answer.update { it?.copy(phase = AnswerPhase.RECORDING, startedAt = System.currentTimeMillis(), transcript = "", result = null, aiNote = null) }

    fun recorded(text: String) = _answer.update { s ->
        s?.copy(phase = AnswerPhase.REVIEW, transcript = text.trim(), seconds = ((System.currentTimeMillis() - s.startedAt) / 1000).toInt())
    }

    fun retake() = _answer.update { it?.copy(phase = AnswerPhase.PREP, transcript = "", result = null, aiNote = null) }

    /** AI rating when a key is set; otherwise (or if it fails) the offline check. The score is saved. */
    fun evaluate() {
        val s = _answer.value ?: return
        if (s.transcript.isBlank()) return
        _answer.value = s.copy(phase = AnswerPhase.CHECKING)
        viewModelScope.launch {
            val offline = com.onikki.app.domain.speaking.OralCheck.offline(s.prompt, s.transcript, s.seconds)
            var note: String? = null
            val result = when (val r = ai.rateOral(s.prompt, s.transcript, s.seconds, offline.length)) {
                is com.onikki.app.data.repository.AiOutcome.Ok -> r.value
                is com.onikki.app.data.repository.AiOutcome.Failed -> offline.also { note = "AI ishlamadi (${r.message}) — oddiy tekshiruv" }
                com.onikki.app.data.repository.AiOutcome.Offline -> offline.also { note = "Internet yo'q — oddiy tekshiruv" }
                com.onikki.app.data.repository.AiOutcome.NoKey -> offline
            }
            val xp = com.onikki.app.domain.speaking.OralCheck.xp(result, s.seconds)
            dao.insert(SpeakingAttempt(kind = KIND_ANSWER, itemKey = s.prompt.id, level = s.prompt.level, at = System.currentTimeMillis(), day = LocalDate.now(), percent = result.overall, xp = xp))
            _answer.update { it?.copy(phase = AnswerPhase.RESULT, result = result, aiNote = note) }
        }
    }

    fun closeAnswer() { _answer.value = null }

    companion object {
        const val KIND_READ = "READ"
        const val KIND_ANSWER = "ANSWER"
    }
}
