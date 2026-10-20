package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.onikki.app.data.db.entity.SpeakingAttempt
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakingDao {
    @Insert
    suspend fun insert(attempt: SpeakingAttempt): Long

    @Query("SELECT * FROM speaking_attempts ORDER BY at")
    fun observeAll(): Flow<List<SpeakingAttempt>>

    @Query("SELECT day, SUM(xp) AS xp FROM speaking_attempts GROUP BY day")
    fun observeXpByDay(): Flow<List<DayXp>>
}
