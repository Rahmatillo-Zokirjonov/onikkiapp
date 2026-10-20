package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One finished podcast: how much was understood. */
@Entity(tableName = "podcast_attempts", indices = [Index("podcastId"), Index("day")])
data class PodcastAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val podcastId: String,
    val level: String,
    val at: Long,
    val day: LocalDate,
    /** 0–100: "qancha tushundim". */
    val percent: Int,
    val xp: Int
)

/** A podcast the AI wrote (transcript + questions in Podcasts.parse format), kept for reuse. */
@Entity(tableName = "podcast_texts")
data class PodcastCustom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val minutes: Int,
    val title: String,
    val topic: String,
    val transcript: String,
    val questions: String,
    val createdAt: Long
)
