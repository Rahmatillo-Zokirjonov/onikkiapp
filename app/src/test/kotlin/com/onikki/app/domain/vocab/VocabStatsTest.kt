package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class VocabStatsTest {
    private val today = LocalDate.of(2026, 10, 19)
    private fun w(id: Long, status: WordStatus, due: LocalDate?) = VocabWord(id = id, english = "e$id", uzbek = "u$id", status = status, dueDate = due)

    @Test
    fun countsWeekAndForecast() {
        val words = listOf(
            w(1, WordStatus.NEW, null),
            w(2, WordStatus.LEARNING, today),
            w(3, WordStatus.REVIEW, today.minusDays(2)),
            w(4, WordStatus.REVIEW, today.plusDays(1)),
            w(5, WordStatus.MASTERED, today.plusDays(40)),
            w(6, WordStatus.REVIEW, today.plusDays(6))
        )
        val days = listOf(DayPractice(today, 40, 5, 4), DayPractice(today.minusDays(3), 20, 5, 2), DayPractice(today.minusDays(9), 99, 9, 9))
        val s = VocabStats.build(words, days, setOf(today, today.minusDays(1), today.minusDays(3)), today)
        assertEquals(6, s.total)
        assertEquals(1, s.new)
        assertEquals(5, s.learned)
        assertEquals(1, s.mastered)
        assertEquals(7, s.week.size)
        assertEquals(today, s.week.last().day)
        assertEquals(listOf(0, 0, 0, 20, 0, 0, 40), s.week.map { it.xp }) // the 9-days-ago entry is outside the week
        assertEquals(60, s.weekXp)
        assertEquals(60, s.weekAccuracy)
        assertEquals(2, s.dueToday)
        assertEquals(1, s.dueTomorrow)
        assertEquals(2, s.dueThisWeek)
        assertEquals(2, s.streak)
        assertNull(VocabStats.build(emptyList(), emptyList(), emptySet(), today).weekAccuracy)
    }

    @Test
    fun bestStreakFindsTheLongestRun() {
        val days = setOf(1, 2, 3, 7, 8, 9, 10, 20).map { today.minusDays(it.toLong()) }.toSet()
        assertEquals(4, VocabStats.bestStreak(days))
        assertEquals(0, VocabStats.bestStreak(emptySet()))
    }
}
