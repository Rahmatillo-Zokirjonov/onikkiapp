package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A listening text the AI wrote for the user; sentences one per line. Built-in texts live in code. */
@Entity(tableName = "listening_texts")
data class ListeningCustomText(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val title: String,
    val body: String,
    val createdAt: Long
)

/** One finished listening exercise (a text can be done many times). */
@Entity(tableName = "listening_attempts", indices = [Index("textId"), Index("day")])
data class ListeningAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: String,
    val level: String,
    val at: Long,
    val day: LocalDate,
    val accuracy: Int,
    val xp: Int
)
