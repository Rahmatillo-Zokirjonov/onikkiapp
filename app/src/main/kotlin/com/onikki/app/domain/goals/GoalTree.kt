package com.onikki.app.domain.goals

import com.onikki.app.data.db.entity.Goal
import com.onikki.app.data.db.entity.GoalKind
import com.onikki.app.data.db.entity.LifeArea
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** One measurable node: a stage, or a big goal without stages. */
data class NodeProgress(
    val goal: Goal,
    /** 0..1 */
    val fraction: Float,
    /** NUMBER goals: the effective current value (from Moliya savings when linked). */
    val current: Long,
    val doneTasks: Int,
    val totalTasks: Int,
    val openTasksToday: Int,
    val daysLeft: Long?
) {
    val isDone: Boolean get() = goal.doneAt != null
    val percent: Int get() = (fraction * 100).toInt()

    /** "Kuniga ~3 bet kerak" for number goals with a deadline. */
    val perDayHint: String?
        get() {
            if (goal.kind != GoalKind.NUMBER || daysLeft == null || daysLeft <= 0 || isDone) return null
            val remaining = goal.target - current
            if (remaining <= 0) return null
            val perDay = ceil(remaining / daysLeft.toDouble()).toLong()
            return "Kuniga ~$perDay ${goal.unit.orEmpty()} kerak".trim()
        }
}

/** A big goal with its stages; progress rolls up from them. */
data class BigGoal(
    val goal: Goal,
    /** Its own measure when it has no stages. */
    val self: NodeProgress,
    val stages: List<NodeProgress>,
    val fraction: Float,
    val daysLeft: Long?
) {
    val isDone: Boolean get() = goal.doneAt != null
    val percent: Int get() = (fraction * 100).toInt()
    /** The first unfinished stage, in order — where daily work goes. */
    val activeStage: NodeProgress? get() = stages.firstOrNull { !it.isDone }
    /** What today's tasks should be linked to: the active stage, or the goal itself if it has no stages. */
    val workTarget: NodeProgress? get() = if (stages.isEmpty()) self.takeUnless { isDone } else activeStage
    val doneStages: Int get() = stages.count { it.isDone }
}

data class AreaSummary(val area: LifeArea, val goals: Int, val fraction: Float)

/** Builds the goal tree and its numbers. Pure — the ViewModel feeds it rows from Room. */
object GoalTree {

    fun build(
        goals: List<Goal>,
        taskCounts: Map<Long, Pair<Int, Int>>,      // goalId → (done, total)
        openTodayByGoal: Map<Long, Int>,
        savingsById: Map<Long, Long>,
        today: LocalDate
    ): List<BigGoal> {
        fun node(g: Goal): NodeProgress {
            val (done, total) = taskCounts[g.id] ?: (0 to 0)
            val current = g.linkedSavingsId?.let { savingsById[it] } ?: g.current
            val fraction = when {
                g.doneAt != null -> 1f
                g.kind == GoalKind.NUMBER -> if (g.target <= 0) 0f else (current.toFloat() / g.target).coerceIn(0f, 1f)
                else -> if (total == 0) 0f else done.toFloat() / total
            }
            return NodeProgress(g, fraction, current, done, total, openTodayByGoal[g.id] ?: 0, g.deadline?.let { ChronoUnit.DAYS.between(today, it) })
        }

        val stagesByParent = goals.filter { it.parentId != null }.groupBy { it.parentId!! }
        return goals.filter { it.parentId == null }.map { big ->
            val stages = stagesByParent[big.id].orEmpty().sortedWith(compareBy({ it.orderIndex }, { it.id })).map(::node)
            val self = node(big)
            val fraction = when {
                big.doneAt != null -> 1f
                stages.isNotEmpty() -> stages.map { it.fraction }.average().toFloat()
                else -> self.fraction
            }
            BigGoal(big, self, stages, fraction, big.deadline?.let { ChronoUnit.DAYS.between(today, it) })
        }
    }

    /**
     * The goal Bosh sahifa shows: the one the user pinned while it's still active, otherwise the
     * active goal with the nearest deadline (undated ones last, then the one furthest along).
     */
    fun main(bigGoals: List<BigGoal>, pinnedId: Long?): BigGoal? {
        val active = bigGoals.filter { !it.isDone }
        return active.firstOrNull { it.goal.id == pinnedId }
            ?: active.minWithOrNull(compareBy<BigGoal>({ it.goal.deadline == null }, { it.goal.deadline }, { -it.fraction }))
    }

    /** Every life area with how many active big goals it has and their average progress. */
    fun areas(bigGoals: List<BigGoal>): List<AreaSummary> {
        val active = bigGoals.filter { !it.isDone }
        return LifeArea.entries.map { area ->
            val inArea = active.filter { it.goal.area == area }
            AreaSummary(area, inArea.size, if (inArea.isEmpty()) 0f else inArea.map { it.fraction }.average().toFloat())
        }
    }
}
