package com.onikki.app.data.repository

import com.onikki.app.data.db.dao.HabitDao
import com.onikki.app.data.db.dao.HabitLogDao
import com.onikki.app.data.db.entity.Habit
import com.onikki.app.data.db.entity.HabitLog
import com.onikki.app.domain.habits.HABIT_LOOKBACK_DAYS
import com.onikki.app.domain.habits.HabitDayState
import com.onikki.app.domain.habits.HabitStats
import com.onikki.app.domain.habits.HabitStatsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

class HabitRepository(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao
) {
    /** Every habit with its computed stats (streaks, rates, history), live. One logs query for all habits. */
    fun observeStats(today: LocalDate): Flow<List<HabitStats>> =
        combine(
            habitDao.observeAll(),
            habitLogDao.observeAllBetween(today.minusDays(HABIT_LOOKBACK_DAYS - 1), today)
        ) { habits, logs ->
            val byHabit = logs.groupBy { it.habitId }
            habits.map { HabitStatsCalculator.compute(it, byHabit[it.id].orEmpty(), today) }
        }

    suspend fun addHabit(name: String, icon: String, dailyTarget: Int, activeDays: Int) {
        habitDao.insert(Habit(name = name, icon = icon, dailyTarget = dailyTarget, activeDays = activeDays))
    }

    suspend fun updateHabit(habit: Habit) {
        habitDao.update(habit)
        // Target changed → re-derive isDone for existing logs so plain done/not-done queries stay right.
        habitLogDao.syncDoneFlags(habit.id, habit.dailyTarget.coerceAtLeast(1))
    }

    suspend fun deleteHabit(habit: Habit) = habitDao.delete(habit)

    suspend fun setCount(habit: Habit, date: LocalDate, count: Int) {
        val clamped = count.coerceIn(0, MAX_DAILY_COUNT)
        val existing = habitLogDao.findForDate(habit.id, date)
        habitLogDao.upsert(
            HabitLog(
                id = existing?.id ?: 0,
                habitId = habit.id,
                date = date,
                isDone = clamped >= habit.dailyTarget.coerceAtLeast(1),
                count = clamped
            )
        )
    }

    /** One tap on the quick control: +1 toward the target, and back to 0 once it's reached. */
    suspend fun tapToday(stats: HabitStats, today: LocalDate) {
        setCount(stats.habit, today, if (stats.isDoneToday) 0 else stats.todayCount + 1)
    }

    /** Past-day correction from the history grid: a day is either fully done or cleared. */
    suspend fun toggleDay(habit: Habit, date: LocalDate, currentlyDone: Boolean) {
        setCount(habit, date, if (currentlyDone) 0 else habit.dailyTarget.coerceAtLeast(1))
    }

    companion object {
        const val MAX_DAILY_COUNT = 99
    }
}

/** This week's (Monday → today) done share of scheduled habit-days, or null when nothing was due yet. */
fun weekCompletionPercent(stats: List<HabitStats>, today: LocalDate): Int? {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var due = 0
    var done = 0
    stats.forEach { habit ->
        habit.history.filter { !it.date.isBefore(monday) && !it.date.isAfter(today) }.forEach { day ->
            when (day.state) {
                HabitDayState.DONE -> { done++; due++ }
                HabitDayState.MISSED, HabitDayState.PARTIAL -> if (day.date != today) due++
                else -> Unit
            }
        }
    }
    return if (due == 0) null else ((done * 100.0) / due).roundToInt().coerceAtMost(100)
}
