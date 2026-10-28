package com.onikki.app.domain.notifications

import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.ui.reminders.priority
import com.onikki.app.ui.reminders.reminderLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ReminderRepeatTest {
    private val fri = LocalDateTime.of(2026, 10, 30, 7, 30) // Friday

    @Test
    fun nextTimes() {
        assertNull(ReminderRepeat.NONE.next(fri))
        assertEquals(fri.plusDays(1), ReminderRepeat.DAILY.next(fri))
        assertEquals(fri.plusWeeks(1), ReminderRepeat.WEEKLY.next(fri))
        assertEquals(LocalDateTime.of(2026, 11, 2, 7, 30), ReminderRepeat.WEEKDAYS.next(fri)) // Friday → Monday
        assertEquals(LocalDateTime.of(2026, 10, 29, 7, 30), ReminderRepeat.WEEKDAYS.next(fri.minusDays(2))) // Wed → Thu
    }

    @Test
    fun aMissedReminderJumpsToTheFutureNotABurst() {
        val now = fri.plusDays(3).plusHours(1) // Monday 08:30
        assertEquals(LocalDateTime.of(2026, 11, 3, 7, 30), ReminderRepeat.DAILY.next(fri, now))
        assertEquals(LocalDateTime.of(2026, 11, 3, 7, 30), ReminderRepeat.WEEKDAYS.next(fri, now))
    }

    @Test
    fun levelsMapToHowTheNoteIsStored() {
        val n = Note(title = "x", content = "", createdAt = 0)
        ReminderLevel.entries.forEach { l ->
            val stored = n.copy(priority = l.priority(), remindGate = l == ReminderLevel.BEFORE_APP)
            assertEquals(l, stored.reminderLevel)
        }
        assertEquals(NotePriority.JUDA_MUHIM, ReminderLevel.FULL_SCREEN.priority())
        assertEquals(NotePriority.MUHIM, ReminderLevel.TOP.priority())
    }
}
