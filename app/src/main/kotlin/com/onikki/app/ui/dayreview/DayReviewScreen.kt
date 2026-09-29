package com.onikki.app.ui.dayreview

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.onikki.app.ui.util.weekdayAbbrUz
import com.onikki.app.ui.util.formatSom
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.data.repository.moodLabel
import com.onikki.app.data.repository.MOOD_EMOJI
import com.onikki.app.domain.habits.HabitStats
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.DailyReview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.local.ApiKeyStore
import com.onikki.app.data.repository.AiAnswerStatus
import com.onikki.app.data.repository.AiInsightStatus
import com.onikki.app.data.repository.DayReviewRepository
import com.onikki.app.data.repository.DayStats
import com.onikki.app.data.repository.FinanceRepository
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.data.repository.Recommendation
import com.onikki.app.ui.components.CircularProgressRing
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatFullDateUz
import java.time.LocalDate

@Composable
fun DayReviewRoute(onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val repository = remember {
        val db = app.database
        DayReviewRepository(
            context = app,
            taskDao = db.taskDao(),
            transactionDao = db.transactionDao(),
            habitRepository = HabitRepository(db.habitDao(), db.habitLogDao()),
            financeRepository = FinanceRepository(
                db.transactionDao(), db.categoryBudgetDao(), db.debtDao(), db.savingsGoalDao(),
                db.accountDao(), db.plannedExpenseDao()
            ),
            dailyReviewDao = db.dailyReviewDao(),
            apiKeyStore = ApiKeyStore(app)
        )
    }
    val apiKeyStore = remember { ApiKeyStore(app) }
    val viewModel: DayReviewViewModel = viewModel(factory = DayReviewViewModel.factory(repository, apiKeyStore))
    val state by viewModel.uiState.collectAsState()
    DayReviewScreen(state = state, viewModel = viewModel, onBack = onBack)
}

@Composable
fun DayReviewScreen(state: DayReviewUiState, viewModel: DayReviewViewModel, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val day = state.day
    val ai = state.ai
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "←",
                color = colors.text.muted(0.6f),
                fontSize = 18.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onBack).padding(4.dp)
            )
            Column {
                Text(text = formatFullDateUz(state.today), color = colors.text.muted(0.45f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                Text(text = "Kun yakuni", color = colors.text, style = OnIkkiType.screenTitle, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (day == null) return@Column

        ScoreCard(stats = day.stats, aiStatus = ai.aiStatus, review = day.review)
        StatsCard(stats = day.stats)

        if (state.showForm) {
            UnfinishedCard(
                tasks = day.tasks.filter { !it.isCompleted },
                onToggle = viewModel::toggleTask,
                onMoveAll = viewModel::moveUnfinishedToTomorrow
            )
            PendingHabitsCard(habits = day.pendingHabits, onTap = viewModel::tapHabit)
            ReflectionForm(
                existing = day.review,
                isEditing = ai.isEditing,
                onFinish = viewModel::finishDay,
                onCancel = viewModel::cancelEditing
            )
        } else {
            FinishedCard(review = day.review!!, onEdit = viewModel::startEditing)
            when {
                !ai.hasApiKey -> ApiKeyPromptCard(onOpenSheet = viewModel::openApiKeySheet)
                ai.isLoadingAi -> LoadingAiCard()
                !ai.hasInternet && ai.aiStatus !is AiInsightStatus.Success -> OfflineCard(onRetry = viewModel::retryAi)
                ai.aiStatus is AiInsightStatus.Failed -> AiErrorCard(message = ai.aiStatus.message, onRetry = viewModel::retryAi)
                ai.aiStatus is AiInsightStatus.Success -> RecommendationsSection(ai.aiStatus.insight.recommendations)
                else -> OnIkkiButton(text = "AI tahlilni olish", onClick = viewModel::retryAi, modifier = Modifier.fillMaxWidth())
            }
            if (ai.hasApiKey && ai.aiStatus is AiInsightStatus.Success) {
                FollowUpSection(isAsking = ai.isAskingFollowUp, answer = ai.followUpAnswer, onAsk = viewModel::askFollowUp)
            }
        }

        WeekStrip(today = state.today, week = day.week, onOpen = viewModel::openPastDay)
    }

    if (ai.isApiKeySheetOpen) {
        ApiKeySheet(onDismiss = viewModel::dismissApiKeySheet, onSave = viewModel::saveApiKey)
    }
    ai.viewingPast?.let { past -> PastDaySheet(review = past, parse = viewModel::parseInsight, onDismiss = viewModel::closePastDay) }
}

// ---------------------------------------------------------------- Bajarilmaganlar

@Composable
private fun UnfinishedCard(tasks: List<Task>, onToggle: (Task) -> Unit, onMoveAll: () -> Unit) {
    if (tasks.isEmpty()) return
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Bajarilmay qolgan · ${tasks.size}",
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Ertaga o'tkazish",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onMoveAll).padding(4.dp)
            )
        }
        tasks.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle(task) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(20.dp).border(BorderStroke(1.5.dp, colors.divider), RoundedCornerShape(6.dp))
                )
                Text(text = task.title, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                task.time?.let {
                    Text(text = "%02d:%02d".format(it.hour, it.minute), color = colors.text.muted(0.45f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        }
        Text(
            text = "Belgilash uchun bosing — yoki hammasini ertangi rejaga o'tkazing.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun PendingHabitsCard(habits: List<HabitStats>, onTap: (HabitStats) -> Unit) {
    if (habits.isEmpty()) return
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Text(text = "Belgilanmagan odatlar", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            habits.forEach { habit ->
                Text(
                    text = "${habit.habit.icon} ${habit.habit.name}" + if (habit.target > 1) " ${habit.todayCount}/${habit.target}" else "",
                    color = colors.text,
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(50))
                        .clickable { onTap(habit) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        Text(text = "Bajargan bo'lsangiz — bosing.", color = colors.text.muted(0.45f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
    }
}

// ---------------------------------------------------------------- Xulosa va yakunlash

@Composable
private fun ReflectionForm(
    existing: DailyReview?,
    isEditing: Boolean,
    onFinish: (mood: Int?, reflection: String, tomorrow: List<String>) -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    var mood by rememberSaveable { mutableStateOf(existing?.mood) }
    var reflection by rememberSaveable { mutableStateOf(existing?.reflection.orEmpty()) }
    var p1 by rememberSaveable { mutableStateOf("") }
    var p2 by rememberSaveable { mutableStateOf("") }
    var p3 by rememberSaveable { mutableStateOf("") }

    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        Text(text = "Kayfiyatingiz qanday?", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MOOD_EMOJI.forEachIndexed { index, emoji ->
                val value = index + 1
                val selected = mood == value
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(if (selected) colors.accent800 else colors.background, CircleShape)
                        .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.divider), CircleShape)
                        .clickable { mood = if (selected) null else value },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 24.sp)
                }
            }
        }
        moodLabel(mood)?.let { Text(text = it, color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily) }

        OutlinedTextField(
            value = reflection,
            onValueChange = { reflection = it },
            label = { Text("Bugun nima yaxshi bo'ldi, nimani o'rgandingiz?") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
        )

        Text(text = "Ertaga eng muhim 3 ta ish", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
        listOf(p1 to { v: String -> p1 = v }, p2 to { v: String -> p2 = v }, p3 to { v: String -> p3 = v }).forEachIndexed { i, (value, set) ->
            OutlinedTextField(
                value = value,
                onValueChange = set,
                label = { Text("${i + 1}.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = "Yozganlaringiz ertangi Kunlik rejaga vazifa bo'lib tushadi.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
        OnIkkiButton(
            text = if (isEditing) "Saqlash" else "Kunni yakunlash",
            onClick = { onFinish(mood, reflection, listOf(p1, p2, p3)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (isEditing) {
            OnIkkiButton(text = "Bekor", onClick = onCancel, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FinishedCard(review: DailyReview, onEdit: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), borderColor = colors.accent700, gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✓ Kun yakunlandi", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
            Text(
                text = "Tahrirlash",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onEdit).padding(4.dp)
            )
        }
        review.mood?.let { m ->
            Text(text = "${MOOD_EMOJI[m - 1]} ${moodLabel(m)}", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        }
        review.reflection?.let { Text(text = it, color = colors.text.muted(0.75f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
    }
}

// ---------------------------------------------------------------- Hafta

@Composable
private fun WeekStrip(today: LocalDate, week: List<DailyReview>, onOpen: (DailyReview) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val byDate = week.associateBy { it.date }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Oxirgi 7 kun", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (6 downTo 0).map { today.minusDays(it.toLong()) }.forEach { date ->
                val review = byDate[date]
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.surface, RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, if (date == today) colors.accent700 else colors.cardBorder), RoundedCornerShape(10.dp))
                        .clickable(enabled = review != null) { review?.let(onOpen) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = weekdayAbbrUz(date.dayOfWeek), color = colors.text.muted(0.5f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
                    Text(
                        text = review?.mood?.let { MOOD_EMOJI[it - 1] } ?: if (review != null) "✓" else "·",
                        fontSize = 16.sp,
                        color = colors.text.muted(0.5f)
                    )
                    Text(
                        text = review?.score?.toString() ?: "",
                        color = colors.warmAccent,
                        fontSize = 11.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun PastDaySheet(review: DailyReview, parse: (String) -> com.onikki.app.data.repository.AiInsight, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiSheet(title = formatFullDateUz(review.date), onDismiss = onDismiss) {
        Text(
            text = listOfNotNull(
                review.score?.let { "Ball: $it" },
                "Vazifalar: ${review.completedCount}/${review.totalCount}",
                review.habitsTotal?.let { "Odatlar: ${review.habitsCompleted ?: 0}/$it" },
                review.spent?.takeIf { it > 0 }?.let { "Sarflandi: ${formatSom(it)} so'm" }
            ).joinToString(" · "),
            color = colors.text.muted(0.7f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        review.mood?.let { Text(text = "${MOOD_EMOJI[it - 1]} ${moodLabel(it)}", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily) }
        review.reflection?.let { Text(text = it, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily) }
        review.aiSummary?.let { raw ->
            val insight = parse(raw)
            Text(text = "AI: ${insight.dayLabel}", color = colors.accent, style = OnIkkiType.kicker)
            Text(text = insight.summary, color = colors.text.muted(0.75f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ScoreCard(stats: DayStats, aiStatus: AiInsightStatus, review: DailyReview?) {
    val colors = LocalOnIkkiColors.current
    val label = (aiStatus as? AiInsightStatus.Success)?.insight?.dayLabel
        ?: if (stats.score >= 80) "Yaxshi kun" else if (stats.score >= 60) "O'rtacha kun" else "Qiyin kun"
    val description = (aiStatus as? AiInsightStatus.Success)?.insight?.summary

    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressRing(
                progress = stats.score / 100f,
                trackColor = colors.neutral800,
                progressColor = colors.warmAccent,
                size = 86.dp,
                strokeWidth = 7.dp
            ) {
                Text(
                    text = stats.score.toString(),
                    color = colors.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
                if (description != null) {
                    Text(
                        text = description,
                        color = colors.text.muted(0.7f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: DayStats) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 11.dp) {
        StatRow(
            label = "Vazifalar",
            valueText = "${stats.completedTasks}/${stats.totalTasks}",
            progress = if (stats.totalTasks == 0) 0f else stats.completedTasks.toFloat() / stats.totalTasks
        )
        StatRow(
            label = "Odatlar",
            valueText = "${stats.completedHabits}/${stats.totalHabits}",
            progress = if (stats.totalHabits == 0) 0f else stats.completedHabits.toFloat() / stats.totalHabits
        )
        if (stats.spentToday > 0 || stats.earnedToday > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Pul", color = colors.text.muted(0.8f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                Text(
                    text = listOfNotNull(
                        stats.earnedToday.takeIf { it > 0 }?.let { "+ ${formatSom(it)}" },
                        stats.spentToday.takeIf { it > 0 }?.let { "− ${formatSom(it)}" }
                    ).joinToString("  ") + " so'm",
                    color = colors.text.muted(0.55f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        if (stats.worstBudgetPercent != null) {
            StatRow(
                label = "Budjet",
                valueText = "limitning ${stats.worstBudgetPercent}%",
                progress = (stats.worstBudgetPercent / 100f).coerceIn(0f, 1f),
                isWarning = stats.worstBudgetPercent >= 90
            )
        }
    }
}

@Composable
private fun StatRow(label: String, valueText: String, progress: Float, isWarning: Boolean = false) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, color = colors.text.muted(0.8f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            Text(text = valueText, color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        LinearProgressTrack(
            progress = progress,
            trackColor = colors.neutral800,
            progressColor = if (isWarning) colors.warmAccent else colors.accent
        )
    }
}

@Composable
private fun ApiKeyPromptCard(onOpenSheet: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        Text(text = "AI tahlil uchun", color = colors.accent, style = OnIkkiType.kicker)
        Text(
            text = "Claude API kaliti kerak",
            color = colors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily
        )
        Text(
            text = "Kun saqlandi. AI tahlil va tavsiyalar uchun Claude API kalitini kiriting.",
            color = colors.text.muted(0.65f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        OnIkkiButton(text = "Kalit kiritish", onClick = onOpenSheet, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OfflineCard(onRetry: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
        Text(
            text = "Internet yo'q — AI tahlil olinmadi. Kun baribir saqlandi.",
            color = colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        Text(
            text = "Internet qaytganda qayta urinib ko'ring.",
            color = colors.text.muted(0.6f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
        OnIkkiButton(text = "Qayta urinish", onClick = onRetry)
    }
}

@Composable
private fun LoadingAiCard() {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
            Text(text = "AI tahlil tayyorlanmoqda…", color = colors.text.muted(0.7f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        }
    }
}

@Composable
private fun AiErrorCard(message: String, onRetry: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Text(text = "AI tahlil olinmadi", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
        Text(text = message, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        OnIkkiButton(text = "Qayta urinish", onClick = onRetry)
    }
}

@Composable
private fun RecommendationsSection(recommendations: List<Recommendation>) {
    if (recommendations.isEmpty()) return
    val colors = LocalOnIkkiColors.current
    Column {
        Text(
            text = "AI tavsiyalari",
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            recommendations.forEach { rec ->
                OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp, borderColor = colors.accent800) {
                    Text(text = rec.kicker.uppercase(), color = colors.accent, style = OnIkkiType.kicker)
                    Text(text = rec.text, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        }
    }
}

@Composable
private fun FollowUpSection(isAsking: Boolean, answer: AiAnswerStatus?, onAsk: (String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var question by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (answer != null) {
            OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
                val text = when (answer) {
                    is AiAnswerStatus.Success -> answer.text
                    is AiAnswerStatus.Failed -> "Javob olinmadi: ${answer.message}"
                    AiAnswerStatus.NotAttempted -> "Internet yo'q."
                }
                Text(text = text, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text("AI'dan so'ra: \"nega kechikdim?\"", fontSize = 13.sp) },
                singleLine = true,
                enabled = !isAsking,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onAsk(question)
                    question = ""
                }),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colors.accent, CircleShape)
                    .clickable(enabled = !isAsking) {
                        onAsk(question)
                        question = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isAsking) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.onAccent)
                } else {
                    Text(text = "→", color = colors.onAccent, fontSize = 16.sp)
                }
            }
        }
    }
}
