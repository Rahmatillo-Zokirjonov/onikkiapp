package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One English–Uzbek pair for the unlock challenge. Several accepted answers can be split by "," or "/". */
@Entity(tableName = "vocab_words")
data class VocabWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val english: String,
    val uzbek: String,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    /** Epoch millis of the last time it was asked; 0 = never. */
    val lastAskedAt: Long = 0
)
