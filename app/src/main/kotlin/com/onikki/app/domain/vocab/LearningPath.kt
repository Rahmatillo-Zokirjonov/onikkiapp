package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.VocabDeck
import com.onikki.app.data.db.entity.VocabLessonResult
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import kotlin.random.Random

enum class LessonState { LOCKED, AVAILABLE, DONE }

data class PathLesson(
    val deckId: Long,
    /** Position inside its deck (0-based). */
    val index: Int,
    val words: List<VocabWord>,
    val state: LessonState,
    val result: VocabLessonResult?
) {
    val title: String get() = "Lesson ${index + 1}"
}

/** A deck on the path. */
data class PathUnit(val deck: VocabDeck, val lessons: List<PathLesson>) {
    val done: Int get() = lessons.count { it.state == LessonState.DONE }
}

/**
 * The learning path: decks in their order become Units, each deck's words (A1 → A2 → B1, then the
 * order they were added) are cut into lessons of [LESSON_SIZE]. Inside a deck lessons unlock one after
 * another; every deck has its own next lesson, so own words and verbs never wait behind another deck.
 * Finished ones stay open for replay. A finished lesson that has a never-studied word again (words were
 * added and the chunks moved) counts as not finished, so no new word is skipped.
 */
object LearningPath {
    const val LESSON_SIZE = 8
    private const val MIN_LAST = 3
    private val LEVEL_ORDER = listOf("A1", "A2", "B1", "B2")

    fun lessonsOf(words: List<VocabWord>): List<List<VocabWord>> {
        val ordered = words.sortedWith(compareBy<VocabWord>({ LEVEL_ORDER.indexOf(it.level?.uppercase()).let { i -> if (i < 0) LEVEL_ORDER.size else i } }, { it.id }))
        val chunks = ordered.chunked(LESSON_SIZE).toMutableList()
        // A tiny last lesson joins the one before it.
        if (chunks.size >= 2 && chunks.last().size < MIN_LAST) {
            val last = chunks.removeAt(chunks.lastIndex)
            chunks[chunks.lastIndex] = chunks.last() + last
        }
        return chunks
    }

    fun build(decks: List<VocabDeck>, words: List<VocabWord>, results: List<VocabLessonResult>): List<PathUnit> {
        val byDeck = words.groupBy { it.deckId }
        val done = results.associateBy { it.deckId to it.lessonIndex }
        return decks.sortedWith(compareBy({ it.sortOrder }, { it.id })).mapNotNull { deck ->
            val chunks = lessonsOf(byDeck[deck.id].orEmpty())
            if (chunks.isEmpty()) return@mapNotNull null
            var unlockedNext = true
            val lessons = chunks.mapIndexed { i, chunk ->
                val result = done[deck.id to i]
                val hasNew = chunk.any { it.status == WordStatus.NEW }
                val state = when {
                    result != null && !hasNew -> LessonState.DONE
                    // Done before, but a new word moved in: open again (keeps its old result for the score).
                    result != null -> LessonState.AVAILABLE
                    unlockedNext -> LessonState.AVAILABLE.also { unlockedNext = false }
                    else -> LessonState.LOCKED
                }
                PathLesson(deck.id, i, chunk, state, result)
            }
            PathUnit(deck, lessons)
        }
    }

    fun nextLesson(units: List<PathUnit>): PathLesson? =
        units.asSequence().flatMap { it.lessons.asSequence() }.firstOrNull { it.state == LessonState.AVAILABLE }

    /** Words never studied yet, in path order (deck, then lesson) — for "Yangi so'zlar". */
    fun untouched(units: List<PathUnit>): List<VocabWord> =
        units.flatMap { u -> u.lessons.flatMap { it.words } }.filter { it.status == WordStatus.NEW }
}

/** Builds the exercise list for a lesson or a review session (pure). */
object LessonPlanner {
    const val MAX_EXERCISES = 15
    const val MAX_REVIEW_WORDS = 12

    /**
     * New words come in pairs: two cards, then a question on one of them. Known words get one exercise at
     * their level. Then a matching round (words not asked yet first), then words go one step harder
     * (typing or their sentence) — up to [MAX_EXERCISES], so a lesson stays 3–5 minutes.
     */
    fun lesson(words: List<VocabWord>, pool: List<VocabWord>, random: Random, canListen: Boolean = true): List<Exercise> {
        val all = (pool + words).distinctBy { it.id }
        val out = mutableListOf<Exercise>()
        val asked = mutableSetOf<Long>()
        val (fresh, known) = words.partition { it.stage == 0 || it.status == WordStatus.NEW }
        fresh.chunked(2).forEach { pair ->
            pair.forEach { out += Exercise.Card(it) }
            val q = pair[random.nextInt(pair.size)]
            out += Exercises.forWord(q, 1, all, random, canListen)
            asked += q.id
        }
        known.forEach { w ->
            out += Exercises.forWord(w, w.stage, all, random, canListen)
            asked += w.id
        }
        val forMatch = words.shuffled(random).sortedBy { it.id in asked }
        Exercises.match(forMatch, random)?.let { m ->
            out += m
            asked += m.wordIds
        }
        for (w in words.shuffled(random).sortedBy { it.id in asked }) {
            if (out.size >= MAX_EXERCISES) break
            out += Exercises.forWord(w, maxOf(2, w.stage + 1).coerceAtMost(Srs.MAX_STAGE), all, random, canListen)
        }
        return out.take(MAX_EXERCISES)
    }

    /** Due words, one exercise each at their own level (a card-level word gets a question instead). */
    fun review(due: List<VocabWord>, pool: List<VocabWord>, random: Random, canListen: Boolean = true): List<Exercise> {
        val words = due.take(MAX_REVIEW_WORDS)
        val all = (pool + words).distinctBy { it.id }
        val out = words.map { w -> Exercises.forWord(w, w.stage.coerceAtLeast(1), all, random, canListen) }.toMutableList()
        if (words.size >= 5) Exercises.match(words.shuffled(random), random)?.let { out.add(out.size / 2, it) }
        return out
    }

    /** A second chance for a missed word, later in the same session: a plain recognition question. */
    fun retry(word: VocabWord, pool: List<VocabWord>, random: Random): Exercise = Exercises.choose(word, pool, random)
}

/**
 * Per-session scheduling: a word's SRS moves once per session (its first graded answer), so two right answers
 * in one lesson don't jump it from 1 to 3 days. A later miss still pulls it back.
 * Returns the grade to apply, or null when this answer is only logged.
 */
class SessionScheduler {
    private val applied = mutableMapOf<Long, ReviewGrade>()

    fun gradeToApply(wordId: Long, grade: ReviewGrade): ReviewGrade? {
        val before = applied[wordId]
        return when {
            before == null -> grade.also { applied[wordId] = it }
            grade == ReviewGrade.AGAIN && before != ReviewGrade.AGAIN -> grade.also { applied[wordId] = it }
            else -> null
        }
    }
}
