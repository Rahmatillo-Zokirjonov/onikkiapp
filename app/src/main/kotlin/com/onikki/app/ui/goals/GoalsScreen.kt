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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
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
import com.onikki.app.data.db.entity.LifeArea
import com.onikki.app.data.db.entity.SavingsGoal
import com.onikki.app.data.db.entity.Task
import com.onikki.app.domain.goals.AreaSummary
import com.onikki.app.domain.goals.BigGoal
import com.onikki.app.domain.goals.NodeProgress
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
    var bigId by rememberSaveable { mutableStateOf<Long?>(null) }
    var stageId by rememberSaveable { mutableStateOf<Long?>(null) }
    val big = bigId?.let(state::find)
    val stage = stageId?.let { id -> big?.stages?.firstOrNull { it.goal.id == id } }

    LaunchedEffect(bigId, big, state.isLoaded) { if (bigId != null && big == null && state.isLoaded) { bigId = null; stageId = null } }
    LaunchedEffect(stageId, stage) { if (stageId != null && stage == null && big != null) stageId = null }
    BackHandler(enabled = bigId != null) { if (stageId != null) stageId = null else bigId = null }

    when {
        big != null && stage != null -> StageDetail(big, stage, viewModel, onBack = { stageId = null })
        big != null -> BigGoalDetail(big, viewModel, onBack = { bigId = null }, onOpenStage = { stageId = it.goal.id })
        else -> GoalsList(state, viewModel, onOpen = { bigId = it.goal.id })
    }

    when (val sheet = state.sheet) {
        null -> Unit
        is GoalSheetTarget.EditBig -> GoalForm(
            goal = sheet.goal, parent = null, savings = state.savings,
            onDismiss = viewModel::dismissSheet,
            onSave = { viewModel.saveBig(sheet.goal, it) },
            onDelete = { sheet.goal?.let(viewModel::delete) }
        )
        is GoalSheetTarget.EditStage -> GoalForm(
            goal = sheet.stage, parent = sheet.parent, savings = state.savings,
            onDismiss = viewModel::dismissSheet,
            onSave = { viewModel.saveStage(sheet.parent, sheet.stage, it) },
            onDelete = { sheet.stage?.let(viewModel::delete) }
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

// ---------------------------------------------------------------- helpers

private fun deadlineText(daysLeft: Long?, doneAt: LocalDate?): String? = when {
    doneAt != null -> "Erishildi · ${formatRelativeDateUz(doneAt)}"
    daysLeft == null -> null
    daysLeft < 0 -> "Muddat ${-daysLeft} kun o'tdi"
    daysLeft == 0L -> "Muddat — bugun"
    daysLeft > 60 -> "${daysLeft / 30} oy qoldi"
    else -> "$daysLeft kun qoldi"
}

private fun amount(value: Long, unit: String?): String =
    (if (value >= 10_000) formatSom(value) else value.toString()) + (unit?.let { " $it" } ?: "")

private fun measureText(n: NodeProgress): String =
    if (n.goal.kind == GoalKind.NUMBER) "${amount(n.current, n.goal.unit)} / ${amount(n.goal.target, n.goal.unit)}"
    else "${n.doneTasks}/${n.totalTasks} vazifa"

// ---------------------------------------------------------------- list

@Composable
private fun GoalsList(state: GoalsUiState, viewModel: GoalsViewModel, onOpen: (BigGoal) -> Unit) {
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
                        text = "Katta maqsad → bosqichlar → kunlik qadamlar",
                        color = colors.text.muted(0.5f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
            if (state.active.isNotEmpty()) {
                item { AreasStrip(state.areas, state.areaFilter, onSelect = viewModel::setAreaFilter) }
            }
            if (state.isLoaded && state.active.isEmpty() && state.finished.isEmpty()) {
                item {
                    HeroCard {
                        Text(text = "Birinchi katta maqsadingiz", color = colors.accent, style = OnIkkiType.kicker)
                        Text(
                            text = "Masalan: \"2027-yilgacha uy sotib olish\". Keyin uni bosqichlarga bo'lasiz " +
                                "(20 mln jamg'arish → ipoteka → boshlang'ich to'lov), har kuni esa faol bosqich uchun vazifa qo'yasiz.",
                            color = colors.text.muted(0.75f),
                            fontSize = 13.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        OnIkkiButton(text = "Maqsad qo'yish", onClick = viewModel::openNewBig, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            items(state.visibleActive, key = { it.goal.id }) { g ->
                BigGoalCard(g, onClick = { onOpen(g) }, onAddTask = { g.workTarget?.let { viewModel.openNewTask(it.goal) } }, onAddStage = { viewModel.openNewStage(g.goal) })
            }
            if (state.finished.isNotEmpty()) {
                item { Text(text = "ERISHILGANLAR", color = colors.text.muted(0.45f), style = OnIkkiType.kicker, modifier = Modifier.padding(top = 8.dp)) }
                items(state.finished, key = { "done-${it.goal.id}" }) { g -> BigGoalCard(g, onClick = { onOpen(g) }, onAddTask = null, onAddStage = null) }
            }
        }
        AddFab(onClick = viewModel::openNewBig, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp))
    }
}

/** Life areas: how many active goals each has and their average progress; tap to filter. */
@Composable
private fun AreasStrip(areas: List<AreaSummary>, selected: LifeArea?, onSelect: (LifeArea) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "HAYOT SOHALARI", color = colors.text.muted(0.45f), style = OnIkkiType.kicker)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            areas.forEach { a ->
                val isSelected = selected == a.area
                Column(
                    modifier = Modifier
                        .width(86.dp)
                        .alpha(if (a.goals == 0 && !isSelected) 0.5f else 1f)
                        .background(if (isSelected) colors.accent800 else colors.surface, RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.dp, if (isSelected) colors.accent else colors.cardBorder), RoundedCornerShape(14.dp))
                        .clickable { onSelect(a.area) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = a.area.icon, fontSize = 18.sp)
                    Text(text = a.area.label, color = colors.text, fontSize = 11.sp, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = if (a.goals == 0) "maqsad yo'q" else "${a.goals} ta · ${(a.fraction * 100).toInt()}%",
                        color = colors.text.muted(0.5f),
                        fontSize = 10.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                    LinearProgressTrack(progress = a.fraction, trackColor = colors.neutral800, progressColor = colors.accent, height = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun BigGoalCard(g: BigGoal, onClick: () -> Unit, onAddTask: (() -> Unit)?, onAddStage: (() -> Unit)?) {
    val colors = LocalOnIkkiColors.current
    val late = !g.isDone && (g.daysLeft ?: 1) < 0
    OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), borderColor = if (late) colors.warmBorder else colors.cardBorder, gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressRing(progress = g.fraction, trackColor = colors.neutral800, progressColor = colors.accent, size = 54.dp) {
                Text(text = g.goal.icon, fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = g.goal.title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(g.goal.area?.let { "${it.icon} ${it.label}" }, "${g.percent}%", deadlineText(g.daysLeft, g.goal.doneAt)).joinToString(" · "),
                    color = if (late) colors.warmAccent else colors.text.muted(0.55f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        val stage = g.activeStage
        when {
            g.isDone -> Unit
            stage != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Hozirgi bosqich ${stage.goal.orderIndex}/${g.stages.size}: ${stage.goal.title}",
                        color = colors.text,
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressTrack(progress = stage.fraction, trackColor = colors.neutral800, progressColor = colors.accent, height = 4.dp)
                    Text(
                        text = listOfNotNull(measureText(stage), deadlineText(stage.daysLeft, null), stage.perDayHint).joinToString(" · "),
                        color = colors.text.muted(0.5f),
                        fontSize = 11.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
            g.stages.isEmpty() && onAddStage != null -> Text(
                text = "+ Bosqichlarga bo'lish",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onAddStage).padding(vertical = 2.dp)
            )
        }
        if (onAddTask != null && g.workTarget != null) {
            val today = g.workTarget!!.openTasksToday
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (today > 0) "Bugun: $today ta vazifa" else "Bugun uchun vazifa yo'q",
                    color = if (today > 0) colors.habitAccent else colors.text.muted(0.5f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "+ Bugunga vazifa", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clickable(onClick = onAddTask).padding(4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- big goal detail

@Composable
private fun BigGoalDetail(g: BigGoal, viewModel: GoalsViewModel, onBack: () -> Unit, onOpenStage: (NodeProgress) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val ownTasks by viewModel.tasksOf(g.goal.id).collectAsState(initial = emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SubScreenHeader(title = g.goal.title, onBack = onBack) {
                OnIkkiButton(text = "Tahrirlash", onClick = { viewModel.openEditBig(g.goal) }, variant = OnIkkiButtonVariant.SECONDARY, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp))
            }
        }
        item {
            HeroCard(gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressRing(progress = g.fraction, trackColor = colors.neutral800, progressColor = colors.accent, size = 84.dp, strokeWidth = 7.dp) {
                        Text(text = "${g.percent}%", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        g.goal.area?.let { Text(text = "${it.icon} ${it.label}", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
                        Text(text = deadlineText(g.daysLeft, g.goal.doneAt) ?: "Muddatsiz", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                        if (g.stages.isNotEmpty()) {
                            Text(text = "${g.doneStages}/${g.stages.size} bosqich bajarildi", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                        } else {
                            Text(text = measureText(g.self), color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                        }
                    }
                }
                g.goal.why?.let { Text(text = "Nima uchun: $it", color = colors.text.muted(0.75f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
                if (g.stages.isEmpty() && g.goal.kind == GoalKind.NUMBER && !g.isDone) NumberStepper(g.goal, onAdjust = { viewModel.adjustNumber(g.goal, it) })
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
                Text(text = "Bosqichlar", color = colors.text, style = OnIkkiType.sectionHeader, modifier = Modifier.weight(1f))
                if (!g.isDone) Text(text = "+ Bosqich", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clickable { viewModel.openNewStage(g.goal) }.padding(4.dp))
            }
        }
        if (g.stages.isEmpty()) {
            item {
                Text(
                    text = "Katta maqsadni 3–5 ta bosqichga bo'ling — har birining o'z muddati va o'lchovi bo'lsin. Har kuni faqat hozirgi bosqich uchun vazifa qo'yasiz.",
                    color = colors.text.muted(0.55f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        items(g.stages, key = { it.goal.id }) { s -> StageRow(s, isActive = g.activeStage?.goal?.id == s.goal.id, isLast = s == g.stages.last(), onClick = { onOpenStage(s) }) }
        if (g.stages.isEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Vazifalar", color = colors.text, style = OnIkkiType.sectionHeader, modifier = Modifier.weight(1f))
                    if (!g.isDone) Text(text = "+ Vazifa", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clickable { viewModel.openNewTask(g.goal) }.padding(4.dp))
                }
            }
            items(ownTasks, key = { "t-${it.id}" }) { task -> GoalTaskRow(task, onToggle = { viewModel.toggleTask(task) }) }
        }
    }
}

/** One stage on the timeline: ✓ done, ● current (gold), ○ upcoming. */
@Composable
private fun StageRow(s: NodeProgress, isActive: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        when {
                            s.isDone -> colors.accent
                            isActive -> colors.accent800
                            else -> colors.surface
                        },
                        CircleShape
                    )
                    .border(BorderStroke(1.5.dp, if (s.isDone || isActive) colors.accent else colors.neutral700), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (s.isDone) "✓" else s.goal.orderIndex.toString(),
                    color = if (s.isDone) colors.onAccent else if (isActive) colors.accent else colors.text.muted(0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OnIkkiFontFamily
                )
            }
            if (!isLast) Box(modifier = Modifier.width(2.dp).height(58.dp).background(if (s.isDone) colors.accent700 else colors.neutral800))
        }
        OnIkkiCard(
            modifier = Modifier.weight(1f),
            borderColor = if (isActive) colors.heroBorder else colors.cardBorder,
            padding = PaddingValues(12.dp),
            gap = 6.dp
        ) {
            Text(
                text = s.goal.title,
                color = if (s.isDone) colors.text.muted(0.55f) else colors.text,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = OnIkkiFontFamily
            )
            LinearProgressTrack(progress = s.fraction, trackColor = colors.neutral800, progressColor = colors.accent, height = 4.dp)
            Text(
                text = listOfNotNull(measureText(s), deadlineText(s.daysLeft, s.goal.doneAt), if (isActive) "hozirgi" else null).joinToString(" · "),
                color = if (!s.isDone && (s.daysLeft ?: 1) < 0) colors.warmAccent else colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

// ---------------------------------------------------------------- stage detail

@Composable
private fun StageDetail(big: BigGoal, s: NodeProgress, viewModel: GoalsViewModel, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val tasks by viewModel.tasksOf(s.goal.id).collectAsState(initial = emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SubScreenHeader(title = "${s.goal.orderIndex}-bosqich", onBack = onBack) {
                OnIkkiButton(text = "Tahrirlash", onClick = { viewModel.openEditStage(big.goal, s.goal) }, variant = OnIkkiButtonVariant.SECONDARY, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp))
            }
        }
        item {
            HeroCard(gap = 12.dp) {
                Text(text = "${big.goal.icon} ${big.goal.title}", color = colors.accent, style = OnIkkiType.kicker)
                Text(text = s.goal.title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressRing(progress = s.fraction, trackColor = colors.neutral800, progressColor = colors.accent, size = 64.dp, strokeWidth = 6.dp) {
                        Text(text = "${s.percent}%", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(text = measureText(s), color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                        deadlineText(s.daysLeft, s.goal.doneAt)?.let { Text(text = it, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily) }
                        s.perDayHint?.let { Text(text = it, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily) }
                    }
                }
                if (s.goal.linkedSavingsId != null) {
                    Text(text = "💰 Moliya jamg'armasi bilan bog'langan — pul qo'shsangiz shu yerda ham o'sadi.", color = colors.moneyAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                } else if (s.goal.kind == GoalKind.NUMBER && !s.isDone) {
                    NumberStepper(s.goal, onAdjust = { viewModel.adjustNumber(s.goal, it) })
                }
                OnIkkiButton(
                    text = if (s.isDone) "Qayta ochish" else "✓ Bosqich bajarildi",
                    onClick = { viewModel.setDone(s.goal, !s.isDone) },
                    variant = if (s.isDone) OnIkkiButtonVariant.SECONDARY else OnIkkiButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Vazifalar", color = colors.text, style = OnIkkiType.sectionHeader, modifier = Modifier.weight(1f))
                if (!s.isDone) Text(text = "+ Vazifa", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clickable { viewModel.openNewTask(s.goal) }.padding(4.dp))
            }
        }
        if (tasks.isEmpty()) {
            item { Text(text = "Hali vazifa yo'q. Bu bosqichni kunlik qadamlarga bo'ling.", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
        }
        items(tasks, key = { it.id }) { task -> GoalTaskRow(task, onToggle = { viewModel.toggleTask(task) }) }
    }
}

@Composable
private fun GoalTaskRow(task: Task, onToggle: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = if (task.date == today) "Bugun" else formatRelativeDateUz(task.date),
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
                    text = "+${amount(step, null)}",
                    color = colors.onAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.background(colors.accent, CircleShape).clickable { onAdjust(step) }.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
            Text(
                text = "−${amount(steps.first(), null)}",
                color = colors.text,
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.border(BorderStroke(1.dp, colors.divider), CircleShape).clickable { onAdjust(-steps.first()) }.padding(horizontal = 14.dp, vertical = 7.dp)
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

// ---------------------------------------------------------------- form

private val GOAL_ICONS = listOf("🎯", "🏠", "🎓", "💼", "💰", "💪", "🕌", "📚", "🗣️", "✈️", "🚗", "❤️")

/** Big goal ([parent] null: area, why) or stage ([parent] set: can mirror a Moliya savings goal). */
@Composable
private fun GoalForm(
    goal: Goal?,
    parent: Goal?,
    savings: List<SavingsGoal>,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val isStage = parent != null
    val key = goal?.id
    var title by rememberSaveable(key) { mutableStateOf(goal?.title ?: "") }
    var why by rememberSaveable(key) { mutableStateOf(goal?.why ?: "") }
    var icon by rememberSaveable(key) { mutableStateOf(goal?.icon ?: GOAL_ICONS.first()) }
    var area by rememberSaveable(key) { mutableStateOf(goal?.area) }
    var deadline by rememberSaveable(key) { mutableStateOf(goal?.deadline) }
    var kind by rememberSaveable(key) { mutableStateOf(goal?.kind ?: GoalKind.TASKS) }
    var target by rememberSaveable(key) { mutableStateOf(goal?.target?.takeIf { it > 0 }?.toString() ?: "") }
    var current by rememberSaveable(key) { mutableStateOf(goal?.current?.takeIf { it > 0 }?.toString() ?: "") }
    var unit by rememberSaveable(key) { mutableStateOf(goal?.unit ?: "") }
    var savingsId by rememberSaveable(key) { mutableStateOf(goal?.linkedSavingsId) }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(
        title = when {
            isStage && goal == null -> "Yangi bosqich"
            isStage -> "Bosqichni tahrirlash"
            goal == null -> "Yangi katta maqsad"
            else -> "Maqsadni tahrirlash"
        },
        onDismiss = onDismiss
    ) {
        if (isStage) Text(text = "${parent!!.icon} ${parent.title}", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; error = null },
            label = { Text(if (isStage) "Bosqich (aniq natija)" else "Katta maqsad") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (goal == null && !isStage) {
            SuggestionChips(
                options = listOf("Uy sotib olish", "IELTS 7.0", "IT'da ishga kirish", "O'z biznesim", "10 kg ozish", "Qur'on yodlash"),
                selected = title,
                onSelect = {
                    title = it
                    when (it) {
                        "Uy sotib olish" -> { area = LifeArea.MOLIYA; icon = "🏠" }
                        "IELTS 7.0" -> { area = LifeArea.TALIM; icon = "🎓" }
                        "IT'da ishga kirish" -> { area = LifeArea.KARYERA; icon = "💼" }
                        "O'z biznesim" -> { area = LifeArea.KARYERA; icon = "💰" }
                        "10 kg ozish" -> { area = LifeArea.SOGLIQ; icon = "💪" }
                        "Qur'on yodlash" -> { area = LifeArea.DIN; icon = "🕌" }
                    }
                }
            )
        }
        if (!isStage) {
            SheetFieldLabel("Hayot sohasi")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifeArea.entries.forEach { option ->
                    FilterChip(selected = area == option, onClick = { area = option; error = null }, label = { Text("${option.icon} ${option.label}") })
                }
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
            OutlinedTextField(value = why, onValueChange = { why = it }, label = { Text("Nima uchun? (motivatsiya)") }, modifier = Modifier.fillMaxWidth())
        }
        DatePickerField(label = "Muddat", date = deadline, onDateChange = { deadline = it }, allowClear = true)
        SheetFieldLabel("Qanday o'lchanadi")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalKind.entries.forEach { option -> FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.label) }) }
        }
        if (kind == GoalKind.NUMBER) {
            if (isStage && savings.isNotEmpty()) {
                SheetFieldLabel("Moliya jamg'armasi bilan bog'lash (ixtiyoriy)")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = savingsId == null, onClick = { savingsId = null }, label = { Text("Bog'lanmagan") })
                    savings.forEach { s ->
                        FilterChip(
                            selected = savingsId == s.id,
                            onClick = {
                                savingsId = s.id
                                if (target.isBlank()) target = s.targetAmount.toString()
                                unit = "so'm"
                            },
                            label = { Text(s.name) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 12) { target = v; error = null } },
                    label = { Text("Maqsad soni") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(value = unit, onValueChange = { unit = it.take(12) }, label = { Text("Birlik") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            if (savingsId == null) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 12) current = v },
                    label = { Text("Hozirgacha (ixtiyoriy)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Text(
                text = if (isStage) "Progress — shu bosqichga bog'langan kunlik vazifalarning bajarilgani."
                else "Bosqichlar qo'shsangiz, progress ulardan yig'iladi. Bosqichsiz bo'lsa — bog'langan vazifalardan.",
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
                    title.isBlank() -> error = if (isStage) "Bosqichni yozing" else "Maqsadni yozing"
                    !isStage && area == null -> error = "Hayot sohasini tanlang"
                    kind == GoalKind.NUMBER && t <= 0 -> error = "Maqsad sonini kiriting"
                    else -> onSave(
                        (goal ?: Goal(title = title)).copy(
                            title = title.trim(),
                            why = if (isStage) goal?.why else why.trim().ifBlank { null },
                            icon = if (isStage) goal?.icon ?: "🎯" else icon,
                            area = if (isStage) null else area,
                            deadline = deadline,
                            kind = kind,
                            target = if (kind == GoalKind.NUMBER) t else 0,
                            current = if (kind == GoalKind.NUMBER) current.toLongOrNull() ?: 0 else 0,
                            unit = unit.trim().ifBlank { null },
                            linkedSavingsId = if (kind == GoalKind.NUMBER && isStage) savingsId else null
                        )
                    )
                }
            },
            onDelete = if (goal != null) onDelete else null
        )
    }
}
