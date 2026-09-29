package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

enum class RepeatKind(val label: String) {
    NONE("Bir marta"), WEEKLY("Har hafta"), MONTHLY("Har oy"), YEARLY("Har yil");

    fun next(date: LocalDate): LocalDate = when (this) {
        NONE -> date
        WEEKLY -> date.plusWeeks(1)
        MONTHLY -> date.plusMonths(1)
        YEARLY -> date.plusYears(1)
    }
}

/**
 * A future expense to remember (rent, internet, a birthday gift...). Paying it records a real
 * transaction; a repeating one then moves [dueDate] to the next period, a one-time one gets [paidDate].
 */
@Entity(tableName = "planned_expenses")
data class PlannedExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val category: String,
    /** Wallet it will be paid from; null = decide when paying. */
    val accountId: Long? = null,
    val dueDate: LocalDate,
    val repeat: RepeatKind = RepeatKind.NONE,
    val remindEnabled: Boolean = true,
    /** 0 = on the due date only; otherwise also this many days before. */
    val remindDaysBefore: Int = 1,
    val remindTime: LocalTime = LocalTime.of(9, 0),
    /** Set when a one-time expense is paid; it then leaves the upcoming list. */
    val paidDate: LocalDate? = null
)
