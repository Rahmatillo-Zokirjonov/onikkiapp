package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Answers of practice modes that aren't tied to one list word (confusing pairs, word families), so the
 * stats can show every mode in one place. Word modes log to vocab_reviews (and the SRS) instead.
 */
@Entity(tableName = "practice_log", indices = [Index("mode"), Index("day")])
data class PracticeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val itemKey: String,
    val wordId: Long? = null,
    /** 100 right, 50 "deyarli", 0 wrong. */
    val score: Int,
    val at: Long,
    val day: LocalDate
)

/** A confusing pair the user added ("lend / borrow"); [answer] is 0 or 1 — which word fills [sentence]. */
@Entity(tableName = "confusable_pairs")
data class ConfusablePair(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val first: String,
    val second: String,
    val firstUz: String,
    val secondUz: String,
    val sentence: String,
    val answer: Int,
    val why: String
)
