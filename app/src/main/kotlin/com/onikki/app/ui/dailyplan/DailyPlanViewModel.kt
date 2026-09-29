@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.ui.dailyplan

import com.onikki.app.data.repository.payPlanned
import com.onikki.app.data.db.entity.DEFAULT_CASH_ACCOUNT_ID
import com.onikki.app.data.db.entity.PlannedExpense
import com.onikki.app.data.db.AppDatabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.dao.TaskDao
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TaskCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Which task the add/edit sheet is showing; [task] null means "new task". */
data class TaskSheetTarget(val task: Task?)

/** Per-day summary for the week strip's indicator dot. */
data class DayLoad(val total: Int, val done: Int)

data class DailyPlanUiState(
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val tasks: List<Task> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val weekLoad: Map<LocalDate, DayLoad> = emptyMap(),
    val overdue: List<Task> = emptyList(),
    /** Planned payments/incomes due on the selected day (today also shows overdue ones). */
    val money: List<PlannedExpense> = emptyList(),
    val sheet: TaskSheetTarget? = null
)

private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

class DailyPlanViewModel(
    private val taskDao: TaskDao,
    private val db: AppDatabase
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val selectedDate = MutableStateFlow(today)
    private val sheet = MutableStateFlow<TaskSheetTarget?>(null)

    private val weekLoadFlow = selectedDate.flatMapLatest { date ->
        val monday = mondayOf(date)
        taskDao.observeBetween(monday, monday.plusDays(6))
    }

    private val tasksAndMoney = combine(
        selectedDate.flatMapLatest { taskDao.observeByDate(it) },
        db.plannedExpenseDao().observeAll()
    ) { tasks, planned -> tasks to planned }

    val uiState: StateFlow<DailyPlanUiState> = combine(
        selectedDate,
        tasksAndMoney,
        weekLoadFlow,
        taskDao.observeOverdue(today),
        sheet
    ) { date, (tasks, planned), weekTasks, overdue, openSheet ->
        val money = planned.filter { it.paidDate == null }.filter {
            it.dueDate == date || (date == today && it.dueDate.isBefore(today))
        }.sortedBy { it.dueDate }
        DailyPlanUiState(
            today = today,
            selectedDate = date,
            tasks = tasks,
            completedCount = tasks.count { it.isCompleted },
            totalCount = tasks.size,
            weekLoad = weekTasks.groupBy { it.date }.mapValues { (_, day) ->
                DayLoad(total = day.size, done = day.count { it.isCompleted })
            },
            overdue = overdue,
            money = money,
            sheet = openSheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyPlanUiState())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    /** Moves the selection by whole weeks, keeping the same weekday. */
    fun shiftWeek(weeks: Long) {
        selectedDate.value = selectedDate.value.plusWeeks(weeks)
    }

    fun goToToday() {
        selectedDate.value = today
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { taskDao.setCompleted(task.id, !task.isCompleted) }
    }

    fun openNewTask() {
        sheet.value = TaskSheetTarget(null)
    }

    fun openTask(task: Task) {
        sheet.value = TaskSheetTarget(task)
    }

    fun dismissSheet() {
        sheet.value = null
    }

    fun saveTask(existing: Task?, title: String, date: LocalDate, time: LocalTime?, category: TaskCategory) {
        if (title.isBlank()) return
        viewModelScope.launch {
            if (existing == null) {
                taskDao.insert(Task(title = title.trim(), date = date, time = time, category = category))
            } else {
                taskDao.update(existing.copy(title = title.trim(), date = date, time = time, category = category))
            }
            sheet.value = null
            // Follow the task if it was moved to (or created on) another day, so it doesn't seem to vanish.
            selectedDate.value = date
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskDao.delete(task)
            sheet.value = null
        }
    }

    /** Marks a planned payment/income done from the plan: full amount, its wallet (or cash), today. */
    fun completeMoney(expense: PlannedExpense) {
        viewModelScope.launch {
            val accounts = db.accountDao()
            val account = expense.accountId?.let { accounts.findById(it) } ?: accounts.findById(DEFAULT_CASH_ACCOUNT_ID) ?: return@launch
            payPlanned(db.transactionDao(), db.plannedExpenseDao(), expense, account, expense.amount, today)
        }
    }

    /** "Bugunga ko'chirish": carry every unfinished task from earlier days over to today. */
    fun moveOverdueToToday() {
        viewModelScope.launch {
            taskDao.moveUnfinishedBefore(today)
            selectedDate.value = today
        }
    }

    companion object {
        fun factory(db: AppDatabase) = viewModelFactory {
            initializer { DailyPlanViewModel(db.taskDao(), db) }
        }
    }
}
