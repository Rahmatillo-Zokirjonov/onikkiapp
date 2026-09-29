package com.onikki.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.entity.Habit
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.data.repository.weekCompletionPercent
import com.onikki.app.domain.habits.HabitStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The add/edit sheet; [habit] null = new habit. */
data class HabitSheetTarget(val habit: Habit?)

data class HabitsUiState(
    val today: LocalDate = LocalDate.now(),
    /** Scheduled-today habits first, then the ones resting today. */
    val habits: List<HabitStats> = emptyList(),
    val weekCompletionPercent: Int? = null,
    val sheet: HabitSheetTarget? = null,
    val isLoaded: Boolean = false
) {
    val topStreak: HabitStats? get() = habits.filter { it.currentStreak > 0 }.maxByOrNull { it.currentStreak }
    val doneTodayCount: Int get() = habits.count { it.isActiveToday && it.isDoneToday }
    val dueTodayCount: Int get() = habits.count { it.isActiveToday }
    fun find(habitId: Long): HabitStats? = habits.firstOrNull { it.habit.id == habitId }
}

class HabitsViewModel(private val habitRepository: HabitRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val sheet = MutableStateFlow<HabitSheetTarget?>(null)

    val uiState: StateFlow<HabitsUiState> = combine(habitRepository.observeStats(today), sheet) { stats, sheetTarget ->
        HabitsUiState(
            today = today,
            habits = stats.sortedBy { !it.isActiveToday },
            weekCompletionPercent = weekCompletionPercent(stats, today),
            sheet = sheetTarget,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun tapToday(stats: HabitStats) {
        viewModelScope.launch { habitRepository.tapToday(stats, today) }
    }

    fun setTodayCount(stats: HabitStats, count: Int) {
        viewModelScope.launch { habitRepository.setCount(stats.habit, today, count) }
    }

    fun toggleDay(stats: HabitStats, date: LocalDate, currentlyDone: Boolean) {
        if (date.isAfter(today)) return
        viewModelScope.launch { habitRepository.toggleDay(stats.habit, date, currentlyDone) }
    }

    fun openNewHabit() {
        sheet.value = HabitSheetTarget(null)
    }

    fun openEdit(habit: Habit) {
        sheet.value = HabitSheetTarget(habit)
    }

    fun dismissSheet() {
        sheet.value = null
    }

    fun saveHabit(existing: Habit?, name: String, icon: String, dailyTarget: Int, activeDays: Int) {
        if (name.isBlank() || activeDays == 0) return
        viewModelScope.launch {
            if (existing == null) {
                habitRepository.addHabit(name.trim(), icon, dailyTarget, activeDays)
            } else {
                habitRepository.updateHabit(
                    existing.copy(name = name.trim(), icon = icon, dailyTarget = dailyTarget, activeDays = activeDays)
                )
            }
            sheet.value = null
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habit)
            sheet.value = null
        }
    }

    companion object {
        fun factory(habitRepository: HabitRepository) = viewModelFactory {
            initializer { HabitsViewModel(habitRepository) }
        }
    }
}
