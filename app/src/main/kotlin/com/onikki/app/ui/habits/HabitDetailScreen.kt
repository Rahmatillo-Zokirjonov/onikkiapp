package com.onikki.app.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.domain.habits.HabitDay
import com.onikki.app.domain.habits.HabitDayState
import com.onikki.app.domain.habits.HabitStats
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.monthAbbrUz
import com.onikki.app.ui.util.weekdayAbbrUz
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun HabitDetailScreen(
    stats: HabitStats,
    today: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSetTodayCount: (Int) -> Unit,
    onToggleDay: (HabitDay) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubScreenHeader(title = stats.habit.name, onBack = onBack) {
            OnIkkiButton(
                text = "Tahrirlash",
                onClick = onEdit,
                variant = OnIkkiButtonVariant.SECONDARY,
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
            )
        }
        TodayCard(stats, onSetTodayCount)
        StatsGrid(stats)
        HistoryCard(stats, today, onToggleDay)
    }
}

@Composable
private fun TodayCard(stats: HabitStats, onSetTodayCount: (Int) -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitIcon(stats.habit.icon, boxSize = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Bugun", color = colors.accent, style = OnIkkiType.kicker)
                Text(
                    text = when {
                        stats.isDoneToday -> "Bajarildi"
                        !stats.isActiveToday -> "Dam kuni — xohlasangiz belgilang"
                        stats.todayCount > 0 -> "${stats.target - stats.todayCount} ta qoldi"
                        else -> "Hali belgilanmagan"
                    },
                    color = colors.text,
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = scheduleLabel(stats.habit),
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            if (stats.target == 1) {
                TodayCheck(
                    stats = stats,
                    onClick = { onSetTodayCount(if (stats.isDoneToday) 0 else 1) },
                    size = 46.dp
                )
            }
        }
        if (stats.target > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CountButton("−", enabled = stats.todayCount > 0) { onSetTodayCount(stats.todayCount - 1) }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.todayCount} / ${stats.target}",
                        color = if (stats.isDoneToday) colors.accent else colors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = OnIkkiFontFamily
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(5.dp)
                            .background(colors.neutral800, RoundedCornerShape(50))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((stats.todayCount.toFloat() / stats.target).coerceIn(0f, 1f))
                                .height(5.dp)
                                .background(colors.accent, RoundedCornerShape(50))
                        )
                    }
                }
                CountButton("+", enabled = stats.todayCount < HabitRepository.MAX_DAILY_COUNT) {
                    onSetTodayCount(stats.todayCount + 1)
                }
            }
        }
    }
}

@Composable
private fun CountButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(BorderStroke(1.dp, if (enabled) colors.accent else colors.divider), OnIkkiShapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) colors.accent else colors.text.muted(0.3f),
            fontSize = 20.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun StatsGrid(stats: HabitStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Joriy streak", "${stats.currentStreak}", "kun", warm = true, modifier = Modifier.weight(1f))
        StatTile("Eng yaxshi", "${stats.bestStreak}", "kun", warm = false, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("30 kunlik", "${(stats.completionRate * 100).roundToInt()}%", "bajarilish", warm = false, modifier = Modifier.weight(1f))
        StatTile("Jami", "${stats.totalDoneDays}", "kun bajarilgan", warm = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, warm: Boolean, modifier: Modifier) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = modifier, padding = PaddingValues(12.dp), gap = 2.dp) {
        Text(text = label, color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = value,
            color = if (warm && value != "0") colors.warmAccent else colors.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily
        )
        Text(text = unit, color = colors.text.muted(0.45f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
    }
}

/** GitHub-style grid: one column per week (Monday on top), tap a past day to mark/unmark it. */
@Composable
private fun HistoryCard(stats: HabitStats, today: LocalDate, onToggleDay: (HabitDay) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val weeks = stats.history.chunked(7)
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Tarix", color = colors.accent, style = OnIkkiType.kicker)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(modifier = Modifier.width(19.dp))
            weeks.forEachIndexed { index, week ->
                val monday = week.first().date
                val showMonth = index == 0 || weeks[index - 1].first().date.month != monday.month
                Box(modifier = Modifier.weight(1f)) {
                    if (showMonth && index < weeks.size - 1) {
                        Text(
                            text = monthAbbrUz(monday.monthValue),
                            color = colors.text.muted(0.45f),
                            fontSize = 9.sp,
                            fontFamily = OnIkkiFontFamily,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DayOfWeek.entries.forEachIndexed { row, dayOfWeek ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (row % 2 == 0) weekdayAbbrUz(dayOfWeek) else "",
                        color = colors.text.muted(0.4f),
                        fontSize = 9.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.width(19.dp)
                    )
                    weeks.forEach { week ->
                        val day = week[row]
                        val tappable = !day.date.isAfter(today)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .dayCell(
                                    state = if (day.date.isAfter(today)) HabitDayState.NONE else day.state,
                                    colors = colors,
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .then(if (day.date == today) Modifier.border(BorderStroke(1.dp, colors.text.muted(0.6f)), RoundedCornerShape(3.dp)) else Modifier)
                                .clickable(enabled = tappable) { onToggleDay(day) }
                        )
                    }
                }
            }
        }
        Legend()
        Text(
            text = "O'tgan kunni bosib belgilang yoki bekor qiling",
            color = colors.text.muted(0.4f),
            fontSize = 10.sp,
            fontFamily = OnIkkiFontFamily,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun Legend() {
    val colors = LocalOnIkkiColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            HabitDayState.DONE to "bajarildi",
            HabitDayState.PARTIAL to "qisman",
            HabitDayState.MISSED to "o'tkazildi",
            HabitDayState.OFF to "dam"
        ).forEach { (state, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(9.dp).dayCell(state, colors, RoundedCornerShape(2.dp)))
                Text(text = label, color = colors.text.muted(0.5f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
            }
        }
    }
}
