package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onikki.app.data.db.entity.MerchantCategory
import com.onikki.app.data.db.entity.SmsImport
import com.onikki.app.data.db.entity.SmsImportStatus
import com.onikki.app.data.db.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsImportDao {
    @Query("SELECT EXISTS(SELECT 1 FROM sms_imports WHERE hash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SmsImport): Long

    @Query("SELECT * FROM sms_imports WHERE status = :status ORDER BY receivedAt DESC")
    fun observeByStatus(status: SmsImportStatus): Flow<List<SmsImport>>

    @Query("UPDATE sms_imports SET status = :status, transactionId = :transactionId WHERE hash = :hash")
    suspend fun setStatus(hash: String, status: SmsImportStatus, transactionId: Long?)

    /** SMS-created transactions still waiting for the user's note. */
    @Query("SELECT * FROM transactions WHERE fromSms = 1 AND (note IS NULL OR note = '') ORDER BY date DESC, id DESC")
    fun observeUnnoted(): Flow<List<Transaction>>

    @Query("SELECT category FROM merchant_categories WHERE merchant = :merchant")
    suspend fun categoryFor(merchant: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun rememberCategory(item: MerchantCategory)

    /** The user's most used notes — offered as one-tap chips. */
    @Query(
        "SELECT note FROM transactions WHERE note IS NOT NULL AND note != '' " +
            "GROUP BY note ORDER BY COUNT(*) DESC, MAX(id) DESC LIMIT :limit"
    )
    fun observeFrequentNotes(limit: Int): Flow<List<String>>

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: String)
}
