package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate

/** Bit (dayOfWeek.value - 1) set = the habit is scheduled that weekday. 127 = every day. */
const val ALL_DAYS = 0b1111111
const val WEEKDAYS = 0b0011111

fun daysMaskOf(days: Collection<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    /** How many times a day counts as "done" (8 glasses of water → 8). 1 = a simple check-off. */
    val dailyTarget: Int,
    /** Legacy from schema v1; streaks are now computed from the logs (see HabitStatsCalculator), not stored. */
    val streakCount: Int = 0,
    @ColumnInfo(defaultValue = "127") val activeDays: Int = ALL_DAYS,
    /** Days before this don't count as "missed". Null for habits created before schema v2 (first log is used instead). */
    val createdAt: LocalDate? = LocalDate.now()
) {
    fun isActiveOn(date: LocalDate): Boolean = activeDays and (1 shl (date.dayOfWeek.value - 1)) != 0
}
