package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.Task
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY time IS NULL, time ASC")
    fun observeByDate(date: LocalDate): Flow<List<Task>>

    @Query("SELECT COUNT(*) FROM tasks WHERE date = :date")
    fun observeTotalCount(date: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE date = :date AND isCompleted = 1")
    fun observeCompletedCount(date: LocalDate): Flow<Int>

    /** Every task in [from]..[to] — feeds the week strip's per-day indicators. */
    @Query("SELECT * FROM tasks WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Task>>

    /** Unfinished tasks left behind on days before [today]. */
    @Query("SELECT * FROM tasks WHERE date < :today AND isCompleted = 0 ORDER BY date ASC, time IS NULL, time ASC")
    fun observeOverdue(today: LocalDate): Flow<List<Task>>

    @Query("UPDATE tasks SET date = :date WHERE date < :date AND isCompleted = 0")
    suspend fun moveUnfinishedBefore(date: LocalDate): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): Task?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun setCompleted(taskId: Long, isCompleted: Boolean)
}
