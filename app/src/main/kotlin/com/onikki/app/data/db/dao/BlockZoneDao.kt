package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.BlockZone
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockZoneDao {
    @Query("SELECT * FROM block_zones ORDER BY name")
    fun observeAll(): Flow<List<BlockZone>>

    @Query("SELECT * FROM block_zones")
    suspend fun getAll(): List<BlockZone>

    @Insert
    suspend fun insert(zone: BlockZone): Long

    @Update
    suspend fun update(zone: BlockZone)

    @Delete
    suspend fun delete(zone: BlockZone)
}
