package com.onikki.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class TransactionType { KIRIM, CHIQIM }
/** Legacy (schema v1–3) wallet kind; still written (= the account's kind) so old queries keep working. */
enum class Wallet { NAQD, KARTA }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val category: String,
    val wallet: Wallet,
    val date: LocalDate,
    val note: String? = null,
    /** The [Account] this moved money in/out of. Accounts 1 (Naqd) and 2 (Karta) always exist. */
    @ColumnInfo(defaultValue = "1") val accountId: Long = DEFAULT_CASH_ACCOUNT_ID
)

const val DEFAULT_CASH_ACCOUNT_ID = 1L
const val DEFAULT_CARD_ACCOUNT_ID = 2L
