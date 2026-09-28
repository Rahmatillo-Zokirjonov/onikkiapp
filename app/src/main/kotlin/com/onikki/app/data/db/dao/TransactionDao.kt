package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Result row for a category-grouped expense total. */
data class CategoryTotal(val category: String, val total: Long)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'KIRIM' THEN amount ELSE -amount END), 0) FROM transactions")
    fun observeBalance(): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'KIRIM' THEN amount ELSE -amount END), 0) " +
            "FROM transactions WHERE wallet = :wallet"
    )
    fun observeWalletBalance(wallet: Wallet): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type")
    fun observeTotalByType(type: TransactionType): Flow<Long>

    @Query(
        "SELECT category, SUM(amount) as total FROM transactions " +
            "WHERE type = 'CHIQIM' AND date BETWEEN :from AND :to " +
            "GROUP BY category ORDER BY total DESC"
    )
    fun observeExpenseByCategory(from: LocalDate, to: LocalDate): Flow<List<CategoryTotal>>

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE type = 'CHIQIM' AND category = :category AND date BETWEEN :from AND :to"
    )
    fun observeSpentForCategory(category: String, from: LocalDate, to: LocalDate): Flow<Long>

    @Query(
        "SELECT category FROM transactions WHERE type = :type " +
            "GROUP BY category ORDER BY COUNT(*) DESC LIMIT :limit"
    )
    fun observeTopCategories(type: TransactionType, limit: Int): Flow<List<String>>

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)
}
