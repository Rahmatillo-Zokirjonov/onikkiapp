package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.GrammarAttempt
import com.onikki.app.domain.grammar.GrammarBank
import com.onikki.app.domain.grammar.GrammarExercise
import com.onikki.app.domain.grammar.TopicProgress
import com.onikki.app.domain.grammar.score
import com.onikki.app.domain.vocab.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/** A grammar practice round on one topic. */
data class GrammarSession(
    val topicId: String,
    val exercises: List<GrammarExercise>,
    val index: Int = 0,
    /** Set once the current exercise is answered. */
    val verdict: Verdict? = null,
    val scores: List<Int> = emptyList(),
    val xp: Int = 0,
    val done: Boolean = false,
    /** Exercises in this round already answered in the topic's last window (the pool is running out). */
    val repeats: Int = 0
) {
    val current: GrammarExercise? get() = exercises.getOrNull(index)
    val accuracy: Int get() = if (scores.isEmpty()) 0 else scores.sum() / scores.size
}

sealed interface GrammarAiState {
    data object Idle : GrammarAiState
    data object Loading : GrammarAiState
    data class Done(val added: Int) : GrammarAiState
    data class Failed(val message: String) : GrammarAiState
    data object NeedsKey : GrammarAiState
}

class GrammarViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val dao = db.grammarDao()
    private val ai = com.onikki.app.data.repository.AiRepository(application, db)
    private val random = Random(System.nanoTime())
    private var attempts: List<GrammarAttempt> = emptyList()

    /** Progress per topic id. */
    val progress: StateFlow<Map<String, TopicProgress>> = dao.observeAttempts().map { list ->
        attempts = list
        list.groupBy { it.topicId }.mapValues { (_, a) -> GrammarBank.progress(a.map { it.score }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _session = MutableStateFlow<GrammarSession?>(null)
    val session: StateFlow<GrammarSession?> = _session

    fun start(topicId: String) {
        viewModelScope.launch {
            val recent = attempts.filter { it.topicId == topicId }.map { it.exerciseKey }
            val pool = GrammarBank.builtIn(topicId) + cachedFor(topicId)
            val exercises = GrammarBank.session(pool, recent, random)
            val window = recent.takeLast(GrammarBank.WINDOW).toSet()
            if (exercises.isNotEmpty()) _session.value = GrammarSession(topicId, exercises, repeats = exercises.count { it.key in window })
        }
    }

    /** AI-written exercises kept for this topic. */
    private suspend fun cachedFor(topicId: String): List<GrammarExercise> =
        dao.cached(topicId).mapNotNull { GrammarBank.decode(topicId, it.payload) }

    /** How many AI exercises are kept per topic. */
    val cacheCounts: StateFlow<Map<String, Int>> = dao.observeCacheCounts().map { list -> list.associate { it.topicId to it.n } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _ai = MutableStateFlow<GrammarAiState>(GrammarAiState.Idle)
    val aiState: StateFlow<GrammarAiState> = _ai

    /**
     * One AI call → up to 10 new exercises, each checked (GrammarBank.valid) and de-duplicated before it is
     * kept; kept ones are reused in every later round, so a topic costs a call only when it runs out.
     */
    fun writeWithAi(topicId: String) {
        val topic = com.onikki.app.domain.grammar.Grammar.byId(topicId) ?: return
        if (_ai.value == GrammarAiState.Loading) return
        _ai.value = GrammarAiState.Loading
        viewModelScope.launch {
            val existing = GrammarBank.builtIn(topicId) + cachedFor(topicId)
            _ai.value = when (val r = ai.writeGrammar(topic, existing.map { it.prompt })) {
                is com.onikki.app.data.repository.AiOutcome.Ok -> {
                    val keys = existing.map { it.key }.toMutableSet()
                    val fresh = r.value.filter { GrammarBank.valid(it) && keys.add(it.key) }
                    if (fresh.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        dao.insertCached(fresh.map { com.onikki.app.data.db.entity.GrammarCached(topicId = topicId, payload = GrammarBank.encode(it), createdAt = now) })
                    }
                    if (fresh.isEmpty()) GrammarAiState.Failed("AI yaroqli mashq yoza olmadi, qayta urinib ko'ring") else GrammarAiState.Done(fresh.size)
                }
                is com.onikki.app.data.repository.AiOutcome.Failed -> GrammarAiState.Failed(r.message)
                com.onikki.app.data.repository.AiOutcome.Offline -> GrammarAiState.Failed("Internet yo'q")
                com.onikki.app.data.repository.AiOutcome.NoKey -> GrammarAiState.NeedsKey
            }
        }
    }

    fun dismissAi() { _ai.value = GrammarAiState.Idle }

    fun saveKey(key: String, topicId: String) {
        viewModelScope.launch {
            ai.saveKey(key)
            _ai.value = GrammarAiState.Idle
            writeWithAi(topicId)
        }
    }

    fun answer(input: String, picked: Int = -1) {
        val s = _session.value ?: return
        val ex = s.current ?: return
        if (s.verdict != null) return
        val v = GrammarBank.check(ex, input, picked)
        val xp = GrammarBank.xp(v)
        _session.value = s.copy(verdict = v, scores = s.scores + v.score(), xp = s.xp + xp)
        viewModelScope.launch {
            dao.insertAttempt(GrammarAttempt(topicId = ex.topicId, kind = ex.kind.name, exerciseKey = ex.key, at = System.currentTimeMillis(), day = LocalDate.now(), score = v.score(), xp = xp))
        }
    }

    fun next() = _session.update { s ->
        when {
            s == null -> null
            s.index + 1 >= s.exercises.size -> s.copy(done = true, verdict = null)
            else -> s.copy(index = s.index + 1, verdict = null)
        }
    }

    fun close() { _session.value = null }
}
