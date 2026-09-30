package com.onikki.app.domain.screentime

import com.onikki.app.data.db.entity.AppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UsagePeriodsTest {
    private val today = LocalDate.of(2026, 9, 30) // Wednesday

    @Test
    fun rangesAreMondayWeeksAndCalendarMonths() {
        assertEquals(LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 4), UsagePeriods.range(UsagePeriod.WEEK, today))
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), UsagePeriods.range(UsagePeriod.MONTH, today))
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28), UsagePeriods.range(UsagePeriod.MONTH, LocalDate.of(2026, 2, 14)))
    }

    @Test
    fun shiftNeverGoesPastToday() {
        assertEquals(LocalDate.of(2026, 9, 23), UsagePeriods.shift(UsagePeriod.WEEK, today, -1, today))
        assertEquals(today, UsagePeriods.shift(UsagePeriod.DAY, today, 1, today))
        assertEquals(today, UsagePeriods.shift(UsagePeriod.MONTH, LocalDate.of(2026, 8, 31), 1, today))
        assertTrue(UsagePeriods.isCurrent(UsagePeriod.WEEK, LocalDate.of(2026, 9, 28), today))
        assertFalse(UsagePeriods.isCurrent(UsagePeriod.WEEK, LocalDate.of(2026, 9, 27), today))
    }

    @Test
    fun summaryAveragesOverTrackedDaysOnly() {
        val rows = listOf(
            AppUsage("tg", "Telegram", LocalDate.of(2026, 9, 28), 60),
            AppUsage("yt", "YouTube", LocalDate.of(2026, 9, 28), 30),
            AppUsage("tg", "Telegram", LocalDate.of(2026, 9, 30), 30),
            AppUsage("tg", "Telegram", LocalDate.of(2026, 9, 27), 500) // outside the week
        )
        val (from, to) = UsagePeriods.range(UsagePeriod.WEEK, today)
        val s = UsagePeriods.summarize(rows, from, to)
        assertEquals(120, s.total)
        assertEquals(listOf(90, 0, 30, 0, 0, 0, 0), s.dailyTotals)
        assertEquals(2, s.trackedDays)
        assertEquals(60, s.averagePerDay)
        assertEquals(LocalDate.of(2026, 9, 28) to 90, s.busiest)
        assertEquals(listOf("tg" to 90, "yt" to 30), s.apps.map { it.packageName to it.minutes })
        assertEquals(2, s.apps.first().daysUsed)
    }

    @Test
    fun hoursRoundTripAndRejectBadData() {
        val hours = List(24) { it }
        assertEquals(hours, UsagePeriods.decodeHours(UsagePeriods.encodeHours(hours)))
        assertNull(UsagePeriods.decodeHours("1,2,3"))
        assertNull(UsagePeriods.decodeHours(null))
    }
}
