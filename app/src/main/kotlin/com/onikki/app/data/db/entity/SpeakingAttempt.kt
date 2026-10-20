package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One spoken item: a read-aloud sentence (kind READ) or an oral answer (kind ANSWER), best score of its tries. */
@Entity(tableName = "speaking_attempts", indices = [Index("day")])
data class SpeakingAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val itemKey: String,
    val level: String,
    val at: Long,
    val day: LocalDate,
    val percent: Int,
    val xp: Int
)
