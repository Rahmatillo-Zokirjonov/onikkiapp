package com.onikki.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.repository.HabitListItem
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.data.repository.WeeklyStreakChart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitsUiState(
    val habits: List<HabitListItem> = emptyList(),
    val weekCompletionPercent: Int = 0,
    val topStreak: WeeklyStreakChart? = null,
    val isAddSheetOpen: Boolean = false
)

class HabitsViewModel(private val habitRepository: HabitRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val isAddSheetOpen = MutableStateFlow(false)

    val uiState: StateFlow<HabitsUiState> = combine(
        habitRepository.observeHabitList(today),
        habitRepository.observeWeekCompletionPercent(today),
        habitRepository.observeTopStreakChart(today),
        isAddSheetOpen
    ) { habits, weekPercent, topStreak, sheetOpen ->
        HabitsUiState(
            habits = habits,
            weekCompletionPercent = weekPercent,
            topStreak = topStreak,
            isAddSheetOpen = sheetOpen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun toggleToday(item: HabitListItem) {
        viewModelScope.launch { habitRepository.toggleToday(item.habit, today) }
    }

    fun openAddSheet() {
        isAddSheetOpen.value = true
    }

    fun dismissAddSheet() {
        isAddSheetOpen.value = false
    }

    fun addHabit(name: String, icon: String, dailyTarget: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            habitRepository.addHabit(name.trim(), icon, dailyTarget)
            isAddSheetOpen.value = false
        }
    }

    companion object {
        fun factory(habitRepository: HabitRepository) = viewModelFactory {
            initializer { HabitsViewModel(habitRepository) }
        }
    }
}
