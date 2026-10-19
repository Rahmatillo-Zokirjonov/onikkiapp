package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ListeningTest {
    @Test
    fun everyLibraryTextGetsItsGaps() {
        Listening.LEVELS.forEach { l -> assertEquals(6, ListeningLibrary.TEXTS.count { it.level == l }) }
        assertEquals(ListeningLibrary.TEXTS.size, ListeningLibrary.TEXTS.map { it.id }.toSet().size)
        ListeningLibrary.TEXTS.forEach { t ->
            repeat(5) { seed ->
                val plan = Listening.plan(t, emptySet(), Random(seed))
                assertEquals(t.id, Listening.gapsFor(t.level), plan.answers.size)
                // The pieces put back together are the text.
                assertEquals(t.sentences, plan.sentences.map { s -> s.joinToString("") { it.text } })
                // Gaps are numbered in reading order and hold their answer.
                val gaps = plan.sentences.flatten().filter { it.gap != null }
                assertEquals(plan.answers.indices.toList(), gaps.map { it.gap })
                assertEquals(plan.answers, gaps.map { it.text })
                plan.answers.forEach { a -> assertTrue(a, a.length >= 3 && a[0].isLowerCase() && '\'' !in a) }
            }
        }
    }

    @Test
    fun gapsPreferTheUsersWords_andAreNeverNextToEachOther() {
        val t = ListeningLibrary.TEXTS.first { it.id == "a1-3" }
        val plan = Listening.plan(t, setOf("melons", "heavy"), Random(1))
        assertTrue("melons" in plan.answers && "heavy" in plan.answers)
        plan.sentences.forEach { s ->
            val idx = s.mapIndexedNotNull { i, p -> if (p.gap != null) i else null }
            idx.zipWithNext().forEach { (a, b) -> assertTrue(b - a > 2) }
        }
    }

    @Test
    fun checkingAndScoring() {
        val v = Listening.check(listOf("market", "tomatos", ""), listOf("market", "tomatoes", "fresh"))
        assertEquals(listOf(Verdict.CORRECT, Verdict.ALMOST, Verdict.WRONG), v)
        assertEquals(50, Listening.accuracy(v))
        assertEquals(7, Listening.xp(v))
        assertEquals(30, Listening.xp(List(4) { Verdict.CORRECT }))
    }

    @Test
    fun bankHoldsAnswersPlusDistractors() {
        val t = ListeningLibrary.TEXTS.first { it.id == "b1-1" }
        val plan = Listening.plan(t, emptySet(), Random(2))
        val bank = Listening.bank(plan, Random(2))
        assertTrue(bank.containsAll(plan.answers))
        assertEquals(plan.answers.size + 2, bank.size)
    }

    @Test
    fun levelSuggestionFollowsLearnedWords() {
        fun words(n: Int) = (1..n).map { VocabWord(id = it.toLong(), english = "w$it", uzbek = "s", status = WordStatus.REVIEW) }
        assertEquals("A1", Listening.suggestLevel(words(10)))
        assertEquals("A2", Listening.suggestLevel(words(100)))
        assertEquals("B1", Listening.suggestLevel(words(300)))
        assertEquals("B2", Listening.suggestLevel(words(600)))
    }
}
