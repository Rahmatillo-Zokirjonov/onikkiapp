@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.ui.dailyplan

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
import java.time.LocalDate
import java.time.LocalTime

data class DailyPlanUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val tasks: List<Task> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val isAddSheetOpen: Boolean = false
)

class DailyPlanViewModel(private val taskDao: TaskDao) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val isAddSheetOpen = MutableStateFlow(false)

    val uiState: StateFlow<DailyPlanUiState> = combine(
        selectedDate,
        selectedDate.flatMapLatest { taskDao.observeByDate(it) },
        isAddSheetOpen
    ) { date, tasks, sheetOpen ->
        DailyPlanUiState(
            selectedDate = date,
            tasks = tasks,
            completedCount = tasks.count { it.isCompleted },
            totalCount = tasks.size,
            isAddSheetOpen = sheetOpen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyPlanUiState())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { taskDao.setCompleted(task.id, !task.isCompleted) }
    }

    fun openAddSheet() {
        isAddSheetOpen.value = true
    }

    fun dismissAddSheet() {
        isAddSheetOpen.value = false
    }

    fun addTask(title: String, time: LocalTime, category: TaskCategory) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskDao.insert(
                Task(title = title.trim(), date = selectedDate.value, time = time, category = category)
            )
            isAddSheetOpen.value = false
        }
    }

    companion object {
        fun factory(taskDao: TaskDao) = viewModelFactory {
            initializer { DailyPlanViewModel(taskDao) }
        }
    }
}
