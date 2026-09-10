package com.onikki.app.ui.screentime

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.AppUsageRow
import com.onikki.app.data.repository.BlockRuleFlag
import com.onikki.app.data.repository.ScreenTimeRepository
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.OnIkkiRowCard
import com.onikki.app.ui.components.TagChip
import com.onikki.app.ui.components.TagVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatMinutesAsDuration

@Composable
fun ScreenTimeRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val repository = remember {
        ScreenTimeRepository(app, app.database.appUsageDao(), app.database.appLimitDao(), app.database.dailyReviewDao())
    }
    val viewModel: ScreenTimeViewModel = viewModel(factory = ScreenTimeViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()
    ScreenTimeScreen(
        state = state,
        onToggleHarmful = viewModel::toggleHarmful,
        onOpenLimitEditor = viewModel::openLimitEditor,
        onDismissLimitEditor = viewModel::dismissLimitEditor,
        onSaveLimit = viewModel::saveLimit
    )
}

@Composable
fun ScreenTimeScreen(
    state: ScreenTimeUiState,
    onToggleHarmful: (AppUsageRow) -> Unit,
    onOpenLimitEditor: (AppUsageRow) -> Unit,
    onDismissLimitEditor: () -> Unit,
    onSaveLimit: (dailyLimitMinutes: Int, isHarmful: Boolean, rules: Set<BlockRuleFlag>) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(text = "Ilovalar nazorati", color = colors.text, style = OnIkkiType.screenTitle)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = formatMinutesAsDuration(state.totalMinutesToday.toLong()),
                    color = colors.text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = "  ${comparisonCaption(state.totalMinutesToday, state.averageMinutesLast7Days)}",
                    color = colors.text.muted(0.5f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }

        if (!state.hasUsageAccess) {
            UsageAccessPromptCard(
                onOpenSettings = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
        } else {
            WeeklyHistogramCard(state.dailyTotalsLast7Days)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Eng ko'p ishlatilgan",
                    color = colors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
                Text(text = "bugun", color = colors.text.muted(0.45f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            }

            if (state.apps.isEmpty()) {
                Text(
                    text = "Bugun uchun ma'lumot yo'q",
                    color = colors.text.muted(0.5f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.apps.forEach { row ->
                        AppUsageCard(
                            row = row,
                            totalMinutesToday = state.totalMinutesToday,
                            onToggleHarmful = { onToggleHarmful(row) },
                            onClick = { onOpenLimitEditor(row) }
                        )
                    }
                }
            }

            if (state.blockedApps.isNotEmpty()) {
                BlockedSummaryCard(blockedApps = state.blockedApps)
            }
        }
    }

    state.editingApp?.let { app ->
        AppLimitSheet(app = app, onDismiss = onDismissLimitEditor, onSave = onSaveLimit)
    }
}

private fun comparisonCaption(totalToday: Int, average: Int): String {
    if (average <= 0) return "bugun"
    val diff = totalToday - average
    return when {
        diff > 0 -> "bugun · o'rtachadan ${formatMinutesAsDuration(diff.toLong())} ko'p"
        diff < 0 -> "bugun · o'rtachadan ${formatMinutesAsDuration(-diff.toLong())} kam"
        else -> "bugun · o'rtacha darajada"
    }
}

@Composable
private fun UsageAccessPromptCard(onOpenSettings: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        Text(text = "Ruxsat kerak", color = colors.accent, style = OnIkkiType.kicker)
        Text(
            text = "Ilova ishlatish statistikasi",
            color = colors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily
        )
        Text(
            text = "Bu ma'lumotni ko'rish uchun Sozlamalar > Ilova ishlatish ruxsatidan \"On ikki\"ni qo'lda yoqing.",
            color = colors.text.muted(0.65f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        OnIkkiButton(text = "Sozlamalarga o'tish", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WeeklyHistogramCard(dailyTotals: List<Int>) {
    val colors = LocalOnIkkiColors.current
    val maxValue = (dailyTotals.maxOrNull() ?: 0).coerceAtLeast(1)
    OnIkkiRowCard(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(
            modifier = Modifier.weight(1f).height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            dailyTotals.forEachIndexed { index, minutes ->
                val ratio = (minutes / maxValue.toFloat()).coerceAtLeast(0.05f)
                val isLast = index == dailyTotals.lastIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(ratio)
                        .background(if (isLast) colors.accent else colors.accent700, RoundedCornerShape(3.dp))
                )
            }
        }
        Text(
            text = "7 kun",
            color = colors.text.muted(0.45f),
            fontSize = 10.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun AppUsageCard(
    row: AppUsageRow,
    totalMinutesToday: Int,
    onToggleHarmful: () -> Unit,
    onClick: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val limit = row.limit
    val isHarmful = limit?.isHarmful == true
    val isOverLimit = isHarmful && row.minutesUsed >= (limit?.dailyLimitMinutes ?: 0)
    val percentOfDay = if (totalMinutesToday <= 0) 0 else (row.minutesUsed * 100 / totalMinutesToday)

    OnIkkiCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        borderColor = if (isOverLimit) colors.warmBorder else colors.cardBorder,
        gap = 9.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                modifier = Modifier.size(34.dp).background(colors.neutral800, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = row.appName.take(2).uppercase(),
                    color = colors.neutral300,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = row.appName,
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = OnIkkiFontFamily
                    )
                    if (isHarmful) TagChip(text = "Zararli", variant = TagVariant.OUTLINE)
                }
                Text(
                    text = "${formatMinutesAsDuration(row.minutesUsed.toLong())} · $percentOfDay%",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(checked = isHarmful, onCheckedChange = { onToggleHarmful() })
        }
        LinearProgressTrack(
            progress = if (limit != null && limit.dailyLimitMinutes > 0) {
                (row.minutesUsed / limit.dailyLimitMinutes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
            trackColor = colors.neutral800,
            progressColor = if (isOverLimit) colors.warmAccent else colors.accent
        )
        if (limit != null && limit.dailyLimitMinutes > 0) {
            val remaining = limit.dailyLimitMinutes - row.minutesUsed
            Text(
                text = if (remaining <= 0) {
                    "${row.minutesUsed} / ${limit.dailyLimitMinutes} daqiqa — limit tugadi"
                } else {
                    "${row.minutesUsed} / ${limit.dailyLimitMinutes} daqiqa — $remaining daqiqa qoldi"
                },
                color = if (isOverLimit) colors.warmAccent else colors.text.muted(0.55f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

@Composable
private fun BlockedSummaryCard(blockedApps: List<AppUsageRow>) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(7.dp).background(colors.warmAccent, CircleShape))
            Text(
                text = "Kunlik tahlil bajarilmaguncha bloklangan",
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            blockedApps.forEach { app -> TagChip(text = app.appName, variant = TagVariant.NEUTRAL) }
        }
        Text(
            text = "Kun yakunini to'ldirgach ochiladi.",
            color = colors.text.muted(0.6f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}
