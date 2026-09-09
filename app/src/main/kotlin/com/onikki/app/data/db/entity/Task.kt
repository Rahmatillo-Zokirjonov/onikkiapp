package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

enum class TaskCategory { ISH, SHAXSIY }

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val category: TaskCategory,
    val isCompleted: Boolean = false,
    val habitId: Long? = null
)
