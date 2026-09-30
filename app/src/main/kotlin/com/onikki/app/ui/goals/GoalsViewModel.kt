package com.onikki.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.AppDatabase
import com.onikki.app.data.db.entity.Goal
import com.onikki.app.data.db.entity.LifeArea
import com.onikki.app.data.db.entity.SavingsGoal
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.domain.goals.AreaSummary
import com.onikki.app.domain.goals.BigGoal
import com.onikki.app.domain.goals.GoalTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed interface GoalSheetTarget {
    /** Big goal: new ([goal] null) or edit. */
    data class EditBig(val goal: Goal?) : GoalSheetTarget
    /** Stage of [parent]: new ([stage] null) or edit. */
    data class EditStage(val parent: Goal, val stage: Goal?) : GoalSheetTarget
    /** New daily task linked to [goal] (a stage, or a big goal without stages). */
    data class NewTask(val goal: Goal) : GoalSheetTarget
}

data class GoalsUiState(
    val active: List<BigGoal> = emptyList(),
    val finished: List<BigGoal> = emptyList(),
    val areas: List<AreaSummary> = emptyList(),
    val areaFilter: LifeArea? = null,
    val savings: List<SavingsGoal> = emptyList(),
    val sheet: GoalSheetTarget? = null,
    val isLoaded: Boolean = false
) {
    val visibleActive: List<BigGoal> get() = active.filter { areaFilter == null || it.goal.area == areaFilter }
    fun find(id: Long) = (active + finished).firstOrNull { it.goal.id == id }
}

class GoalsViewModel(private val db: AppDatabase) : ViewModel() {
    private val today = LocalDate.now()
    private val sheet = MutableStateFlow<GoalSheetTarget?>(null)
    private val areaFilter = MutableStateFlow<LifeArea?>(null)

    private val treeFlow = combine(
        db.goalDao().observeAll(),
        db.goalDao().observeTaskCounts(),
        db.taskDao().observeByDate(today),
        db.savingsGoalDao().observeAll()
    ) { goals, counts, todayTasks, savings ->
        val tree = GoalTree.build(
            goals = goals,
            taskCounts = counts.associate { it.goalId to (it.done to it.total) },
            openTodayByGoal = todayTasks.filter { !it.isCompleted && it.goalId != null }.groupingBy { it.goalId!! }.eachCount(),
            savingsById = savings.associate { it.id to it.currentAmount },
            today = today
        )
        tree to savings
    }

    val uiState: StateFlow<GoalsUiState> = combine(treeFlow, sheet, areaFilter) { (tree, savings), openSheet, filter ->
        GoalsUiState(
            active = tree.filter { !it.isDone }.sortedWith(compareBy({ it.goal.deadline == null }, { it.goal.deadline })),
            finished = tree.filter { it.isDone },
            areas = GoalTree.areas(tree),
            areaFilter = filter,
            savings = savings,
            sheet = openSheet,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun tasksOf(goalId: Long) = db.goalDao().observeTasks(goalId)

    fun setAreaFilter(area: LifeArea?) { areaFilter.value = if (areaFilter.value == area) null else area }

    fun openNewBig() { sheet.value = GoalSheetTarget.EditBig(null) }
    fun openEditBig(goal: Goal) { sheet.value = GoalSheetTarget.EditBig(goal) }
    fun openNewStage(parent: Goal) { sheet.value = GoalSheetTarget.EditStage(parent, null) }
    fun openEditStage(parent: Goal, stage: Goal) { sheet.value = GoalSheetTarget.EditStage(parent, stage) }
    fun openNewTask(goal: Goal) { sheet.value = GoalSheetTarget.NewTask(goal) }
    fun dismissSheet() { sheet.value = null }

    fun saveBig(existing: Goal?, draft: Goal) {
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            if (existing == null) db.goalDao().insert(draft.copy(id = 0, parentId = null))
            else db.goalDao().update(draft.copy(id = existing.id, parentId = null))
            sheet.value = null
        }
    }

    fun saveStage(parent: Goal, existing: Goal?, draft: Goal) {
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            if (existing == null) {
                val order = db.goalDao().maxStageOrder(parent.id) + 1
                db.goalDao().insert(draft.copy(id = 0, parentId = parent.id, orderIndex = order, area = null))
            } else {
                db.goalDao().update(draft.copy(id = existing.id, parentId = parent.id, orderIndex = existing.orderIndex, area = null))
            }
            sheet.value = null
        }
    }

    fun delete(goal: Goal) {
        viewModelScope.launch {
            if (!goal.isStage) {
                db.goalDao().unlinkStageTasks(goal.id)
                db.goalDao().deleteStages(goal.id)
            }
            db.goalDao().unlinkTasks(goal.id)
            db.goalDao().delete(goal)
            sheet.value = null
        }
    }

    fun adjustNumber(goal: Goal, delta: Long) {
        viewModelScope.launch { db.goalDao().update(goal.copy(current = (goal.current + delta).coerceAtLeast(0))) }
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
