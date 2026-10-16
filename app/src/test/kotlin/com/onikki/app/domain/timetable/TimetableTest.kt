package com.onikki.app.domain.timetable

import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.data.db.entity.TimetableRepeat
import com.onikki.app.data.db.entity.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimetableTest {
    private val mon = LocalDate.of(2026, 10, 12)  // ISO week 42 (even)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val english = TimetableEntry(id = 1, title = "Ingliz", repeat = TimetableRepeat.WEEKLY, dayOfWeek = 1, start = t(8), end = t(9, 30))
    private val math = TimetableEntry(id = 2, title = "Matematika", repeat = TimetableRepeat.WEEKLY, dayOfWeek = 1, start = t(9, 30), end = t(11))
    private val sport = TimetableEntry(id = 3, title = "Sport", repeat = TimetableRepeat.DAILY, start = t(6), end = t(7))
    private val oddLab = TimetableEntry(id = 4, title = "Lab", repeat = TimetableRepeat.WEEKLY, dayOfWeek = 3, parity = WeekParity.ODD, start = t(14), end = t(16))
    private val once = TimetableEntry(id = 5, title = "Uchrashuv", repeat = TimetableRepeat.ONCE, date = mon.plusDays(1), start = t(15), end = t(16))

    @Test
    fun dailyWeeklyOnceAndParity() {
        assertEquals(listOf(3L, 1L, 2L), Timetable.on(listOf(english, math, sport, oddLab, once), mon).map { it.id })
        assertEquals(listOf(3L, 5L), Timetable.on(listOf(english, math, sport, oddLab, once), mon.plusDays(1)).map { it.id })
        assertFalse(Timetable.occursOn(oddLab, mon.plusDays(2)))      // week 42 is even
        assertTrue(Timetable.occursOn(oddLab, mon.plusDays(9)))       // week 43 is odd
    }

    @Test
    fun nowAndNext() {
        val during = Timetable.nowNext(listOf(english, math, sport), mon.atTime(8, 45))
        assertEquals(1L, during.current!!.id)
        assertEquals(45L, during.minutesLeft)
        assertEquals(2L, during.next!!.id)
        // Exactly at a boundary the new block is "now".
        assertEquals(2L, Timetable.nowNext(listOf(english, math), mon.atTime(9, 30)).current!!.id)
        val after = Timetable.nowNext(listOf(english, math), mon.atTime(12, 0))
        assertNull(after.current)
        assertNull(after.next)
    }

    @Test
    fun gridRangeAndClashes() {
        assertEquals(6..17, Timetable.hourRange(listOf(sport, english)))
        assertEquals(8..21, Timetable.hourRange(listOf(english.copy(end = t(20, 15)))))
        val clash = english.copy(id = 9, start = t(9), end = t(10))
        assertEquals(listOf(1L, 2L), Timetable.clashes(listOf(english, math, sport), clash, mon).map { it.id })
        assertTrue(Timetable.clashes(listOf(english), math, mon).isEmpty()) // back to back is fine
    }

    @Test
    fun occurrencesForReminders() {
        assertEquals(listOf(mon.atTime(8, 0), mon.plusDays(7).atTime(8, 0)), Timetable.occurrences(english, mon, mon.plusDays(7)))
        assertEquals(3, Timetable.occurrences(sport, mon, mon.plusDays(2)).size)
    }

    @Test
    fun roomLabels() {
        assertEquals("204-xona", Timetable.roomLabel(" 204 "))
        assertEquals("A-12", Timetable.roomLabel("A-12"))
        assertNull(Timetable.roomLabel("  "))
        assertNull(Timetable.roomLabel(null))
        assertEquals(2L, Timetable.lastUsed(listOf(english, math, english.copy(id = 2, place = "301")), "ingliz")!!.id)
    }
}
