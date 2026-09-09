package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onikki.app.data.db.entity.HabitLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE date = :date")
    fun observeByDate(date: LocalDate): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY date DESC")
    fun observeByHabit(habitId: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun findForDate(habitId: Long, date: LocalDate): HabitLog?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date LIMIT 1")
    fun observeByHabitAndDate(habitId: Long, date: LocalDate): Flow<HabitLog?>

    @Query(
        "SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND date BETWEEN :from AND :to AND isDone = 1"
    )
    fun observeDoneCountBetween(habitId: Long, from: LocalDate, to: LocalDate): Flow<Int>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date BETWEEN :from AND :to")
    fun observeByHabitBetween(habitId: Long, from: LocalDate, to: LocalDate): Flow<List<HabitLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: HabitLog)
}
