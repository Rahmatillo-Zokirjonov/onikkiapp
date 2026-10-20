package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.onikki.app.data.db.entity.GrammarAttempt
import com.onikki.app.data.db.entity.GrammarCached
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** XP earned on a day (any source that keeps its own log). */
data class DayXp(val day: LocalDate, val xp: Int)

@Dao
interface GrammarDao {
    @Insert
    suspend fun insertAttempt(attempt: GrammarAttempt): Long

    @Query("SELECT * FROM grammar_attempts ORDER BY at")
    fun observeAttempts(): Flow<List<GrammarAttempt>>

    @Query("SELECT day, SUM(xp) AS xp FROM grammar_attempts GROUP BY day")
    fun observeXpByDay(): Flow<List<DayXp>>

    @Query("SELECT * FROM grammar_cache WHERE topicId = :topicId ORDER BY id")
    suspend fun cached(topicId: String): List<GrammarCached>

    @Query("SELECT topicId, COUNT(*) AS n FROM grammar_cache GROUP BY topicId")
    fun observeCacheCounts(): Flow<List<TopicCount>>

    @Insert
    suspend fun insertCached(items: List<GrammarCached>)
}

data class TopicCount(val topicId: String, val n: Int)
