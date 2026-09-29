package com.onikki.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.onikki.app.data.db.dao.DailyReviewDao
import com.onikki.app.data.db.dao.TaskDao
import com.onikki.app.data.db.entity.DailyReview
import com.onikki.app.data.local.ApiKeyStore
import com.onikki.app.domain.ai.ClaudeApiClient
import com.onikki.app.domain.ai.ClaudeResult
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
    val score: Int
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

private const val PROGRESS_WEIGHT_TASKS = 0.4f
private const val PROGRESS_WEIGHT_HABITS = 0.3f
private const val PROGRESS_WEIGHT_BUDGET = 0.3f

private const val SYSTEM_PROMPT = """Sen "On ikki" shaxsiy hayot boshqaruv ilovasidagi kun yakuni tahlilchisisan.
Foydalanuvchi bugungi statistikasini yuboradi. Shu asosida quyidagi ANIQ formatda javob ber, boshqa hech narsa yozma:

BAHO: <kunni 2-3 so'z bilan baholash, masalan "Yaxshi kun">
TAHLIL: <statistikaga asoslangan 1-2 jumlali qisqa sharh>
TAVSIYA1: [Kategoriya] <bitta aniq, amaliy tavsiya>
TAVSIYA2: [Kategoriya] <yana bitta aniq, amaliy tavsiya>

Kategoriya — "Vaqt", "Moliya", "Odat" yoki shunga o'xshash bitta so'z. O'zbek tilida, lotin alifbosida, do'stona va qisqa yoz."""

private const val FOLLOWUP_SYSTEM_PROMPT = """Sen "On ikki" shaxsiy hayot boshqaruv ilovasidagi yordamchisan.
Foydalanuvchiga bugungi statistikasi berilgan, u shu haqda savol beradi. 2-3 jumladan oshmaydigan, aniq va qisqa javob ber.
O'zbek tilida, lotin alifbosida yoz."""

class DayReviewRepository(
    private val context: Context,
    private val taskDao: TaskDao,
    private val habitRepository: HabitRepository,
    private val financeRepository: FinanceRepository,
    private val dailyReviewDao: DailyReviewDao,
    private val apiKeyStore: ApiKeyStore
) {
    suspend fun computeStats(today: LocalDate): DayStats {
        val totalTasks = taskDao.observeTotalCount(today).first()
        val completedTasks = taskDao.observeCompletedCount(today).first()

        // Only habits scheduled today count toward the day; one done on an off-day is a bonus.
        val habits = habitRepository.observeStats(today).first()
        val totalHabits = habits.count { it.isActiveToday }
        val completedHabits = habits.count { it.isActiveToday && it.isDoneToday }

        val monthStart = today.withDayOfMonth(1)
        val budgets = financeRepository.observeBudgetProgress(monthStart, today).first()
        val worstBudgetPercent = budgets
            .filter { it.budget.monthlyLimit > 0 }
            .maxOfOrNull { (it.spent * 100 / it.budget.monthlyLimit).toInt() }

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

        return DayStats(completedTasks, totalTasks, completedHabits, totalHabits, worstBudgetPercent, score)
    }

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

    /**
     * Saves today's stats immediately (works offline) — this alone counts as "kun yakunlandi"
     * for TZ 3.7's unblock rule, without waiting on the AI part. Preserves any AI summary
     * already fetched earlier today instead of clobbering it back to null.
     */
    suspend fun ensureDaySaved(today: LocalDate, stats: DayStats): AiInsight? {
        val existing = dailyReviewDao.findByDate(today)
        dailyReviewDao.upsert(
            DailyReview(
                date = today,
                completedCount = stats.completedTasks,
                totalCount = stats.totalTasks,
                aiSummary = existing?.aiSummary,
                synced = existing?.synced ?: false
            )
        )
        return existing?.aiSummary?.let(::parseInsight)
    }

    suspend fun saveAiSummary(today: LocalDate, stats: DayStats, rawText: String) {
        dailyReviewDao.upsert(
            DailyReview(
                date = today,
                completedCount = stats.completedTasks,
                totalCount = stats.totalTasks,
                aiSummary = rawText,
                synced = true
            )
        )
    }

    suspend fun fetchAiInsight(stats: DayStats): AiInsightStatus {
        val apiKey = apiKeyStore.apiKey.first()
        if (apiKey.isNullOrBlank() || !hasInternet()) return AiInsightStatus.NotAttempted
        return when (val result = ClaudeApiClient(apiKey).sendMessage(SYSTEM_PROMPT, buildStatsMessage(stats))) {
            is ClaudeResult.Success -> AiInsightStatus.Success(parseInsight(result.text), result.text)
            is ClaudeResult.Error -> AiInsightStatus.Failed(result.message)
        }
    }

    suspend fun askFollowUp(stats: DayStats, question: String): AiAnswerStatus {
        val apiKey = apiKeyStore.apiKey.first()
        if (apiKey.isNullOrBlank() || !hasInternet()) return AiAnswerStatus.NotAttempted
        val message = "${buildStatsMessage(stats)}\n\nSavol: $question"
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

    private fun buildStatsMessage(stats: DayStats): String = buildString {
        appendLine("Bugungi statistika:")
        appendLine("- Vazifalar: ${stats.completedTasks}/${stats.totalTasks} bajarildi")
        appendLine("- Odatlar: ${stats.completedHabits}/${stats.totalHabits} bajarildi")
        if (stats.worstBudgetPercent != null) {
            appendLine("- Budjet: eng yuqori kategoriya limitning ${stats.worstBudgetPercent}% ishlatilgan")
        }
        appendLine("- Umumiy ball: ${stats.score}/100")
    }
}
