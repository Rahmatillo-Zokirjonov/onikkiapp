package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onikki.app.data.db.entity.DailyReview
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyReviewDao {
    @Query("SELECT * FROM daily_reviews WHERE date = :date LIMIT 1")
    fun observeByDate(date: LocalDate): Flow<DailyReview?>

    @Query("SELECT * FROM daily_reviews WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: LocalDate): DailyReview?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: DailyReview)
}
