package com.onikki.app.domain.timetable

import com.onikki.app.data.db.entity.TimetableEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class HomeworkQuickTest {
    // Tuesday 2026-10-27; Website Development on Monday and Friday 11:00, Study Skills on Tuesday 11:00.
    private val web1 = TimetableEntry(id = 1, title = "Website Development", dayOfWeek = 1, start = LocalTime.of(11, 0), end = LocalTime.of(12, 20))
    private val web5 = TimetableEntry(id = 2, title = "Website Development", dayOfWeek = 5, start = LocalTime.of(11, 0), end = LocalTime.of(12, 20))
    private val study = TimetableEntry(id = 3, title = "Study Skills", dayOfWeek = 2, start = LocalTime.of(11, 0), end = LocalTime.of(12, 20))
    private val entries = listOf(web1, web5, study)
    private val tue = LocalDateTime.of(2026, 10, 27, 12, 30)

    @Test
    fun dueAtTheNextLesson_remindedTheEveningBefore() {
        val hw = HomeworkQuick.make("Website Development", " 12-mashq ", entries, tue)
        assertEquals("12-mashq", hw.title)
        assertEquals(LocalDateTime.of(2026, 10, 30, 11, 0), hw.dueAt) // Friday's lesson
        assertEquals(LocalDateTime.of(2026, 10, 29, 20, 0), hw.remindAt)
        // No lesson for it → tomorrow 09:00.
        assertEquals(LocalDateTime.of(2026, 10, 28, 9, 0), HomeworkQuick.make("Chemistry", "x", entries, tue).dueAt)
    }

    @Test
    fun aLessonTomorrowMorningIsRemindedTwoHoursBefore_ifTheEveningHasPassed() {
        val late = LocalDateTime.of(2026, 10, 27, 21, 0) // Tuesday evening; Study Skills next Tuesday… use Website: Friday
        val hw = HomeworkQuick.make("Website Development", "x", listOf(web5.copy(dayOfWeek = 3)), late) // Wednesday 11:00
        assertEquals(LocalDateTime.of(2026, 10, 28, 11, 0), hw.dueAt)
        assertEquals(LocalDateTime.of(2026, 10, 28, 9, 0), hw.remindAt)
    }

    @Test
    fun subjectSuggestion() {
        assertEquals("Study Skills", HomeworkQuick.suggestSubject(entries, tue)) // just ended
        assertEquals("Study Skills", HomeworkQuick.suggestSubject(entries, tue.withHour(11).withMinute(30))) // going on
        assertEquals("Study Skills", HomeworkQuick.suggestSubject(entries, tue.withHour(8))) // later today
        assertEquals("Website Development", HomeworkQuick.suggestSubject(entries, LocalDateTime.of(2026, 10, 28, 10, 0))) // no lesson today → most frequent
        assertNull(HomeworkQuick.suggestSubject(emptyList(), tue))
    }
}
