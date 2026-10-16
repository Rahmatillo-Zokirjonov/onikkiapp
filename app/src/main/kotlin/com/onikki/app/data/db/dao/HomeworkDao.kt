package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.Homework
import com.onikki.app.data.db.entity.HomeworkStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework ORDER BY dueAt")
    fun observeAll(): Flow<List<Homework>>

    @Query("SELECT * FROM homework WHERE status = 'PENDING' ORDER BY dueAt")
    fun observePending(): Flow<List<Homework>>

    /** Read on every app switch by the blocker, so it's a plain query (no Flow). */
    @Query("SELECT * FROM homework WHERE status = 'PENDING' ORDER BY dueAt")
    suspend fun pending(): List<Homework>

    @Query("SELECT * FROM homework WHERE id = :id")
    suspend fun findById(id: Long): Homework?

    @Query("UPDATE homework SET status = :status, finishedAt = :at WHERE id = :id")
    suspend fun setStatus(id: Long, status: HomeworkStatus, at: Long?)

    @Insert
    suspend fun insert(homework: Homework): Long

    @Update
    suspend fun update(homework: Homework)

    @Delete
    suspend fun delete(homework: Homework)
}
