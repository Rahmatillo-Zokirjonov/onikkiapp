package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.Account
import kotlinx.coroutines.flow.Flow

data class AccountNet(val accountId: Long, val net: Long)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Account>>

    @Query(
        "SELECT accountId, COALESCE(SUM(CASE WHEN type = 'KIRIM' THEN amount ELSE -amount END), 0) AS net " +
            "FROM transactions GROUP BY accountId"
    )
    fun observeNetByAccount(): Flow<List<AccountNet>>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun transactionCount(accountId: Long): Int

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): Account?

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)
}
