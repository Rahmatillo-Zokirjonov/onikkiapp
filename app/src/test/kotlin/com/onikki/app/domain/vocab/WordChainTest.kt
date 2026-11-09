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
    fun goalIsSpreadEvenlyAndReachedOnTheLastDay() {
        // 150 words over 75 days: +2 every day.
        assertEquals(listOf(2, 4, 6), (1..3).map { WordChain.reached(it, 75, 150) })
        assertEquals(150, WordChain.reached(75, 75, 150))
        assertTrue((1..75).all { WordChain.newCount(it, 75, 150) == 2 })
        // 150 over 68 days: 2 or 3 a day, never less, exactly 150 at the end.
        val counts = (1..68).map { WordChain.newCount(it, 68, 150) }
        assertTrue(counts.all { it in 2..3 })
        assertEquals(150, counts.sum())
        assertEquals(0, WordChain.reached(0, 68, 150))
        assertEquals(150, WordChain.reached(90, 68, 150))
    }

    @Test
    fun eachDayGetsTheNextWordsInOrder() {
        assertEquals(listOf("word1", "word2"), WordChain.wordsFor(1, small, 5, 10).map { it.english })
        assertEquals(listOf("word3", "word4"), WordChain.wordsFor(2, small, 5, 10).map { it.english })
        assertEquals(listOf("word9", "word10"), WordChain.wordsFor(5, small, 5, 10).map { it.english })
        assertTrue(WordChain.wordsFor(0, small, 5, 10).isEmpty())
        val all = (1..5).flatMap { WordChain.wordsFor(it, small, 5, 10) }
        assertEquals(small, all)
    }

    @Test
    fun shortListIsReported() {
        val start = WordChain.END.minusDays(9) // 10 days
        assertEquals(6, WordChain.firstShortDay(start, small, 20)) // 2 a day: day 6 needs 12 > 10
        val day6 = WordChain.days(start, start, emptySet(), small, 20)[5]
        assertFalse(day6.complete)
        assertEquals(12, day6.total)
        assertNull(WordChain.firstShortDay(start, small, 10))
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
        val days = WordChain.days(start, today, setOf(1), WordChain.pool(emptyList()), 150)
        assertEquals(WordChain.END, days.last().date)
        assertEquals(75, days.size)
        assertEquals(150, days.last().total)
        assertEquals(listOf(ChainDayState.DONE, ChainDayState.MISSED, ChainDayState.TODAY, ChainDayState.FUTURE), days.take(4).map { it.state })
        assertEquals(2, days[2].words.size)
        assertEquals(6, days[2].total)
        assertEquals(3, WordChain.dayNumber(start, today))
    }

    @Test
    fun checkAndStreak() {
        val words = WordChain.wordsFor(1, small, 5, 10)
        assertTrue(WordChain.passed(WordChain.check(listOf("word1", " Word2 "), words)))
        assertFalse(WordChain.passed(WordChain.check(listOf("word1", ""), words)))
        assertEquals(3, WordChain.streak(setOf(1, 2, 3), 3))
        assertEquals(2, WordChain.streak(setOf(1, 2), 3))
        assertEquals(0, WordChain.streak(setOf(1), 3))
    }
}
