package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onikki.app.data.db.entity.UsageHours
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface UsageHoursDao {
    @Query("SELECT * FROM usage_hours WHERE date = :date")
    fun observe(date: LocalDate): Flow<UsageHours?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(hours: UsageHours)
}
