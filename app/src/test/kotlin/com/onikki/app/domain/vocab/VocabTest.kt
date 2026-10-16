package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabTest {
    private val dict = EnglishDictionary(
        sequenceOf("look\tqaramoq", "book\tkitob", "look for\tqidirmoq", "good\tyaxshi", "lookup\tqidiruv", "Look\tdublikat", "broken line", "the\t(artikl)")
    )

    @Test
    fun dictionaryLooksUpAndSuggestsLikeASearchBox() {
        assertEquals("qaramoq", dict.lookup("  LOOK ")!!.uzbek)
        assertNull(dict.lookup("cat"))
        assertEquals(listOf("look", "look for", "lookup"), dict.suggest("lo").map { it.english })
        assertEquals(listOf("look for"), dict.suggest("fo").map { it.english })
        assertTrue(dict.suggest(" ").isEmpty())
        assertEquals(listOf("book", "look for"), dict.popularMissing(setOf("look"), 2).map { it.english })
    }

    @Test
    fun spacesApostrophesAndCaseNeverMakeItWrong() {
        assertEquals(Verdict.CORRECT, AnswerCheck.judge("Do‘st", listOf("do'st")))
        assertEquals(Verdict.CORRECT, AnswerCheck.judge("dost", listOf("do'st")))
        assertEquals(Verdict.CORRECT, AnswerCheck.judge("yordambermoq", listOf("yordam bermoq")))
        assertEquals(Verdict.CORRECT, AnswerCheck.judge("ulkan", listOf("katta", "ulkan")))
    }

    @Test
    fun oneSlipInALongerWordIsAlmost_shortWordsMustBeExact() {
        assertEquals(Verdict.ALMOST, AnswerCheck.judge("kitab", listOf("kitob")))
        assertEquals(Verdict.WRONG, AnswerCheck.judge("sut", listOf("suv")))
        assertEquals(Verdict.ALMOST, AnswerCheck.judge("o'qituvchu", listOf("o'qituvchi")))
        assertEquals(Verdict.WRONG, AnswerCheck.judge("daraxtlar", listOf("olma")))
        assertEquals(Verdict.WRONG, AnswerCheck.judge("", listOf("olma")))
    }

    @Test
    fun letterBoxesKeepSpacesAndApostrophesFixed() {
        val slots = AnswerCheck.slots("yo'l top")
        assertEquals(6, AnswerCheck.letterCount(slots))
        assertEquals(listOf('y', 'o', '\'', 'l', ' ', 't', null, null), AnswerCheck.fill(slots, "yolt"))
        assertEquals("yo'l top", AnswerCheck.assemble(slots, "yoltop"))
    }

    @Test
    fun choiceQuestionsHaveTheAnswerAndThreeDistinctWrongOnes() {
        val words = listOf("apple" to "olma", "book" to "kitob", "water" to "suv", "house" to "uy", "tree" to "daraxt")
            .mapIndexed { i, (en, uz) -> com.onikki.app.data.db.entity.VocabWord(id = i + 1L, english = en, uzbek = uz) }
        val qs = com.onikki.app.domain.screentime.VocabChallenge.pickQuestions(
            words, 3, com.onikki.app.data.local.ChallengeDirection.EN_UZ, seed = 7, type = com.onikki.app.data.local.QuestionType.CHOICE
        )
        qs.forEach { q ->
            assertEquals(4, q.options.size)
            assertEquals(4, q.options.toSet().size)
            assertTrue(q.primaryAnswer in q.options)
        }
        // Too few words for four options → typed questions instead; no TTS → no listening questions.
        val few = com.onikki.app.domain.screentime.VocabChallenge.pickQuestions(
            words.take(2), 2, com.onikki.app.data.local.ChallengeDirection.EN_UZ, seed = 1,
            type = com.onikki.app.data.local.QuestionType.MIXED, canListen = false
        )
        assertTrue(few.all { it.kind == com.onikki.app.domain.screentime.QuestionKind.TYPE })
    }
}
