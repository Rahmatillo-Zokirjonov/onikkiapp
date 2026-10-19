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
    val streak: Int = 0,
    val wordCount: Int = 0,
    val isLoaded: Boolean = false
)

sealed interface SessionKind {
    data class Path(val lesson: PathLesson, val unitName: String) : SessionKind
    data object Review : SessionKind
}

/** What the panel under an answered exercise shows. */
data class Feedback(val verdict: Verdict, val rightAnswer: String?, val xp: Int)

data class LessonSummary(val accuracy: Int, val seconds: Int, val xp: Int, val mistakes: List<VocabWord>, val answered: Int)

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
    val summary: LessonSummary? = null
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

    val home: StateFlow<LearnHomeState> = combine(
        db.vocabDeckDao().observeAll(),
        db.vocabWordDao().observeInPathOrder(),
        db.vocabLessonDao().observeAll(),
        db.vocabReviewDao().observeTotalXp(),
        db.vocabReviewDao().observeActiveDays()
    ) { decks, words, results, xp, days ->
        allWords = words
        val units = LearningPath.build(decks, words, results)
        val today = LocalDate.now()
        LearnHomeState(
            units = units,
            next = LearningPath.nextLesson(units),
            due = Srs.dueQueue(words, today),
            totalXp = xp,
            streak = Journal.streak(days.toSet(), today),
            wordCount = words.size,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnHomeState())

    fun startLesson(lesson: PathLesson, unitName: String) {
        begin(SessionKind.Path(lesson, unitName), LessonPlanner.lesson(lesson.words, allWords, random))
    }

    fun startReview() {
        val due = home.value.due
        if (due.isEmpty()) return
        begin(SessionKind.Review, LessonPlanner.review(due, allWords, random))
    }

    private fun begin(kind: SessionKind, exercises: List<Exercise>) {
        scheduler = SessionScheduler()
        retried.clear()
        _session.value = LessonSession(kind, exercises)
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
        // A missed word comes back once at the end of the session as a plain question.
        val retries = missed.filter { retried.add(it.id) }.map { LessonPlanner.retry(it, allWords, random) }
        _session.update {
            it?.copy(
                exercises = it.exercises + retries,
                graded = it.graded + answers.size,
                correct = it.correct + answers.count { a -> a.verdict != Verdict.WRONG },
                xp = it.xp + gainedXp,
                mistakes = (it.mistakes + missed).distinctBy { w -> w.id },
                feedback = Feedback(worst, rightAnswerOf(exercise), gainedXp)
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
        if (next < s.exercises.size) {
            _session.value = s.copy(index = next, feedback = null)
            return
        }
        val seconds = ((System.currentTimeMillis() - s.startedAt) / 1000).toInt()
        val accuracy = if (s.graded == 0) 100 else s.correct * 100 / s.graded
        val summary = LessonSummary(accuracy, seconds, s.xp, s.mistakes, s.graded)
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
