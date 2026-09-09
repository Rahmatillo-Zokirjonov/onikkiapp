package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val dailyLimitMinutes: Int,
    val isHarmful: Boolean = false,
    val blockedHours: String? = null
)
