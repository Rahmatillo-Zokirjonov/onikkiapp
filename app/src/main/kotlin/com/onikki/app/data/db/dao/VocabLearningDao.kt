package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.VocabDeck
import com.onikki.app.data.db.entity.VocabLessonResult
import com.onikki.app.data.db.entity.VocabReview
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface VocabDeckDao {
    @Query("SELECT * FROM vocab_decks ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<VocabDeck>>

    @Query("SELECT * FROM vocab_decks ORDER BY sortOrder, id")
    suspend fun getAll(): List<VocabDeck>

    @Insert
    suspend fun insert(deck: VocabDeck): Long

    @Update
    suspend fun update(deck: VocabDeck)

    @Delete
    suspend fun delete(deck: VocabDeck)
}

/** XP and answers per day, for the streak and the weekly chart. */
data class VocabDayTotal(val day: LocalDate, val xp: Int, val answers: Int)

/** How often a word was missed, for "Eng qiyin so'zlar". */
data class VocabMissCount(val wordId: Long, val wrong: Int, val total: Int)

@Dao
interface VocabReviewDao {
    @Insert
    suspend fun insert(review: VocabReview): Long

    @Query("SELECT day, SUM(xp) AS xp, COUNT(*) AS answers FROM vocab_reviews WHERE day >= :from GROUP BY day ORDER BY day")
    fun observeDailyTotals(from: LocalDate): Flow<List<VocabDayTotal>>

    @Query("SELECT DISTINCT day FROM vocab_reviews ORDER BY day DESC LIMIT 400")
    fun observeActiveDays(): Flow<List<LocalDate>>

    @Query("SELECT COALESCE(SUM(xp), 0) FROM vocab_reviews")
    fun observeTotalXp(): Flow<Int>

    @Query(
        "SELECT wordId, SUM(CASE WHEN grade = 'AGAIN' THEN 1 ELSE 0 END) AS wrong, COUNT(*) AS total " +
            "FROM vocab_reviews GROUP BY wordId HAVING wrong > 0 ORDER BY wrong DESC, total DESC LIMIT :limit"
    )
    fun observeMostMissed(limit: Int): Flow<List<VocabMissCount>>
}

@Dao
interface VocabLessonDao {
    @Query("SELECT * FROM vocab_lessons")
    fun observeAll(): Flow<List<VocabLessonResult>>

    /** A replayed lesson keeps its latest result. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: VocabLessonResult)
}
