package com.onikki.app.domain.notifications

import com.onikki.app.data.db.entity.Debt
import com.onikki.app.data.db.entity.DebtStatus
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.local.NotificationSettings
import com.onikki.app.domain.prayer.PrayerTimeCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderType { TASK, PRAYER, HABITS, DAY_REVIEW, DEBT, NOTE, REFRESH }

/**
 * One alarm to set. [key] is stable across re-plans so the same reminder is replaced, not duplicated.
 * [refId] points at the task/debt row; [label]/[eventTime] carry display data that isn't in the DB (prayers).
 */
data class PlannedReminder(
    val key: String,
    val type: ReminderType,
    val at: LocalDateTime,
    val refId: Long = 0,
    val label: String = "",
    val eventTime: LocalTime? = null
)

/** Debts fall due in the morning, not at midnight. */
val DEBT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

/**
 * Pure planning: given "now", the settings and a snapshot of the data, which alarms should exist.
 * Only a rolling [HORIZON_HOURS] window is planned; a daily REFRESH alarm re-plans the next window.
 */
object ReminderPlanner {
    const val HORIZON_HOURS = 36L

    fun plan(
        now: LocalDateTime,
        settings: NotificationSettings,
        tasks: List<Task>,
        debts: List<Debt>,
        notes: List<Note>,
        prayerTimesFor: (LocalDate) -> PrayerTimeCalculator.PrayerTimes
    ): List<PlannedReminder> {
        val horizon = now.plusHours(HORIZON_HOURS)
        fun inWindow(at: LocalDateTime) = at.isAfter(now) && !at.isAfter(horizon)

        val days = generateSequence(now.toLocalDate()) { it.plusDays(1) }.takeWhile { !it.isAfter(horizon.toLocalDate()) }.toList()
        val result = mutableListOf<PlannedReminder>()

        if (settings.tasksEnabled) {
            tasks.filter { !it.isCompleted && it.time != null }.forEach { task ->
                // A task created too close to its time for the lead (e.g. 3 min before, 10 min lead) gets no alarm
                // rather than an instant one — firing "now" would repeat on every re-plan.
                val at = task.date.atTime(task.time).minusMinutes(settings.taskLeadMinutes.toLong())
                if (inWindow(at)) result += PlannedReminder("task:${task.id}", ReminderType.TASK, at, refId = task.id)
            }
        }

        days.forEach { day ->
            if (settings.prayerEnabled) {
                prayerTimesFor(day).asOrderedList().forEach { (name, time) ->
                    val at = day.atTime(time).minusMinutes(settings.prayerLeadMinutes.toLong())
                    if (inWindow(at)) {
                        result += PlannedReminder("prayer:$day:$name", ReminderType.PRAYER, at, label = name, eventTime = time)
                    }
                }
            }
            if (settings.habitsEnabled) {
                val at = day.atTime(settings.habitsTime)
                if (inWindow(at)) result += PlannedReminder("habits:$day", ReminderType.HABITS, at)
            }
            if (settings.dayReviewEnabled) {
                val at = day.atTime(settings.dayReviewTime)
                if (inWindow(at)) result += PlannedReminder("review:$day", ReminderType.DAY_REVIEW, at)
            }
        }

        if (settings.debtsEnabled) {
            debts.filter { it.status == DebtStatus.OCHIQ && it.dueDate != null }.forEach { debt ->
                val at = debt.dueDate!!.atTime(DEBT_REMINDER_TIME)
                if (inWindow(at)) result += PlannedReminder("debt:${debt.id}:${debt.dueDate}", ReminderType.DEBT, at, refId = debt.id)
            }
        }

        // Note reminders are the user's own explicit choice per note — no global switch, no lead time.
        // The time is part of the key, so moving/snoozing a reminder replaces the old alarm.
        notes.forEach { note ->
            val at = note.remindAt ?: return@forEach
            if (inWindow(at)) result += PlannedReminder("note:${note.id}:$at", ReminderType.NOTE, at, refId = note.id)
        }

        val nextMidnight = now.toLocalDate().plusDays(1).atTime(0, 5)
        result += PlannedReminder("refresh", ReminderType.REFRESH, nextMidnight)
        return result
    }
}
