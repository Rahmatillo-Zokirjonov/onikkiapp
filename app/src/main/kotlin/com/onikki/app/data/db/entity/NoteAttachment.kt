package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AttachmentKind { IMAGE, AUDIO }

/** A photo or voice recording kept in the app's private storage (filesDir/note_media/[fileName]). */
@Entity(tableName = "note_attachments", indices = [Index("noteId")])
data class NoteAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val kind: AttachmentKind,
    val fileName: String,
    /** Audio length; null for images. */
    val durationMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
