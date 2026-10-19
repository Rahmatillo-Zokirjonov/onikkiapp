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
    fun dueWordsComeFirst_newWordsLast() {
        val c = BlockerChallenge(words, today, needed = 5, type = QuestionType.CHOICE, random = Random(1))
        val asked = (1..5).map { c.next()!!.wordIds.single() }
        assertEquals(listOf(3L, 4L, 5L, 2L, 1L), asked)
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
        assertTrue(last is Exercise.TypeIn)
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
        assertTrue(second is Exercise.TypeIn)
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
