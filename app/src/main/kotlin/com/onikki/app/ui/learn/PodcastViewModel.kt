package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.PodcastAttempt
import com.onikki.app.data.db.entity.PodcastCustom
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.domain.podcast.Podcast
import com.onikki.app.domain.podcast.Podcasts
import com.onikki.app.domain.vocab.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PodcastHomeState(
    val level: String = "A1",
    val podcasts: List<Podcast> = emptyList(),
    /** Best "tushundim" % per podcast id. */
    val best: Map<String, Int> = emptyMap()
)

enum class PodcastPhase { LISTEN, QUESTIONS, RESULT }

data class PodcastSession(
    val podcast: Podcast,
    val phase: PodcastPhase = PodcastPhase.LISTEN,
    /** Per question: picked option (choice / true-false) and typed text. */
    val picked: List<Int> = List(podcast.questions.size) { -1 },
    val typed: List<String> = List(podcast.questions.size) { "" },
    val verdicts: List<Verdict> = emptyList(),
    val percent: Int = 0,
    val xp: Int = 0
) {
    val answered: Int get() = podcast.questions.indices.count { picked[it] >= 0 || typed[it].isNotBlank() }
}

class PodcastViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val dao = db.podcastDao()
    private val prefs = LearnPrefs(application)
    private val level = MutableStateFlow<String?>(prefs.listenLevel())
    private val suggested = MutableStateFlow("A1")

    /** The level suggested from learned words, used until the user picks one. */
    fun setSuggested(s: String) { suggested.value = s }

    val home: StateFlow<PodcastHomeState> = combine(level, suggested, dao.observeCustom(), dao.observeAttempts()) { picked, sug, custom, attempts ->
        val current = picked ?: sug
        PodcastHomeState(
            level = current,
            podcasts = Podcasts.forLevel(current, custom.mapNotNull { it.toPodcast() }),
            best = attempts.groupBy { it.podcastId }.mapValues { (_, a) -> a.maxOf { it.percent } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastHomeState())

    private val _session = MutableStateFlow<PodcastSession?>(null)
    val session: StateFlow<PodcastSession?> = _session

    fun setLevel(l: String) {
        prefs.setListenLevel(l)
        level.value = l
    }

    fun open(p: Podcast) { _session.value = PodcastSession(p) }

    fun toQuestions() = _session.update { it?.copy(phase = PodcastPhase.QUESTIONS) }

    fun toListen() = _session.update { it?.copy(phase = PodcastPhase.LISTEN) }

    fun pick(q: Int, option: Int) = _session.update { s -> s?.copy(picked = s.picked.toMutableList().also { it[q] = option }) }

    fun type(q: Int, text: String) = _session.update { s -> s?.copy(typed = s.typed.toMutableList().also { it[q] = text.take(60) }) }

    fun submit() {
        val s = _session.value ?: return
        val verdicts = s.podcast.questions.mapIndexed { i, q -> Podcasts.check(q, s.typed[i], s.picked[i]) }
        val percent = Podcasts.understood(verdicts)
        val xp = Podcasts.xp(verdicts, s.podcast.minutes)
        _session.value = s.copy(phase = PodcastPhase.RESULT, verdicts = verdicts, percent = percent, xp = xp)
        viewModelScope.launch {
            dao.insertAttempt(PodcastAttempt(podcastId = s.podcast.id, level = s.podcast.level, at = System.currentTimeMillis(), day = LocalDate.now(), percent = percent, xp = xp))
        }
    }

    /** Listen again and answer again (questions start empty). */
    fun again() = _session.update { s -> s?.let { PodcastSession(it.podcast) } }

    fun next() {
        val s = _session.value ?: return
        val list = home.value.podcasts
        val i = list.indexOfFirst { it.id == s.podcast.id }
        val n = list.getOrNull(i + 1) ?: list.firstOrNull { home.value.best[it.id] == null }
        _session.value = n?.let { PodcastSession(it) }
    }

    fun close() { _session.value = null }

    private fun PodcastCustom.toPodcast(): Podcast? = runCatching {
        Podcasts.parse("pai-$id", level, minutes, title, topic, transcript, questions).copy(ai = true)
            .takeIf { it.lines.size >= 3 && it.questions.size >= 3 }
    }.getOrNull()
}
