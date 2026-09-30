package com.onikki.app.ui.home

import androidx.compose.ui.unit.em
import com.onikki.app.ui.components.ModuleTile
import com.onikki.app.ui.components.ModuleIcon
import com.onikki.app.ui.components.ModuleCard
import com.onikki.app.ui.components.HeroCard
import com.onikki.app.ui.components.AppModule
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.DailyReview
import com.onikki.app.data.db.entity.PlannedExpense
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.ProfileStore
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.domain.habits.HabitStats
import com.onikki.app.ui.components.CircularProgressRing
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.OnIkkiRowCard
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.OnIkkiTab
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.TaskRowCard
import com.onikki.app.ui.finance.FilledSparkline
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatFullDateUz
import com.onikki.app.ui.util.formatHmsCountdown
import com.onikki.app.ui.util.formatSom
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

private const val HOME_TASK_LIMIT = 5

@Composable
fun HomeRoute(onNavigateToDayReview: () -> Unit = {}, onNavigateToTab: (OnIkkiTab) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val db = app.database
    val habitRepository = remember { HabitRepository(db.habitDao(), db.habitLogDao()) }
    val locationStore = remember { LocationStore(app) }
    val profileStore = remember { ProfileStore(app) }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(db, habitRepository, locationStore, profileStore))
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var editingName by rememberSaveable { mutableStateOf(false) }

    HomeScreen(
        state = state,
        onToggleTask = viewModel::toggleTask,
        onTapHabit = viewModel::tapHabit,
        onCompleteMoney = viewModel::completeMoney,
        onOpenDayReview = onNavigateToDayReview,
        onOpenTab = onNavigateToTab,
        onEditName = { editingName = true }
    )
    if (editingName) {
        NameSheet(
            current = state.name,
            onDismiss = { editingName = false },
            onSave = { name ->
                scope.launch { profileStore.setName(name) }
                editingName = false
            }
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggleTask: (Task) -> Unit,
    onTapHabit: (HabitStats) -> Unit,
    onCompleteMoney: (PlannedExpense) -> Unit,
    onOpenDayReview: () -> Unit,
    onOpenTab: (OnIkkiTab) -> Unit,
    onEditName: () -> Unit
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
        GreetingHeader(name = state.name, now = state.now, onEditName = onEditName)
        NextPrayerCard(state)
        ModuleTiles(state, onOpenTab = onOpenTab)
        AttentionCard(
            overdueCount = state.overdueCount,
            moneyDue = state.moneyDue,
            today = state.now.toLocalDate(),
            onOpenPlan = { onOpenTab(OnIkkiTab.PLAN) },
            onCompleteMoney = onCompleteMoney
        )
        TodayPlanSection(state, onToggleTask = onToggleTask, onOpenPlan = { onOpenTab(OnIkkiTab.PLAN) })
        HabitsSection(habits = state.habits, onTapHabit = onTapHabit, onOpenAll = { onOpenTab(OnIkkiTab.PLAN) })
        DayReviewEntryRow(review = state.review, now = state.now, onClick = onOpenDayReview)
    }
}

// ---------------------------------------------------------------- Salomlashuv

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 4..10 -> "Xayrli tong"
    in 11..16 -> "Xayrli kun"
    in 17..22 -> "Xayrli kech"
    else -> "Assalomu alaykum"
}

@Composable
private fun GreetingHeader(name: String, now: LocalDateTime, onEditName: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (name.isBlank()) greetingFor(now.toLocalTime()) else "${greetingFor(now.toLocalTime())},\n$name",
                color = colors.text,
                style = OnIkkiType.greetingName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFullDateUz(now.toLocalDate()),
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(colors.accent800, CircleShape)
                .border(BorderStroke(1.dp, colors.accent700), CircleShape)
                .clickable(onClick = onEditName),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "+",
                color = colors.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

@Composable
fun NameSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    OnIkkiSheet(title = "Sizga qanday murojaat qilaylik?", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) name = it },
            label = { Text("Ismingiz") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SheetActions(onSave = { onSave(name) }, onDelete = null)
    }
}

// ---------------------------------------------------------------- Namoz

/** The "Tun" hero: next prayer with a live countdown and all five of today's times. */
@Composable
private fun NextPrayerCard(state: HomeUiState) {
    val colors = LocalOnIkkiColors.current
    HeroCard(gap = 12.dp) {
        Text(text = "Keyingi namoz · ${state.nextPrayerName}", color = colors.accent, style = OnIkkiType.kicker)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatHmsCountdown(state.secondsUntilNextPrayer.coerceAtLeast(0)),
                color = colors.text,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.02).em,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "%02d:%02d".format(state.nextPrayerTime.hour, state.nextPrayerTime.minute),
                color = colors.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        if (state.prayers.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.prayers.forEach { (name, time) ->
                    val isNext = name == state.nextPrayerName && time == state.nextPrayerTime
                    val passed = !isNext && time.isBefore(state.now.toLocalTime())
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (passed) 0.45f else 1f)) {
                        Text(
                            text = name,
                            color = if (isNext) colors.accent else colors.text.muted(0.55f),
                            fontSize = 11.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        Text(
                            text = "%02d:%02d".format(time.hour, time.minute),
                            color = if (isNext) colors.accent else colors.text,
                            fontSize = 14.sp,
                            fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = OnIkkiFontFamily
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Bo'lim plitkalari

/** The "Rangli" tiles: plan (blue) and habits (green) side by side, money (gold) full width. */
@Composable
private fun ModuleTiles(state: HomeUiState, onOpenTab: (OnIkkiTab) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val dueHabits = state.habits.count { it.isActiveToday }
    val doneHabits = state.habits.count { it.isActiveToday && it.isDoneToday }
    val bestStreak = state.habits.maxOfOrNull { it.currentStreak } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModuleTile(
                module = AppModule.PLAN,
                icon = "☑",
                value = if (state.totalCount == 0) "—" else "${state.completedCount}/${state.totalCount}",
                caption = if (state.totalCount == 0) "bugun vazifa yo'q" else "vazifa bajarildi",
                modifier = Modifier.weight(1f),
                onClick = { onOpenTab(OnIkkiTab.PLAN) }
            )
            ModuleTile(
                module = AppModule.HABITS,
                icon = "🔥",
                value = if (bestStreak > 0) "$bestStreak kun" else "$doneHabits/$dueHabits",
                caption = if (bestStreak > 0) "eng uzun streak" else "odat bajarildi",
                modifier = Modifier.weight(1f),
                onClick = { onOpenTab(OnIkkiTab.PLAN) }
            )
        }
        ModuleCard(module = AppModule.MONEY, modifier = Modifier.clickable { onOpenTab(OnIkkiTab.MONEY) }, gap = 6.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModuleIcon(AppModule.MONEY, "💰")
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatSom(state.balance) + " so'm",
                        color = colors.moneyAccent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OnIkkiFontFamily,
                        maxLines = 1
                    )
                    Text(
                        text = "Balans · shu oy +${formatSom(state.monthIncome)} / −${formatSom(state.monthExpense)}",
                        color = colors.text.muted(0.6f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (state.balanceTrend.distinct().size > 1) {
                FilledSparkline(points = state.balanceTrend, strokeColor = colors.moneyAccent, fillColor = colors.moneyAccent.copy(alpha = 0.12f))
            }
        }
    }
}

// ---------------------------------------------------------------- Diqqat

/** Things that are already late or due right now — only shown when there are some. */
@Composable
private fun AttentionCard(
    overdueCount: Int,
    moneyDue: List<PlannedExpense>,
    today: java.time.LocalDate,
    onOpenPlan: () -> Unit,
    onCompleteMoney: (PlannedExpense) -> Unit
) {
    if (overdueCount == 0 && moneyDue.isEmpty()) return
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), borderColor = colors.warmBorder, gap = 9.dp) {
        Text(text = "DIQQAT", color = colors.warmAccent, style = OnIkkiType.kicker)
        if (overdueCount > 0) {
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPlan), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$overdueCount ta vazifa o'tgan kunlardan qolgan",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "→", color = colors.text.muted(0.5f), fontFamily = OnIkkiFontFamily)
            }
        }
        moneyDue.take(3).forEach { item ->
            val late = java.time.temporal.ChronoUnit.DAYS.between(item.dueDate, today)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.title, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, maxLines = 1)
                    Text(
                        text = (if (item.isIncome) "+ " else "− ") + formatSom(item.amount) + " so'm" +
                            if (late > 0) " · $late kun kechikdi" else " · bugun",
                        color = if (late > 0) colors.warmAccent else colors.text.muted(0.55f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
                OnIkkiButton(
                    text = item.doneLabel,
                    onClick = { onCompleteMoney(item) },
                    variant = OnIkkiButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Bugungi reja

@Composable
private fun SectionTitle(title: String, trailing: String?, onTrailing: (() -> Unit)?) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(text = title, color = colors.text, style = OnIkkiType.sectionHeader, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = if (onTrailing != null) Modifier.clickable(onClick = onTrailing).padding(start = 8.dp) else Modifier
            )
        }
    }
}

@Composable
private fun TodayPlanSection(state: HomeUiState, onToggleTask: (Task) -> Unit, onOpenPlan: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(
            title = "Bugungi reja",
            trailing = if (state.totalCount > 0) "${state.completedCount}/${state.totalCount} · Hammasi →" else "Reja →",
            onTrailing = onOpenPlan
        )
        if (state.tasks.isEmpty()) {
            OnIkkiRowCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPlan)) {
                Text(
                    text = "Bugun uchun vazifa yo'q",
                    color = colors.text.muted(0.55f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "+ Vazifa", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
        } else {
            state.tasks.take(HOME_TASK_LIMIT).forEach { task ->
                TaskRowCard(task = task, onToggle = { onToggleTask(task) }, onClick = onOpenPlan)
            }
            val hidden = state.tasks.size - HOME_TASK_LIMIT
            if (hidden > 0) {
                Text(
                    text = "yana $hidden ta vazifa",
                    color = colors.text.muted(0.5f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.clickable(onClick = onOpenPlan)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Odatlar

@Composable
private fun HabitsSection(habits: List<HabitStats>, onTapHabit: (HabitStats) -> Unit, onOpenAll: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val due = habits.count { it.isActiveToday }
    val done = habits.count { it.isActiveToday && it.isDoneToday }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(
            title = "Odatlar",
            trailing = if (habits.isEmpty()) "Qo'shish →" else "$done/$due · Hammasi →",
            onTrailing = onOpenAll
        )
        if (habits.isEmpty()) {
            Text(text = "Hali odat qo'shilmagan", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(habits, key = { it.habit.id }) { stats -> HabitTodayCard(stats, onClick = { onTapHabit(stats) }) }
            }
        }
    }
}

/** Today's progress, not a long-term rate: a full ring = done today; "2/3" for counted habits. */
@Composable
private fun HabitTodayCard(stats: HabitStats, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val progress = (stats.todayCount.toFloat() / stats.target).coerceIn(0f, 1f)
    OnIkkiCard(
        modifier = Modifier
            .width(96.dp)
            .alpha(if (stats.isActiveToday || stats.isDoneToday) 1f else 0.5f)
            .clickable(onClick = onClick),
        padding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
        gap = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressRing(progress = progress, trackColor = colors.neutral800, progressColor = colors.accent, size = 52.dp) {
                Text(
                    text = when {
                        stats.isDoneToday -> "✓"
                        stats.target > 1 -> "${stats.todayCount}/${stats.target}"
                        else -> stats.habit.icon
                    },
                    color = if (stats.isDoneToday) colors.accent else colors.text,
                    fontSize = if (stats.isDoneToday) 18.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        Text(
            text = stats.habit.name,
            color = colors.text,
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = if (!stats.isActiveToday && !stats.isDoneToday) "bugun dam" else "${stats.currentStreak} kun",
            color = if (stats.currentStreak > 0 && stats.isActiveToday) colors.accent else colors.text.muted(0.45f),
            fontSize = 10.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------- Kun yakuni

@Composable
private fun DayReviewEntryRow(review: DailyReview?, now: LocalDateTime, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    // Evenings nudge harder: after 18:00 an unfinished review gets the warning border.
    val nudge = review == null && now.hour >= 18
    OnIkkiRowCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (nudge) Modifier.border(BorderStroke(1.dp, colors.warmBorder), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Kun yakuni", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            Text(
                text = when {
                    review != null -> "Bugun yakunlandi · ${review.completedCount}/${review.totalCount} vazifa"
                    nudge -> "Kunni yakunlash vaqti"
                    else -> "Kechqurun kunni yakunlang"
                },
                color = when {
                    review != null -> colors.accent
                    nudge -> colors.warmAccent
                    else -> colors.text.muted(0.5f)
                },
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Text(text = "→", color = colors.text.muted(0.5f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
    }
}
