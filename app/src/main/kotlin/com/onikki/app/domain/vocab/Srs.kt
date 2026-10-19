package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Simplified SM-2 on a fixed ladder of days (1 → 3 → 7 → 14 → 30):
 * - AGAIN (Bilmadim): back to LEARNING, due tomorrow, ease −0.2, one exercise stage down.
 * - HARD (Qiyin): stays on its step (a little longer), ease −0.15.
 * - EASY (Oson): next step on the ladder (stretched a bit by a high ease), one exercise stage up.
 * Past the 30-day step a word is MASTERED. Pure; Room rows go in and come out.
 */
object Srs {
    val LADDER = listOf(1, 3, 7, 14, 30)
    const val MIN_EASE = 1.3
    const val MAX_EASE = 3.0
    const val MAX_STAGE = 3
    private const val MASTERED_AFTER = 30

    fun review(word: VocabWord, grade: ReviewGrade, today: LocalDate, nowMillis: Long): VocabWord {
        val wasKnown = word.status == WordStatus.REVIEW || word.status == WordStatus.MASTERED
        return when (grade) {
            ReviewGrade.AGAIN -> word.copy(
                status = WordStatus.LEARNING,
                intervalDays = 1,
                ease = (word.ease - 0.2).coerceAtLeast(MIN_EASE),
                dueDate = today.plusDays(1),
                stage = (word.stage - 1).coerceAtLeast(if (word.stage > 0) 1 else 0),
                lapses = word.lapses + if (wasKnown) 1 else 0,
                wrongCount = word.wrongCount + 1,
                lastReviewedAt = nowMillis,
                lastAskedAt = nowMillis
            )
            ReviewGrade.HARD -> {
                val interval = if (word.intervalDays <= 0) 1 else maxOf(word.intervalDays + 1, (word.intervalDays * 1.2).roundToInt())
                word.copy(
                    status = statusFor(interval, word.status),
                    intervalDays = interval,
                    ease = (word.ease - 0.15).coerceAtLeast(MIN_EASE),
                    dueDate = today.plusDays(interval.toLong()),
                    stage = maxOf(word.stage, 1),
                    correctCount = word.correctCount + 1,
                    lastReviewedAt = nowMillis,
                    lastAskedAt = nowMillis
                )
            }
            ReviewGrade.EASY -> {
                val step = nextStep(word.intervalDays, word.ease)
                // A "qiyin" word (forgotten again and again) climbs slower.
                val interval = if (word.lapses >= Relearn.LEECH_LAPSES) maxOf(word.intervalDays + 1, (step * 0.6).roundToInt()) else step
                word.copy(
                    status = statusFor(interval, word.status),
                    intervalDays = interval,
                    ease = (word.ease + 0.05).coerceAtMost(MAX_EASE),
                    dueDate = today.plusDays(interval.toLong()),
                    stage = (word.stage + 1).coerceAtMost(MAX_STAGE),
                    correctCount = word.correctCount + 1,
                    lastReviewedAt = nowMillis,
                    lastAskedAt = nowMillis
                )
            }
        }
    }

    /** The next rung above [interval]; past the ladder it grows by the ease. A high ease stretches a rung slightly. */
    fun nextStep(interval: Int, ease: Double): Int {
        val rung = LADDER.firstOrNull { it > interval } ?: return (interval * ease).roundToInt().coerceAtLeast(interval + 1)
        val stretch = if (ease > 2.5) ease / 2.5 else 1.0
        return (rung * stretch).roundToInt().coerceAtLeast(rung)
    }

    private fun statusFor(interval: Int, current: WordStatus): WordStatus = when {
        interval > MASTERED_AFTER -> WordStatus.MASTERED
        interval >= 3 -> WordStatus.REVIEW
        current == WordStatus.MASTERED || current == WordStatus.REVIEW -> WordStatus.REVIEW
        else -> WordStatus.LEARNING
    }

    /** Studied words whose day has come (NEW words are introduced by lessons, not "due"). */
    fun isDue(word: VocabWord, today: LocalDate): Boolean =
        word.status != WordStatus.NEW && word.dueDate != null && !word.dueDate.isAfter(today)

    /** Most overdue first, then the most-forgotten. */
    fun dueQueue(words: List<VocabWord>, today: LocalDate): List<VocabWord> =
        words.filter { isDue(it, today) }.sortedWith(compareBy<VocabWord> { it.dueDate }.thenByDescending { it.lapses + it.wrongCount })

    /** An exercise answer → a grade: right = EASY, "deyarli" or a hint = HARD, wrong = AGAIN. */
    fun gradeFor(verdict: Verdict, usedHint: Boolean): ReviewGrade = when {
        verdict == Verdict.WRONG -> ReviewGrade.AGAIN
        verdict == Verdict.ALMOST || usedHint -> ReviewGrade.HARD
        else -> ReviewGrade.EASY
    }

    /** XP for an answer: 10 right, 5 "deyarli"/hint, 0 wrong; +2 for a typed or sentence exercise. */
    fun xpFor(grade: ReviewGrade, hardExercise: Boolean): Int = when (grade) {
        ReviewGrade.AGAIN -> 0
        ReviewGrade.HARD -> 5
        ReviewGrade.EASY -> 10
    } + if (hardExercise && grade != ReviewGrade.AGAIN) 2 else 0
}
