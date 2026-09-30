package com.onikki.app.domain.goals

import com.onikki.app.data.db.entity.Goal
import com.onikki.app.data.db.entity.GoalKind
import com.onikki.app.data.db.entity.LifeArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalTreeTest {
    private val today = LocalDate.of(2026, 9, 30)

    private val house = Goal(id = 1, title = "Uy sotib olish", area = LifeArea.MOLIYA, deadline = LocalDate.of(2027, 12, 31))
    private val save = Goal(id = 2, title = "20 mln jamg'arish", parentId = 1, orderIndex = 1, kind = GoalKind.NUMBER,
        target = 20_000_000, current = 0, unit = "so'm", linkedSavingsId = 7, deadline = LocalDate.of(2026, 12, 31))
    private val research = Goal(id = 3, title = "Ipoteka shartlari", parentId = 1, orderIndex = 2)

    @Test
    fun bigGoalProgressIsTheAverageOfItsStages_andSavingsFeedTheMoneyStage() {
        val tree = GoalTree.build(
            goals = listOf(house, save, research),
            taskCounts = mapOf(3L to (1 to 4)),
            openTodayByGoal = emptyMap(),
            savingsById = mapOf(7L to 5_000_000L),
            today = today
        )
        val big = tree.single()
        assertEquals(0.25f, big.stages[0].fraction, 0.001f)   // 5 mln of 20 mln from Moliya
        assertEquals(0.25f, big.stages[1].fraction, 0.001f)   // 1 of 4 tasks
        assertEquals(0.25f, big.fraction, 0.001f)
        assertEquals(2L, big.activeStage!!.goal.id)
        assertEquals("Kuniga ~163044 so'm kerak", big.stages[0].perDayHint)
    }

    @Test
    fun finishingAStageHandsOverToTheNext() {
        val tree = GoalTree.build(listOf(house, save.copy(doneAt = today), research), emptyMap(), emptyMap(), emptyMap(), today)
        val big = tree.single()
        assertEquals(3L, big.workTarget!!.goal.id)
        assertEquals(1, big.doneStages)
        assertEquals(0.5f, big.fraction, 0.001f)
    }

    @Test
    fun goalWithoutStagesIsItsOwnWorkTarget_andDoneGoalHasNone() {
        val solo = Goal(id = 9, title = "IELTS 7.0", area = LifeArea.TALIM)
        val tree = GoalTree.build(listOf(solo), mapOf(9L to (3 to 6)), emptyMap(), emptyMap(), today)
        assertEquals(0.5f, tree.single().fraction, 0.001f)
        assertEquals(9L, tree.single().workTarget!!.goal.id)
        val done = GoalTree.build(listOf(solo.copy(doneAt = today)), emptyMap(), emptyMap(), emptyMap(), today)
        assertNull(done.single().workTarget)
    }

    @Test
    fun areasCountOnlyActiveBigGoals() {
        val tree = GoalTree.build(listOf(house, save, research, Goal(id = 20, title = "Tong namozi", area = LifeArea.DIN, doneAt = today)),
            emptyMap(), emptyMap(), emptyMap(), today)
        val areas = GoalTree.areas(tree).associateBy { it.area }
        assertEquals(1, areas[LifeArea.MOLIYA]!!.goals)
        assertEquals(0, areas[LifeArea.DIN]!!.goals)
    }
}
