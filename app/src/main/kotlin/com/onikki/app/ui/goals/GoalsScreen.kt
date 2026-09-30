package com.onikki.app.ui.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Goal
import com.onikki.app.data.db.entity.GoalKind
import com.onikki.app.data.db.entity.Task
import com.onikki.app.ui.components.AddFab
import com.onikki.app.ui.components.CircularProgressRing
import com.onikki.app.ui.components.DatePickerField
import com.onikki.app.ui.components.HeroCard
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.components.SuggestionChips
import com.onikki.app.ui.components.TaskRowCard
import com.onikki.app.ui.dailyplan.TaskSheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.formatSom
import java.time.LocalDate

@Composable
fun GoalsRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val viewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.factory(app.database))
    val state by viewModel.uiState.collectAsState()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = openId?.let(state::find)

    LaunchedEffect(openId, open, state.isLoaded) { if (openId != null && open == null && state.isLoaded) openId = null }
    BackHandler(enabled = openId != null) { openId = null }

    if (open != null) {
        GoalDetailScreen(open, viewModel, onBack = { openId = null })
    } else {
        GoalsListScreen(state, onOpen = { openId = it.goal.id }, onNew = viewModel::openNew, onAddTask = { viewModel.openNewTask(it.goal) })
    }

    when (val sheet = state.sheet) {
        null -> Unit
        is GoalSheetTarget.Edit -> GoalSheet(
            goal = sheet.goal,
            onDismiss = viewModel::dismissSheet,
            onSave = { draft -> viewModel.saveGoal(sheet.goal, draft) },
            onDelete = { sheet.goal?.let(viewModel::deleteGoal) }
        )
        is GoalSheetTarget.NewTask -> TaskSheet(
            task = null,
            defaultDate = LocalDate.now(),
            onDismiss = viewModel::dismissSheet,
            onSave = { title, date, time, category, _ -> viewModel.addTask(sheet.goal, title, date, time, category) },
            onDelete = {}
        )
    }
}

@Composable
private fun GoalsListScreen(
    state: GoalsUiState,
    onOpen: (GoalProgress) -> Unit,
    onNew: () -> Unit,
    onAddTask: (GoalProgress) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(text = "Maqsadlar", color = colors.text, style = OnIkkiType.screenTitle)
                    Text(
                        text = if (state.active.isEmpty()) "Katta maqsad — kichik kunlik qadamlar" else "${state.active.size} ta faol maqsad",
                        color = colors.text.muted(0.5f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
            if (state.isLoaded && state.active.isEmpty() && state.finished.isEmpty()) {
                item {
                    HeroCard {
                        Text(text = "Birinchi maqsadingiz", color = colors.accent, style = OnIkkiType.kicker)
                        Text(
                            text = "Masalan: \"IELTS 7.0 — 1-dekabrgacha\" yoki \"20 ta kitob o'qish\". " +
                                "Keyin Kunlik rejada har kuni shu maqsad uchun vazifa qo'yasiz — progress o'zi yig'iladi.",
                            color = colors.text.muted(0.75f),
                            fontSize = 13.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        OnIkkiButton(text = "Maqsad qo'yish", onClick = onNew, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            items(state.active, key = { it.goal.id }) { g -> GoalCard(g, onClick = { onOpen(g) }, onAddTask = { onAddTask(g) }) }
            if (state.finished.isNotEmpty()) {
                item { Text(text = "ERISHILGANLAR", color = colors.text.muted(0.45f), style = OnIkkiType.kicker, modifier = Modifier.padding(top = 8.dp)) }
                items(state.finished, key = { "done-${it.goal.id}" }) { g -> GoalCard(g, onClick = { onOpen(g) }, onAddTask = null) }
            }
        }
        AddFab(onClick = onNew, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp))
    }
}

private fun deadlineText(g: GoalProgress): String? = when {
    g.isDone -> g.goal.doneAt?.let { "Erishildi · ${formatRelativeDateUz(it)}" }
    g.daysLeft == null -> null
    g.daysLeft < 0 -> "Muddat ${-g.daysLeft} kun o'tdi"
    g.daysLeft == 0L -> "Muddat — bugun"
    else -> "${g.daysLeft} kun qoldi"
}

private fun amountText(value: Long, unit: String?): String =
    (if (value >= 10_000) formatSom(value) else value.toString()) + (unit?.let { " $it" } ?: "")

@Composable
private fun GoalCard(g: GoalProgress, onClick: () -> Unit, onAddTask: (() -> Unit)?) {
    val colors = LocalOnIkkiColors.current
    val late = !g.isDone && (g.daysLeft ?: 1) < 0
    OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), borderColor = if (late) colors.warmBorder else colors.cardBorder, gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressRing(progress = g.fraction, trackColor = colors.neutral800, progressColor = colors.accent, size = 52.dp) {
                Text(text = g.goal.icon, fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = g.goal.title,
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        "${g.percent}%",
                        if (g.goal.kind == GoalKind.NUMBER) "${amountText(g.goal.current, g.goal.unit)} / ${amountText(g.goal.target, g.goal.unit)}"
                        else "${g.doneTasks}/${g.totalTasks} vazifa",
                        deadlineText(g)
                    ).joinToString(" · "),
                    color = if (late) colors.warmAccent else colors.text.muted(0.55f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        if (onAddTask != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        g.todayTasks > 0 -> "Bugun: ${g.todayTasks} ta vazifa"
                        else -> g.perDayHint ?: "Bugun uchun vazifa yo'q"
                    },
                    color = if (g.todayTasks > 0) colors.habitAccent else colors.text.muted(0.5f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "+ Bugunga vazifa",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.clickable(onClick = onAddTask).padding(4.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Detail

@Composable
private fun GoalDetailScreen(g: GoalProgress, viewModel: GoalsViewModel, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val tasks by viewModel.tasksOf(g.goal.id).collectAsState(initial = emptyList())
    val today = LocalDate.now()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SubScreenHeader(title = g.goal.title, onBack = onBack) {
                OnIkkiButton(
                    text = "Tahrirlash",
                    onClick = { viewModel.openEdit(g.goal) },
                    variant = OnIkkiButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }
        item {
            HeroCard(gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressRing(progress = g.fraction, trackColor = colors.neutral800, progressColor = colors.accent, size = 84.dp, strokeWidth = 7.dp) {
                        Text(text = "${g.percent}%", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(text = g.goal.icon + " " + (deadlineText(g) ?: "Muddatsiz"), color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                        if (g.goal.kind == GoalKind.NUMBER) {
                            Text(
                                text = "${amountText(g.goal.current, g.goal.unit)} / ${amountText(g.goal.target, g.goal.unit)}",
                                color = colors.text,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = OnIkkiFontFamily
                            )
                        } else {
                            Text(text = "${g.doneTasks}/${g.totalTasks} vazifa bajarildi", color = colors.text, fontSize = 16.sp, fontFamily = OnIkkiFontFamily)
                        }
                        g.perDayHint?.let { Text(text = it, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily) }
                    }
                }
                g.goal.why?.let {
                    Text(text = "Nima uchun: $it", color = colors.text.muted(0.75f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
                if (g.goal.kind == GoalKind.NUMBER && !g.isDone) NumberStepper(g.goal, onAdjust = { viewModel.adjustNumber(g.goal, it) })
                OnIkkiButton(
                    text = if (g.isDone) "Qayta faollashtirish" else "✓ Maqsadga erishdim",
                    onClick = { viewModel.setDone(g.goal, !g.isDone) },
                    variant = if (g.isDone) OnIkkiButtonVariant.SECONDARY else OnIkkiButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Vazifalar", color = colors.text, style = OnIkkiType.sectionHeader, modifier = Modifier.weight(1f))
                if (!g.isDone) {
                    Text(
                        text = "+ Vazifa",
                        color = colors.accent,
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.clickable { viewModel.openNewTask(g.goal) }.padding(4.dp)
                    )
                }
            }
        }
        if (tasks.isEmpty()) {
            item {
                Text(
                    text = "Hali vazifa yo'q. Maqsadni kichik qadamlarga bo'ling: har biri — bitta kunlik vazifa.",
                    color = colors.text.muted(0.5f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        items(tasks, key = { it.id }) { task -> GoalTaskRow(task, today, onToggle = { viewModel.toggleTask(task) }) }
    }
}

@Composable
private fun GoalTaskRow(task: Task, today: LocalDate, onToggle: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = when {
                task.date == today -> "Bugun"
                else -> formatRelativeDateUz(task.date)
            },
            color = if (!task.isCompleted && task.date.isBefore(today)) colors.warmAccent else colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
        TaskRowCard(task = task, onToggle = onToggle)
    }
}

@Composable
private fun NumberStepper(goal: Goal, onAdjust: (Long) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var custom by remember { mutableStateOf("") }
    val steps = if (goal.target >= 100_000) listOf(10_000L, 50_000L, 100_000L) else listOf(1L, 5L, 10L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEach { step ->
                Text(
                    text = "+${amountText(step, null)}",
                    color = colors.onAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier
                        .background(colors.accent, CircleShape)
                        .clickable { onAdjust(step) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
            Text(
                text = "−${amountText(steps.first(), null)}",
                color = colors.text,
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier
                    .border(BorderStroke(1.dp, colors.divider), CircleShape)
                    .clickable { onAdjust(-steps.first()) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = custom,
                onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 12) custom = v },
                label = { Text("Boshqa miqdor") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OnIkkiButton(text = "Qo'shish", onClick = { custom.toLongOrNull()?.let(onAdjust); custom = "" })
        }
    }
}

// ---------------------------------------------------------------- Sheet

private val GOAL_ICONS = listOf("🎯", "📚", "🎓", "💪", "💰", "🏃", "🕌", "💼", "🗣️", "🏠", "✈️", "❤️")

@Composable
private fun GoalSheet(goal: Goal?, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val key = goal?.id
    var title by rememberSaveable(key) { mutableStateOf(goal?.title ?: "") }
    var why by rememberSaveable(key) { mutableStateOf(goal?.why ?: "") }
    var icon by rememberSaveable(key) { mutableStateOf(goal?.icon ?: GOAL_ICONS.first()) }
    var deadline by rememberSaveable(key) { mutableStateOf(goal?.deadline) }
    var kind by rememberSaveable(key) { mutableStateOf(goal?.kind ?: GoalKind.TASKS) }
    var target by rememberSaveable(key) { mutableStateOf(goal?.target?.takeIf { it > 0 }?.toString() ?: "") }
    var current by rememberSaveable(key) { mutableStateOf(goal?.current?.takeIf { it > 0 }?.toString() ?: "") }
    var unit by rememberSaveable(key) { mutableStateOf(goal?.unit ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(title = if (goal == null) "Yangi maqsad" else "Maqsadni tahrirlash", onDismiss = onDismiss) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; error = null },
            label = { Text("Maqsad (aniq va o'lchanadigan)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (goal == null) {
            SuggestionChips(
                options = listOf("IELTS 7.0", "20 ta kitob o'qish", "5 kg ozish", "Haydovchilik guvohnomasi", "Qur'on yodlash"),
                selected = title,
                onSelect = {
                    title = it
                    when (it) {
                        "20 ta kitob o'qish" -> { kind = GoalKind.NUMBER; target = "20"; unit = "kitob"; icon = "📚" }
                        "5 kg ozish" -> { kind = GoalKind.NUMBER; target = "5"; unit = "kg"; icon = "💪" }
                        "IELTS 7.0" -> { kind = GoalKind.TASKS; icon = "🎓" }
                        "Qur'on yodlash" -> { icon = "🕌" }
                    }
                }
            )
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GOAL_ICONS.forEach { candidate ->
                val selected = candidate == icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (selected) colors.accent800 else colors.background, CircleShape)
                        .border(BorderStroke(1.dp, if (selected) colors.accent else colors.divider), CircleShape)
                        .clickable { icon = candidate },
                    contentAlignment = Alignment.Center
                ) { Text(text = candidate, fontSize = 17.sp) }
            }
        }
        OutlinedTextField(
            value = why,
            onValueChange = { why = it },
            label = { Text("Nima uchun? (ixtiyoriy)") },
            modifier = Modifier.fillMaxWidth()
        )
        DatePickerField(label = "Muddat", date = deadline, onDateChange = { deadline = it }, allowClear = true)
        SheetFieldLabel("Qanday o'lchanadi")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalKind.entries.forEach { option ->
                FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.label) })
            }
        }
        if (kind == GoalKind.NUMBER) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 12) { target = v; error = null } },
                    label = { Text("Maqsad soni") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it.take(12) },
                    label = { Text("Birlik") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = current,
                onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 12) current = v },
                label = { Text("Hozirgacha (ixtiyoriy)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "Progress — shu maqsadga bog'langan vazifalarning bajarilgani.",
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        SheetErrorText(error)
        SheetActions(
            onSave = {
                val t = target.toLongOrNull() ?: 0L
                when {
                    title.isBlank() -> error = "Maqsadni yozing"
                    kind == GoalKind.NUMBER && t <= 0 -> error = "Maqsad sonini kiriting"
                    else -> onSave(
                        (goal ?: Goal(title = title)).copy(
                            title = title.trim(),
                            why = why.trim().ifBlank { null },
                            icon = icon,
                            deadline = deadline,
                            kind = kind,
                            target = if (kind == GoalKind.NUMBER) t else 0,
                            current = if (kind == GoalKind.NUMBER) current.toLongOrNull() ?: 0 else 0,
                            unit = unit.trim().ifBlank { null }
                        )
                    )
                }
            },
            onDelete = if (goal != null) onDelete else null
        )
    }
}
