package com.onikki.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.AppDatabase
import com.onikki.app.data.db.entity.DEFAULT_CASH_ACCOUNT_ID
import com.onikki.app.data.db.entity.DailyReview
import com.onikki.app.data.db.entity.PlannedExpense
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.local.CityLocation
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.ProfileStore
import com.onikki.app.data.repository.BALANCE_TREND_DAYS
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.data.repository.dailyBalanceTrend
import com.onikki.app.data.repository.payPlanned
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
    val name: String = "",
    val now: LocalDateTime = LocalDateTime.now(),
    /** Today's tasks: unfinished first (by time, untimed last), then finished. */
    val tasks: List<Task> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val overdueCount: Int = 0,
    /** Planned payments/incomes due today or already late. */
    val moneyDue: List<PlannedExpense> = emptyList(),
    /** Scheduled-today habits first, resting ones after. */
    val habits: List<HabitStats> = emptyList(),
    val balance: Long = 0,
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val balanceTrend: List<Long> = emptyList(),
    val review: DailyReview? = null,
    val prayers: List<Pair<String, LocalTime>> = emptyList(),
    val nextPrayerName: String = "",
    val nextPrayerTime: LocalTime = LocalTime.MIDNIGHT,
    val secondsUntilNextPrayer: Long = 0
)

private data class PlanPart(val tasks: List<Task>, val overdue: Int, val money: List<PlannedExpense>, val habits: List<HabitStats>)
private data class MoneyPart(val balance: Long, val income: Long, val expense: Long, val trend: List<Long>)
private data class ProfilePart(val name: String, val review: DailyReview?, val city: CityLocation)

class HomeViewModel(
    private val db: AppDatabase,
    private val habitRepository: HabitRepository,
    locationStore: LocationStore,
    profileStore: ProfileStore
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    private val planFlow: Flow<PlanPart> = combine(
        db.taskDao().observeByDate(today),
        db.taskDao().observeOverdue(today),
        db.plannedExpenseDao().observeAll(),
        habitRepository.observeStats(today)
    ) { tasks, overdue, planned, habits ->
        PlanPart(
            tasks = tasks.sortedWith(compareBy<Task>({ it.isCompleted }, { it.time == null }, { it.time })),
            overdue = overdue.size,
            money = planned.filter { it.paidDate == null && !it.dueDate.isAfter(today) }.sortedBy { it.dueDate },
            habits = habits.sortedBy { !it.isActiveToday }
        )
    }

    private val moneyFlow: Flow<MoneyPart> = combine(
        db.transactionDao().observeBalance(),
        db.transactionDao().observeTotalByTypeBetween(TransactionType.KIRIM, today.withDayOfMonth(1), today),
        db.transactionDao().observeTotalByTypeBetween(TransactionType.CHIQIM, today.withDayOfMonth(1), today),
        db.transactionDao().observeSince(today.minusDays(BALANCE_TREND_DAYS.toLong()))
    ) { balance, income, expense, recent ->
        MoneyPart(balance, income, expense, dailyBalanceTrend(recent, balance, today))
    }

    private val profileFlow: Flow<ProfilePart> = combine(
        profileStore.name,
        db.dailyReviewDao().observeByDate(today),
        locationStore.city
    ) { name, review, city -> ProfilePart(name, review, city) }

    val uiState: StateFlow<HomeUiState> = combine(planFlow, moneyFlow, profileFlow, secondTicker()) { plan, money, profile, _ ->
        val now = LocalDateTime.now()
        val prayers = PrayerTimeCalculator
            .calculate(now.toLocalDate(), profile.city.latitude, profile.city.longitude, profile.city.utcOffsetHours)
            .asOrderedList()
        val (prayerName, prayerTime, secondsUntil) = nextPrayer(profile.city, prayers, now)
        HomeUiState(
            name = profile.name,
            now = now,
            tasks = plan.tasks,
            completedCount = plan.tasks.count { it.isCompleted },
            totalCount = plan.tasks.size,
            overdueCount = plan.overdue,
            moneyDue = plan.money,
            habits = plan.habits,
            balance = money.balance,
            monthIncome = money.income,
            monthExpense = money.expense,
            balanceTrend = money.trend,
            review = profile.review,
            prayers = prayers,
            nextPrayerName = prayerName,
            nextPrayerTime = prayerTime,
            secondsUntilNextPrayer = secondsUntil
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun secondTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { db.taskDao().setCompleted(task.id, !task.isCompleted) }
    }

    fun tapHabit(stats: HabitStats) {
        viewModelScope.launch { habitRepository.tapToday(stats, today) }
    }

    /** "To'landi"/"Olindi" straight from Home: full amount, its wallet (or cash), today. */
    fun completeMoney(expense: PlannedExpense) {
        viewModelScope.launch {
            val accounts = db.accountDao()
            val account = expense.accountId?.let { accounts.findById(it) } ?: accounts.findById(DEFAULT_CASH_ACCOUNT_ID) ?: return@launch
            payPlanned(db.transactionDao(), db.plannedExpenseDao(), expense, account, expense.amount, today)
        }
    }

    private fun nextPrayer(
        city: CityLocation,
        todays: List<Pair<String, LocalTime>>,
        now: LocalDateTime
    ): Triple<String, LocalTime, Long> {
        val upcoming = todays.firstOrNull { it.second.isAfter(now.toLocalTime()) }
        if (upcoming != null) {
            return Triple(upcoming.first, upcoming.second, Duration.between(now, now.toLocalDate().atTime(upcoming.second)).seconds)
        }
        val tomorrow = now.toLocalDate().plusDays(1)
        val fajr = PrayerTimeCalculator.calculate(tomorrow, city.latitude, city.longitude, city.utcOffsetHours).fajr
        return Triple("Bomdod", fajr, Duration.between(now, tomorrow.atTime(fajr)).seconds)
    }

    companion object {
        fun factory(db: AppDatabase, habitRepository: HabitRepository, locationStore: LocationStore, profileStore: ProfileStore) =
            viewModelFactory {
                initializer { HomeViewModel(db, habitRepository, locationStore, profileStore) }
            }
    }
}
