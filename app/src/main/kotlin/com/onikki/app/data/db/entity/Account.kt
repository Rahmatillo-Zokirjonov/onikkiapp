package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountKind(val label: String) { NAQD("Naqd"), KARTA("Karta") }

/**
 * A wallet: cash or one bank card. Balance = [initialBalance] + its transactions, so a card added
 * mid-life starts from its real balance without a fake "income" skewing the stats.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AccountKind,
    /** Last 4 card digits, shown as "•• 1234". Null for cash. */
    val lastDigits: String? = null,
    val initialBalance: Long = 0,
    val sortOrder: Int = 0
)
