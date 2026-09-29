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
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.domain.habits.HabitStats
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
    val habits: List<HabitStats> = emptyList(),
    val balance: Long = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val nextPrayerName: String = "",
    val nextPrayerTime: LocalTime = LocalTime.MIDNIGHT,
    val secondsUntilNextPrayer: Long = 0
)

private data class MoneyAndPlanSnapshot(
    val tasks: List<Task>,
    val habits: List<HabitStats>,
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
        secondTicker(),
        locationStore.city
    ) { snapshot, _, city ->
        val (prayerName, prayerTime, secondsUntil) = nextPrayer(city)
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
            secondsUntilNextPrayer = secondsUntil
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun dataSnapshotFlow(): Flow<MoneyAndPlanSnapshot> = combine(
        taskDao.observeByDate(today),
        habitRepository.observeStats(today),
        transactionDao.observeBalance(),
        transactionDao.observeTotalByType(TransactionType.KIRIM),
        transactionDao.observeTotalByType(TransactionType.CHIQIM)
    ) { tasks, habits, balance, income, expense ->
        MoneyAndPlanSnapshot(tasks, habits, balance, income, expense)
    }

    private fun secondTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { taskDao.setCompleted(task.id, !task.isCompleted) }
    }

    fun toggleHabitToday(stats: HabitStats) {
        viewModelScope.launch { habitRepository.tapToday(stats, today) }
    }

    private fun nextPrayer(city: CityLocation): Triple<String, LocalTime, Long> {
        val now = LocalDateTime.now()
        val currentDate = LocalDate.now()
        val todaysTimes = PrayerTimeCalculator.calculate(currentDate, city.latitude, city.longitude, city.utcOffsetHours)
        val upcoming = todaysTimes.asOrderedList().firstOrNull { it.second.isAfter(now.toLocalTime()) }
        if (upcoming != null) {
            val target = LocalDateTime.of(currentDate, upcoming.second)
            return Triple(upcoming.first, upcoming.second, Duration.between(now, target).seconds)
        }
        val tomorrow = currentDate.plusDays(1)
        val tomorrowFajr = PrayerTimeCalculator.calculate(tomorrow, city.latitude, city.longitude, city.utcOffsetHours).fajr
        val target = LocalDateTime.of(tomorrow, tomorrowFajr)
        return Triple("Bomdod", tomorrowFajr, Duration.between(now, target).seconds)
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
