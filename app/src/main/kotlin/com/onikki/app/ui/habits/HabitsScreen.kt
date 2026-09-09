package com.onikki.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.HabitListItem
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.data.repository.WeeklyStreakChart
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiColorTokens
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted

@Composable
fun HabitsRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val habitRepository = remember { HabitRepository(app.database.habitDao(), app.database.habitLogDao()) }
    val viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.factory(habitRepository))
    val state by viewModel.uiState.collectAsState()
    HabitsScreen(
        state = state,
        onToggleToday = viewModel::toggleToday,
        onOpenAddSheet = viewModel::openAddSheet,
        onDismissAddSheet = viewModel::dismissAddSheet,
        onSaveHabit = viewModel::addHabit
    )
}

@Composable
fun HabitsScreen(
    state: HabitsUiState,
    onToggleToday: (HabitListItem) -> Unit,
    onOpenAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onSaveHabit: (name: String, icon: String, dailyTarget: Int) -> Unit
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Odatlar", color = colors.text, style = OnIkkiType.screenTitle)
                Text(
                    text = "${state.habits.size} faol · shu hafta ${state.weekCompletionPercent}%",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            OnIkkiButton(
                text = "+ Odat",
                onClick = onOpenAddSheet,
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
            )
        }

        state.topStreak?.let { chart -> TopStreakCard(chart) }

        if (state.habits.isEmpty()) {
            Text(
                text = "Hali odat qo'shilmagan",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.habits.forEach { item ->
                    HabitRow(item = item, onToggle = { onToggleToday(item) })
                }
            }
        }
    }

    if (state.isAddSheetOpen) {
        AddHabitSheet(onDismiss = onDismissAddSheet, onSave = onSaveHabit)
    }
}

@Composable
private fun TopStreakCard(chart: WeeklyStreakChart) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Eng uzun streak", color = colors.accent, style = OnIkkiType.kicker)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = chart.streakCount.toString(),
                color = colors.warmAccent,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.03).em,
                fontFamily = OnIkkiFontFamily
            )
            Text(
                text = "kun — ${chart.habitName}",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp).padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            chart.weeks.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(weekBarColor(fraction, colors), RoundedCornerShape(3.dp))
                )
            }
        }
        Text(
            text = "oxirgi 12 hafta",
            color = colors.text.muted(0.4f),
            fontSize = 10.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

private fun weekBarColor(fraction: Float, colors: OnIkkiColorTokens): Color = when {
    fraction >= 0.85f -> colors.accent
    fraction >= 0.6f -> colors.accent600
    fraction >= 0.3f -> colors.accent700
    else -> colors.accent800
}

@Composable
private fun HabitRow(item: HabitListItem, onToggle: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        padding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
        gap = 9.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(colors.accent900, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.habit.icon, color = colors.accent300, fontSize = 14.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.habit.name,
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = "har kuni",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.habit.streakCount.toString(),
                    color = colors.warmAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = "kun",
                    color = colors.text.muted(0.45f),
                    fontSize = 10.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            item.last7Days.forEach { done ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .background(if (done) colors.accent else colors.neutral800, RoundedCornerShape(50))
                )
            }
        }
    }
}
