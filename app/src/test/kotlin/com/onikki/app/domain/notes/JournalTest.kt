package com.onikki.app.domain.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class JournalTest {
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(3, Journal.streak(days, today))
        assertEquals(2, Journal.streak(days - today, today)) // today not written yet: still counts from yesterday
        assertEquals(0, Journal.streak(setOf(today.minusDays(3)), today))
    }
}
