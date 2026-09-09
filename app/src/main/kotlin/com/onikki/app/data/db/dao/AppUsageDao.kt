package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onikki.app.data.db.entity.AppUsage
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Result row for a date-grouped total across all apps. */
data class DailyUsageTotal(val date: LocalDate, val total: Int)

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage WHERE date = :date ORDER BY minutesUsed DESC")
    fun observeByDate(date: LocalDate): Flow<List<AppUsage>>

    @Query("SELECT * FROM app_usage WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<AppUsage>>

    @Query(
        "SELECT date, SUM(minutesUsed) as total FROM app_usage WHERE date BETWEEN :from AND :to GROUP BY date"
    )
    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyUsageTotal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: AppUsage)
}
