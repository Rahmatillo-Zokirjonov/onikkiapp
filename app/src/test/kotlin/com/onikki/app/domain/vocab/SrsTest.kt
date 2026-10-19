package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SrsTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val fresh = VocabWord(id = 1, english = "decide", uzbek = "qaror qilmoq")

    private fun VocabWord.easyOn(day: LocalDate) = Srs.review(this, ReviewGrade.EASY, day, 0)

    @Test
    fun easyAnswersClimbTheLadder_thenMastered() {
        var w = fresh
        var day = today
        val seen = mutableListOf<Int>()
        repeat(6) {
            w = w.copy(ease = 2.5) // keep the plain ladder for the check
            w = w.easyOn(day)
            seen += w.intervalDays
            day = w.dueDate!!
        }
        assertEquals(listOf(1, 3, 7, 14, 30), seen.take(5))
        assertTrue(seen[5] > 30)
        assertEquals(WordStatus.MASTERED, w.status)
        assertEquals(Srs.MAX_STAGE, w.stage)
    }

    @Test
    fun statusFollowsTheInterval() {
        val first = fresh.easyOn(today)
        assertEquals(WordStatus.LEARNING, first.status)
        assertEquals(today.plusDays(1), first.dueDate)
        assertEquals(1, first.stage)
        assertEquals(WordStatus.REVIEW, first.easyOn(today.plusDays(1)).status)
    }

    @Test
    fun aWrongAnswerSendsAKnownWordBackToLearning() {
        val known = fresh.copy(status = WordStatus.REVIEW, intervalDays = 14, ease = 2.5, stage = 3, dueDate = today)
        val missed = Srs.review(known, ReviewGrade.AGAIN, today, 0)
        assertEquals(WordStatus.LEARNING, missed.status)
        assertEquals(1, missed.intervalDays)
        assertEquals(today.plusDays(1), missed.dueDate)
        assertEquals(1, missed.lapses)
        assertEquals(1, missed.wrongCount)
        assertEquals(2, missed.stage)
        assertEquals(2.3, missed.ease, 1e-9)
        // A new word missed in its first lesson isn't a "lapse".
        assertEquals(0, Srs.review(fresh, ReviewGrade.AGAIN, today, 0).lapses)
    }

    @Test
    fun hardStaysNearItsStepAndLowersEase() {
        val w = fresh.copy(status = WordStatus.REVIEW, intervalDays = 7, ease = 2.5, stage = 2)
        val hard = Srs.review(w, ReviewGrade.HARD, today, 0)
        assertEquals(8, hard.intervalDays)
        assertEquals(2.35, hard.ease, 1e-9)
        assertEquals(2, hard.stage)
        assertEquals(WordStatus.REVIEW, hard.status)
        // Ease never drops below the floor.
        val floor = Srs.review(w.copy(ease = 1.35), ReviewGrade.HARD, today, 0)
        assertEquals(Srs.MIN_EASE, floor.ease, 1e-9)
    }

    @Test
    fun dueQueueSkipsNewAndFutureWords_mostOverdueFirst() {
        val a = fresh.copy(id = 1, status = WordStatus.REVIEW, dueDate = today)
        val b = fresh.copy(id = 2, status = WordStatus.LEARNING, dueDate = today.minusDays(3))
        val c = fresh.copy(id = 3, status = WordStatus.REVIEW, dueDate = today.plusDays(1))
        val d = fresh.copy(id = 4, status = WordStatus.NEW, dueDate = null)
        assertEquals(listOf(2L, 1L), Srs.dueQueue(listOf(a, b, c, d), today).map { it.id })
        assertFalse(Srs.isDue(d, today))
    }

    @Test
    fun answersBecomeGradesAndXp() {
        assertEquals(ReviewGrade.EASY, Srs.gradeFor(Verdict.CORRECT, usedHint = false))
        assertEquals(ReviewGrade.HARD, Srs.gradeFor(Verdict.CORRECT, usedHint = true))
        assertEquals(ReviewGrade.HARD, Srs.gradeFor(Verdict.ALMOST, usedHint = false))
        assertEquals(ReviewGrade.AGAIN, Srs.gradeFor(Verdict.WRONG, usedHint = false))
        assertEquals(12, Srs.xpFor(ReviewGrade.EASY, hardExercise = true))
        assertEquals(5, Srs.xpFor(ReviewGrade.HARD, hardExercise = false))
        assertEquals(0, Srs.xpFor(ReviewGrade.AGAIN, hardExercise = true))
    }
}
