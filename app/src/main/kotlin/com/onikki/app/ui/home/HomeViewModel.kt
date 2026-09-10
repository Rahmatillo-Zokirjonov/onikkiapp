package com.onikki.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.dao.TaskDao
import com.onikki.app.data.db.dao.TransactionDao
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.local.CityLocation
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.repository.HabitProgress
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.domain.prayer.PrayerTimeCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class HomeUiState(
    val tasks: List<Task> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val habits: List<HabitProgress> = emptyList(),
    val balance: Long = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val nextPrayerName: String = "",
    val nextPrayerTime: LocalTime = LocalTime.MIDNIGHT,
    val minutesUntilNextPrayer: Long = 0
)

private data class MoneyAndPlanSnapshot(
    val tasks: List<Task>,
    val habits: List<HabitProgress>,
    val balance: Long,
    val income: Long,
    val expense: Long
)

class HomeViewModel(
    private val taskDao: TaskDao,
    private val habitRepository: HabitRepository,
    private val transactionDao: TransactionDao,
    private val locationStore: LocationStore
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val uiState: StateFlow<HomeUiState> = combine(
        dataSnapshotFlow(),
        minuteTicker(),
        locationStore.city
    ) { snapshot, _, city ->
        val (prayerName, prayerTime, minutesUntil) = nextPrayer(city)
        HomeUiState(
            tasks = snapshot.tasks,
            completedCount = snapshot.tasks.count { it.isCompleted },
            totalCount = snapshot.tasks.size,
            habits = snapshot.habits,
            balance = snapshot.balance,
            income = snapshot.income,
            expense = snapshot.expense,
            nextPrayerName = prayerName,
            nextPrayerTime = prayerTime,
            minutesUntilNextPrayer = minutesUntil
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun dataSnapshotFlow(): Flow<MoneyAndPlanSnapshot> = combine(
        taskDao.observeByDate(today),
        habitRepository.observeProgress(today),
        transactionDao.observeBalance(),
        transactionDao.observeTotalByType(TransactionType.KIRIM),
        transactionDao.observeTotalByType(TransactionType.CHIQIM)
    ) { tasks, habits, balance, income, expense ->
        MoneyAndPlanSnapshot(tasks, habits, balance, income, expense)
    }

    private fun minuteTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { taskDao.setCompleted(task.id, !task.isCompleted) }
    }

    fun toggleHabitToday(progress: HabitProgress) {
        viewModelScope.launch { habitRepository.toggleToday(progress.habit, today) }
    }

    private fun nextPrayer(city: CityLocation): Triple<String, LocalTime, Long> {
        val now = LocalDateTime.now()
        val todaysTimes = PrayerTimeCalculator.calculate(today, city.latitude, city.longitude, city.utcOffsetHours)
        val upcoming = todaysTimes.asOrderedList().firstOrNull { it.second.isAfter(now.toLocalTime()) }
        if (upcoming != null) {
            val target = LocalDateTime.of(today, upcoming.second)
            return Triple(upcoming.first, upcoming.second, Duration.between(now, target).toMinutes())
        }
        val tomorrow = today.plusDays(1)
        val tomorrowFajr = PrayerTimeCalculator.calculate(tomorrow, city.latitude, city.longitude, city.utcOffsetHours).fajr
        val target = LocalDateTime.of(tomorrow, tomorrowFajr)
        return Triple("Bomdod", tomorrowFajr, Duration.between(now, target).toMinutes())
    }

    companion object {
        fun factory(
            taskDao: TaskDao,
            habitRepository: HabitRepository,
            transactionDao: TransactionDao,
            locationStore: LocationStore
        ) = viewModelFactory {
            initializer { HomeViewModel(taskDao, habitRepository, transactionDao, locationStore) }
        }
    }
}
