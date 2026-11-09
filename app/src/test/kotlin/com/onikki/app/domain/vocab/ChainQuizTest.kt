package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChainQuizTest {
    private fun w(e: String) = ImportedWord(e, "uz-$e", null, null)
    private val words = (0 until 10).map { w("word$it") }

    @Test
    fun questionsPointAtTheRightWords() {
        val quiz = ChainQuiz(words, 200, emptyMap(), Random(1))
        val seen = HashSet<ChainQuestionKind>()
        repeat(60) {
            val q = quiz.next()!!
            seen += q.kind
            when (q.kind) {
                ChainQuestionKind.NEXT -> assertEquals(q.given!! + 1, q.target)
                ChainQuestionKind.PREVIOUS -> assertEquals(q.given!! - 1, q.target)
                ChainQuestionKind.AT_POSITION -> assertNull(q.given)
                ChainQuestionKind.POSITION_OF -> assertEquals(q.given, q.target)
            }
            assertTrue(q.target in words.indices)
        }
        assertEquals(ChainQuestionKind.entries.toSet(), seen)
    }

    @Test
    fun judgingWordsAndPlaces() {
        val quiz = ChainQuiz(words, 2, emptyMap(), Random(2))
        val next = ChainQuestion(ChainQuestionKind.NEXT, 3, 4)
        assertEquals(Verdict.CORRECT, quiz.judge(" Word4 ", next))
        assertEquals(Verdict.WRONG, quiz.judge("word5", next))
        val place = ChainQuestion(ChainQuestionKind.POSITION_OF, 6, 6)
        assertEquals(Verdict.CORRECT, quiz.judge("7", place))
        assertEquals(Verdict.CORRECT, quiz.judge("7-o'rin", place))
        assertEquals(Verdict.WRONG, quiz.judge("6", place))
        assertEquals("7-o'rin", quiz.answerText(place))
        assertEquals("word4", quiz.answerText(next))
    }

    @Test
    fun passesAfterNeededRightAnswersAndReportsLinks() {
        val quiz = ChainQuiz(words, 2, emptyMap(), Random(3))
        assertEquals("word3>word4", quiz.answer(ChainQuestion(ChainQuestionKind.NEXT, 3, 4), Verdict.WRONG))
        assertFalse(quiz.passed)
        assertEquals("word4>word3", quiz.answer(ChainQuestion(ChainQuestionKind.PREVIOUS, 4, 3), Verdict.CORRECT))
        assertNull(quiz.answer(ChainQuestion(ChainQuestionKind.AT_POSITION, null, 2), Verdict.ALMOST))
        assertTrue(quiz.passed)
        assertNull(quiz.next())
        assertNull(ChainQuiz(words.take(1), 3, emptyMap(), Random(0)).next())
    }

    @Test
    fun weakLinksAreAskedMore() {
        val weak = mapOf(ChainSequence.linkKey(words[5], words[6]) to 2)
        val quiz = ChainQuiz(words, 500, weak, Random(4))
        // Each question is new within one challenge, so count only the first few.
        val hits = (0 until 100).count { seed ->
            val q = ChainQuiz(words, 3, weak, Random(seed)).next()!!
            q.kind == ChainQuestionKind.NEXT && q.given == 5
        }
        assertTrue("weak link first in $hits of 100", hits >= 40)
        assertTrue(quiz.next() != null)
    }
}
