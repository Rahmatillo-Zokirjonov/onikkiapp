package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WordPracticeTest {
    @Test
    fun everyPairHasQuestions_andBothWordsAreAnswers() {
        val required = listOf("lend-borrow", "interesting-interested", "boring-bored", "advice-advise", "expensive-expansive", "say-tell", "make-do", "learn-teach", "look-watch", "then-than")
        val keys = ConfusingPairs.PAIRS.map { it.key }
        assertTrue(keys.containsAll(required))
        ConfusingPairs.PAIRS.forEach { p ->
            val qs = ConfusingPairs.BUILT_IN.filter { it.pair.key == p.key }
            assertTrue(p.key, qs.size >= 2)
            assertEquals(p.key, setOf(0, 1), qs.map { it.answer }.toSet())
            qs.forEach { assertTrue(it.sentence, "___" in it.sentence && it.why.isNotBlank()) }
        }
        // The user's own examples are in.
        assertTrue(ConfusingPairs.BUILT_IN.any { it.sentence == "Can you ___ me your pen?" && it.right == "lend" })
        assertTrue(ConfusingPairs.BUILT_IN.any { it.sentence == "Tashkent is bigger ___ Fergana." && it.right == "than" })
    }

    @Test
    fun roundsSpreadPairsAndPreferUnseen() {
        val pool = ConfusingPairs.BUILT_IN
        repeat(10) { seed ->
            val r = ConfusingPairs.round(pool, emptyList(), Random(seed))
            assertEquals(10, r.size)
            r.zipWithNext().forEach { (a, b) -> assertFalse(a.pair.key == b.pair.key) }
        }
        val seen = pool.take(30).map { it.key }
        assertTrue(ConfusingPairs.round(pool, seen, Random(1), size = 5).none { it.key in seen })
    }

    @Test
    fun customPairs() {
        assertTrue(ConfusingPairs.isValid("affect", "effect", "Stress can ___ your health.", 0))
        assertFalse(ConfusingPairs.isValid("affect", "affect", "x ___", 0))
        assertFalse(ConfusingPairs.isValid("affect", "effect", "no gap here", 0))
        val q = ConfusingPairs.custom(7, " affect ", "effect", "ta'sir qilmoq", "ta'sir", "Stress can ___ your health.", 0, "fe'l")
        assertEquals("u7", q.pair.key)
        assertEquals("affect", q.right)
    }

    @Test
    fun familiesSplitRootAndEnding() {
        val required = listOf("danger", "health", "help", "care", "success", "beauty", "friend", "interest", "teach", "write", "play", "sun", "rain")
        assertTrue(WordFamilies.ALL.map { it.root }.containsAll(required))
        assertEquals("danger" to "ous", WordFamilies.split("danger", "dangerous"))
        assertEquals("beaut" to "iful", WordFamilies.split("beauty", "beautiful"))
        assertEquals("sun" to "ny", WordFamilies.split("sun", "sunny"))
        assertEquals("teach" to "er", WordFamilies.split("teach", "teacher"))
        WordFamilies.QUESTIONS.forEach { assertTrue(it.member.sentence, "___" in it.member.sentence) }
    }

    @Test
    fun familyLookupAndChecking() {
        assertEquals("danger", WordFamilies.of("Dangerous")?.root)
        assertEquals("interest", WordFamilies.of("interested")?.root)
        assertEquals("care", WordFamilies.of("careless", familyRoot = "care")?.root)
        assertNull(WordFamilies.of("table"))
        val q = WordFamilies.QUESTIONS.first { it.member.word == "dangerous" }
        assertEquals(Verdict.CORRECT, WordFamilies.check(q, "Dangerous"))
        assertEquals(Verdict.WRONG, WordFamilies.check(q, "dangerus"))
        assertNotNull(ModeStats.LABELS["PAIR"])
        assertNotNull(ModeStats.LABELS["FAMILY"])
    }
}
