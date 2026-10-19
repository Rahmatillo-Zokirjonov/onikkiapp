package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.onikki.app.data.db.entity.ListeningAttempt
import com.onikki.app.data.db.entity.ListeningCustomText
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningDao {
    @Query("SELECT * FROM listening_texts ORDER BY id")
    fun observeTexts(): Flow<List<ListeningCustomText>>

    @Insert
    suspend fun insertText(text: ListeningCustomText): Long

    @Delete
    suspend fun deleteText(text: ListeningCustomText)

    @Insert
    suspend fun insertAttempt(attempt: ListeningAttempt): Long

    @Query("SELECT * FROM listening_attempts ORDER BY at")
    fun observeAttempts(): Flow<List<ListeningAttempt>>
}
