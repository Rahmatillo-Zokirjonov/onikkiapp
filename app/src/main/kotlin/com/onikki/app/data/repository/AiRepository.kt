package com.onikki.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.onikki.app.data.db.AppDatabase
import com.onikki.app.data.db.entity.DebtDirection
import com.onikki.app.data.db.entity.DebtStatus
import com.onikki.app.data.db.entity.Goal
import com.onikki.app.data.db.entity.GoalKind
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.local.ApiKeyStore
import com.onikki.app.domain.ai.AiPlans
import com.onikki.app.domain.ai.CategorySuggestion
import com.onikki.app.domain.ai.ChatTurn
import com.onikki.app.domain.ai.ClaudeApiClient
import com.onikki.app.domain.ai.ClaudeResult
import com.onikki.app.domain.ai.GoalPlan
import com.onikki.app.domain.goals.GoalTree
import com.onikki.app.domain.screentime.UsageStatsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Outcome of any AI call, in the words the UI shows. */
sealed class AiOutcome<out T> {
    data class Ok<T>(val value: T) : AiOutcome<T>()
    data class Failed(val message: String) : AiOutcome<Nothing>()
    /** No key saved yet — the UI offers the key sheet instead of an error. */
    data object NoKey : AiOutcome<Nothing>()
    data object Offline : AiOutcome<Nothing>()
}

private val WEEKDAYS_UZ = listOf("dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba", "yakshanba")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

private const val STYLE = """O'zbek tilida, lotin alifbosida yoz. Markdown ishlatma (yulduzcha, #, jadval yo'q) — oddiy matn, ro'yxat uchun "• " bilan boshlanadigan qatorlar.
Summalarni "1 250 000 so'm" ko'rinishida yoz. Faqat berilgan ma'lumotga tayan; ma'lumot yo'q bo'lsa, taxmin qilma — shuni ayt."""

private const val CHAT_SYSTEM = """Sen "On ikki" — foydalanuvchining shaxsiy hayot boshqaruv ilovasidagi yordamchisan.
Har bir savol bilan birga foydalanuvchining hozirgi holati (vazifalar, odatlar, maqsadlar, moliya, ekran vaqti) yuboriladi — javobni shunga asoslab ber.
Qisqa va amaliy bo'l: odatda 2–6 jumla yoki 3–5 bandli ro'yxat. Aniq raqamlar va nomlarni keltir.
Ilova ichida biror narsani o'zing o'zgartira olmaysan; kerak bo'lsa, foydalanuvchi qaysi bo'limda nima qilishini ayt (Reja → Maqsadlar, Moliya, va h.k.).
$STYLE"""

private const val FINANCE_SYSTEM = """Sen "On ikki" ilovasidagi shaxsiy moliya tahlilchisisan. Foydalanuvchining shu oygi moliyaviy ma'lumoti beriladi.
Quyidagi tartibda qisqa tahlil yoz (jami 150 so'zdan oshmasin):
XULOSA: <oy qanday ketayotgani, 1–2 jumla>
• <eng katta xarajat yo'nalishi va o'tgan oyga nisbatan o'zgarish>
• <budjet yoki kutilayotgan to'lovlar bo'yicha ogohlantirish, bo'lsa>
• <jamg'arma/maqsad bo'yicha holat, bo'lsa>
MASLAHAT: <keyingi 7 kun uchun 2 ta aniq, raqamli maslahat, har biri yangi qatorda "• " bilan>
$STYLE"""

private const val GOAL_SYSTEM = """Sen "On ikki" ilovasida katta maqsadlarni reja qilishga yordam beruvchi murabbiysan.
Foydalanuvchi katta maqsadini beradi. Uni ketma-ket bajariladigan 3–6 ta bosqichga bo'l:
- har bir bosqich aniq, tekshirib bo'ladigan natija bo'lsin ("Ingliz tilini o'rganish" emas, "B1 darajadagi testdan 60% olish");
- raqam bilan o'lchanadigan bo'lsa measure=NUMBER, target va unit ber (masalan 20000000 va "so'm", 300 va "so'z"); aks holda measure=TASKS, target=0, unit="";
- deadline — bugundan keyin, maqsad muddatidan oshmasin, bosqichlar ketma-ket; muddat berilmagan bo'lsa real muddat qo'y;
- tasks — shu bosqich uchun 2–4 ta birinchi, kichik (1 kunda bajariladigan) qadam; birinchi bosqichnikilar ertadan boshlab qilinadi;
- advice — bitta qisqa jumla: nimadan boshlash kerakligi.
Foydalanuvchining moliyasi va boshqa maqsadlari ham beriladi — pul bosqichlarini real daromadga moslab qo'y.
Barcha matnlar o'zbek tilida, lotin alifbosida."""

private const val NOTE_SUMMARY_SYSTEM = """Sen "On ikki" ilovasidagi qaydlarni qisqartiruvchisan. Foydalanuvchining qaydini o'qib,
eng muhim fikrlarni 2–5 ta qisqa band qilib yoz (har biri "• " bilan). Qaydda bo'lmagan narsani qo'shma. Qayd tilida (odatda o'zbekcha, lotin) yoz.
Markdown ishlatma."""

private const val NOTE_TASKS_SYSTEM = """Sen qaydlardan bajariladigan ishlarni ajratib oluvchisan. Qayddagi har bir aniq harakatni alohida vazifa qil:
- title: qisqa, fe'l bilan ("Bankka borish", "Onamga qo'ng'iroq qilish"), qayd tilida;
- date: qaydda kun aytilgan bo'lsa YYYY-MM-DD ("ertaga", "juma" kabi so'zlarni bugungi sanadan hisobla), aks holda "";
- time: vaqt aytilgan bo'lsa HH:MM, aks holda "".
Faqat haqiqiy ishlarni ol; fikr, eslatma yoki ma'lumotni vazifa qilma. Hech narsa bo'lmasa, bo'sh ro'yxat qaytar."""

private const val CATEGORY_SYSTEM = """Sen bank SMS'laridan kelgan to'lovlarni toifalaysan. Har bir tranzaksiya uchun berilgan ro'yxatdan eng mos toifani tanla
va 1–3 so'zli qisqa izoh yoz (o'zbekcha, masalan "Supermarket", "Taksi", "Telefon to'lovi", "Do'stga o'tkazma").
Do'kon nomini bilsang, izohda uning turini yoz. Bilmasang, summaga va nomga qarab eng ehtimolli toifani tanla."""

/**
 * All Claude features in one place: the key/connectivity checks, a text snapshot of the user's data
 * that gives each request its context, and the four calls (chat, month analysis, goal plan, SMS categories).
 */
class AiRepository(private val context: Context, private val db: AppDatabase) {
    private val keyStore = ApiKeyStore(context)
    private val finance = FinanceRepository(
        db.transactionDao(), db.categoryBudgetDao(), db.debtDao(), db.savingsGoalDao(), db.accountDao(), db.plannedExpenseDao()
    )
    private val habits = HabitRepository(db.habitDao(), db.habitLogDao())

    suspend fun hasKey(): Boolean = !keyStore.apiKey.first().isNullOrBlank()

    suspend fun saveKey(key: String) = keyStore.setApiKey(key.trim())

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun <T> call(block: suspend (ClaudeApiClient) -> AiOutcome<T>): AiOutcome<T> {
        val key = keyStore.apiKey.first()
        if (key.isNullOrBlank()) return AiOutcome.NoKey
        if (!hasInternet()) return AiOutcome.Offline
        return block(ClaudeApiClient(key))
    }

    // ------------------------------------------------------------ features

    /** [history] oldest first, ending with the new user question; the live snapshot is attached to that last turn. */
    suspend fun chat(history: List<ChatTurn>): AiOutcome<String> = call { client ->
        val snapshot = snapshot(Section.entries.toSet())
        val turns = history.takeLast(MAX_CHAT_TURNS).dropWhile { !it.fromUser }.toMutableList()
        val last = turns.removeAt(turns.lastIndex)
        turns += ChatTurn(true, "[Hozirgi holatim]\n$snapshot\n\n[Savolim]\n${last.text}")
        client.send(CHAT_SYSTEM, turns, maxTokens = 6000, effort = "medium").toOutcome { it.trim() }
    }

    suspend fun analyzeMonth(): AiOutcome<String> = call { client ->
        client.sendMessage(FINANCE_SYSTEM, snapshot(setOf(Section.MONEY)), maxTokens = 6000).toOutcome { it.trim() }
    }

    suspend fun planGoal(goal: Goal): AiOutcome<GoalPlan> = call { client ->
        val today = LocalDate.now()
        val message = buildString {
            appendLine("Bugun: $today")
            appendLine("Katta maqsad: ${goal.title}")
            goal.area?.let { appendLine("Soha: ${it.label}") }
            goal.why?.let { appendLine("Nima uchun: $it") }
            appendLine("Muddat: ${goal.deadline ?: "berilmagan"}")
            if (goal.kind == GoalKind.NUMBER) appendLine("O'lchov: ${goal.current}/${goal.target} ${goal.unit.orEmpty()}")
            appendLine()
            append(snapshot(setOf(Section.GOALS, Section.MONEY)))
        }
        when (val r = client.send(GOAL_SYSTEM, listOf(ChatTurn(true, message)), maxTokens = 8000, effort = "medium", jsonSchema = AiPlans.goalPlanSchema)) {
            is ClaudeResult.Error -> AiOutcome.Failed(r.message)
            is ClaudeResult.Success -> AiPlans.parseGoalPlan(r.text, today, goal.deadline)?.let { AiOutcome.Ok(it) }
                ?: AiOutcome.Failed("AI reja tuza olmadi, qayta urinib ko'ring")
        }
    }

    suspend fun suggestCategories(items: List<Transaction>, expense: List<String>, income: List<String>): AiOutcome<List<CategorySuggestion>> = call { client ->
        val batch = items.take(MAX_CATEGORY_BATCH)
        val categories = (expense + income).distinct()
        if (batch.isEmpty() || categories.isEmpty()) return@call AiOutcome.Ok(emptyList())
        val message = buildString {
            appendLine("Chiqim toifalari: ${expense.joinToString(", ")}")
            appendLine("Kirim toifalari: ${income.joinToString(", ")}")
            appendLine("Tranzaksiyalar (id | turi | summa | nomi | sana):")
            batch.forEach { tx ->
                appendLine("${tx.id} | ${if (tx.type == TransactionType.KIRIM) "kirim" else "chiqim"} | ${tx.amount} so'm | ${tx.merchant ?: "-"} | ${tx.date}")
            }
        }
        when (val r = client.send(CATEGORY_SYSTEM, listOf(ChatTurn(true, message)), maxTokens = 6000, jsonSchema = AiPlans.categorySchema(categories))) {
            is ClaudeResult.Error -> AiOutcome.Failed(r.message)
            is ClaudeResult.Success -> AiOutcome.Ok(AiPlans.parseCategories(r.text, batch.map { it.id }.toSet(), categories))
        }
    }

    suspend fun summarizeNote(title: String, content: String): AiOutcome<String> = call { client ->
        client.sendMessage(NOTE_SUMMARY_SYSTEM, "Sarlavha: ${title.ifBlank { "-" }}\n\n$content", maxTokens = 4000).toOutcome { it.trim() }
    }

    suspend fun extractTasks(title: String, content: String): AiOutcome<List<com.onikki.app.domain.ai.ExtractedTask>> = call { client ->
        val today = LocalDate.now()
        val message = "Bugun: $today (${WEEKDAYS_UZ[today.dayOfWeek.value - 1]})\nSarlavha: ${title.ifBlank { "-" }}\n\n$content"
        when (val r = client.send(NOTE_TASKS_SYSTEM, listOf(ChatTurn(true, message)), maxTokens = 5000, jsonSchema = AiPlans.noteTasksSchema)) {
            is ClaudeResult.Error -> AiOutcome.Failed(r.message)
            is ClaudeResult.Success -> AiOutcome.Ok(AiPlans.parseNoteTasks(r.text, today))
        }
    }

    private inline fun <T> ClaudeResult.toOutcome(map: (String) -> T): AiOutcome<T> = when (this) {
        is ClaudeResult.Success -> AiOutcome.Ok(map(text))
        is ClaudeResult.Error -> AiOutcome.Failed(message)
    }

    // ------------------------------------------------------------ snapshot

    enum class Section { PLAN, HABITS, GOALS, MONEY, SCREEN }

    /** Plain-text picture of the user's current state, read fresh from the database. */
    suspend fun snapshot(sections: Set<Section>): String = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        buildString {
            appendLine("Hozir: $today (${WEEKDAYS_UZ[today.dayOfWeek.value - 1]}), soat ${now.format(TIME)}")
            if (Section.PLAN in sections) plan(today)
            if (Section.HABITS in sections) habits(today)
            if (Section.GOALS in sections) goals(today)
            if (Section.MONEY in sections) money(today)
            if (Section.SCREEN in sections) screen(today)
        }.trim()
    }

    private suspend fun StringBuilder.plan(today: LocalDate) {
        val goalLabels = db.goalDao().observeAll().first().associate { it.id to it.title }
        val tasks = db.taskDao().observeByDate(today).first()
        appendLine("\nBUGUNGI VAZIFALAR (${tasks.count { it.isCompleted }}/${tasks.size} bajarildi):")
        if (tasks.isEmpty()) appendLine("• yo'q")
        tasks.take(20).forEach { t ->
            val goal = t.goalId?.let(goalLabels::get)?.let { " [maqsad: $it]" }.orEmpty()
            appendLine("• ${if (t.isCompleted) "✓" else "○"} ${t.time?.format(TIME)?.plus(" ").orEmpty()}${t.title}$goal")
        }
        val overdue = db.taskDao().observeOverdue(today).first()
        if (overdue.isNotEmpty()) appendLine("Kechikkan vazifalar: ${overdue.size} ta — ${overdue.take(5).joinToString("; ") { it.title }}")
        val tomorrow = db.taskDao().observeByDate(today.plusDays(1)).first()
        if (tomorrow.isNotEmpty()) appendLine("Ertaga: ${tomorrow.size} ta — ${tomorrow.take(8).joinToString("; ") { it.title }}")
    }

    private suspend fun StringBuilder.habits(today: LocalDate) {
        val stats = habits.observeStats(today).first()
        if (stats.isEmpty()) return
        appendLine("\nODATLAR:")
        stats.forEach { h ->
            val todayState = when {
                !h.isActiveToday -> "bugun rejada yo'q"
                h.isDoneToday -> "bugun bajarildi"
                else -> "bugun hali ${h.todayCount}/${h.target}"
            }
            appendLine("• ${h.habit.icon} ${h.habit.name}: $todayState, ketma-ket ${h.currentStreak} kun, 30 kunda ${(h.completionRate * 100).toInt()}%")
        }
    }

    private suspend fun StringBuilder.goals(today: LocalDate) {
        val goals = db.goalDao().observeAll().first()
        if (goals.isEmpty()) {
            appendLine("\nMAQSADLAR: hali qo'yilmagan")
            return
        }
        val counts = db.goalDao().observeTaskCounts().first().associate { it.goalId to (it.done to it.total) }
        val savings = db.savingsGoalDao().observeAll().first().associate { it.id to it.currentAmount }
        val tree = GoalTree.build(goals, counts, emptyMap(), savings, today)
        val active = tree.filter { !it.isDone }
        appendLine("\nMAQSADLAR (${active.size} ta faol, ${tree.size - active.size} ta erishilgan):")
        active.forEach { g ->
            val deadline = g.daysLeft?.let { if (it < 0) ", muddat ${-it} kun o'tgan" else ", $it kun qoldi" }.orEmpty()
            appendLine("• ${g.goal.title} (${g.goal.area?.label ?: "soha yo'q"}) — ${g.percent}%$deadline")
            if (g.stages.isNotEmpty()) appendLine("  bosqichlar: ${g.doneStages}/${g.stages.size} bajarilgan")
            g.activeStage?.let { s ->
                val measure = if (s.goal.kind == GoalKind.NUMBER) "${s.current}/${s.goal.target} ${s.goal.unit.orEmpty()}" else "${s.doneTasks}/${s.totalTasks} vazifa"
                appendLine("  hozirgi bosqich: ${s.goal.title} — ${s.percent}% ($measure)${s.perDayHint?.let { h -> ", $h" }.orEmpty()}")
            }
        }
    }

    private suspend fun StringBuilder.money(today: LocalDate) {
        val monthStart = today.withDayOfMonth(1)
        val prevStart = monthStart.minusMonths(1)
        val tx = db.transactionDao().observeSince(prevStart).first()
        val thisMonth = tx.filter { !it.date.isBefore(monthStart) }
        val lastMonth = tx.filter { it.date.isBefore(monthStart) }
        fun List<Transaction>.sum(type: TransactionType) = filter { it.type == type }.sumOf { it.amount }
        appendLine("\nMOLIYA (${monthStart}–$today, oyning ${today.dayOfMonth}/${today.lengthOfMonth()}-kuni):")
        appendLine("Kirim: ${thisMonth.sum(TransactionType.KIRIM)} so'm, chiqim: ${thisMonth.sum(TransactionType.CHIQIM)} so'm")
        appendLine("O'tgan oy jami: kirim ${lastMonth.sum(TransactionType.KIRIM)}, chiqim ${lastMonth.sum(TransactionType.CHIQIM)} so'm")

        val byCategory = db.transactionDao().observeExpenseByCategory(monthStart, today).first()
        val lastByCategory = lastMonth.filter { it.type == TransactionType.CHIQIM }.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }
        if (byCategory.isNotEmpty()) {
            appendLine("Chiqim toifalar bo'yicha (bu oy / o'tgan oy):")
            byCategory.forEach { appendLine("• ${it.category}: ${it.total} / ${lastByCategory[it.category] ?: 0}") }
        }
        val spots = thisMonth.filter { it.type == TransactionType.CHIQIM }
            .groupBy { (it.note ?: it.merchant)?.trim()?.lowercase() }
            .filterKeys { !it.isNullOrBlank() }
            .map { (name, list) -> Triple(name!!, list.size, list.sumOf { it.amount }) }
            .sortedByDescending { it.third }
            .take(8)
        if (spots.isNotEmpty()) appendLine("Eng ko'p pul ketgan joylar/izohlar: ${spots.joinToString("; ") { "${it.first} (${it.second} marta, ${it.third})" }}")

        val budgets = finance.observeBudgetProgress(monthStart, today).first().filter { it.budget.monthlyLimit > 0 }
        if (budgets.isNotEmpty()) appendLine("Oylik budjetlar: ${budgets.joinToString("; ") { "${it.budget.category} ${it.spent}/${it.budget.monthlyLimit}" }}")
        val balances = finance.observeAccountBalances().first()
        if (balances.isNotEmpty()) appendLine("Hamyonlar: ${balances.joinToString("; ") { "${it.account.name} ${it.balance}" }} (jami ${balances.sumOf { it.balance }})")

        val planned = db.plannedExpenseDao().observeAll().first().filter { it.paidDate == null && !it.dueDate.isAfter(today.plusDays(21)) }.sortedBy { it.dueDate }
        if (planned.isNotEmpty()) {
            appendLine("Kutilayotgan to'lov/tushumlar (21 kun ichida):")
            planned.take(10).forEach { appendLine("• ${it.dueDate}: ${it.title} ${if (it.isIncome) "+" else "-"}${it.amount}") }
        }
        val savings = db.savingsGoalDao().observeAll().first()
        if (savings.isNotEmpty()) appendLine("Jamg'armalar: ${savings.joinToString("; ") { "${it.name} ${it.currentAmount}/${it.targetAmount}${it.deadline?.let { d -> " ($d gacha)" }.orEmpty()}" }}")
        val debts = db.debtDao().observeAll().first().filter { it.status == DebtStatus.OCHIQ }
        if (debts.isNotEmpty()) {
            val mine = debts.filter { it.direction == DebtDirection.MEN_QARZDORMAN }.sumOf { it.amount }
            val theirs = debts.filter { it.direction == DebtDirection.MENGA_QARZDOR }.sumOf { it.amount }
            appendLine("Qarzlar: men qarzdorman $mine, menga qarzdor $theirs so'm")
        }
        val unnoted = db.smsImportDao().observeUnnoted().first().size
        if (unnoted > 0) appendLine("Izoh yozilmagan SMS to'lovlar: $unnoted ta")
    }

    private fun StringBuilder.screen(today: LocalDate) {
        val provider = UsageStatsProvider(context)
        if (!provider.hasUsageAccess()) return
        val usage = provider.timeline(today).totalMsByApp.filter { it.value >= 60_000L && it.key != context.packageName }
        if (usage.isEmpty()) return
        val pm = context.packageManager
        fun label(pkg: String) = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
        appendLine("\nEKRAN VAQTI BUGUN: jami ${usage.values.sum() / 60_000} daqiqa")
        usage.entries.sortedByDescending { it.value }.take(6).forEach { appendLine("• ${label(it.key)}: ${it.value / 60_000} daq") }
    }

    companion object {
        /** Older turns are dropped so a long chat doesn't grow the bill without bound. */
        private const val MAX_CHAT_TURNS = 16
        private const val MAX_CATEGORY_BATCH = 40
    }
}
