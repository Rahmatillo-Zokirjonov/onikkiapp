package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import java.time.LocalDate
import kotlin.random.Random

/**
 * "Bilmaganlarim" — relearning the words the user doesn't know, Englify-style:
 * - inside a session a missed word comes back [GAP] exercises later, in a new shape (choose → listen → type),
 *   until it is answered right (at most [MAX_TRIES] times);
 * - across sessions a missed word stays on the "Bilmaganlarim" list until it is answered right in
 *   [CLEAR_AFTER] different sessions;
 * - a word forgotten [LEECH_LAPSES] times is "qiyin": its intervals grow slower.
 * Pure; the ViewModel and the repository call it.
 */
object Relearn {
    const val GAP = 2
    /** A missed word comes back at most twice in one session (more felt like the same word over and over). */
    const val MAX_TRIES = 2
    const val CLEAR_AFTER = 2
    const val LEECH_LAPSES = 4
    const val MAX_WORDS = 8
    const val MAX_EXERCISES = 18

    /** Where the next try of a word missed at [current] goes in a list of [size] exercises. */
    fun position(current: Int, size: Int, offset: Int = 0): Int = (current + 1 + GAP + offset).coerceAtMost(size)

    /** The shape of the [attempt]-th try (1-based): recognise it, hear it, then write it. */
    fun retryExercise(word: VocabWord, attempt: Int, pool: List<VocabWord>, random: Random, canListen: Boolean = true): Exercise = when {
        attempt <= 1 -> Exercises.choose(word, pool, random)
        attempt == 2 && canListen -> Exercises.listen(word, pool, random)
        attempt == 2 -> Exercises.choose(word, pool, random)
        else -> Exercises.typeIn(word)
    }

    /** After a scheduled answer: a miss puts the word on the list, right answers in later sessions take it off. */
    fun track(word: VocabWord, grade: ReviewGrade, nowMillis: Long): VocabWord = when {
        grade == ReviewGrade.AGAIN -> word.copy(unknownSince = word.unknownSince ?: nowMillis, relearnHits = 0)
        word.unknownSince == null -> word
        word.relearnHits + 1 >= CLEAR_AFTER -> word.copy(unknownSince = null, relearnHits = 0)
        else -> word.copy(relearnHits = word.relearnHits + 1)
    }

    fun isLeech(word: VocabWord): Boolean = word.lapses >= LEECH_LAPSES

    /** The list, oldest unknown first. */
    fun unknown(words: List<VocabWord>): List<VocabWord> =
        words.filter { it.unknownSince != null }.sortedWith(compareBy<VocabWord> { it.unknownSince }.thenByDescending { it.lapses })

    /**
     * A "Bilmaganlarim" session: every word is shown again as a card first (its meaning is the point),
     * two at a time, then asked; then a matching round and a typing round.
     */
    fun session(words: List<VocabWord>, pool: List<VocabWord>, random: Random, canListen: Boolean = true): List<Exercise> {
        val picked = words.take(MAX_WORDS)
        val all = (pool + picked).distinctBy { it.id }
        val out = mutableListOf<Exercise>()
        picked.chunked(2).forEach { pair ->
            pair.forEach { out += Exercise.Card(it) }
            pair.shuffled(random).forEach { out += Exercises.forWord(it, 1, all, random, canListen) }
        }
        Exercises.match(picked.shuffled(random), random)?.let { out += it }
        for (w in picked.shuffled(random)) {
            if (out.size >= MAX_EXERCISES) break
            out += Exercises.forWord(w, maxOf(2, w.stage).coerceAtMost(Srs.MAX_STAGE), all, random, canListen)
        }
        return out.take(MAX_EXERCISES)
    }

    /** "Bilaman" on a new word: skip the lesson cards, but check it again in a few days. */
    fun markKnown(word: VocabWord, today: LocalDate, nowMillis: Long): VocabWord = word.copy(
        status = WordStatus.REVIEW,
        intervalDays = 3,
        stage = maxOf(word.stage, 2),
        dueDate = today.plusDays(3),
        unknownSince = null,
        relearnHits = 0,
        lastReviewedAt = nowMillis
    )

    /** "Bilmayman": straight onto the list (a new word also stays in its lesson). */
    fun markUnknown(word: VocabWord, nowMillis: Long): VocabWord =
        word.copy(unknownSince = word.unknownSince ?: nowMillis, relearnHits = 0)
}

/** Exercises with a flag per item: a retry of a missed word (it doesn't cost a heart). */
data class ExerciseQueue(val items: List<Exercise>, val retries: Set<Int> = emptySet()) {
    fun isRetry(i: Int): Boolean = i in retries

    /** Inserts [exercise] at [at], shifting later retry flags. */
    fun insertRetry(at: Int, exercise: Exercise): ExerciseQueue {
        val pos = at.coerceIn(0, items.size)
        val shifted = retries.map { if (it >= pos) it + 1 else it }.toSet()
        return ExerciseQueue(items.toMutableList().apply { add(pos, exercise) }, shifted + pos)
    }
}
