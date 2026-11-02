package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WordChainTest {
    private fun w(e: String) = ImportedWord(e, "uz-$e", null, null)
    private val small = (1..10).map { w("word$it") }

    @Test
    fun dayNBringsNNewWords() {
        assertEquals(listOf(1, 3, 6, 10, 15), (1..5).map { WordChain.totalUpTo(it) })
        assertEquals(listOf("word1"), WordChain.wordsFor(1, small).map { it.english })
        assertEquals(listOf("word2", "word3"), WordChain.wordsFor(2, small).map { it.english })
        assertEquals(listOf("word4", "word5", "word6"), WordChain.wordsFor(3, small).map { it.english })
        assertEquals(4, WordChain.wordsFor(4, small).size)
        assertTrue(WordChain.wordsFor(0, small).isEmpty())
        // No word appears on two days.
        val all = (1..4).flatMap { WordChain.wordsFor(it, small) }
        assertEquals(all.size, all.distinct().size)
    }

    @Test
    fun shortListIsReported() {
        val start = LocalDate.of(2026, 11, 2)
        assertEquals(5, WordChain.firstShortDay(start, small)) // 15 > 10
        val day5 = WordChain.days(start, start, emptySet(), small)[4]
        assertFalse(day5.complete)
        assertEquals(15, day5.total)
        assertNull(WordChain.firstShortDay(start, (1..10_000).map { w("x$it") }))
    }

    @Test
    fun poolStartsWithCuratedWordsWithoutRepeats() {
        val pool = WordChain.pool(listOf(w("Weather"), w("brandnew")))
        assertEquals(WordChain.WORDS.size + 1, pool.size)
        assertEquals("achieve", pool.first().english)
        assertEquals("brandnew", pool.last().english)
    }

    @Test
    fun daysUntilJanuary15WithStates() {
        val start = LocalDate.of(2026, 11, 2)
        val today = LocalDate.of(2026, 11, 4)
        val days = WordChain.days(start, today, setOf(1), WordChain.pool(emptyList()))
        assertEquals(WordChain.END, days.last().date)
        assertEquals(75, days.size)
        assertEquals(listOf(ChainDayState.DONE, ChainDayState.MISSED, ChainDayState.TODAY, ChainDayState.FUTURE), days.take(4).map { it.state })
        assertEquals(3, days[2].words.size)
        assertEquals(3, WordChain.dayNumber(start, today))
    }

    @Test
    fun checkAndStreak() {
        val words = WordChain.wordsFor(2, small)
        assertTrue(WordChain.passed(WordChain.check(listOf("word2", " Word3 "), words)))
        assertFalse(WordChain.passed(WordChain.check(listOf("word2", ""), words)))
        assertEquals(3, WordChain.streak(setOf(1, 2, 3), 3))
        assertEquals(2, WordChain.streak(setOf(1, 2), 3))
        assertEquals(0, WordChain.streak(setOf(1), 3))
    }
}
