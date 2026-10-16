package com.onikki.app.domain.timetable

import com.onikki.app.data.db.entity.Homework
import com.onikki.app.data.db.entity.HomeworkStatus
import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.data.db.entity.TimetableRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class HomeworkRulesTest {
    private val due = LocalDateTime.of(2026, 10, 20, 9, 0)
    private val hw = Homework(id = 1, subject = "Matematika", title = "5-mashq", dueAt = due, blockHoursBefore = 8)

    @Test
    fun calmUntilEightHoursBefore_thenDanger_thenOverdue() {
        assertEquals(HomeworkState.CALM, HomeworkRules.state(hw, due.minusHours(9)))
        assertEquals(HomeworkState.DANGER, HomeworkRules.state(hw, due.minusHours(8)))
        assertEquals(HomeworkState.OVERDUE, HomeworkRules.state(hw, due.plusMinutes(1)))
        assertEquals(HomeworkState.DONE, HomeworkRules.state(hw.copy(status = HomeworkStatus.DONE), due))
    }

    @Test
    fun onlyPendingWorkInsideItsWindowBlocks_evenAfterTheDeadline() {
        val noBlock = hw.copy(id = 2, blockHoursBefore = null)
        val done = hw.copy(id = 3, status = HomeworkStatus.DONE)
        val missed = hw.copy(id = 4, status = HomeworkStatus.MISSED)
        val list = listOf(hw, noBlock, done, missed)
        assertTrue(HomeworkRules.blocking(list, due.minusHours(9)).isEmpty())
        assertEquals(listOf(1L), HomeworkRules.blocking(list, due.minusHours(2)).map { it.id })
        assertEquals(listOf(1L), HomeworkRules.blocking(list, due.plusHours(5)).map { it.id })
    }

    @Test
    fun nextLessonIsTheDefaultDeadline() {
        val mon = LocalDate.of(2026, 10, 19)
        val math = TimetableEntry(id = 1, title = "Matematika", repeat = TimetableRepeat.WEEKLY, dayOfWeek = 3, start = LocalTime.of(9, 30), end = LocalTime.of(11, 0))
        assertEquals(LocalDateTime.of(2026, 10, 21, 9, 30), HomeworkRules.nextLesson(listOf(math), "matematika", mon.atTime(12, 0)))
        // Asked during Wednesday's lesson: the next one is a week later.
        assertEquals(LocalDateTime.of(2026, 10, 28, 9, 30), HomeworkRules.nextLesson(listOf(math), "Matematika", LocalDateTime.of(2026, 10, 21, 10, 0)))
        assertNull(HomeworkRules.nextLesson(listOf(math), "Fizika", mon.atTime(0, 0)))
    }

    @Test
    fun timeLeftText() {
        assertEquals("8 soat 20 daq", HomeworkRules.formatLeft(Duration.ofMinutes(500)))
        assertEquals("2 kun 3 soat", HomeworkRules.formatLeft(Duration.ofHours(51)))
        assertEquals("15 daq o'tdi", HomeworkRules.formatLeft(Duration.ofMinutes(-15)))
    }
}
