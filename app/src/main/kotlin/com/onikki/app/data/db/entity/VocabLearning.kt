package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A word set ("Lesson 1", "Mening so'zlarim", an import). On the learning path each deck is a Unit. */
@Entity(tableName = "vocab_decks")
data class VocabDeck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0
)

/** How the answer was graded for spaced repetition. */
enum class ReviewGrade(val label: String) { AGAIN("Bilmadim"), HARD("Qiyin"), EASY("Oson") }

/** Where an answer was given. */
enum class ReviewSource { LESSON, REVIEW, BLOCKER, LISTENING }

/**
 * One answered exercise. Streak, XP per day, today's reviews and the hardest words are all
 * computed from this log, so nothing is double-counted in separate counters.
 */
@Entity(tableName = "vocab_reviews", indices = [Index("wordId"), Index("day")])
data class VocabReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    /** Epoch millis. */
    val at: Long,
    /** Local day of [at], for per-day stats without timezone maths in SQL. */
    val day: LocalDate,
    val grade: ReviewGrade,
    @ColumnInfo(defaultValue = "0") val xp: Int = 0,
    val source: ReviewSource,
    /** Exercise type name (CARD, CHOOSE, TYPE, …); free text for the log. */
    val exercise: String
)

/** A finished lesson on the learning path: deck + its position (lessons are 8-word chunks of the deck). */
@Entity(tableName = "vocab_lessons", primaryKeys = ["deckId", "lessonIndex"])
data class VocabLessonResult(
    val deckId: Long,
    val lessonIndex: Int,
    val completedAt: Long,
    /** 0..100 */
    val accuracy: Int,
    val xp: Int,
    val seconds: Int
)
