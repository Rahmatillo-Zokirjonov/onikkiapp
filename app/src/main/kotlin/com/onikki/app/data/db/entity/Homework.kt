package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

enum class HomeworkStatus(val label: String) { PENDING("Bajarilmoqda"), DONE("Bajarildi"), MISSED("Bajarilmadi") }

/** Which apps a homework in its danger window keeps closed. */
enum class HomeworkBlockScope(val label: String) {
    /** Apps added under Ilovalar nazorati. */
    CONTROLLED("Nazoratdagi ilovalar"),
    /** Every app except calls, SMS, settings, the home screen and On ikki itself. */
    ALL("Barcha ilovalar")
}

/**
 * A task for a subject (fan) from the timetable. From [blockHoursBefore] hours before [dueAt] it is
 * "xavfli": a reminder fires and apps are blocked until it's marked done (or honestly marked missed).
 */
@Entity(tableName = "homework")
data class Homework(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Subject name — the timetable lesson title it belongs to. */
    val subject: String,
    val title: String,
    val note: String? = null,
    /** Deadline. */
    val dueAt: LocalDateTime,
    /** Extra reminder chosen by the user; null = none (the danger reminder still comes when blocking is on). */
    val remindAt: LocalDateTime? = null,
    /** Hours before [dueAt] the danger window starts; null = never block. */
    val blockHoursBefore: Int? = 8,
    @ColumnInfo(defaultValue = "'CONTROLLED'") val blockScope: HomeworkBlockScope = HomeworkBlockScope.CONTROLLED,
    @ColumnInfo(defaultValue = "'PENDING'") val status: HomeworkStatus = HomeworkStatus.PENDING,
    /** When it was marked done / missed (millis). */
    val finishedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
