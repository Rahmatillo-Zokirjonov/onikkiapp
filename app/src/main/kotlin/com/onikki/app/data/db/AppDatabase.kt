package com.onikki.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.onikki.app.data.db.dao.AppLimitDao
import com.onikki.app.data.db.dao.AppUsageDao
import com.onikki.app.data.db.dao.CategoryBudgetDao
import com.onikki.app.data.db.dao.DailyReviewDao
import com.onikki.app.data.db.dao.DebtDao
import com.onikki.app.data.db.dao.HabitDao
import com.onikki.app.data.db.dao.HabitLogDao
import com.onikki.app.data.db.dao.NoteDao
import com.onikki.app.data.db.dao.SavingsGoalDao
import com.onikki.app.data.db.dao.TaskDao
import com.onikki.app.data.db.dao.TransactionDao
import com.onikki.app.data.db.entity.AppLimit
import com.onikki.app.data.db.entity.AppUsage
import com.onikki.app.data.db.entity.CategoryBudget
import com.onikki.app.data.db.entity.DailyReview
import com.onikki.app.data.db.entity.Debt
import com.onikki.app.data.db.entity.Habit
import com.onikki.app.data.db.entity.HabitLog
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.SavingsGoal
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.Transaction

@Database(
    entities = [
        Task::class, Habit::class, HabitLog::class, Transaction::class,
        CategoryBudget::class, Debt::class, SavingsGoal::class, AppUsage::class,
        AppLimit::class, DailyReview::class, Note::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun debtDao(): DebtDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun dailyReviewDao(): DailyReviewDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context, AppDatabase::class.java, "onikki.db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
