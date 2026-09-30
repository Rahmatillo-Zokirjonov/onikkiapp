package com.onikki.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.onikki.app.data.db.dao.DailyReviewDao
import com.onikki.app.data.db.dao.TaskDao
import com.onikki.app.data.db.dao.TransactionDao
import com.onikki.app.data.db.entity.DailyReview
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.local.ApiKeyStore
import com.onikki.app.domain.ai.ClaudeApiClient
import com.onikki.app.domain.ai.ClaudeResult
import com.onikki.app.domain.habits.HabitStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/** Locally computed, always-available offline (TZ: "faqat hisoblangan statistika"). */
data class DayStats(
    val completedTasks: Int,
    val totalTasks: Int,
    val completedHabits: Int,
    val totalHabits: Int,
    /** Highest usage % among configured category budgets this month; null if none configured. */
    val worstBudgetPercent: Int?,
    /** Simple composite 0..100 — a provisional formula, not specified by the TZ. */
    val score: Int,
    val spentToday: Long = 0,
    val earnedToday: Long = 0
)

/** Everything the Kun yakuni screen shows about one day, live. */
data class DayData(
    val stats: DayStats,
    val tasks: List<Task>,
    /** Habits scheduled today that aren't done yet. */
    val pendingHabits: List<HabitStats>,
    /** Null until the user presses "Kunni yakunlash". */
    val review: DailyReview?,
    /** The last 7 days' saved reviews (today included once finished), oldest first. */
    val week: List<DailyReview>
)

data class Recommendation(val kicker: String, val text: String)
data class AiInsight(val dayLabel: String, val summary: String, val recommendations: List<Recommendation>)

sealed class AiInsightStatus {
    data object NotAttempted : AiInsightStatus()
    data class Failed(val message: String) : AiInsightStatus()
    data class Success(val insight: AiInsight, val rawText: String) : AiInsightStatus()
}

sealed class AiAnswerStatus {
    data object NotAttempted : AiAnswerStatus()
    data class Failed(val message: String) : AiAnswerStatus()
    data class Success(val text: String) : AiAnswerStatus()
}

val MOOD_EMOJI = listOf("😞", "😕", "😐", "🙂", "😄")
fun moodLabel(mood: Int?): String? = mood?.let { listOf("Yomon", "Unchalik", "O'rtacha", "Yaxshi", "Zo'r").getOrNull(it - 1) }

private const val PROGRESS_WEIGHT_TASKS = 0.4f
private const val PROGRESS_WEIGHT_HABITS = 0.3f
private const val PROGRESS_WEIGHT_BUDGET = 0.3f

private const val SYSTEM_PROMPT = """Sen "On ikki" shaxsiy hayot boshqaruv ilovasidagi kun yakuni tahlilchisisan.
Foydalanuvchi bugungi statistikasini, kayfiyatini va o'z xulosasini yuboradi. Shu asosida quyidagi ANIQ formatda javob ber, boshqa hech narsa yozma:

BAHO: <kunni 2-3 so'z bilan baholash, masalan "Yaxshi kun">
TAHLIL: <statistika va foydalanuvchi xulosasiga asoslangan 1-2 jumlali qisqa sharh>
TAVSIYA1: [Kategoriya] <ertaga uchun bitta aniq, amaliy tavsiya>
TAVSIYA2: [Kategoriya] <yana bitta aniq, amaliy tavsiya>

Kategoriya — "Vaqt", "Moliya", "Odat" yoki shunga o'xshash bitta so'z. O'zbek tilida, lotin alifbosida, do'stona va qisqa yoz."""

private const val FOLLOWUP_SYSTEM_PROMPT = """Sen "On ikki" shaxsiy hayot boshqaruv ilovasidagi yordamchisan.
Foydalanuvchiga bugungi statistikasi berilgan, u shu haqda savol beradi. 2-3 jumladan oshmaydigan, aniq va qisqa javob ber.
O'zbek tilida, lotin alifbosida yoz."""

class DayReviewRepository(
    private val context: Context,
    private val taskDao: TaskDao,
    private val transactionDao: TransactionDao,
    private val habitRepository: HabitRepository,
    private val financeRepository: FinanceRepository,
    private val dailyReviewDao: DailyReviewDao,
    private val apiKeyStore: ApiKeyStore,
    /** Adds goals (and, for questions, the rest of the user's state) to the AI context. */
    private val aiRepository: AiRepository? = null
) {
    fun observeDay(today: LocalDate): Flow<DayData> {
        val money = combine(
            financeRepository.observeBudgetProgress(today.withDayOfMonth(1), today),
            transactionDao.observeTotalByTypeBetween(TransactionType.CHIQIM, today, today),
            transactionDao.observeTotalByTypeBetween(TransactionType.KIRIM, today, today)
        ) { budgets, spent, earned ->
            val worst = budgets.filter { it.budget.monthlyLimit > 0 }.maxOfOrNull { (it.spent * 100 / it.budget.monthlyLimit).toInt() }
            Triple(worst, spent, earned)
        }
        return combine(
            taskDao.observeByDate(today),
            habitRepository.observeStats(today),
            money,
            dailyReviewDao.observeByDate(today),
            dailyReviewDao.observeBetween(today.minusDays(6), today)
        ) { tasks, habits, (worstBudget, spent, earned), review, week ->
            DayData(
                stats = computeStats(tasks, habits, worstBudget, spent, earned),
                tasks = tasks,
                pendingHabits = habits.filter { it.isActiveToday && !it.isDoneToday },
                review = review,
                week = week
            )
        }
    }

    private fun computeStats(tasks: List<Task>, habits: List<HabitStats>, worstBudgetPercent: Int?, spent: Long, earned: Long): DayStats {
        val totalTasks = tasks.size
        val completedTasks = tasks.count { it.isCompleted }
        // Only habits scheduled today count toward the day; one done on an off-day is a bonus.
        val totalHabits = habits.count { it.isActiveToday }
        val completedHabits = habits.count { it.isActiveToday && it.isDoneToday }

        val taskRate = if (totalTasks == 0) 1f else completedTasks.toFloat() / totalTasks
        val habitRate = if (totalHabits == 0) 1f else completedHabits.toFloat() / totalHabits
        val budgetHealth = when {
            worstBudgetPercent == null -> 1f
            worstBudgetPercent <= 100 -> 1f
            else -> (1f - (worstBudgetPercent - 100) / 100f).coerceIn(0f, 1f)
        }
        val score = (
            (taskRate * PROGRESS_WEIGHT_TASKS + habitRate * PROGRESS_WEIGHT_HABITS + budgetHealth * PROGRESS_WEIGHT_BUDGET) * 100
        ).roundToInt().coerceIn(0, 100)

        return DayStats(completedTasks, totalTasks, completedHabits, totalHabits, worstBudgetPercent, score, spent, earned)
    }

    /** One-shot stats for callers outside the screen. */
    suspend fun computeStats(today: LocalDate): DayStats = observeDay(today).first().stats

    fun localDayLabel(score: Int): String = when {
        score >= 80 -> "Yaxshi kun"
        score >= 60 -> "O'rtacha kun"
        else -> "Qiyin kun"
    }

    suspend fun hasApiKey(): Boolean = !apiKeyStore.apiKey.first().isNullOrBlank()

    fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun setTaskDone(task: Task, done: Boolean) = taskDao.setCompleted(task.id, done)

    suspend fun tapHabit(stats: HabitStats, today: LocalDate) = habitRepository.tapToday(stats, today)

    /** "Ertaga o'tkazish": today's unfinished tasks move to tomorrow, keeping their time. */
    suspend fun moveUnfinishedToTomorrow(tasks: List<Task>, today: LocalDate) {
        tasks.filter { !it.isCompleted }.forEach { taskDao.update(it.copy(date = today.plusDays(1))) }
    }

    /**
     * The explicit end of the day: saves the snapshot (this row is what counts as "kun yakunlandi"
     * everywhere) and turns the "tomorrow's priorities" lines into real tasks for tomorrow. Re-finishing
     * the same day updates it but keeps an AI summary already fetched.
     */
    suspend fun finishDay(today: LocalDate, stats: DayStats, mood: Int?, reflection: String, tomorrow: List<String>) {
        val existing = dailyReviewDao.findByDate(today)
        dailyReviewDao.upsert(
            DailyReview(
                date = today,
                completedCount = stats.completedTasks,
                totalCount = stats.totalTasks,
                aiSummary = existing?.aiSummary,
                synced = existing?.synced ?: false,
                score = stats.score,
                mood = mood,
                reflection = reflection.trim().ifBlank { null },
                habitsCompleted = stats.completedHabits,
                habitsTotal = stats.totalHabits,
                spent = stats.spentToday,
                finishedAt = System.currentTimeMillis()
            )
        )
        tomorrow.map { it.trim() }.filter { it.isNotEmpty() }.forEach { title ->
            taskDao.insert(Task(title = title, date = today.plusDays(1), time = null, category = TaskCategory.SHAXSIY))
        }
    }

    suspend fun saveAiSummary(today: LocalDate, rawText: String) {
        val existing = dailyReviewDao.findByDate(today) ?: return
        dailyReviewDao.upsert(existing.copy(aiSummary = rawText, synced = true))
    }

    suspend fun fetchAiInsight(stats: DayStats, review: DailyReview?, unfinished: List<String>): AiInsightStatus {
        val apiKey = apiKeyStore.apiKey.first()
        if (apiKey.isNullOrBlank() || !hasInternet()) return AiInsightStatus.NotAttempted
        val goals = aiRepository?.snapshot(setOf(AiRepository.Section.GOALS))?.let { "\n\nMaqsadlar holati (tavsiyalardan biri faol bosqichga oid bo'lsin, agar bo'lsa):\n$it" }.orEmpty()
        return when (val result = ClaudeApiClient(apiKey).sendMessage(SYSTEM_PROMPT, buildStatsMessage(stats, review, unfinished) + goals)) {
            is ClaudeResult.Success -> AiInsightStatus.Success(parseInsight(result.text), result.text)
            is ClaudeResult.Error -> AiInsightStatus.Failed(result.message)
        }
    }

    suspend fun askFollowUp(stats: DayStats, review: DailyReview?, question: String): AiAnswerStatus {
        val apiKey = apiKeyStore.apiKey.first()
        if (apiKey.isNullOrBlank() || !hasInternet()) return AiAnswerStatus.NotAttempted
        val context = aiRepository?.snapshot(AiRepository.Section.entries.toSet())?.let { "\n\nUmumiy holat:\n$it" }.orEmpty()
        val message = "${buildStatsMessage(stats, review, emptyList())}$context\n\nSavol: $question"
        return when (val result = ClaudeApiClient(apiKey).sendMessage(FOLLOWUP_SYSTEM_PROMPT, message)) {
            is ClaudeResult.Success -> AiAnswerStatus.Success(result.text.trim())
            is ClaudeResult.Error -> AiAnswerStatus.Failed(result.message)
        }
    }

    /** Re-derives the structured insight from stored raw text — same parser used right after fetching. */
    fun parseInsight(rawText: String): AiInsight {
        val lines = rawText.lines()
        fun valueAfter(prefix: String): String? =
            lines.firstOrNull { it.trim().startsWith(prefix, ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        fun recommendationFrom(prefix: String): Recommendation? {
            val raw = valueAfter(prefix) ?: return null
            val match = Regex("""^\[([^\]]+)]\s*(.*)$""").find(raw)
            return if (match != null) {
                Recommendation(match.groupValues[1].trim(), match.groupValues[2].trim())
            } else {
                Recommendation("Tavsiya", raw)
            }
        }

        val dayLabel = valueAfter("BAHO") ?: "Kun yakunlandi"
        val summary = valueAfter("TAHLIL") ?: rawText.trim()
        val recommendations = listOfNotNull(recommendationFrom("TAVSIYA1"), recommendationFrom("TAVSIYA2"))
        return AiInsight(dayLabel, summary, recommendations)
    }

    private fun buildStatsMessage(stats: DayStats, review: DailyReview?, unfinished: List<String>): String = buildString {
        appendLine("Bugungi statistika:")
        appendLine("- Vazifalar: ${stats.completedTasks}/${stats.totalTasks} bajarildi")
        if (unfinished.isNotEmpty()) appendLine("- Bajarilmay qolganlar: ${unfinished.joinToString(", ")}")
        appendLine("- Odatlar: ${stats.completedHabits}/${stats.totalHabits} bajarildi")
        if (stats.worstBudgetPercent != null) {
            appendLine("- Budjet: eng yuqori kategoriya limitning ${stats.worstBudgetPercent}% ishlatilgan")
        }
        if (stats.spentToday > 0) appendLine("- Bugun sarflandi: ${stats.spentToday} so'm")
        appendLine("- Umumiy ball: ${stats.score}/100")
        moodLabel(review?.mood)?.let { appendLine("- Kayfiyat: $it") }
        review?.reflection?.let { appendLine("- Foydalanuvchi xulosasi: $it") }
    }
}
