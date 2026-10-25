package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.domain.ai.AiPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SentenceTest {
    private val go = VocabWord(id = 1, english = "go", uzbek = "bormoq", pastForm = "went", participle = "gone", example = "She went to school by bus.", exampleUz = "U maktabga avtobusda bordi.", stage = 3)

    @Test
    fun everyStarterExampleHasItsTranslation() {
        val all = VocabImport.parse(VocabImport.LESSON_1) + IrregularVerbs.ALL.flatMap { VocabImport.parse(it.second) }
        assertEquals(121, all.size)
        all.forEach { assertTrue(it.english, it.example != null && !it.exampleUz.isNullOrBlank()) }
        val line = VocabImport.parseLine("go (went, gone) | bormoq | She went home. | U uyga ketdi.")!!
        assertEquals("U uyga ketdi.", line.exampleUz)
    }

    @Test
    fun translationComparison() {
        assertEquals(Verdict.CORRECT, SentenceCheck.compare("she went to school by bus", go.example!!))
        assertEquals(Verdict.CORRECT, SentenceCheck.compare("I'm busy today.", "I am busy today."))
        assertEquals(Verdict.ALMOST, SentenceCheck.compare("She went to the school by bus.", go.example!!))
        assertEquals(Verdict.ALMOST, SentenceCheck.compare("She wnet to school by bus.", go.example!!))
        assertEquals(Verdict.WRONG, SentenceCheck.compare("He goes home.", go.example!!))
        assertEquals(Verdict.WRONG, SentenceCheck.compare("", go.example!!))
    }

    @Test
    fun ownSentenceOfflineCheck() {
        assertEquals(Verdict.CORRECT, SentenceCheck.offline("Yesterday I went to the market.", go).verdict)
        assertEquals(Verdict.CORRECT, SentenceCheck.offline("He has gone home already.", go).verdict)
        assertEquals(Verdict.WRONG, SentenceCheck.offline("I like apples very much.", go).verdict)
        assertEquals(Verdict.WRONG, SentenceCheck.offline("Go home.", go).verdict)
    }

    @Test
    fun aiVerdictParsing() {
        val ok = AiPlans.parseSentence("""{"uses_word":true,"correct":true,"minor":false,"corrected":"I went home.","explanation":"Zo'r!"}""", "I went home.")!!
        assertEquals(Verdict.CORRECT, ok.verdict)
        assertNull(ok.corrected)
        val slip = AiPlans.parseSentence("""{"uses_word":true,"correct":false,"minor":true,"corrected":"I went to the park.","explanation":"Predlog kerak."}""", "I went park.")!!
        assertEquals(Verdict.ALMOST, slip.verdict)
        assertEquals("I went to the park.", slip.corrected)
        val off = AiPlans.parseSentence("""{"uses_word":false,"correct":true,"minor":false,"corrected":"","explanation":"So'z yo'q"}""", "Hi there friend.")!!
        assertEquals(Verdict.WRONG, off.verdict)
        assertNull(AiPlans.parseSentence("not json", "x"))
    }

    @Test
    fun practiceAndLessonShapes() {
        val young = go.copy(id = 2, stage = 1)
        val plain = VocabWord(id = 3, english = "apple", uzbek = "olma", status = com.onikki.app.data.db.entity.WordStatus.REVIEW)
        val ex = Exercises.sentencePractice(listOf(go, young, plain), listOf(go, young, plain), Random(1))
        assertTrue(ex[0] is Exercise.Translate)
        assertTrue(ex[1] is Exercise.Compose)
        assertTrue(ex[2] is Exercise.Build && (ex[2] as Exercise.Build).translation == go.exampleUz)
        assertTrue(ex[3] is Exercise.Compose)
        assertTrue(ex[4] is Exercise.Compose)
        assertEquals(5, ex.size)
        // In lessons, stage-3 words with a translation sometimes get Uzbek → English.
        val shapes = (0 until 60).map { Exercises.forWord(go.copy(pastForm = null, participle = null), 3, listOf(go), Random(it)) }
        assertTrue(shapes.any { it is Exercise.Translate || (it is Exercise.Build && it.translation != null) })
        assertFalse(shapes.any { it is Exercise.Compose })
    }
}
