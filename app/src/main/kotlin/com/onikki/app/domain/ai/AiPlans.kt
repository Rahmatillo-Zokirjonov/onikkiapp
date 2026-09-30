package com.onikki.app.domain.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One stage Claude proposes for a big goal; the user reviews it before anything is saved. */
data class ProposedStage(
    val title: String,
    /** true: measured by a number ([target] [unit]); false: by its linked tasks. */
    val isNumber: Boolean,
    val target: Long,
    val unit: String?,
    val deadline: LocalDate?,
    /** First concrete steps; only the first stage's are added as tasks. */
    val tasks: List<String>
)

data class GoalPlan(val stages: List<ProposedStage>, val advice: String?)

/** A task Claude found in a note; [date] null = no specific day (the user picks). */
data class ExtractedTask(val title: String, val date: LocalDate?, val time: java.time.LocalTime?)

/** A suggested category + note for one bank-SMS transaction. */
data class CategorySuggestion(val transactionId: Long, val category: String, val note: String)

/**
 * Structured-output schemas and their parsers. Schemas follow the structured-outputs subset
 * (every object `additionalProperties: false` with all fields required; no min/max constraints),
 * so limits like "3–6 stages" live in the prompt and are enforced again here when parsing.
 */
object AiPlans {
    private const val MAX_STAGES = 7
    private const val MAX_TASKS = 5

    private fun obj(vararg props: Pair<String, JSONObject>): JSONObject {
        val properties = JSONObject()
        props.forEach { (name, schema) -> properties.put(name, schema) }
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(props.map { it.first }))
            .put("additionalProperties", false)
    }

    private fun type(t: String) = JSONObject().put("type", t)
    private fun arrayOf(items: JSONObject) = JSONObject().put("type", "array").put("items", items)

    val goalPlanSchema: JSONObject
        get() = obj(
            "stages" to arrayOf(
                obj(
                    "title" to type("string"),
                    "measure" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("TASKS", "NUMBER"))),
                    "target" to type("integer"),
                    "unit" to type("string"),
                    "deadline" to JSONObject().put("type", "string").put("format", "date"),
                    "tasks" to arrayOf(type("string"))
                )
            ),
            "advice" to type("string")
        )

    val noteTasksSchema: JSONObject
        get() = obj(
            "tasks" to arrayOf(
                obj(
                    "title" to type("string"),
                    "date" to type("string"),
                    "time" to type("string")
                )
            )
        )

    /** Tasks from a note; past dates are dropped to "no date", bad times ignored, at most 15. */
    fun parseNoteTasks(json: String, today: LocalDate): List<ExtractedTask> = try {
        val tasks = JSONObject(extractJson(json)).optJSONArray("tasks") ?: JSONArray()
        (0 until tasks.length()).mapNotNull { i ->
            val t = tasks.optJSONObject(i) ?: return@mapNotNull null
            val title = t.optString("title").trim()
            if (title.isEmpty()) return@mapNotNull null
            val time = runCatching { java.time.LocalTime.parse(t.optString("time").trim().take(5)) }.getOrNull()
            ExtractedTask(title.take(120), parseDate(t.optString("date"))?.takeUnless { it.isBefore(today) }, time)
        }.distinctBy { it.title.lowercase() }.take(15)
    } catch (e: JSONException) {
        emptyList()
    }

    fun categorySchema(categories: List<String>): JSONObject = obj(
        "items" to arrayOf(
            obj(
                "id" to type("integer"),
                "category" to JSONObject().put("type", "string").put("enum", JSONArray(categories)),
                "note" to type("string")
            )
        )
    )

    /**
     * Stages in order; deadlines that are missing, in the past, or beyond the goal's own deadline are
     * dropped/clamped rather than trusted. Null when the reply isn't usable at all.
     */
    fun parseGoalPlan(json: String, today: LocalDate, goalDeadline: LocalDate?): GoalPlan? = try {
        val root = JSONObject(extractJson(json))
        val stagesJson = root.optJSONArray("stages") ?: JSONArray()
        val stages = (0 until stagesJson.length()).mapNotNull { i ->
            val s = stagesJson.optJSONObject(i) ?: return@mapNotNull null
            val title = s.optString("title").trim()
            if (title.isEmpty()) return@mapNotNull null
            val isNumber = s.optString("measure") == "NUMBER" && s.optLong("target") > 0
            val deadline = parseDate(s.optString("deadline"))?.let { d ->
                when {
                    d.isBefore(today) -> null
                    goalDeadline != null && d.isAfter(goalDeadline) -> goalDeadline
                    else -> d
                }
            }
            val tasksJson = s.optJSONArray("tasks") ?: JSONArray()
            ProposedStage(
                title = title.take(120),
                isNumber = isNumber,
                target = if (isNumber) s.optLong("target") else 0,
                unit = s.optString("unit").trim().take(12).ifBlank { null }.takeIf { isNumber },
                deadline = deadline,
                tasks = (0 until tasksJson.length()).map { tasksJson.optString(it).trim() }.filter { it.isNotEmpty() }.take(MAX_TASKS)
            )
        }.take(MAX_STAGES)
        if (stages.isEmpty()) null else GoalPlan(stages, root.optString("advice").trim().ifBlank { null })
    } catch (e: JSONException) {
        null
    }

    /** Only suggestions for transactions that were asked about, with a known category. */
    fun parseCategories(json: String, askedIds: Set<Long>, categories: List<String>): List<CategorySuggestion> = try {
        val items = JSONObject(extractJson(json)).optJSONArray("items") ?: JSONArray()
        (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optLong("id", -1)
            val category = categories.firstOrNull { it.equals(item.optString("category").trim(), ignoreCase = true) }
            if (id !in askedIds || category == null) null
            else CategorySuggestion(id, category, item.optString("note").trim().take(60))
        }.distinctBy { it.transactionId }
    } catch (e: JSONException) {
        emptyList()
    }

    private fun parseDate(text: String): LocalDate? = try {
        if (text.isBlank()) null else LocalDate.parse(text.trim().take(10))
    } catch (e: DateTimeParseException) {
        null
    }

    /** Structured output is already bare JSON; this also tolerates a ```json fence or stray text around it. */
    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }
}
