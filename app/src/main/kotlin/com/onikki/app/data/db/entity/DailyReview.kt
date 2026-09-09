package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "daily_reviews")
data class DailyReview(
    @PrimaryKey val date: LocalDate,
    val completedCount: Int,
    val totalCount: Int,
    val aiSummary: String? = null,
    val synced: Boolean = false
)
