package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A Qaydlar folder (Ish, Shaxsiy, O'qish…). Deleting one moves its notes out, never deletes them. */
@Entity(tableName = "note_folders")
data class NoteFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "📁",
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0
)
