package com.onikki.app.data.db.entity

import androidx.room.Entity
import java.time.LocalDate

@Entity(tableName = "app_usage", primaryKeys = ["packageName", "date"])
data class AppUsage(
    val packageName: String,
    val appName: String,
    val date: LocalDate,
    val minutesUsed: Int
)
