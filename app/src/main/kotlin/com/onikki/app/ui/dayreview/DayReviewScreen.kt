package com.onikki.app.ui.dayreview

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
        DayReviewRepository(
            context = app,
            taskDao = app.database.taskDao(),
            habitRepository = HabitRepository(app.database.habitDao(), app.database.habitLogDao()),
            financeRepository = FinanceRepository(
                app.database.transactionDao(),
                app.database.categoryBudgetDao(),
                app.database.debtDao(),
                app.database.savingsGoalDao()
            ),
            dailyReviewDao = app.database.dailyReviewDao(),
            apiKeyStore = ApiKeyStore(app)
        )
    }
    val apiKeyStore = remember { ApiKeyStore(app) }
    val viewModel: DayReviewViewModel = viewModel(factory = DayReviewViewModel.factory(repository, apiKeyStore))
    val state by viewModel.uiState.collectAsState()
    DayReviewScreen(
        state = state,
        onBack = onBack,
        onOpenApiKeySheet = viewModel::openApiKeySheet,
        onDismissApiKeySheet = viewModel::dismissApiKeySheet,
        onSaveApiKey = viewModel::saveApiKey,
        onRetryAi = viewModel::retryAi,
        onAskFollowUp = viewModel::askFollowUp
    )
}

@Composable
fun DayReviewScreen(
    state: DayReviewUiState,
    onBack: () -> Unit = {},
    onOpenApiKeySheet: () -> Unit,
    onDismissApiKeySheet: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onRetryAi: () -> Unit,
    onAskFollowUp: (String) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 16.dp),
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
                Text(
                    text = formatFullDateUz(LocalDate.now()),
                    color = colors.text.muted(0.45f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = "Kun yakuni",
                    color = colors.text,
                    style = OnIkkiType.screenTitle,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        val stats = state.stats
        if (stats != null) {
            ScoreCard(stats = stats, aiStatus = state.aiStatus)
            StatsCard(stats = stats)
        }

        when {
            !state.hasApiKey -> ApiKeyPromptCard(onOpenSheet = onOpenApiKeySheet)
            !state.hasInternet -> OfflineCard()
            state.isLoadingAi -> LoadingAiCard()
            state.aiStatus is AiInsightStatus.Failed -> AiErrorCard(
                message = (state.aiStatus as AiInsightStatus.Failed).message,
                onRetry = onRetryAi
            )
            state.aiStatus is AiInsightStatus.Success -> RecommendationsSection(
                recommendations = (state.aiStatus as AiInsightStatus.Success).insight.recommendations
            )
        }

        if (state.hasApiKey && state.hasInternet && state.aiStatus is AiInsightStatus.Success) {
            FollowUpSection(
                isAsking = state.isAskingFollowUp,
                answer = state.followUpAnswer,
                onAsk = onAskFollowUp
            )
        }
    }

    if (state.isApiKeySheetOpen) {
        ApiKeySheet(onDismiss = onDismissApiKeySheet, onSave = onSaveApiKey)
    }
}

@Composable
private fun ScoreCard(stats: DayStats, aiStatus: AiInsightStatus) {
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
            text = "Kalit kiritilmaguncha faqat statistika ko'rsatiladi.",
            color = colors.text.muted(0.65f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        OnIkkiButton(text = "Kalit kiritish", onClick = onOpenSheet, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OfflineCard() {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
        Text(
            text = "Internet yo'q — faqat statistika ko'rsatilmoqda.",
            color = colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        Text(
            text = "Internet qaytganda AI tahlil avtomatik yuklanadi.",
            color = colors.text.muted(0.6f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
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
