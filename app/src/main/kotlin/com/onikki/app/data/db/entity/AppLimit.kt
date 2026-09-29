package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * Every control rule for one app. Historical column names are kept for the migration's sake:
 * [isHarmful] means "the daily limit is on", [blockedHours] holds the extra flags (prayer / day review).
 */
@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val dailyLimitMinutes: Int,
    val isHarmful: Boolean = false,
    val blockedHours: String? = null,
    /** Label saved when the rule is made, so apps with no usage today still show a name. */
    val appName: String? = null,
    /** Blocked between these times on [scheduleDays] (weekday bitmask like habits). End before start = overnight. */
    val scheduleStart: LocalTime? = null,
    val scheduleEnd: LocalTime? = null,
    @ColumnInfo(defaultValue = "127") val scheduleDays: Int = ALL_DAYS,
    /** Comma-separated [BlockZone] ids where this app is blocked. */
    val zoneIds: String? = null,
    /** No way in while a block applies — no word challenge, no waiting. */
    @ColumnInfo(defaultValue = "0") val strictMode: Boolean = false,
    /** Every time it's opened (after the grace period) the word/text challenge must be passed first. */
    @ColumnInfo(defaultValue = "0") val challengeOnOpen: Boolean = false
) {
    val hasSchedule: Boolean get() = scheduleStart != null && scheduleEnd != null
    val zoneIdList: List<Long> get() = zoneIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

    fun isScheduledBlock(date: LocalDate, time: LocalTime): Boolean {
        val start = scheduleStart ?: return false
        val end = scheduleEnd ?: return false
        fun dayOn(d: LocalDate) = scheduleDays and (1 shl (d.dayOfWeek.value - 1)) != 0
        return if (!end.isBefore(start)) {
            dayOn(date) && !time.isBefore(start) && time.isBefore(end)
        } else {
            // Overnight window (22:00–07:00): the evening part belongs to today, the morning part to yesterday.
            (dayOn(date) && !time.isBefore(start)) || (dayOn(date.minusDays(1)) && time.isBefore(end))
        }
    }
}
