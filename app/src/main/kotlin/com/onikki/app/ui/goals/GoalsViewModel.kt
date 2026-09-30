package com.onikki.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.AppDatabase
import com.onikki.app.data.db.entity.Goal
import com.onikki.app.data.db.entity.GoalKind
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TaskCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** A goal with everything its card shows. */
data class GoalProgress(
    val goal: Goal,
    /** 0..1 */
    val fraction: Float,
    val doneTasks: Int,
    val totalTasks: Int,
    val todayTasks: Int,
    val daysLeft: Long?
) {
    val isDone: Boolean get() = goal.doneAt != null
    val percent: Int get() = (fraction * 100).toInt()

    /** "Kuniga ~3 bet kerak" for number goals with a deadline; null otherwise. */
    val perDayHint: String?
        get() {
            if (goal.kind != GoalKind.NUMBER || daysLeft == null || daysLeft <= 0) return null
            val remaining = goal.target - goal.current
            if (remaining <= 0) return null
            val perDay = ceil(remaining / daysLeft.toDouble()).toLong()
            return "Kuniga ~$perDay ${goal.unit.orEmpty()} kerak".trim()
        }
}

fun computeProgress(goal: Goal, total: Int, done: Int, today: Int, date: LocalDate): GoalProgress {
    val fraction = when {
        goal.doneAt != null -> 1f
        goal.kind == GoalKind.NUMBER -> if (goal.target <= 0) 0f else (goal.current.toFloat() / goal.target).coerceIn(0f, 1f)
        else -> if (total == 0) 0f else done.toFloat() / total
    }
    return GoalProgress(
        goal = goal,
        fraction = fraction,
        doneTasks = done,
        totalTasks = total,
        todayTasks = today,
        daysLeft = goal.deadline?.let { ChronoUnit.DAYS.between(date, it) }
    )
}

sealed interface GoalSheetTarget {
    data class Edit(val goal: Goal?) : GoalSheetTarget
    /** New task for this goal (from the goal card or its detail). */
    data class NewTask(val goal: Goal) : GoalSheetTarget
}

data class GoalsUiState(
    val active: List<GoalProgress> = emptyList(),
    val finished: List<GoalProgress> = emptyList(),
    val sheet: GoalSheetTarget? = null,
    val isLoaded: Boolean = false
) {
    fun find(id: Long) = (active + finished).firstOrNull { it.goal.id == id }
}

class GoalsViewModel(private val db: AppDatabase) : ViewModel() {
    private val today = LocalDate.now()
    private val sheet = MutableStateFlow<GoalSheetTarget?>(null)

    val uiState: StateFlow<GoalsUiState> = combine(
        db.goalDao().observeAll(),
        db.goalDao().observeTaskCounts(),
        db.taskDao().observeByDate(today),
        sheet
    ) { goals, counts, todayTasks, openSheet ->
        val byGoal = counts.associateBy { it.goalId }
        val todayByGoal = todayTasks.filter { !it.isCompleted }.groupingBy { it.goalId }.eachCount()
        val all = goals.map { g ->
            val c = byGoal[g.id]
            computeProgress(g, c?.total ?: 0, c?.done ?: 0, todayByGoal[g.id] ?: 0, today)
        }
        GoalsUiState(
            active = all.filter { !it.isDone },
            finished = all.filter { it.isDone },
            sheet = openSheet,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun tasksOf(goalId: Long) = db.goalDao().observeTasks(goalId)

    fun openNew() { sheet.value = GoalSheetTarget.Edit(null) }
    fun openEdit(goal: Goal) { sheet.value = GoalSheetTarget.Edit(goal) }
    fun openNewTask(goal: Goal) { sheet.value = GoalSheetTarget.NewTask(goal) }
    fun dismissSheet() { sheet.value = null }

    fun saveGoal(existing: Goal?, draft: Goal) {
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            if (existing == null) db.goalDao().insert(draft.copy(id = 0)) else db.goalDao().update(draft.copy(id = existing.id))
            sheet.value = null
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            db.goalDao().unlinkTasks(goal.id)
            db.goalDao().delete(goal)
            sheet.value = null
        }
    }

    fun adjustNumber(goal: Goal, delta: Long) {
        viewModelScope.launch {
            val next = (goal.current + delta).coerceAtLeast(0)
            db.goalDao().update(goal.copy(current = next))
        }
    }

    fun setDone(goal: Goal, done: Boolean) {
        viewModelScope.launch { db.goalDao().update(goal.copy(doneAt = if (done) today else null)) }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { db.taskDao().setCompleted(task.id, !task.isCompleted) }
    }

    fun addTask(goal: Goal, title: String, date: LocalDate, time: LocalTime?, category: TaskCategory) {
        if (title.isBlank()) return
        viewModelScope.launch {
            db.taskDao().insert(Task(title = title.trim(), date = date, time = time, category = category, goalId = goal.id))
            sheet.value = null
        }
    }

    companion object {
        fun factory(db: AppDatabase) = viewModelFactory { initializer { GoalsViewModel(db) } }
    }
}
