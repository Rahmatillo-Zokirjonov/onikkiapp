package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.Debt
import com.onikki.app.data.db.entity.DebtStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY dueDate ASC")
    fun observeAll(): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE status = :status ORDER BY dueDate ASC")
    fun observeByStatus(status: DebtStatus): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun findById(id: Long): Debt?

    @Insert
    suspend fun insert(debt: Debt): Long

    @Update
    suspend fun update(debt: Debt)

    @Delete
    suspend fun delete(debt: Debt)
}
