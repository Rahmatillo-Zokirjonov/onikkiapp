package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChainPathTest {
    private fun w(e: String) = ImportedWord(e, "uz-$e", null, null)

    @Test
    fun reviewDaysAreOneThreeAndSevenDaysAgo() {
        assertEquals(listOf(9, 7, 3), ChainPath.reviewDays(10))
        assertEquals(listOf(2), ChainPath.reviewDays(3))
        assertTrue(ChainPath.reviewDays(1).isEmpty())
    }

    @Test
    fun choicesHaveTheAnswerAndThreeOthers() {
        val today = listOf(w("price"), w("future"))
        val qs = ChainPath.choices(today, listOf(w("weather"), w("Price ")), Random(3))
        assertEquals(2, qs.size)
        qs.forEach { q ->
            assertEquals(4, q.options.size)
            assertEquals(4, q.options.map(AnswerCheck::normalize).distinct().size)
            assertEquals(1, q.options.count { ChainPath.isRight(q, it) })
        }
        assertTrue(ChainPath.isRight(qs[0], " ${qs[0].answer.english.uppercase()} "))
    }

    @Test
    fun matchingIsFilledWithEarlierWordsAndShuffled() {
        val today = listOf(w("a1"), w("a2"))
        val earlier = (1..10).map { w("e$it") }
        assertEquals(listOf("a1", "a2", "e10", "e9", "e8"), ChainPath.matchWords(today, earlier).map { it.english })
        val many = (1..7).map { w("t$it") }
        assertEquals(7, ChainPath.matchWords(many, earlier).size)
        repeat(30) { seed ->
            val order = ChainPath.matchOrder(5, Random(seed))
            assertEquals((0 until 5).toSet(), order.toSet())
            assertNotEquals((0 until 5).toList(), order)
        }
        assertEquals(listOf(0), ChainPath.matchOrder(1))
        assertFalse(ChainPath.matchWords(today, emptyList()).isEmpty())
    }
}
