package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Screen time per hour for one day ("24 comma-joined minute counts", see UsagePeriods.encodeHours).
 * Android keeps raw usage events only ~10 days; storing this keeps each past day's hourly chart.
 */
@Entity(tableName = "usage_hours")
data class UsageHours(
    @PrimaryKey val date: LocalDate,
    val minutes: String
)
