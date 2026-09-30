package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** How a goal's progress is measured. */
enum class GoalKind(val label: String) {
    /** By the tasks linked to it: done / all. ("IELTS 7.0" → study tasks.) */
    TASKS("Vazifalar orqali"),
    /** By a number moving toward a target. ("20 ta kitob", "5 kg", "10 000 000 so'm".) */
    NUMBER("Raqam bilan")
}

/**
 * A concrete goal the daily plan works toward. Tasks point at it via [Task.goalId]; the daily plan
 * nudges when an active goal has nothing scheduled for today.
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** "Nima uchun?" — the reason, shown on the goal to keep motivation visible. */
    val why: String? = null,
    val icon: String = "🎯",
    val deadline: LocalDate? = null,
    val kind: GoalKind = GoalKind.TASKS,
    /** NUMBER goals: target and current value, with a unit ("bet", "kg", "so'm"). */
    val target: Long = 0,
    val current: Long = 0,
    val unit: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
    /** Set when the user marks it achieved; it then moves to the finished list. */
    val doneAt: LocalDate? = null
)
