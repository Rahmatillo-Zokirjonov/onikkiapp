package com.onikki.app.domain.habits

import com.onikki.app.data.db.entity.Habit
import com.onikki.app.data.db.entity.HabitLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class HabitDayState {
    /** Target reached. */
    DONE,
    /** Some progress, target not reached (only possible when dailyTarget > 1). */
    PARTIAL,
    /** Scheduled day in the past with no/insufficient progress. */
    MISSED,
    /** Today, scheduled, not done yet — still counts neither for nor against the habit. */
    PENDING,
    /** Not a scheduled weekday for this habit. */
    OFF,
    /** Before the habit existed, or in the future. */
    NONE
}

data class HabitDay(val date: LocalDate, val state: HabitDayState, val count: Int)

data class HabitStats(
    val habit: Habit,
    val todayCount: Int,
    val isActiveToday: Boolean,
    /** Consecutive scheduled days done, ending today (or yesterday while today is still pending). */
    val currentStreak: Int,
    val bestStreak: Int,
    /** 0..1 over the last 30 days' scheduled days since the habit started; today only counts once done. */
    val completionRate: Float,
    /** Oldest-to-newest, size 7, last entry is today. */
    val last7Days: List<HabitDay>,
    /** Oldest-to-newest, whole Monday-started weeks ending with the current week (future days are NONE). */
    val history: List<HabitDay>,
    val totalDoneDays: Int
) {
    val target: Int get() = habit.dailyTarget.coerceAtLeast(1)
    val isDoneToday: Boolean get() = todayCount >= target
}

const val HABIT_LOOKBACK_DAYS = 400L
private const val RATE_WINDOW_DAYS = 30L
const val HABIT_HISTORY_WEEKS = 15

/**
 * Pure computation of everything the UI shows about a habit, from its logs.
 * Streaks are derived here on every read instead of being stored, so they can't go stale
 * after missed days and editing a past day immediately fixes them.
 */
object HabitStatsCalculator {

    fun compute(habit: Habit, logs: List<HabitLog>, today: LocalDate): HabitStats {
        val target = habit.dailyTarget.coerceAtLeast(1)
        val counts = logs.associate { it.date to it.count }
        val start = startDate(habit, logs, today)

        fun stateOf(date: LocalDate): HabitDayState {
            if (date.isAfter(today) || date.isBefore(start)) return HabitDayState.NONE
            val count = counts[date] ?: 0
            return when {
                count >= target -> HabitDayState.DONE
                !habit.isActiveOn(date) -> HabitDayState.OFF
                date == today -> if (count > 0) HabitDayState.PARTIAL else HabitDayState.PENDING
                count > 0 -> HabitDayState.PARTIAL
                else -> HabitDayState.MISSED
            }
        }

        fun day(date: LocalDate) = HabitDay(date, stateOf(date), counts[date] ?: 0)

        // Current streak: walk back from today; OFF days are skipped, a pending/partial today doesn't break it.
        var currentStreak = 0
        var cursor = today
        while (!cursor.isBefore(start)) {
            when (stateOf(cursor)) {
                HabitDayState.DONE -> currentStreak++
                HabitDayState.OFF -> Unit
                HabitDayState.PENDING, HabitDayState.PARTIAL -> if (cursor != today) break
                else -> break
            }
            cursor = cursor.minusDays(1)
        }

        var bestStreak = 0
        var run = 0
        var totalDone = 0
        var date = start
        while (!date.isAfter(today)) {
            when (stateOf(date)) {
                HabitDayState.DONE -> { run++; totalDone++; bestStreak = maxOf(bestStreak, run) }
                HabitDayState.OFF, HabitDayState.PENDING -> Unit
                else -> run = 0
            }
            date = date.plusDays(1)
        }

        var scheduled = 0
        var done = 0
        val rateStart = maxOf(today.minusDays(RATE_WINDOW_DAYS - 1), start)
        date = rateStart
        while (!date.isAfter(today)) {
            when (stateOf(date)) {
                HabitDayState.DONE -> { done++; if (habit.isActiveOn(date)) scheduled++ }
                HabitDayState.MISSED -> scheduled++
                HabitDayState.PARTIAL -> if (date != today) scheduled++
                else -> Unit
            }
            date = date.plusDays(1)
        }
        // An unscheduled day that was done anyway counts as a bonus but can't push the rate past 100%.
        val completionRate = if (scheduled == 0) (if (done > 0) 1f else 0f) else (done.toFloat() / scheduled).coerceAtMost(1f)

        val historyStart = today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks((HABIT_HISTORY_WEEKS - 1).toLong())

        return HabitStats(
            habit = habit,
            todayCount = counts[today] ?: 0,
            isActiveToday = habit.isActiveOn(today),
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            completionRate = completionRate,
            last7Days = (6 downTo 0).map { day(today.minusDays(it.toLong())) },
            history = (0 until HABIT_HISTORY_WEEKS * 7).map { day(historyStart.plusDays(it.toLong())) },
            totalDoneDays = totalDone
        )
    }

    private fun startDate(habit: Habit, logs: List<HabitLog>, today: LocalDate): LocalDate {
        val firstLog = logs.filter { it.count > 0 }.minOfOrNull { it.date }
        val created = habit.createdAt
        val start = listOfNotNull(created, firstLog).minOrNull() ?: today
        return maxOf(start, today.minusDays(HABIT_LOOKBACK_DAYS - 1))
    }
}
