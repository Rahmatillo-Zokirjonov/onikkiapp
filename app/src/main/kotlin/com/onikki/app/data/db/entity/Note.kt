package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/** How loudly a note's reminder interrupts. */
enum class NotePriority(val label: String) {
    /** Quiet: lands in the notification shade with a sound, no pop-up. */
    ODDIY("Oddiy"),
    /** Pops up as a banner on top of whatever app is open. */
    MUHIM("Muhim"),
    /** Alarm-style: full-screen alert over any app / the lock screen, keeps ringing until dismissed. */
    JUDA_MUHIM("Juda muhim")
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    /** Null = no reminder (the default). Stored to the minute. Kept after it fires, so the list can show it passed. */
    val remindAt: LocalDateTime? = null,
    @ColumnInfo(defaultValue = "'ODDIY'") val priority: NotePriority = NotePriority.ODDIY
)
