package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class RotationTest {
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    // 30 words; 1–6 "need work"; each asked a different time ago (word 1 most recently).
    private val words = (1..30).map { i ->
        VocabWord(id = i.toLong(), english = "w$i", uzbek = "u$i", status = WordStatus.REVIEW, stage = if (i <= 6) 0 else 2, lastAskedAt = now - i * 10 * hour)
    }

    @Test
    fun needyWordsFillAtMostHalf_restAreLeastRecentlyAsked() {
        val picked = Rotation.pick(words, 10, now, Random(1)) { it.id <= 6 }
        assertEquals(10, picked.size)
        assertEquals(10, picked.map { it.id }.toSet().size)
        assertEquals(5, picked.count { it.id <= 6 })
        // The other five are the ones asked longest ago.
        assertEquals(setOf(26L, 27L, 28L, 29L, 30L), picked.filter { it.id > 6 }.map { it.id }.toSet())
    }

    @Test
    fun wordsAskedInTheLastHoursGoLast() {
        val fresh = words.map { if (it.id <= 6) it.copy(lastAskedAt = now - hour) else it }
        val picked = Rotation.pick(fresh, 10, now, Random(2)) { it.id <= 6 }
        assertTrue(picked.none { it.id <= 6 }) // asked an hour ago → not again yet
    }

    @Test
    fun consecutiveRoundsDontRepeatTheSameWords() {
        var pool = words
        val first = Rotation.pick(pool, 10, now, Random(3)) { it.stage == 0 }
        // Answering marks them as just asked.
        pool = pool.map { w -> if (first.any { it.id == w.id }) w.copy(lastAskedAt = now) else w }
        val second = Rotation.pick(pool, 10, now + 60_000, Random(4)) { it.stage == 0 }
        assertTrue(first.map { it.id }.intersect(second.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun hardWordLeavesTheListAfterTwoRightAnswers() {
        val today = LocalDate.of(2026, 10, 27)
        val misses = listOf(ReviewRow(1, "SPELL", ReviewGrade.AGAIN, today.minusDays(2)), ReviewRow(1, "SPELL", ReviewGrade.AGAIN, today.minusDays(1)))
        assertEquals(listOf(1L), HardSpelling.pick(misses, today))
        val oneRight = misses + ReviewRow(1, "SPELL", ReviewGrade.EASY, today)
        assertEquals(listOf(1L), HardSpelling.pick(oneRight, today))
        val twoRight = oneRight + ReviewRow(1, "DICTATION", ReviewGrade.EASY, today)
        assertTrue(HardSpelling.pick(twoRight, today).isEmpty())
        // Missed again → back on the list.
        assertFalse(HardSpelling.pick(twoRight + ReviewRow(1, "SPELL", ReviewGrade.AGAIN, today), today).isEmpty())
    }

    @Test
    fun aMissedWordComesBackAtMostTwiceInALesson() {
        assertEquals(2, Relearn.MAX_TRIES)
    }
}
