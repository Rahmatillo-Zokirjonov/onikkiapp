package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import com.onikki.app.data.local.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class BlockerChallengeTest {
    private val NOW = 1_800_000_000_000L
    private val today = LocalDate.of(2026, 10, 19)
    private fun w(id: Long, status: WordStatus, due: LocalDate?) =
        VocabWord(id = id, english = "word$id", uzbek = "soz$id", status = status, dueDate = due, stage = 2)

    private val words = listOf(
        w(1, WordStatus.NEW, null),
        w(2, WordStatus.REVIEW, today.plusDays(5)),
        w(3, WordStatus.REVIEW, today.minusDays(1)),
        w(4, WordStatus.LEARNING, today),
        w(5, WordStatus.REVIEW, today.plusDays(1))
    )

    @Test
    fun dueWordsFirst_thenEveryWordGetsItsTurn() {
        val c = BlockerChallenge(words, today, needed = 5, type = QuestionType.CHOICE, random = Random(1), nowMillis = NOW)
        val asked = (1..5).map { c.next()!!.wordIds.single() }
        assertEquals(setOf(3L, 4L), asked.take(2).toSet()) // due today or overdue
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), asked.toSet()) // new and not-due words come too
    }

    @Test
    fun wordsAskedLatelyWait_neverAskedComeFirst() {
        val hour = 3_600_000L
        val recent = words.map {
            when (it.id) {
                2L -> it.copy(lastAskedAt = NOW - hour) // just answered → last
                5L -> it.copy(lastAskedAt = NOW - 50 * hour)
                else -> it
            }
        }
        val c = BlockerChallenge(recent, today, needed = 5, type = QuestionType.CHOICE, random = Random(2), nowMillis = NOW)
        val asked = (1..5).map { c.next()!!.wordIds.single() }
        assertEquals(2L, asked.last())
        assertTrue(asked.indexOf(1L) < asked.indexOf(5L)) // never asked before asked two days ago
    }

    @Test
    fun aNewWordIsOnlyRecognised() {
        val many = words + (6..9).map { w(it.toLong(), WordStatus.REVIEW, today.plusDays(9)) }
        val c = BlockerChallenge(many, today, needed = 9, type = QuestionType.TYPE, random = Random(3), nowMillis = NOW)
        repeat(9) {
            val ex = c.next() ?: return@repeat
            if (ex.wordIds.single() == 1L) assertFalse(ex.type.hard)
        }
    }

    @Test
    fun threeRightOpensTheApp_andOneMustBeTyped() {
        val c = BlockerChallenge(words, today, needed = 3, type = QuestionType.CHOICE, random = Random(2))
        val a = c.next()!!
        assertTrue(a is Exercise.Choose)
        c.answer(a, Verdict.CORRECT)
        val b = c.next()!!
        c.answer(b, Verdict.CORRECT)
        // Two choices right, none typed: the last slot is forced to typing.
        val last = c.next()!!
        assertTrue(last.type.hard)
        assertFalse(c.passed)
        c.answer(last, Verdict.ALMOST) // "deyarli" counts
        assertTrue(c.passed)
        assertNull(c.next())
    }

    @Test
    fun aMissGivesADifferentWordAndDoesNotCount() {
        val c = BlockerChallenge(words, today, needed = 3, type = QuestionType.TYPE, random = Random(3))
        val first = c.next()!!
        c.answer(first, Verdict.WRONG)
        assertEquals(0, c.solved)
        val second = c.next()!!
        assertNotEquals(first.wordIds, second.wordIds)
        assertTrue(second.type.hard || words.first { it.id == second.wordIds.single() }.status == WordStatus.NEW)
    }

    @Test
    fun fewWordsStillWork() {
        val one = listOf(w(9, WordStatus.NEW, null))
        val c = BlockerChallenge(one, today, needed = 3, type = QuestionType.CHOICE, random = Random(4))
        repeat(3) {
            val ex = c.next()!!
            assertTrue(ex is Exercise.TypeIn) // not enough words for four options
            c.answer(ex, Verdict.CORRECT)
        }
        assertTrue(c.passed)
        assertNull(BlockerChallenge(emptyList(), today, 3, QuestionType.MIXED, Random(5)).next())
    }
}
