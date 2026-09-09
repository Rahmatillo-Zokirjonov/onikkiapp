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

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Transaction>>

    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'KIRIM' THEN amount ELSE -amount END), 0) FROM transactions")
    fun observeBalance(): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'KIRIM' THEN amount ELSE -amount END), 0) " +
            "FROM transactions WHERE wallet = :wallet"
    )
    fun observeWalletBalance(wallet: Wallet): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type")
    fun observeTotalByType(type: TransactionType): Flow<Long>

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)
}
