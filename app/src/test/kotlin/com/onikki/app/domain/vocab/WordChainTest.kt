package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WordChainTest {
    private val start = LocalDate.of(2026, 10, 26)

    @Test
    fun ninetyUniqueWords_enoughFromTheEarliestStart() {
        assertEquals(90, WordChain.WORDS.size)
        assertEquals(90, WordChain.WORDS.map { it.english }.toSet().size)
        assertTrue(WordChain.WORDS.all { it.uzbek.isNotBlank() && !it.example.isNullOrBlank() })
        assertEquals(90, WordChain.totalDays(WordChain.earliestStart))
    }

    @Test
    fun dayCountsFromTheStartToFifteenthOfJanuary() {
        assertEquals(82, WordChain.totalDays(start)) // 26 Oct … 15 Jan
        assertEquals(1, WordChain.dayNumber(start, start))
        assertEquals(2, WordChain.dayNumber(start, start.plusDays(1)))
        assertEquals(82, WordChain.dayNumber(start, LocalDate.of(2027, 1, 15)))
        assertEquals(82, WordChain.dayNumber(start, LocalDate.of(2027, 2, 1))) // stays on the last day
        assertEquals(0, WordChain.dayNumber(start, start.minusDays(1)))
        assertEquals(LocalDate.of(2027, 1, 15), WordChain.days(start, start, emptySet()).last().date)
    }

    @Test
    fun eachDayAddsOneWord_chainIsInOrder() {
        assertEquals(listOf("achieve"), WordChain.chain(1).map { it.english })
        assertEquals(listOf("achieve", "weather", "dangerous"), WordChain.chain(3).map { it.english })
        assertEquals(WordChain.chain(5), WordChain.chain(6).take(5))
    }

    @Test
    fun statesAndStreak() {
        val today = start.plusDays(4) // day 5
        val days = WordChain.days(start, today, setOf(1, 2, 4))
        assertEquals(
            listOf(ChainDayState.DONE, ChainDayState.DONE, ChainDayState.MISSED, ChainDayState.DONE, ChainDayState.TODAY, ChainDayState.FUTURE),
            days.take(6).map { it.state }
        )
        assertEquals(1, WordChain.streak(setOf(1, 2, 4), 5)) // only day 4 before today
        assertEquals(3, WordChain.streak(setOf(3, 4, 5), 5))
    }

    @Test
    fun recitingIsStrictAndInOrder() {
        val chain = WordChain.chain(3)
        assertTrue(WordChain.passed(WordChain.check(listOf("Achieve", " weather ", "dangerous"), chain)))
        // Wrong order fails.
        assertFalse(WordChain.passed(WordChain.check(listOf("weather", "achieve", "dangerous"), chain)))
        // A spelling slip is not enough.
        val slip = WordChain.check(listOf("achieve", "wether", "dangerous"), chain)
        assertFalse(WordChain.passed(slip))
        assertEquals(Verdict.CORRECT, slip[0])
    }
}
