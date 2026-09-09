package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class TransactionType { KIRIM, CHIQIM }
enum class Wallet { NAQD, KARTA }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val category: String,
    val wallet: Wallet,
    val date: LocalDate,
    val note: String? = null
)
