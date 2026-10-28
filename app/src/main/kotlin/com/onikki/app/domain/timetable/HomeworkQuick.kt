package com.onikki.app.domain.timetable

import com.onikki.app.data.db.entity.Homework
import com.onikki.app.data.db.entity.TimetableEntry
import java.time.LocalDateTime

/**
 * One-line homework: just "what", and the rest is filled in — due at the subject's next lesson
 * (tomorrow 09:00 if it has none), reminded the evening before. Pure.
 */
object HomeworkQuick {
    fun make(subject: String, text: String, entries: List<TimetableEntry>, now: LocalDateTime): Homework {
        val due = HomeworkRules.nextLesson(entries, subject, now) ?: now.toLocalDate().plusDays(1).atTime(9, 0)
        val evening = due.toLocalDate().minusDays(1).atTime(20, 0)
        return Homework(
            subject = subject.trim(),
            title = text.trim(),
            dueAt = due,
            remindAt = evening.takeIf { it.isAfter(now) } ?: due.minusHours(2).takeIf { it.isAfter(now) }
        )
    }

    /**
     * The subject homework is most likely for right now: the lesson going on, else the last one that
     * ended today, else today's next, else the most frequent subject.
     */
    fun suggestSubject(entries: List<TimetableEntry>, now: LocalDateTime): String? {
        val today = Timetable.on(entries, now.toLocalDate())
        val t = now.toLocalTime()
        return (today.firstOrNull { !t.isBefore(it.start) && t.isBefore(it.end) }
            ?: today.lastOrNull { !it.end.isAfter(t) }
            ?: today.firstOrNull { it.start.isAfter(t) })?.title?.trim()
            ?: HomeworkRules.subjects(entries).firstOrNull()
    }
}
