package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.PlannedExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedExpenseDao {
    @Query("SELECT * FROM planned_expenses ORDER BY paidDate IS NOT NULL, dueDate, id")
    fun observeAll(): Flow<List<PlannedExpense>>

    /** Unpaid ones with reminders on — what the reminder planner needs. */
    @Query("SELECT * FROM planned_expenses WHERE paidDate IS NULL AND remindEnabled = 1")
    fun observeRemindable(): Flow<List<PlannedExpense>>

    @Query("SELECT * FROM planned_expenses WHERE id = :id")
    suspend fun findById(id: Long): PlannedExpense?

    @Insert
    suspend fun insert(expense: PlannedExpense): Long

    @Update
    suspend fun update(expense: PlannedExpense)

    @Delete
    suspend fun delete(expense: PlannedExpense)
}
