package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A finished day. The row only exists once the user presses "Kunni yakunlash" — its existence is what
 * unlocks "until the day review" app blocks and silences the evening reminder, so merely opening the
 * screen must never create it. Fields added in v6 are nullable: rows from older versions lack them.
 */
@Entity(tableName = "daily_reviews")
data class DailyReview(
    @PrimaryKey val date: LocalDate,
    val completedCount: Int,
    val totalCount: Int,
    val aiSummary: String? = null,
    val synced: Boolean = false,
    val score: Int? = null,
    /** 1 (very bad) .. 5 (great), chosen by the user. */
    val mood: Int? = null,
    val reflection: String? = null,
    val habitsCompleted: Int? = null,
    val habitsTotal: Int? = null,
    /** So'm spent that day. */
    val spent: Long? = null,
    val finishedAt: Long? = null
)
