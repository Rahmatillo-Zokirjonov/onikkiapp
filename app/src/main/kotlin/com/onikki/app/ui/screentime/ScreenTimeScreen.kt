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
import com.onikki.app.service.AppBlockAccessibilityService
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
        ScreenTimeRepository(app, db.appUsageDao(), db.appLimitDao(), db.dailyReviewDao(), db.blockZoneDao(), appUsageHoursDao = db.usageHoursDao())
    }
    val viewModel: ScreenTimeViewModel = viewModel(factory = ScreenTimeViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
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
            history = history,
            onPeriod = viewModel::setPeriod,
            onShift = viewModel::shiftPeriod,
            onOpenDay = viewModel::openDay,
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
    history: UsageHistoryState,
    onPeriod: (com.onikki.app.domain.screentime.UsagePeriod) -> Unit,
    onShift: (Int) -> Unit,
    onOpenDay: (java.time.LocalDate) -> Unit,
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
    var serviceAlive by remember { mutableStateOf(AppBlockAccessibilityService.isConnected) }
    var batteryFree by remember { mutableStateOf(PermissionChecker.isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        serviceOn = PermissionChecker.isAccessibilityServiceEnabled(context)
        serviceAlive = AppBlockAccessibilityService.isConnected
        batteryFree = PermissionChecker.isIgnoringBatteryOptimizations(context)
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

        if (!serviceOn) {
            PermissionPromptCard(
                title = "Bloklash o'chiq",
                body = "Ilovalarni bloklash uchun Sozlamalar > Maxsus imkoniyatlar (Accessibility) > \"On ikki\" xizmatini yoqing. " +
                    "Busiz qoidalar saqlanadi, lekin ishlamaydi.",
                button = "Yoqish",
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
        }
        if (serviceOn && !serviceAlive) {
            PermissionPromptCard(
                title = "Bloklash xizmati to'xtab qolgan",
                body = "Telefon \"On ikki\" xizmatini o'chirib qo'ygan (ko'pincha ekrandan tozalagandan keyin). " +
                    "Maxsus imkoniyatlar'da \"On ikki\"ni o'chirib, qayta yoqing — va pastdagi batareya sozlamasini ham bering.",
                button = "Maxsus imkoniyatlarni ochish",
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
        }
        if (serviceOn && !batteryFree) {
            PermissionPromptCard(
                title = "Blok ishonchli ishlashi uchun",
                body = "Telefon batareyani tejash uchun \"On ikki\"ni to'xtatib qo'ymasligi kerak. " +
                    "Aks holda ochilgan ilova qayta bloklanishi yoki blok umuman ishlamay qolishi mumkin. " +
                    "Xiaomi/Redmi'da qo'shimcha: Sozlamalar > Ilovalar > On ikki > Avtoishga tushirish'ni yoqing.",
                button = "Batareya tejashdan chiqarish",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}"))
                        )
                    }
                }
            )
        }
        if (!state.hasUsageAccess) {
            PermissionPromptCard(
                title = "Statistika uchun ruxsat",
                body = "Kunlik limit va ekran vaqti uchun Sozlamalar > Ilova ishlatish ruxsatidan \"On ikki\"ni yoqing.",
                button = "Sozlamalarga o'tish",
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
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

        if (state.hasUsageAccess) {
            SectionHeader(title = "Statistika", action = null, onAction = {})
            UsageHistorySection(
                history = history,
                controlled = state.controlled.map { it.rule.packageName }.toSet(),
                onPeriod = onPeriod,
                onShift = onShift,
                onOpenDay = onOpenDay,
                onOpenApp = onOpenApp
            )
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
            AppIcon(item.rule.packageName, item.label)
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
private fun PermissionPromptCard(title: String, body: String, button: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), borderColor = colors.warmBorder, gap = 10.dp) {
        Text(text = title, color = colors.warmAccent, style = OnIkkiType.kicker)
        Text(text = body, color = colors.text.muted(0.7f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        OnIkkiButton(text = button, onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}
