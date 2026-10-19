package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.ReviewSource
import com.onikki.app.data.db.entity.VocabLessonResult
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.repository.LearnRepository
import com.onikki.app.domain.notes.Journal
import com.onikki.app.domain.vocab.Exercise
import com.onikki.app.domain.vocab.LearningPath
import com.onikki.app.domain.vocab.LessonPlanner
import com.onikki.app.domain.vocab.PathLesson
import com.onikki.app.domain.vocab.PathUnit
import com.onikki.app.domain.vocab.SessionScheduler
import com.onikki.app.domain.vocab.Srs
import com.onikki.app.domain.vocab.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

data class LearnHomeState(
    val units: List<PathUnit> = emptyList(),
    val next: PathLesson? = null,
    val due: List<VocabWord> = emptyList(),
    val totalXp: Int = 0,
    val todayXp: Int = 0,
    val streak: Int = 0,
    val studiedToday: Boolean = false,
    val wordCount: Int = 0,
    val newToday: Int = 0,
    /** Most-missed words, for the heart-refill practice. */
    val hardest: List<VocabWord> = emptyList(),
    val decks: List<com.onikki.app.data.db.entity.VocabDeck> = emptyList(),
    val isLoaded: Boolean = false
)

/** A word the user misses a lot, with how often. */
data class HardWord(val word: VocabWord, val wrong: Int, val total: Int)

data class LearnStatsState(
    val stats: com.onikki.app.domain.vocab.LearnStats = com.onikki.app.domain.vocab.LearnStats(),
    val hardest: List<HardWord> = emptyList(),
    val totalXp: Int = 0
)

sealed interface SessionKind {
    data class Path(val lesson: PathLesson, val unitName: String) : SessionKind
    data object Review : SessionKind
    /** Practising mistakes to win hearts back; ends when the bar is full. */
    data object Heal : SessionKind
}

/** What the panel under an answered exercise shows. */
data class Feedback(val verdict: Verdict, val rightAnswer: String?, val xp: Int, val combo: Int)

data class LessonSummary(
    val accuracy: Int,
    val seconds: Int,
    val xp: Int,
    val mistakes: List<VocabWord>,
    val answered: Int,
    val bestCombo: Int,
    /** This session was the first study of the day: the streak went up. */
    val streakExtended: Boolean
)

data class LessonSession(
    val kind: SessionKind,
    val exercises: List<Exercise>,
    val index: Int = 0,
    val graded: Int = 0,
    val correct: Int = 0,
    val xp: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val mistakes: List<VocabWord> = emptyList(),
    val feedback: Feedback? = null,
    val summary: LessonSummary? = null,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    /** Hearts ran out mid-lesson (or before it): the lesson stops and offers practice. */
    val outOfHearts: Boolean = false,
    val studiedBefore: Boolean = true
) {
    val current: Exercise? get() = exercises.getOrNull(index)
    val progress: Float get() = if (exercises.isEmpty()) 0f else index / exercises.size.toFloat()
}

class LearnViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val repository = LearnRepository(db)
    private val random = Random(System.nanoTime())
    private var scheduler = SessionScheduler()
    private val retried = mutableSetOf<Long>()
    private var allWords: List<VocabWord> = emptyList()

    private val _session = MutableStateFlow<LessonSession?>(null)
    val session: StateFlow<LessonSession?> = _session

    private val prefs = com.onikki.app.data.local.LearnPrefs(application)
    val settings = prefs.settings
    private val _hearts = MutableStateFlow(prefs.hearts())
    val hearts: StateFlow<com.onikki.app.domain.vocab.HeartState> = _hearts

    fun refreshHearts() { _hearts.value = prefs.hearts() }

    fun updateSettings(transform: (com.onikki.app.data.local.LearnSettings) -> com.onikki.app.data.local.LearnSettings) = prefs.update(transform)

    private fun setHearts(state: com.onikki.app.domain.vocab.HeartState) {
        prefs.saveHearts(state)
        _hearts.value = state
    }

    private val today = LocalDate.now()

    private data class Counters(val totalXp: Int, val todayXp: Int, val days: List<LocalDate>, val newToday: Int, val missed: List<com.onikki.app.data.db.dao.VocabMissCount>)

    private val counters = combine(
        db.vocabReviewDao().observeTotalXp(),
        db.vocabReviewDao().observeXpOn(today),
        db.vocabReviewDao().observeActiveDays(),
        db.vocabReviewDao().observeFirstSeenOn(today),
        db.vocabReviewDao().observeMostMissed(12)
    ) { total, todayXp, days, newToday, missed -> Counters(total, todayXp, days, newToday, missed) }

    val home: StateFlow<LearnHomeState> = combine(
        db.vocabDeckDao().observeAll(),
        db.vocabWordDao().observeInPathOrder(),
        db.vocabLessonDao().observeAll(),
        counters
    ) { decks, words, results, c ->
        allWords = words
        val units = LearningPath.build(decks, words, results)
        val byId = words.associateBy { it.id }
        LearnHomeState(
            units = units,
            next = LearningPath.nextLesson(units),
            due = Srs.dueQueue(words, today),
            totalXp = c.totalXp,
            todayXp = c.todayXp,
            streak = Journal.streak(c.days.toSet(), today),
            studiedToday = today in c.days,
            wordCount = words.size,
            newToday = c.newToday,
            hardest = c.missed.mapNotNull { byId[it.wordId] },
            decks = decks,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnHomeState())

    val stats: StateFlow<LearnStatsState> = combine(
        db.vocabWordDao().observeInPathOrder(),
        db.vocabReviewDao().observeDailyTotals(today.minusDays(6)),
        db.vocabReviewDao().observeActiveDays(),
        db.vocabReviewDao().observeMostMissed(10),
        db.vocabReviewDao().observeTotalXp()
    ) { words, days, active, missed, xp ->
        val byId = words.associateBy { it.id }
        LearnStatsState(
            stats = com.onikki.app.domain.vocab.VocabStats.build(
                words,
                days.map { com.onikki.app.domain.vocab.DayPractice(it.day, it.xp, it.answers, it.correct) },
                active.toSet(),
                today
            ),
            hardest = missed.mapNotNull { m -> byId[m.wordId]?.let { HardWord(it, m.wrong, m.total) } },
            totalXp = xp
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnStatsState())

    /** "So'z qo'shish": into an existing deck or a new one; [onDone] gets the message to show. */
    fun addWords(target: DeckTarget, items: List<com.onikki.app.domain.vocab.ImportedWord>, level: String?, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val deckId = when (target) {
                is DeckTarget.Existing -> target.id
                is DeckTarget.New -> repository.createDeck(target.name)
            }
            val r = repository.importWords(deckId, items, level)
            onDone(
                when {
                    r.added == 0 -> "Hammasi ro'yxatda bor edi (${r.skipped} ta)"
                    r.skipped == 0 -> "${r.added} ta so'z qo'shildi — yangi lessonlar tayyor"
                    else -> "${r.added} ta qo'shildi, ${r.skipped} tasi allaqachon bor edi"
                }
            )
        }
    }

    fun startLesson(lesson: PathLesson, unitName: String) {
        refreshHearts()
        if (_hearts.value.hearts <= 0) {
            _session.value = LessonSession(SessionKind.Path(lesson, unitName), emptyList(), outOfHearts = true)
            return
        }
        begin(SessionKind.Path(lesson, unitName), LessonPlanner.lesson(lesson.words, allWords, random))
    }

    /** Win hearts back: the last session's mistakes first, then the most-missed words, then anything due. */
    fun startHeal() {
        val fromSession = _session.value?.mistakes.orEmpty()
        val words = (fromSession + home.value.hardest + home.value.due + allWords.filter { it.status != com.onikki.app.data.db.entity.WordStatus.NEW })
            .distinctBy { it.id }
            .map { w -> allWords.firstOrNull { it.id == w.id } ?: w }
            .take(LessonPlanner.MAX_REVIEW_WORDS)
        if (words.isEmpty()) return
        begin(SessionKind.Heal, LessonPlanner.review(words, allWords, random))
    }

    fun startReview() {
        val due = home.value.due
        if (due.isEmpty()) return
        begin(SessionKind.Review, LessonPlanner.review(due, allWords, random))
    }

    private fun begin(kind: SessionKind, exercises: List<Exercise>) {
        scheduler = SessionScheduler()
        retried.clear()
        _session.value = LessonSession(kind, exercises, studiedBefore = home.value.studiedToday)
    }

    /** An exercise reported its answers (empty for a card: just go on). */
    fun answer(answers: List<WordAnswer>) {
        val s = _session.value ?: return
        val exercise = s.current ?: return
        if (answers.isEmpty()) {
            advance()
            return
        }
        val source = if (s.kind is SessionKind.Review) ReviewSource.REVIEW else ReviewSource.LESSON
        val worst = answers.maxOf { it.verdict.ordinal }.let { Verdict.entries[it] }
        val byId = allWords.associateBy { it.id }
        var gainedXp = 0
        answers.forEach { a ->
            val grade = Srs.gradeFor(a.verdict, a.usedHint)
            gainedXp += Srs.xpFor(grade, exercise.type.hard)
        }
        val missed = answers.filter { it.verdict == Verdict.WRONG }.mapNotNull { byId[it.wordId] }
        // Hearts: lessons cost one per miss; the heal practice gives one per right answer.
        val now = System.currentTimeMillis()
        when (s.kind) {
            is SessionKind.Path -> repeat(missed.size) { setHearts(com.onikki.app.domain.vocab.Hearts.lose(_hearts.value, now)) }
            SessionKind.Heal -> repeat(answers.count { it.verdict != Verdict.WRONG }) { setHearts(com.onikki.app.domain.vocab.Hearts.gain(_hearts.value, now)) }
            SessionKind.Review -> Unit
        }
        var combo = s.combo
        answers.forEach { combo = com.onikki.app.domain.vocab.Combo.next(combo, it.verdict) }
        // A missed word comes back once at the end of the session as a plain question.
        val retries = missed.filter { retried.add(it.id) }.map { LessonPlanner.retry(it, allWords, random) }
        _session.update {
            it?.copy(
                exercises = it.exercises + retries,
                graded = it.graded + answers.size,
                correct = it.correct + answers.count { a -> a.verdict != Verdict.WRONG },
                xp = it.xp + gainedXp,
                mistakes = (it.mistakes + missed).distinctBy { w -> w.id },
                combo = combo,
                bestCombo = maxOf(it.bestCombo, combo),
                feedback = Feedback(worst, rightAnswerOf(exercise), gainedXp, combo)
            )
        }
        viewModelScope.launch {
            answers.forEach { a ->
                val grade = Srs.gradeFor(a.verdict, a.usedHint)
                repository.recordAnswer(
                    wordId = a.wordId,
                    grade = grade,
                    source = source,
                    exercise = exercise.type.name,
                    hardExercise = exercise.type.hard,
                    schedule = scheduler.gradeToApply(a.wordId, grade) != null
                )
            }
        }
    }

    /** "Davom etish" after the feedback panel (or after a card). */
    fun advance() {
        val s = _session.value ?: return
        val next = s.index + 1
        if (s.kind is SessionKind.Path && _hearts.value.hearts <= 0) {
            _session.value = s.copy(feedback = null, outOfHearts = true)
            return
        }
        val healed = s.kind == SessionKind.Heal && _hearts.value.hearts >= com.onikki.app.domain.vocab.Hearts.MAX
        if (next < s.exercises.size && !healed) {
            _session.value = s.copy(index = next, feedback = null)
            return
        }
        val seconds = ((System.currentTimeMillis() - s.startedAt) / 1000).toInt()
        val accuracy = if (s.graded == 0) 100 else s.correct * 100 / s.graded
        val summary = LessonSummary(accuracy, seconds, s.xp, s.mistakes, s.graded, s.bestCombo, streakExtended = !s.studiedBefore && s.graded > 0)
        _session.value = s.copy(index = s.exercises.size, feedback = null, summary = summary)
        val kind = s.kind
        if (kind is SessionKind.Path) {
            viewModelScope.launch {
                db.vocabLessonDao().upsert(
                    VocabLessonResult(kind.lesson.deckId, kind.lesson.index, System.currentTimeMillis(), accuracy, s.xp, seconds)
                )
            }
        }
    }

    /** Practise just the words missed in the finished session. */
    fun practiseMistakes() {
        val mistakes = _session.value?.summary?.mistakes.orEmpty()
        if (mistakes.isEmpty()) return
        begin(SessionKind.Review, LessonPlanner.review(mistakes.map { w -> allWords.firstOrNull { it.id == w.id } ?: w }, allWords, random))
    }

    fun close() {
        _session.value = null
    }
}
