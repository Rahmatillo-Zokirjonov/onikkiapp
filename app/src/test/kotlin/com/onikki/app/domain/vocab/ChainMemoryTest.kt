package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ChainMemoryTest {
    private fun w(e: String) = ImportedWord(e, "uz-$e", null, null)

    @Test
    fun partsOfFifteen() {
        assertEquals(listOf(0..14, 15..29, 30..31), ChainMemory.parts(32))
        assertTrue(ChainMemory.parts(0).isEmpty())
        assertEquals(listOf(0..4), ChainMemory.parts(5))
    }

    @Test
    fun storyIsSplitAndWordsFoundInOrder() {
        val text = "Ali wanted to **achieve** a lot. The **weather** was cold and **dangerous**.\n---\nAli ko'p narsaga erishmoqchi edi."
        val st = ChainMemory.parse(text)
        assertEquals("Ali ko'p narsaga erishmoqchi edi.", st.uzbek)
        assertFalse(st.english.contains("---"))
        assertEquals(listOf("achieve", "weather", "dangerous"), ChainMemory.boldWords(st.english))
        val words = listOf(w("achieve"), w("weather"), w("dangerous"))
        assertTrue(ChainMemory.inOrder(st.english, words))
        assertFalse(ChainMemory.inOrder(st.english, listOf(w("weather"), w("achieve"))))
        // A form of the word counts.
        assertTrue(ChainMemory.inOrder("She **achieved** it.", listOf(w("achieve"))))
        assertEquals(ChainStoryText("only story", ""), ChainMemory.parse("only story"))
        val pieces = ChainMemory.pieces("a **b** c")
        assertEquals(listOf("a " to false, "b" to true, " c" to false), pieces)
    }

    @Test
    fun reminderTimesAndRotatingWords() {
        val now = LocalDateTime.of(2026, 11, 18, 15, 30)
        val times = ChainMemory.reminderTimes(now, 36)
        assertEquals(listOf(18, 21, 10, 14, 18, 21), times.map { it.hour })
        assertTrue(times.all { it.isAfter(now) })
        val words = (1..20).map { w("x$it") }
        val boxes = mapOf("x5" to 1) + (1..20).filter { it != 5 && it != 9 }.associate { "x$it" to 3 }
        // The never-seen (x9) and box-1 (x5) words come first.
        val day = LocalDate.of(2026, 11, 18)
        val picks = ChainMemory.REMINDER_HOURS.map { ChainMemory.reminderWord(words, boxes, day, it)!! }
        assertEquals(4, picks.distinct().size)
        assertTrue(picks.all { it in words.indices })
        assertEquals(null, ChainMemory.reminderWord(emptyList(), boxes, day, 10))
        assertEquals(ChainMemory.signature(listOf(w("A "), w("b"))), "a|b")
    }
}
