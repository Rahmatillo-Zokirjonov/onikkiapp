package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Where a word is in learning. */
enum class WordStatus(val label: String) {
    /** Never studied: shows up as a new-word card in a lesson. */
    NEW("Yangi"),
    /** Being learned: short intervals, comes back soon. */
    LEARNING("O'rganilmoqda"),
    /** Known, coming back on a growing schedule (3 → 7 → 14 → 30 days). */
    REVIEW("Takrorlash"),
    /** Survived the 30-day step. */
    MASTERED("O'zlashtirilgan")
}

/**
 * One English–Uzbek pair, used by the unlock challenge and the O'rganish lessons.
 * Several accepted answers can be split by "," or "/". SRS fields follow a simplified SM-2 (see Srs).
 */
@Entity(tableName = "vocab_words", indices = [Index("deckId"), Index("dueDate")])
data class VocabWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val english: String,
    val uzbek: String,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    /** Epoch millis of the last time it was asked; 0 = never. */
    val lastAskedAt: Long = 0,
    /** noun / verb / adjective …; free text. */
    val partOfSpeech: String? = null,
    /** Irregular verbs: "decided", "forgot". */
    val pastForm: String? = null,
    val example: String? = null,
    /** CEFR level "A1" / "A2" / "B1"; null = unknown. */
    val level: String? = null,
    val deckId: Long? = null,
    @ColumnInfo(defaultValue = "'NEW'") val status: WordStatus = WordStatus.NEW,
    @ColumnInfo(defaultValue = "0") val intervalDays: Int = 0,
    @ColumnInfo(defaultValue = "2.5") val ease: Double = 2.5,
    /** Next review day; null while NEW. */
    val dueDate: LocalDate? = null,
    /** Exercise difficulty reached: 0 card → 1 choose → 2 type → 3 sentence (see Srs.stageAfter). */
    @ColumnInfo(defaultValue = "0") val stage: Int = 0,
    /** Times it was forgotten after being known. */
    @ColumnInfo(defaultValue = "0") val lapses: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastReviewedAt: Long = 0
)
