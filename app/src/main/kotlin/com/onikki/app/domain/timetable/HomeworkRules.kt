package com.onikki.app.domain.timetable

import com.onikki.app.data.db.entity.Homework
import com.onikki.app.data.db.entity.HomeworkStatus
import com.onikki.app.data.db.entity.TimetableEntry
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Where a pending homework stands right now. */
enum class HomeworkState { CALM, DANGER, OVERDUE, DONE, MISSED }

/** Pure homework rules: danger window, what blocks apps, default deadlines. */
object HomeworkRules {

    /** When apps start being blocked; null when blocking is off. */
    fun dangerStart(hw: Homework): LocalDateTime? = hw.blockHoursBefore?.let { hw.dueAt.minusHours(it.toLong()) }

    fun state(hw: Homework, now: LocalDateTime): HomeworkState = when (hw.status) {
        HomeworkStatus.DONE -> HomeworkState.DONE
        HomeworkStatus.MISSED -> HomeworkState.MISSED
        HomeworkStatus.PENDING -> when {
            !now.isBefore(hw.dueAt) -> HomeworkState.OVERDUE
            dangerStart(hw)?.let { !now.isBefore(it) } == true -> HomeworkState.DANGER
            else -> HomeworkState.CALM
        }
    }

    /**
     * Homework keeping apps closed: pending, blocking on, and inside the danger window — also after the
     * deadline, until it's marked done or missed.
     */
    fun blocking(list: List<Homework>, now: LocalDateTime): List<Homework> =
        list.filter { hw ->
            hw.status == HomeworkStatus.PENDING && dangerStart(hw)?.let { !now.isBefore(it) } == true
        }.sortedBy { it.dueAt }

    /** Pending work, most urgent first. */
    fun pending(list: List<Homework>): List<Homework> =
        list.filter { it.status == HomeworkStatus.PENDING }.sortedBy { it.dueAt }

    fun timeLeft(hw: Homework, now: LocalDateTime): Duration = Duration.between(now, hw.dueAt)

    /** "8 soat 20 daq", "2 kun 3 soat", "15 daq" — or how long ago if negative. */
    fun formatLeft(d: Duration): String {
        val minutes = d.toMinutes()
        val abs = kotlin.math.abs(minutes)
        val text = when {
            abs >= 48 * 60 -> "${abs / (24 * 60)} kun ${(abs / 60) % 24} soat"
            abs >= 60 -> "${abs / 60} soat ${abs % 60} daq"
            else -> "$abs daq"
        }
        return if (minutes < 0) "$text o'tdi" else text
    }

    /** The next time [subject] is in the timetable after [after] — the natural deadline ("keyingi dars"). */
    fun nextLesson(entries: List<TimetableEntry>, subject: String, after: LocalDateTime): LocalDateTime? {
        val mine = entries.filter { it.title.equals(subject.trim(), ignoreCase = true) }
        if (mine.isEmpty()) return null
        return (0..60).asSequence()
            .map { after.toLocalDate().plusDays(it.toLong()) }
            .flatMap { day -> Timetable.on(mine, day).map { day.atTime(it.start) }.asSequence() }
            .firstOrNull { it.isAfter(after) }
    }

    /** Subjects to offer: timetable lesson titles, most frequent first. */
    fun subjects(entries: List<TimetableEntry>): List<String> =
        entries.groupingBy { it.title.trim() }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun dueOn(list: List<Homework>, subject: String, day: LocalDate): List<Homework> =
        list.filter { it.subject.equals(subject, ignoreCase = true) && it.dueAt.toLocalDate() == day && it.status == HomeworkStatus.PENDING }
}
