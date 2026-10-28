package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.TimetableEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries ORDER BY start, id")
    fun observeAll(): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries WHERE id = :id")
    suspend fun findById(id: Long): TimetableEntry?

    @Insert
    suspend fun insertAll(entries: List<TimetableEntry>)

    @Update
    suspend fun update(entry: TimetableEntry)

    @Delete
    suspend fun delete(entry: TimetableEntry)

    @Query("SELECT * FROM timetable_entries WHERE source = :source")
    suspend fun bySource(source: String): List<TimetableEntry>

    @Query("DELETE FROM timetable_entries WHERE source = :source")
    suspend fun deleteBySource(source: String)

    /** Swaps every row from [source] for [entries] in one go (the screen never sees a half-synced week). */
    @androidx.room.Transaction
    suspend fun replaceSource(source: String, entries: List<TimetableEntry>) {
        deleteBySource(source)
        insertAll(entries)
    }
}
