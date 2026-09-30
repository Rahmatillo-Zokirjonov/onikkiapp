package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.Goal
import com.onikki.app.data.db.entity.Task
import kotlinx.coroutines.flow.Flow

/** Linked-task counts per goal, for task-measured progress. */
data class GoalTaskCount(val goalId: Long, val total: Int, val done: Int)

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY doneAt IS NOT NULL, orderIndex, deadline IS NULL, deadline, id")
    fun observeAll(): Flow<List<Goal>>

    @Query("SELECT COALESCE(MAX(orderIndex), 0) FROM goals WHERE parentId = :parentId")
    suspend fun maxStageOrder(parentId: Long): Int

    /** A big goal's stages go with it; their tasks are kept, unlinked. */
    @Query("UPDATE tasks SET goalId = NULL WHERE goalId IN (SELECT id FROM goals WHERE parentId = :parentId)")
    suspend fun unlinkStageTasks(parentId: Long)

    @Query("DELETE FROM goals WHERE parentId = :parentId")
    suspend fun deleteStages(parentId: Long)

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun findById(id: Long): Goal?

    @Query("SELECT goalId, COUNT(*) AS total, SUM(isCompleted) AS done FROM tasks WHERE goalId IS NOT NULL GROUP BY goalId")
    fun observeTaskCounts(): Flow<List<GoalTaskCount>>

    @Query("SELECT * FROM tasks WHERE goalId = :goalId ORDER BY isCompleted, date, time IS NULL, time")
    fun observeTasks(goalId: Long): Flow<List<Task>>

    /** Deleting a goal keeps its tasks, just unlinked. */
    @Query("UPDATE tasks SET goalId = NULL WHERE goalId = :goalId")
    suspend fun unlinkTasks(goalId: Long)

    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)
}
