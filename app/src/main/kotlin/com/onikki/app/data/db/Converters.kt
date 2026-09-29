package com.onikki.app.data.db

import androidx.room.TypeConverter
import com.onikki.app.data.db.entity.DebtDirection
import com.onikki.app.data.db.entity.DebtStatus
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class Converters {
    @TypeConverter fun fromLocalDate(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter fun fromNotePriority(value: NotePriority?): String? = value?.name
    @TypeConverter fun toNotePriority(value: String?): NotePriority? =
        value?.let { runCatching { NotePriority.valueOf(it) }.getOrDefault(NotePriority.ODDIY) }

    @TypeConverter fun fromLocalTime(value: LocalTime?): String? = value?.toString()
    @TypeConverter fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter fun fromTaskCategory(value: TaskCategory?): String? = value?.name
    @TypeConverter fun toTaskCategory(value: String?): TaskCategory? = value?.let(TaskCategory::valueOf)

    @TypeConverter fun fromTransactionType(value: TransactionType?): String? = value?.name
    @TypeConverter fun toTransactionType(value: String?): TransactionType? = value?.let(TransactionType::valueOf)

    @TypeConverter fun fromWallet(value: Wallet?): String? = value?.name
    @TypeConverter fun toWallet(value: String?): Wallet? = value?.let(Wallet::valueOf)

    @TypeConverter fun fromDebtDirection(value: DebtDirection?): String? = value?.name
    @TypeConverter fun toDebtDirection(value: String?): DebtDirection? = value?.let(DebtDirection::valueOf)

    @TypeConverter fun fromDebtStatus(value: DebtStatus?): String? = value?.name
    @TypeConverter fun toDebtStatus(value: String?): DebtStatus? = value?.let(DebtStatus::valueOf)

    @TypeConverter fun fromTagList(value: List<String>?): String = value?.joinToString(",") ?: ""
    @TypeConverter fun toTagList(value: String?): List<String> =
        value?.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
}
