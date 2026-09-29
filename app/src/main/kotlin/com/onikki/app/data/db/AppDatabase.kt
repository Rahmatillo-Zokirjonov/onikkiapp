package com.onikki.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

/**
 * v2: habits get weekday scheduling, logs get a per-day count (for "8 stakan" style targets).
 * Existing "done" logs are backfilled to the habit's full target so they stay done.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN activeDays INTEGER NOT NULL DEFAULT 127")
        db.execSQL("ALTER TABLE habits ADD COLUMN createdAt TEXT")
        db.execSQL("ALTER TABLE habit_logs ADD COLUMN count INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE habit_logs SET count = " +
                "(SELECT MAX(dailyTarget, 1) FROM habits WHERE habits.id = habit_logs.habitId) WHERE isDone = 1"
        )
    }
}

/** v3: optional per-note reminder with a priority level. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN remindAt TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN priority TEXT NOT NULL DEFAULT 'ODDIY'")
    }
}
