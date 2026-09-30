package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.NoteAttachment
import com.onikki.app.data.db.entity.NoteFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteFolderDao {
    @Query("SELECT * FROM note_folders ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<NoteFolder>>

    @Insert
    suspend fun insert(folder: NoteFolder): Long

    @Update
    suspend fun update(folder: NoteFolder)

    @Delete
    suspend fun delete(folder: NoteFolder)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)
}

@Dao
interface NoteAttachmentDao {
    @Query("SELECT * FROM note_attachments ORDER BY createdAt")
    fun observeAll(): Flow<List<NoteAttachment>>

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY createdAt")
    fun observeFor(noteId: Long): Flow<List<NoteAttachment>>

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId")
    suspend fun listFor(noteId: Long): List<NoteAttachment>

    @Query("SELECT * FROM note_attachments WHERE noteId IN (SELECT id FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :before)")
    suspend fun listForPurge(before: Long): List<NoteAttachment>

    @Query("SELECT * FROM note_attachments WHERE noteId IN (SELECT id FROM notes WHERE deletedAt IS NOT NULL)")
    suspend fun listInTrash(): List<NoteAttachment>

    @Insert
    suspend fun insert(attachment: NoteAttachment): Long

    @Delete
    suspend fun delete(attachment: NoteAttachment)

    @Query("DELETE FROM note_attachments WHERE noteId = :noteId")
    suspend fun deleteFor(noteId: Long)

    /** Attachments whose note no longer exists (after a purge / hard delete). */
    @Query("DELETE FROM note_attachments WHERE noteId NOT IN (SELECT id FROM notes)")
    suspend fun deleteOrphans()
}
