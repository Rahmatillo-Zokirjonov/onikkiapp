package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.onikki.app.data.db.entity.PodcastAttempt
import com.onikki.app.data.db.entity.PodcastCustom
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Insert
    suspend fun insertAttempt(attempt: PodcastAttempt): Long

    @Query("SELECT * FROM podcast_attempts ORDER BY at")
    fun observeAttempts(): Flow<List<PodcastAttempt>>

    @Query("SELECT day, SUM(xp) AS xp FROM podcast_attempts GROUP BY day")
    fun observeXpByDay(): Flow<List<DayXp>>

    @Query("SELECT * FROM podcast_texts ORDER BY id")
    fun observeCustom(): Flow<List<PodcastCustom>>

    @Insert
    suspend fun insertCustom(p: PodcastCustom): Long
}
