package com.onikki.app.ui.screentime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.ALL_DAYS
import com.onikki.app.data.db.entity.AppLimit
import com.onikki.app.data.db.entity.BlockZone
import com.onikki.app.data.repository.BlockRuleFlag
import com.onikki.app.data.repository.encodeBlockRules
import com.onikki.app.data.repository.parseBlockRules
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.components.TimeInputDialog
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.weekdayAbbrUz
import java.time.DayOfWeek
import java.time.LocalTime

/** Every control rule for one app on one screen: limit, time window, places, prayer, challenge, strict. */
@Composable
fun AppRuleScreen(
    packageName: String,
    viewModel: ScreenTimeViewModel,
    zones: List<BlockZone>,
    onOpenZones: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onDone: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    var loaded by remember(packageName) { mutableStateOf(false) }
    var existing by remember(packageName) { mutableStateOf<AppLimit?>(null) }
    val label = remember(packageName) { viewModel.labelFor(packageName) }

    var limitOn by remember(packageName) { mutableStateOf(false) }
    var limitMinutes by remember(packageName) { mutableStateOf(30f) }
    var scheduleOn by remember(packageName) { mutableStateOf(false) }
    var start by remember(packageName) { mutableStateOf(LocalTime.of(22, 0)) }
    var end by remember(packageName) { mutableStateOf(LocalTime.of(7, 0)) }
    var days by remember(packageName) { mutableStateOf(ALL_DAYS) }
    var zoneIds by remember(packageName) { mutableStateOf(emptySet<Long>()) }
    var prayer by remember(packageName) { mutableStateOf(false) }
    var untilReview by remember(packageName) { mutableStateOf(false) }
    var challenge by remember(packageName) { mutableStateOf(false) }
    var strict by remember(packageName) { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        val rule = viewModel.ruleFor(packageName)
        existing = rule
        if (rule != null) {
            val flags = parseBlockRules(rule.blockedHours)
            limitOn = rule.isHarmful
            limitMinutes = rule.dailyLimitMinutes.toFloat().coerceIn(5f, 240f)
            scheduleOn = rule.hasSchedule
            rule.scheduleStart?.let { start = it }
            rule.scheduleEnd?.let { end = it }
            days = rule.scheduleDays
            zoneIds = rule.zoneIdList.toSet()
            prayer = flags.contains(BlockRuleFlag.PRAYER_TIMES)
            untilReview = flags.contains(BlockRuleFlag.UNTIL_DAY_REVIEW)
            challenge = rule.challengeOnOpen
            strict = rule.strictMode
        }
        loaded = true
    }

    val anyRule = limitOn || scheduleOn || zoneIds.isNotEmpty() || prayer || challenge

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubScreenHeader(title = label, onBack = onDone)
        if (!loaded) return@Column

        RuleCard(title = "Kunlik limit", subtitle = "Belgilangan daqiqadan keyin bloklanadi", checked = limitOn, onChecked = { limitOn = it }) {
            Text(text = "${limitMinutes.toInt()} daqiqa", color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            Slider(value = limitMinutes, onValueChange = { limitMinutes = (it / 5).toInt() * 5f }, valueRange = 5f..240f)
            ToggleLine("Kun yakuni to'ldirilsa limit ochilsin", untilReview) { untilReview = it }
        }

        RuleCard(
            title = "Vaqt bo'yicha blok",
            subtitle = "Shu oraliqda umuman ochilmaydi",
            checked = scheduleOn,
            onChecked = { scheduleOn = it }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeChip(label = "Boshlanishi", time = start, onClick = { editingTime = "start" }, modifier = Modifier.weight(1f))
                Text(text = "→", color = colors.text.muted(0.5f), fontFamily = OnIkkiFontFamily)
                TimeChip(label = "Tugashi", time = end, onClick = { editingTime = "end" }, modifier = Modifier.weight(1f))
            }
            if (end.isBefore(start)) {
                Text(text = "Tungi oraliq: ertasi kuni ${hhmm(end)} gacha", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            }
            DayChips(mask = days, onChange = { days = it })
        }

        RuleCard(
            title = "Hudud bo'yicha blok",
            subtitle = "Tanlangan joylarda bo'lganingizda ochilmaydi",
            checked = zoneIds.isNotEmpty(),
            onChecked = { on -> if (!on) zoneIds = emptySet() else if (zones.isEmpty()) onOpenZones() else zoneIds = setOf(zones.first().id) },
            showBody = zones.isNotEmpty()
        ) {
            zones.forEach { zone ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { zoneIds = if (zone.id in zoneIds) zoneIds - zone.id else zoneIds + zone.id },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = zone.id in zoneIds, onCheckedChange = null)
                    Text(text = "${zone.name} · ${zone.radiusMeters} m", color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
            }
            Text(
                text = "Hududlarni boshqarish",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onOpenZones).padding(vertical = 4.dp)
            )
        }

        RuleCard(title = "Namoz vaqtlarida", subtitle = "Har namozdan 20 daqiqa oldin", checked = prayer, onChecked = { prayer = it }, showBody = false) {}

        RuleCard(
            title = "Ochishda so'z yodlash",
            subtitle = "Har ochganda so'zlarni tarjima qilib kirasiz",
            checked = challenge,
            onChecked = { challenge = it }
        ) {
            Text(
                text = "So'zlar, soni va vaqti — So'z yodlash sozlamalarida",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onOpenVocabulary).padding(vertical = 4.dp)
            )
        }

        RuleCard(
            title = "Qat'iy rejim",
            subtitle = "Blok vaqtida hech qanday yo'l bilan ochilmaydi — so'z yodlash ham yordam bermaydi",
            checked = strict,
            onChecked = { strict = it },
            showBody = false
        ) {}

        OnIkkiButton(
            text = "Saqlash",
            onClick = {
                if (!anyRule) {
                    existing?.let { viewModel.deleteRule(it, onDone) } ?: onDone()
                    return@OnIkkiButton
                }
                val flags = buildSet {
                    if (prayer) add(BlockRuleFlag.PRAYER_TIMES)
                    if (untilReview) add(BlockRuleFlag.UNTIL_DAY_REVIEW)
                }
                viewModel.saveRule(
                    AppLimit(
                        packageName = packageName,
                        dailyLimitMinutes = limitMinutes.toInt(),
                        isHarmful = limitOn,
                        blockedHours = encodeBlockRules(flags),
                        appName = label,
                        scheduleStart = if (scheduleOn) start else null,
                        scheduleEnd = if (scheduleOn) end else null,
                        scheduleDays = if (days == 0) ALL_DAYS else days,
                        zoneIds = zoneIds.takeIf { it.isNotEmpty() }?.joinToString(","),
                        strictMode = strict,
                        challengeOnOpen = challenge
                    ),
                    onDone
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (existing != null) {
            OnIkkiButton(
                text = if (confirmingDelete) "Rostdan nazoratdan olib tashlash?" else "Nazoratdan olib tashlash",
                onClick = { if (confirmingDelete) existing?.let { viewModel.deleteRule(it, onDone) } else confirmingDelete = true },
                variant = OnIkkiButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    editingTime?.let { which ->
        TimeInputDialog(
            initial = if (which == "start") start else end,
            onDismiss = { editingTime = null },
            onConfirm = { picked ->
                if (which == "start") start = picked else end = picked
                editingTime = null
            }
        )
    }
}

@Composable
private fun RuleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    showBody: Boolean = true,
    body: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                Text(text = subtitle, color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
        if (checked && showBody) body()
    }
}

@Composable
private fun ToggleLine(text: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = text, color = colors.text.muted(0.8f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun TimeChip(label: String, time: LocalTime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier
            .border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = colors.text.muted(0.5f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
        Text(text = hhmm(time), color = colors.text, fontSize = 18.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
fun DayChips(mask: Int, onChange: (Int) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            val bit = 1 shl (day.value - 1)
            val on = mask and bit != 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) colors.accent800 else colors.background, OnIkkiShapes.small)
                    .border(BorderStroke(1.dp, if (on) colors.accent else colors.divider), OnIkkiShapes.small)
                    .clickable { onChange(mask xor bit) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = weekdayAbbrUz(day),
                    color = if (on) colors.accent100 else colors.text.muted(0.6f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
    }
}

/** Every launchable app, searchable; controlled ones are marked. */
@Composable
fun AppPickerScreen(
    viewModel: ScreenTimeViewModel,
    controlled: Set<String>,
    onBack: () -> Unit,
    onPick: (String) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val apps by viewModel.installedApps.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }
    val visible = apps.orEmpty().filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { SubScreenHeader(title = "Ilova tanlash", onBack = onBack) }
        item {
            val style = TextStyle(color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, OnIkkiShapes.medium)
                    .border(BorderStroke(1.dp, colors.cardBorder), OnIkkiShapes.medium)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text(text = "Qidirish", style = style.copy(color = colors.text.muted(0.4f)))
                        inner()
                    }
                }
            )
        }
        if (apps == null) {
            item { Text(text = "Yuklanmoqda…", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
        }
        items(visible, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onPick(app.packageName) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                AppBadge(app.label)
                Text(text = app.label, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                if (app.packageName in controlled) {
                    Text(text = "nazoratda", color = colors.accent, fontSize = 11.sp, fontFamily = OnIkkiFontFamily, style = OnIkkiType.kicker)
                }
            }
        }
    }
}
