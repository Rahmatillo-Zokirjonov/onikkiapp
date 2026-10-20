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
    /** "Bilmaganlarim", oldest first. */
    val unknown: List<VocabWord> = emptyList(),
    /** A starting level for listening and grammar, from how many words are learned. */
    val suggestedLevel: String = "A1",
    /** Every never-studied word, in path order. */
    val newWords: List<VocabWord> = emptyList(),
    /** New words not sorted yet, for "Bilaman / Bilmayman". */
    val unsorted: List<VocabWord> = emptyList(),
    /** Irregular verbs (with V2 and V3), for the table and the drill. */
    val verbs: List<VocabWord> = emptyList(),
    val isLoaded: Boolean = false
)

/** "Bilaman / Bilmayman" cards over new words. */
data class SortSession(
    val words: List<VocabWord>,
    val index: Int = 0,
    val revealed: Boolean = false,
    val known: Int = 0,
    val unknown: List<VocabWord> = emptyList()
) {
    val current: VocabWord? get() = words.getOrNull(index)
    val done: Boolean get() = index >= words.size
}

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
    /** "Bilmaganlarim": cards again, then questions. */
    data object Relearn : SessionKind
    /** Irregular verb forms (V2 / V3). */
    data object Verbs : SessionKind
    /** "Gap tuzish": sentences from Uzbek and own sentences. */
    data object Sentences : SessionKind
    /** The next never-studied words, wherever they are on the path. */
    data object NewWords : SessionKind
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
    val streakExtended: Boolean,
    /** Missed, came back and answered right in the same session. */
    val relearned: List<VocabWord> = emptyList()
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
    val studiedBefore: Boolean = true,
    /** Indices of exercises that are another try of a missed word. */
    val retries: Set<Int> = emptySet(),
    val relearned: List<VocabWord> = emptyList()
) {
    val isRetry: Boolean get() = index in retries
    val current: Exercise? get() = exercises.getOrNull(index)
    val progress: Float get() = if (exercises.isEmpty()) 0f else index / exercises.size.toFloat()
}

class LearnViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val repository = LearnRepository(db)
    private val random = Random(System.nanoTime())
    private var scheduler = SessionScheduler()
    /** Tries per missed word in this session (see Relearn). */
    private val tries = mutableMapOf<Long, Int>()
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
        counters,
        combine(db.listeningDao().observeAttempts(), db.grammarDao().observeXpByDay(), db.podcastDao().observeXpByDay()) { l, g, p ->
            l.map { com.onikki.app.data.db.dao.DayXp(it.day, it.xp) } + g + p
        }
    ) { decks, words, results, c0, extra ->
        // Listening and grammar count towards XP and the streak too.
        val c = c0.copy(
            totalXp = c0.totalXp + extra.sumOf { it.xp },
            todayXp = c0.todayXp + extra.filter { it.day == today }.sumOf { it.xp },
            days = (c0.days + extra.map { it.day }).distinct()
        )
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
            unknown = com.onikki.app.domain.vocab.Relearn.unknown(words),
            verbs = words.filter(com.onikki.app.domain.vocab.Exercises::isIrregular),
            newWords = LearningPath.untouched(units),
            suggestedLevel = com.onikki.app.domain.vocab.Listening.suggestLevel(words),
            unsorted = LearningPath.untouched(units).filter { it.unknownSince == null },
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

    /** "Bilmaganlarim": the oldest unknown words, card first. */
    fun startRelearn() {
        val words = home.value.unknown.map { w -> allWords.firstOrNull { it.id == w.id } ?: w }
        if (words.isEmpty()) return
        begin(SessionKind.Relearn, com.onikki.app.domain.vocab.Relearn.session(words, allWords, random))
    }

    /**
     * A verb-forms drill over [among] (the table's current filter): unknown and due verbs first, then those
     * being learned, then new ones; a verb first met as a choice gets typed at the end too.
     */
    fun startVerbDrill(among: List<VocabWord>) {
        val fresh = among.map { w -> allWords.firstOrNull { it.id == w.id } ?: w }.filter(com.onikki.app.domain.vocab.Exercises::isIrregular)
        val picked = fresh.sortedWith(
            compareBy<VocabWord>(
                { if (it.unknownSince != null) 0 else 1 },
                { if (Srs.isDue(it, today)) 0 else 1 },
                {
                    when (it.status) {
                        com.onikki.app.data.db.entity.WordStatus.LEARNING -> 0
                        com.onikki.app.data.db.entity.WordStatus.NEW -> 1
                        com.onikki.app.data.db.entity.WordStatus.REVIEW -> 2
                        com.onikki.app.data.db.entity.WordStatus.MASTERED -> 3
                    }
                },
                { listOf("A1", "A2", "B1", "B2").indexOf(it.level).let { i -> if (i < 0) 9 else i } }
            )
        ).take(VERB_DRILL)
        if (picked.isEmpty()) return
        val first = com.onikki.app.domain.vocab.Exercises.verbDrill(picked.shuffled(random), allWords, random)
        val typedLater = first.filterIsInstance<com.onikki.app.domain.vocab.Exercise.FormChoice>().map { com.onikki.app.domain.vocab.Exercises.forms(it.word) }.shuffled(random)
        begin(SessionKind.Verbs, (first + typedLater).take(LessonPlanner.MAX_EXERCISES))
    }

    private val ai = com.onikki.app.data.repository.AiRepository(application, db)

    /** For own sentences: the AI's verdict, or null (no key, offline, failed) so the screen checks offline. */
    val checkSentence: SentenceChecker = { word, sentence ->
        (ai.checkSentence(word.english, com.onikki.app.domain.vocab.Exercises.primary(word.uzbek), sentence) as? com.onikki.app.data.repository.AiOutcome.Ok)?.value
    }

    /** "Yangi so'zlar": the next lesson-size batch of never-studied words, from any deck. */
    fun startNewWords() {
        val words = home.value.newWords.take(com.onikki.app.domain.vocab.LearningPath.LESSON_SIZE).map { w -> allWords.firstOrNull { it.id == w.id } ?: w }
        if (words.isEmpty()) return
        begin(SessionKind.NewWords, LessonPlanner.lesson(words, allWords, random))
    }

    /** Words for "Gap tuzish": studied ones, unknown and young first, those with a translated example preferred. */
    fun startSentences() {
        val studied = allWords.filter { it.status != com.onikki.app.data.db.entity.WordStatus.NEW }
        if (studied.isEmpty()) return
        val picked = studied.shuffled(random).sortedWith(
            compareBy<VocabWord>({ if (it.unknownSince != null) 0 else 1 }, { if (it.exampleUz != null) 0 else 1 }, { it.stage })
        ).take(SENTENCE_WORDS)
        begin(SessionKind.Sentences, com.onikki.app.domain.vocab.Exercises.sentencePractice(picked.shuffled(random), allWords, random))
    }

    private val _sort = MutableStateFlow<SortSession?>(null)
    val sort: StateFlow<SortSession?> = _sort

    fun startSort() {
        val words = home.value.unsorted.take(SORT_SIZE)
        if (words.isNotEmpty()) _sort.value = SortSession(words)
    }

    fun revealSort() = _sort.update { it?.copy(revealed = true) }

    fun sortAnswer(known: Boolean) {
        val s = _sort.value ?: return
        val word = s.current ?: return
        _sort.value = s.copy(
            index = s.index + 1,
            revealed = false,
            known = s.known + if (known) 1 else 0,
            unknown = if (known) s.unknown else s.unknown + word
        )
        viewModelScope.launch { repository.sortWord(word.id, known) }
    }

    fun closeSort() { _sort.value = null }

    /** From the sort result: learn the "Bilmayman" words right away. */
    fun relearnSorted() {
        val words = _sort.value?.unknown.orEmpty().map { w -> allWords.firstOrNull { it.id == w.id } ?: w }
        _sort.value = null
        if (words.isNotEmpty()) begin(SessionKind.Relearn, com.onikki.app.domain.vocab.Relearn.session(words, allWords, random))
    }

    fun removeUnknown(word: VocabWord) {
        viewModelScope.launch { repository.forgetUnknown(word.id) }
    }

    private fun begin(kind: SessionKind, exercises: List<Exercise>) {
        scheduler = SessionScheduler()
        tries.clear()
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
        val source = if (s.kind is SessionKind.Path || s.kind is SessionKind.Heal) ReviewSource.LESSON else ReviewSource.REVIEW
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
            // Another try of a missed word is part of learning it: no heart for that.
            is SessionKind.Path -> if (!s.isRetry) repeat(missed.size) { setHearts(com.onikki.app.domain.vocab.Hearts.lose(_hearts.value, now)) }
            SessionKind.Heal -> repeat(answers.count { it.verdict != Verdict.WRONG }) { setHearts(com.onikki.app.domain.vocab.Hearts.gain(_hearts.value, now)) }
            SessionKind.Review, SessionKind.Relearn, SessionKind.Verbs, SessionKind.Sentences, SessionKind.NewWords -> Unit
        }
        var combo = s.combo
        answers.forEach { combo = com.onikki.app.domain.vocab.Combo.next(combo, it.verdict) }
        // Englify-style: a missed word comes back a couple of exercises later, in a new shape, until it's right.
        var queue = com.onikki.app.domain.vocab.ExerciseQueue(s.exercises, s.retries)
        missed.forEachIndexed { i, w ->
            val attempt = (tries[w.id] ?: 0) + 1
            if (attempt <= com.onikki.app.domain.vocab.Relearn.MAX_TRIES) {
                tries[w.id] = attempt
                val at = com.onikki.app.domain.vocab.Relearn.position(s.index, queue.items.size, offset = i)
                queue = queue.insertRetry(at, com.onikki.app.domain.vocab.Relearn.retryExercise(w, attempt, allWords, random))
            }
        }
        val fixed = if (s.isRetry) answers.filter { it.verdict != Verdict.WRONG }.mapNotNull { byId[it.wordId] } else emptyList()
        _session.update {
            it?.copy(
                exercises = queue.items,
                retries = queue.retries,
                relearned = (it.relearned + fixed).distinctBy { w -> w.id },
                graded = it.graded + answers.size,
                correct = it.correct + answers.count { a -> a.verdict != Verdict.WRONG },
                xp = it.xp + gainedXp,
                mistakes = (it.mistakes + missed).distinctBy { w -> w.id },
                combo = combo,
                bestCombo = maxOf(it.bestCombo, combo),
                feedback = Feedback(worst, answers.firstNotNullOfOrNull { a -> a.note } ?: rightAnswerOf(exercise), gainedXp, combo)
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
        val summary = LessonSummary(accuracy, seconds, s.xp, s.mistakes, s.graded, s.bestCombo, streakExtended = !s.studiedBefore && s.graded > 0, relearned = s.relearned)
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

    private companion object {
        const val SORT_SIZE = 20
        const val VERB_DRILL = 10
        const val SENTENCE_WORDS = 5
    }
}
