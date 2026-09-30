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

/** Card tint, like paper colours; null = the default surface. */
enum class NoteColor(val label: String) {
    QIZIL("Qizil"), TOQSARIQ("To'q sariq"), SARIQ("Sariq"), YASHIL("Yashil"), MOVIY("Moviy"), BINAFSHA("Binafsha"), KULRANG("Kulrang")
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
    @ColumnInfo(defaultValue = "'ODDIY'") val priority: NotePriority = NotePriority.ODDIY,
    /** Last edit (millis); the default sort. Migrated notes start at their [createdAt]. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    /** Pinned notes stay on top of the list. */
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    val color: NoteColor? = null,
    /** Hidden from the main list but kept (and still reminds). */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    /** Set when moved to Savat; purged 30 days later unless restored. */
    val deletedAt: Long? = null,
    /** [content] holds checklist lines ("☐ …" / "☑ …", see Checklist) instead of free text. */
    @ColumnInfo(defaultValue = "0") val isChecklist: Boolean = false
) {
    val isDeleted: Boolean get() = deletedAt != null
}
