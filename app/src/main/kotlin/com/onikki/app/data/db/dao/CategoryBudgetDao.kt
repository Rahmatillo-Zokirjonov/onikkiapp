package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.CategoryBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets ORDER BY category ASC")
    fun observeAll(): Flow<List<CategoryBudget>>

    @Query("SELECT * FROM category_budgets WHERE category = :category LIMIT 1")
    suspend fun findByCategory(category: String): CategoryBudget?

    @Insert
    suspend fun insert(budget: CategoryBudget): Long

    @Update
    suspend fun update(budget: CategoryBudget)

    @Delete
    suspend fun delete(budget: CategoryBudget)
}
