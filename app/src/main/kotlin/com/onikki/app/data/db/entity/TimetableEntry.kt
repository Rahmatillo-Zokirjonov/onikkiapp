package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/** How a timetable block repeats. */
enum class TimetableRepeat(val label: String) {
    /** Every day at the same time (e.g. morning sport). */
    DAILY("Har kuni"),
    /** One weekday, every week (e.g. Monday 9:00 English); see [WeekParity]. */
    WEEKLY("Har hafta"),
    /** A single block on one date (time blocking a particular day). */
    ONCE("Bir marta")
}

/** For WEEKLY blocks: every week, or only odd / even ISO weeks (university "toq/juft hafta"). */
enum class WeekParity(val label: String) { ALL("Har hafta"), ODD("Toq hafta"), EVEN("Juft hafta") }

@Entity(tableName = "timetable_entries")
data class TimetableEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(defaultValue = "'WEEKLY'") val repeat: TimetableRepeat = TimetableRepeat.WEEKLY,
    /** 1 = Monday … 7 = Sunday; used by WEEKLY. */
    @ColumnInfo(defaultValue = "1") val dayOfWeek: Int = 1,
    /** Used by ONCE. */
    val date: LocalDate? = null,
    val start: LocalTime,
    val end: LocalTime,
    /** Room / place, e.g. "204-xona". */
    val place: String? = null,
    /** Teacher, link, anything. */
    val note: String? = null,
    val color: NoteColor? = null,
    @ColumnInfo(defaultValue = "'ALL'") val parity: WeekParity = WeekParity.ALL,
    /** Minutes before the start to remind; null = no reminder, 0 = at the start. */
    val remindMinutes: Int? = 10
)
