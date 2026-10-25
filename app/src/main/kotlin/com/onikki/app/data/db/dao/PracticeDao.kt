package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.onikki.app.data.db.entity.ConfusablePair
import com.onikki.app.data.db.entity.PracticeLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PracticeDao {
    @Insert
    suspend fun log(entry: PracticeLog)

    @Query("SELECT * FROM practice_log WHERE day >= :from ORDER BY at")
    fun observeSince(from: LocalDate): Flow<List<PracticeLog>>

    @Query("SELECT * FROM confusable_pairs ORDER BY id")
    fun observePairs(): Flow<List<ConfusablePair>>

    @Insert
    suspend fun insertPair(pair: ConfusablePair): Long

    @Delete
    suspend fun deletePair(pair: ConfusablePair)
}
