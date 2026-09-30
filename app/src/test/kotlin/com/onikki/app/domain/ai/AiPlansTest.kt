package com.onikki.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AiPlansTest {
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun goalPlanKeepsOrder_clampsDeadlines_andDropsBadStages() {
        val json = """
            {"stages":[
              {"title":"20 mln jamg'arish","measure":"NUMBER","target":20000000,"unit":"so'm","deadline":"2026-12-31","tasks":["Oylik 3 mln ajratish",""]},
              {"title":"  ","measure":"TASKS","target":0,"unit":"","deadline":"2027-01-10","tasks":[]},
              {"title":"Ipoteka shartlarini o'rganish","measure":"NUMBER","target":0,"unit":"x","deadline":"2029-05-01","tasks":["3 ta bankka borish"]},
              {"title":"Eski sana","measure":"TASKS","target":0,"unit":"","deadline":"2020-01-01","tasks":[]}
            ],"advice":"Avval jamg'arma."}
        """.trimIndent()
        val plan = AiPlans.parseGoalPlan(json, today, goalDeadline = LocalDate.of(2027, 12, 31))!!
        assertEquals(3, plan.stages.size)
        assertTrue(plan.stages[0].isNumber)
        assertEquals(listOf("Oylik 3 mln ajratish"), plan.stages[0].tasks)
        // NUMBER with target 0 falls back to task-measured, and its unit is dropped.
        assertFalse(plan.stages[1].isNumber)
        assertNull(plan.stages[1].unit)
        assertEquals(LocalDate.of(2027, 12, 31), plan.stages[1].deadline)
        assertNull(plan.stages[2].deadline)
        assertEquals("Avval jamg'arma.", plan.advice)
    }

    @Test
    fun goalPlanToleratesFencesAndRejectsGarbage() {
        val fenced = "```json\n{\"stages\":[{\"title\":\"A\",\"measure\":\"TASKS\",\"target\":0,\"unit\":\"\",\"deadline\":\"\",\"tasks\":[]}],\"advice\":\"\"}\n```"
        assertEquals("A", AiPlans.parseGoalPlan(fenced, today, null)!!.stages.single().title)
        assertNull(AiPlans.parseGoalPlan("kechirasiz", today, null))
        assertNull(AiPlans.parseGoalPlan("{\"stages\":[]}", today, null))
    }

    @Test
    fun categorySuggestionsOnlyForAskedIdsAndKnownCategories() {
        val json = """{"items":[
            {"id":5,"category":"oziq-ovqat","note":"Korzinka"},
            {"id":6,"category":"Kosmos","note":"?"},
            {"id":99,"category":"Transport","note":"taksi"},
            {"id":5,"category":"Transport","note":"dublikat"}
        ]}"""
        val result = AiPlans.parseCategories(json, askedIds = setOf(5L, 6L), categories = listOf("Oziq-ovqat", "Transport"))
        assertEquals(listOf(CategorySuggestion(5, "Oziq-ovqat", "Korzinka")), result)
    }
}
