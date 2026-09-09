package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class DebtDirection { MEN_QARZDORMAN, MENGA_QARZDOR }
enum class DebtStatus { OCHIQ, YOPILGAN }

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val amount: Long,
    val direction: DebtDirection,
    val dueDate: LocalDate?,
    val status: DebtStatus = DebtStatus.OCHIQ
)
