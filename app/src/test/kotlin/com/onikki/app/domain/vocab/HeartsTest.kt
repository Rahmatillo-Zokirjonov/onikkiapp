package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartsTest {
    private val h = Hearts.REFILL_MS

    @Test
    fun losingFromFullStartsTheClock_andHeartsComeBackOverTime() {
        var s = HeartState(5, 0)
        s = Hearts.lose(s, now = 1_000)
        assertEquals(HeartState(4, 1_000), s)
        s = Hearts.lose(s, now = 2_000)
        assertEquals(HeartState(3, 1_000), s) // the clock keeps running from the first loss
        assertEquals(3, Hearts.refill(s, 1_000 + h - 1).hearts)
        assertEquals(HeartState(4, 1_000 + h), Hearts.refill(s, 1_000 + h))
        assertEquals(5, Hearts.refill(s, 1_000 + 10 * h).hearts)
        assertEquals(h - 500, Hearts.nextIn(s, 1_500))
        assertNull(Hearts.nextIn(HeartState(5, 0), 99))
    }

    @Test
    fun neverBelowZeroOrAboveFive() {
        assertEquals(0, Hearts.lose(HeartState(0, 0), 10).hearts)
        assertEquals(5, Hearts.gain(HeartState(5, 0), 10).hearts)
        assertEquals(1, Hearts.gain(HeartState(0, 0), 10).hearts)
    }

    @Test
    fun comboCounting() {
        var c = 0
        repeat(3) { c = Combo.next(c, Verdict.CORRECT) }
        assertEquals(3, c)
        assertTrue(Combo.shouldShow(3))
        assertFalse(Combo.shouldShow(4))
        assertTrue(Combo.shouldShow(10))
        assertEquals(4, Combo.next(c, Verdict.ALMOST))
        assertEquals(0, Combo.next(c, Verdict.WRONG))
    }
}
