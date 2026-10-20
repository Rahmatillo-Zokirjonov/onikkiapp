package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One answered grammar exercise; the last 20 per topic decide mastery. */
@Entity(tableName = "grammar_attempts", indices = [Index("topicId"), Index("day")])
data class GrammarAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: String,
    val kind: String,
    /** GrammarExercise.key, so the next round starts with exercises not seen lately. */
    val exerciseKey: String,
    val at: Long,
    val day: LocalDate,
    /** 100 right, 50 "deyarli", 0 wrong. */
    val score: Int,
    val xp: Int
)

/** An AI-written grammar exercise kept for reuse (JSON of GrammarExercise), so each costs one call once. */
@Entity(tableName = "grammar_cache", indices = [Index("topicId")])
data class GrammarCached(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: String,
    val payload: String,
    val createdAt: Long
)
