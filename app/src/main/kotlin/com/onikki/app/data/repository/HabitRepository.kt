package com.onikki.app.data.repository

import com.onikki.app.data.db.dao.HabitDao
import com.onikki.app.data.db.dao.HabitLogDao
import com.onikki.app.data.db.entity.Habit
import com.onikki.app.data.db.entity.HabitLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class HabitProgress(
    val habit: Habit,
    val isDoneToday: Boolean,
    val completionRate: Float
)

/** [last7Days] is oldest-to-newest, size 7, last entry is today. */
data class HabitListItem(
    val habit: Habit,
    val last7Days: List<Boolean>
)

/** [weeks] is oldest-to-newest, size 12, each a 0..1 completion fraction for that 7-day window. */
data class WeeklyStreakChart(
    val habitName: String,
    val streakCount: Int,
    val weeks: List<Float>
)

private const val PROGRESS_WINDOW_DAYS = 30L
private const val STREAK_HISTORY_WEEKS = 12L
private const val STREAK_SAFETY_CAP = 3650

class HabitRepository(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao
) {
    /** All habits with today's done state and a rolling 30-day completion rate, live. */
    fun observeProgress(today: LocalDate): Flow<List<HabitProgress>> =
        habitDao.observeAll().flatMapLatest { habits ->
            if (habits.isEmpty()) {
                flowOf(emptyList<HabitProgress>())
            } else {
                val windowStart = today.minusDays(PROGRESS_WINDOW_DAYS - 1)
                combine(
                    habits.map { habit ->
                        combine(
                            habitLogDao.observeByHabitAndDate(habit.id, today),
                            habitLogDao.observeDoneCountBetween(habit.id, windowStart, today)
                        ) { todayLog, doneCount ->
                            HabitProgress(
                                habit = habit,
                                isDoneToday = todayLog?.isDone == true,
                                completionRate = doneCount / PROGRESS_WINDOW_DAYS.toFloat()
                            )
                        }
                    }
                ) { it.toList() }
            }
        }

    /** Every habit with its last-7-days done/not-done strip, live. */
    fun observeHabitList(today: LocalDate): Flow<List<HabitListItem>> =
        habitDao.observeAll().flatMapLatest { habits ->
            if (habits.isEmpty()) {
                flowOf(emptyList<HabitListItem>())
            } else {
                val weekStart = today.minusDays(6)
                combine(
                    habits.map { habit ->
                        habitLogDao.observeByHabitBetween(habit.id, weekStart, today).map { logs ->
                            val doneDates = logs.filter { it.isDone }.map { it.date }.toSet()
                            val last7 = (0..6).map { offset -> weekStart.plusDays(offset.toLong()) in doneDates }
                            HabitListItem(habit, last7)
                        }
                    }
                ) { it.toList() }
            }
        }

    /** The 12-week completion history for the habit with the longest current streak, live. */
    fun observeTopStreakChart(today: LocalDate): Flow<WeeklyStreakChart?> =
        habitDao.observeAll().flatMapLatest { habits ->
            val top = habits.maxByOrNull { it.streakCount }
            if (top == null) {
                flowOf<WeeklyStreakChart?>(null)
            } else {
                val historyStart = today.minusDays(STREAK_HISTORY_WEEKS * 7 - 1)
                habitLogDao.observeByHabitBetween(top.id, historyStart, today).map { logs ->
                    val doneDates = logs.filter { it.isDone }.map { it.date }.toSet()
                    val weeks = (0 until STREAK_HISTORY_WEEKS.toInt()).map { weekIndex ->
                        val weekStart = historyStart.plusDays(weekIndex * 7L)
                        val doneInWeek = (0..6).count { dayOffset -> weekStart.plusDays(dayOffset.toLong()) in doneDates }
                        doneInWeek / 7f
                    }
                    WeeklyStreakChart(top.name, top.streakCount, weeks)
                }
            }
        }

    /** This-week completion percentage across all habits (Monday through today), live. */
    fun observeWeekCompletionPercent(today: LocalDate): Flow<Int> =
        habitDao.observeAll().flatMapLatest { habits ->
            if (habits.isEmpty()) {
                flowOf(0)
            } else {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val daysElapsed = ChronoUnit.DAYS.between(monday, today) + 1
                combine(habits.map { habit -> habitLogDao.observeDoneCountBetween(habit.id, monday, today) }) { counts ->
                    val possible = habits.size * daysElapsed
                    if (possible == 0L) 0 else ((counts.sum() * 100.0) / possible).roundToInt()
                }
            }
        }

    suspend fun addHabit(name: String, icon: String, dailyTarget: Int) {
        habitDao.insert(Habit(name = name, icon = icon, dailyTarget = dailyTarget))
    }

    /** Flips today's log for [habit] and recomputes its consecutive-day streak. */
    suspend fun toggleToday(habit: Habit, today: LocalDate) {
        val existing = habitLogDao.findForDate(habit.id, today)
        val nowDone = existing?.isDone != true
        habitLogDao.upsert(
            HabitLog(id = existing?.id ?: 0, habitId = habit.id, date = today, isDone = nowDone)
        )
        habitDao.setStreak(habit.id, computeStreak(habit.id, today))
    }

    private suspend fun computeStreak(habitId: Long, from: LocalDate): Int {
        var count = 0
        var day = from
        while (count < STREAK_SAFETY_CAP) {
            val log = habitLogDao.findForDate(habitId, day)
            if (log?.isDone == true) {
                count++
                day = day.minusDays(1)
            } else {
                break
            }
        }
        return count
    }
}
