package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FlashcardsTest {
    private fun c(e: String) = FlashCard(e, "uz-$e", null)
    private val cards = (1..8).map { c("w$it") }

    @Test
    fun missedCardComesBackSoonAndKnownOneLeaves() {
        var s = Flashcards.start(cards, FlashDirection.EN_UZ)
        assertEquals("w1", s.current?.english)
        s = Flashcards.grade(Flashcards.flip(s), FlashGrade.AGAIN)
        assertFalse(s.flipped)
        assertEquals(listOf("w2", "w3", "w4", "w1"), s.queue.take(4).map { it.english })
        s = Flashcards.grade(s, FlashGrade.GOOD) // w2 known
        assertEquals(7, s.queue.size)
        s = Flashcards.grade(s, FlashGrade.HARD) // w3 later than a miss
        assertEquals("w3", s.queue[minOf(Flashcards.HARD_GAP, s.queue.size - 1)].english)
        assertEquals(setOf("w2"), s.firstTry)
        assertEquals(listOf("w1", "w3"), s.struggled.map { it.english })
    }

    @Test
    fun runEndsOnlyWhenEveryCardIsKnown() {
        var s = Flashcards.start(cards.take(2), FlashDirection.EN_UZ)
        s = Flashcards.grade(s, FlashGrade.AGAIN)
        s = Flashcards.grade(s, FlashGrade.GOOD)
        assertFalse(s.finished)
        s = Flashcards.grade(s, FlashGrade.GOOD) // the missed one, known now
        assertTrue(s.finished)
        assertEquals(2, s.done)
        assertEquals(setOf("w2"), s.firstTry) // w1 was missed first
    }

    @Test
    fun directions() {
        assertTrue(Flashcards.start(cards, FlashDirection.EN_UZ).frontUz.isEmpty())
        assertEquals(8, Flashcards.start(cards, FlashDirection.UZ_EN).frontUz.size)
        val mixed = Flashcards.start(cards, FlashDirection.MIXED, Random(5)).frontUz.size
        assertTrue(mixed in 1..7)
        assertEquals(1, Flashcards.start(listOf(c("a"), c("A ")), FlashDirection.EN_UZ).total)
    }

    @Test
    fun leitnerBoxesAndWeakest() {
        assertEquals(1, Flashcards.nextBox(4, FlashGrade.AGAIN))
        assertEquals(1, Flashcards.nextBox(0, FlashGrade.HARD))
        assertEquals(3, Flashcards.nextBox(3, FlashGrade.HARD))
        assertEquals(1, Flashcards.nextBox(0, FlashGrade.GOOD))
        assertEquals(5, Flashcards.nextBox(5, FlashGrade.GOOD))
        val boxes = mapOf("w1" to 5, "w2" to 1, "w3" to 3, "w4" to 2)
        val order = Flashcards.weakestFirst(cards.take(4), boxes, Random(1)).map { it.english }
        assertEquals(listOf("w2", "w4", "w3", "w1"), order)
        assertEquals(listOf("w2", "w4", "w6"), Flashcards.weak(cards, boxes, alsoWeak = setOf("w6")).map { it.english })
    }
}
