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
import java.time.LocalDate

data class HabitProgress(
    val habit: Habit,
    val isDoneToday: Boolean,
    val completionRate: Float
)

private const val PROGRESS_WINDOW_DAYS = 30L
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
