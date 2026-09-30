package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.Note
import kotlinx.coroutines.flow.Flow

data class IdTitle(val id: Long, val title: String)

@Dao
interface NoteDao {
    /** Every note, archived and trashed included — the list screen splits them. */
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Note>>

    /** Titles of tasks some note links to (the note shows it as a chip). */
    @Query("SELECT id, title FROM tasks WHERE id IN (SELECT taskId FROM notes WHERE taskId IS NOT NULL)")
    fun observeLinkedTaskTitles(): Flow<List<IdTitle>>

    @Query("SELECT * FROM notes WHERE journalDate = :date AND deletedAt IS NULL LIMIT 1")
    suspend fun findJournal(date: java.time.LocalDate): Note?

    @Query("SELECT * FROM notes WHERE goalId = :goalId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeForGoal(goalId: Long): Flow<List<Note>>

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET archived = :archived, pinned = 0 WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE notes SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Long?)

    @Query("UPDATE notes SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setContent(id: Long, content: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrash(before: Long)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    /** Notes that have (or had) a reminder — the planner picks the future ones. */
    @Query("SELECT * FROM notes WHERE remindAt IS NOT NULL AND deletedAt IS NULL")
    fun observeWithReminder(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: Long): Note?

    @Query("UPDATE notes SET remindAt = :remindAt WHERE id = :id")
    suspend fun setRemindAt(id: Long, remindAt: java.time.LocalDateTime?)

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}
