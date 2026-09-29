package com.onikki.app.ui.screentime

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.AppUsageRow
import com.onikki.app.data.repository.BlockRuleFlag
import com.onikki.app.data.repository.BlockReason
import com.onikki.app.data.repository.ScreenTimeRepository
import com.onikki.app.data.repository.parseBlockRules
import com.onikki.app.domain.permissions.PermissionChecker
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.OnIkkiRowCard
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.components.TagChip
import com.onikki.app.ui.components.TagVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatMinutesAsDuration

/** Local screens inside Ilovalar nazorati. The app-rule screen carries the package it edits. */
private sealed interface ControlDestination {
    data object Overview : ControlDestination
    data object Picker : ControlDestination
    data class Rule(val packageName: String) : ControlDestination
    data object Zones : ControlDestination
}

@Composable
fun ScreenTimeRoute(onBack: () -> Unit, onOpenVocabulary: () -> Unit) {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val repository = remember {
        val db = app.database
        ScreenTimeRepository(app, db.appUsageDao(), db.appLimitDao(), db.dailyReviewDao(), db.blockZoneDao())
    }
    val viewModel: ScreenTimeViewModel = viewModel(factory = ScreenTimeViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()
    // Saved as a string so it survives rotation ("rule:<pkg>").
    var destinationKey by rememberSaveable { mutableStateOf("overview") }
    val destination: ControlDestination = when {
        destinationKey == "picker" -> ControlDestination.Picker
        destinationKey == "zones" -> ControlDestination.Zones
        destinationKey.startsWith("rule:") -> ControlDestination.Rule(destinationKey.removePrefix("rule:"))
        else -> ControlDestination.Overview
    }
    val toOverview = { destinationKey = "overview" }

    BackHandler(onBack = { if (destination == ControlDestination.Overview) onBack() else toOverview() })

    when (destination) {
        ControlDestination.Overview -> ScreenTimeScreen(
            state = state,
            onBack = onBack,
            onAddApp = { destinationKey = "picker" },
            onOpenApp = { destinationKey = "rule:$it" },
            onOpenZones = { destinationKey = "zones" },
            onOpenVocabulary = onOpenVocabulary,
            onResume = viewModel::refreshUsage
        )
        ControlDestination.Picker -> AppPickerScreen(
            viewModel = viewModel,
            controlled = state.controlled.map { it.rule.packageName }.toSet(),
            onBack = toOverview,
            onPick = { destinationKey = "rule:$it" }
        )
        is ControlDestination.Rule -> AppRuleScreen(
            packageName = destination.packageName,
            viewModel = viewModel,
            zones = state.zones,
            onOpenZones = { destinationKey = "zones" },
            onOpenVocabulary = onOpenVocabulary,
            onDone = toOverview
        )
        ControlDestination.Zones -> ZonesScreen(
            zones = state.zones,
            onBack = toOverview,
            onSave = viewModel::saveZone,
            onDelete = viewModel::deleteZone
        )
    }
}

@Composable
fun ScreenTimeScreen(
    state: ScreenTimeUiState,
    onBack: () -> Unit,
    onAddApp: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenZones: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onResume: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    var serviceOn by remember { mutableStateOf(PermissionChecker.isAccessibilityServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceOn = PermissionChecker.isAccessibilityServiceEnabled(context)
        onResume()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubScreenHeader(title = "Ilovalar nazorati", onBack = onBack)
        Row(verticalAlignment = Alignment.Bottom) {
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

        if (!serviceOn) {
            PermissionPromptCard(
                title = "Bloklash o'chiq",
                body = "Ilovalarni bloklash uchun Sozlamalar > Maxsus imkoniyatlar (Accessibility) > \"On ikki\" xizmatini yoqing. " +
                    "Busiz qoidalar saqlanadi, lekin ishlamaydi.",
                button = "Yoqish",
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
        }
        if (!state.hasUsageAccess) {
            PermissionPromptCard(
                title = "Statistika uchun ruxsat",
                body = "Kunlik limit va ekran vaqti uchun Sozlamalar > Ilova ishlatish ruxsatidan \"On ikki\"ni yoqing.",
                button = "Sozlamalarga o'tish",
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
        } else {
            WeeklyHistogramCard(state.dailyTotalsLast7Days)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutCard(
                title = "Hududlar",
                subtitle = if (state.zones.isEmpty()) "joy qo'shish" else "${state.zones.size} ta joy",
                onClick = onOpenZones,
                modifier = Modifier.weight(1f)
            )
            ShortcutCard(title = "So'z yodlash", subtitle = "ochish sharti", onClick = onOpenVocabulary, modifier = Modifier.weight(1f))
        }

        SectionHeader(title = "Nazoratdagi ilovalar", action = "+ Ilova", onAction = onAddApp)
        if (state.controlled.isEmpty()) {
            Text(
                text = "Hali ilova tanlanmagan. \"+ Ilova\" bilan istalgan ilovaga limit, vaqt yoki hudud bo'yicha blok qo'ying.",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        } else {
            state.controlled.forEach { item -> ControlledAppCard(item, onClick = { onOpenApp(item.rule.packageName) }) }
        }

        val uncontrolled = state.apps.filter { usage -> state.controlled.none { it.rule.packageName == usage.packageName } }
        if (uncontrolled.isNotEmpty()) {
            SectionHeader(title = "Bugun eng ko'p ishlatilgan", action = null, onAction = {})
            uncontrolled.take(8).forEach { row -> UsageRow(row, state.totalMinutesToday, onClick = { onOpenApp(row.packageName) }) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            Text(
                text = action,
                color = colors.accent,
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onAction).padding(4.dp)
            )
        }
    }
}

@Composable
private fun ShortcutCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = modifier.clickable(onClick = onClick), gap = 2.dp) {
        Text(text = title, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        Text(text = subtitle, color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
    }
}

/** Short chips describing each active rule, e.g. "30 daq/kun", "22:00–07:00", "Maktab", "Qat'iy". */
private fun ruleChips(item: ControlledApp, zoneNames: Map<Long, String> = emptyMap()): List<String> {
    val rule = item.rule
    val flags = parseBlockRules(rule.blockedHours)
    return buildList {
        if (rule.isHarmful) add("${rule.dailyLimitMinutes} daq/kun")
        if (rule.hasSchedule) add("${hhmm(rule.scheduleStart!!)}–${hhmm(rule.scheduleEnd!!)}")
        if (rule.zoneIdList.isNotEmpty()) add("${rule.zoneIdList.size} hudud")
        if (flags.contains(BlockRuleFlag.PRAYER_TIMES)) add("Namoz")
        if (rule.challengeOnOpen) add("So'z bilan")
        if (rule.strictMode) add("Qat'iy")
    }
}

fun hhmm(time: java.time.LocalTime) = "%02d:%02d".format(time.hour, time.minute)

@Composable
private fun ControlledAppCard(item: ControlledApp, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val blocked = item.blockedNow
    OnIkkiCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        borderColor = if (blocked != null) colors.warmBorder else colors.cardBorder,
        gap = 8.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            AppBadge(item.label)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (blocked) {
                        null -> "Ochiq · bugun ${formatMinutesAsDuration(item.minutesToday.toLong())}"
                        BlockReason.LimitReached -> "Bloklangan · limit tugadi"
                        is BlockReason.Schedule -> "Bloklangan · ${hhmm(blocked.start)}–${hhmm(blocked.end)}"
                        is BlockReason.Zone -> "Bloklangan · ${blocked.zoneName}"
                        is BlockReason.PrayerTime -> "Bloklangan · ${blocked.prayerName} namozi"
                    },
                    color = if (blocked != null) colors.warmAccent else colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            if (blocked != null) Box(modifier = Modifier.size(8.dp).background(colors.warmAccent, CircleShape))
        }
        val chips = ruleChips(item)
        if (chips.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                chips.forEach { TagChip(text = it, variant = if (it == "Qat'iy") TagVariant.ACCENT else TagVariant.NEUTRAL) }
            }
        }
    }
}

@Composable
fun AppBadge(label: String) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier.size(34.dp).background(colors.neutral800, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.take(2).uppercase(),
            color = colors.neutral300,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun UsageRow(row: AppUsageRow, totalMinutesToday: Int, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val percent = if (totalMinutesToday <= 0) 0 else row.minutesUsed * 100 / totalMinutesToday
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        AppBadge(row.appName)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.appName, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, maxLines = 1)
            Text(
                text = "${formatMinutesAsDuration(row.minutesUsed.toLong())} · $percent%",
                color = colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Text(text = "Cheklash", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
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
private fun PermissionPromptCard(title: String, body: String, button: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), borderColor = colors.warmBorder, gap = 10.dp) {
        Text(text = title, color = colors.warmAccent, style = OnIkkiType.kicker)
        Text(text = body, color = colors.text.muted(0.7f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        OnIkkiButton(text = button, onClick = onClick, modifier = Modifier.fillMaxWidth())
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
