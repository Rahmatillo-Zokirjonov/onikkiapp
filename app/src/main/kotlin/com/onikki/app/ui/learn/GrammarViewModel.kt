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
    val done: Boolean = false
) {
    val current: GrammarExercise? get() = exercises.getOrNull(index)
    val accuracy: Int get() = if (scores.isEmpty()) 0 else scores.sum() / scores.size
}

class GrammarViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as OnIkkiApplication).database.grammarDao()
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
            if (exercises.isNotEmpty()) _session.value = GrammarSession(topicId, exercises)
        }
    }

    /** AI-written exercises kept for this topic (filled in the next stage). */
    private suspend fun cachedFor(topicId: String): List<GrammarExercise> = emptyList()

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
