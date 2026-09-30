package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** How a goal's (or stage's) progress is measured. */
enum class GoalKind(val label: String) {
    /** By the tasks linked to it: done / all. */
    TASKS("Vazifalar orqali"),
    /** By a number moving toward a target ("20 ta kitob", "20 000 000 so'm"). */
    NUMBER("Raqam bilan")
}

/** Life areas, so the user can see which parts of life their goals cover (and which they neglect). */
enum class LifeArea(val label: String, val icon: String) {
    SOGLIQ("Sog'liq", "💪"),
    OILA("Oila", "👨‍👩‍👧"),
    MOLIYA("Moliya", "💰"),
    TALIM("Ta'lim", "📚"),
    KARYERA("Karyera", "💼"),
    DIN("Din", "🕌"),
    SHAXSIY("Shaxsiy o'sish", "🌱")
}

/**
 * A goal. Two levels:
 * - a **big goal** ([parentId] null): a life goal with an [area], e.g. "2027-yilgacha uy sotib olish";
 * - a **stage** (bosqich, [parentId] = the big goal): an intermediate milestone with its own deadline and
 *   measure, ordered by [orderIndex] ("20 mln jamg'arish — dekabrgacha").
 * Daily tasks point at the stage being worked on (or at a big goal that has no stages) via [Task.goalId].
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** "Nima uchun?" — the reason, kept visible for motivation. */
    val why: String? = null,
    val icon: String = "🎯",
    val deadline: LocalDate? = null,
    val kind: GoalKind = GoalKind.TASKS,
    /** NUMBER: target and current value, with a unit ("bet", "kg", "so'm"). */
    val target: Long = 0,
    val current: Long = 0,
    val unit: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
    /** Set when achieved; a done stage hands over to the next one. */
    val doneAt: LocalDate? = null,
    /** Null for a big goal; the big goal's id for a stage. */
    val parentId: Long? = null,
    /** Big goals only. */
    val area: LifeArea? = null,
    /** Stage order inside its big goal (1, 2, 3 …). */
    @ColumnInfo(defaultValue = "0") val orderIndex: Int = 0,
    /** A money stage can mirror a Moliya savings goal: its saved amount is this stage's current value. */
    val linkedSavingsId: Long? = null
) {
    val isStage: Boolean get() = parentId != null
}
