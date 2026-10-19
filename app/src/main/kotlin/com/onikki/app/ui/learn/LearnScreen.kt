package com.onikki.app.ui.learn

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.LessonState
import com.onikki.app.domain.vocab.PathLesson
import com.onikki.app.domain.vocab.PathUnit
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.rememberSpeaker

@Composable
fun LearnRoute(onBack: () -> Unit) {
    val viewModel: LearnViewModel = viewModel()
    val home by viewModel.home.collectAsState()
    val session by viewModel.session.collectAsState()
    val hearts by viewModel.hearts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshHearts() }
    val s = session
    if (s == null) {
        BackHandler(onBack = onBack)
        LearnHome(home, hearts, settings, viewModel, onBack)
    } else {
        BackHandler(onBack = viewModel::close)
        when {
            s.outOfHearts -> OutOfHearts(hearts, canHeal = home.hardest.isNotEmpty() || home.due.isNotEmpty() || s.mistakes.isNotEmpty() || home.wordCount > 0, onHeal = viewModel::startHeal, onClose = viewModel::close)
            s.summary != null -> LessonResult(s, home.streak, onDone = viewModel::close, onPractise = viewModel::practiseMistakes)
            else -> LessonRunner(s, hearts.hearts, settings, viewModel)
        }
    }
}

// ---------------------------------------------------------------- home + path

@Composable
private fun LearnHome(
    state: LearnHomeState,
    hearts: com.onikki.app.domain.vocab.HeartState,
    settings: com.onikki.app.data.local.LearnSettings,
    viewModel: LearnViewModel,
    onBack: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    var settingsOpen by remember { mutableStateOf(false) }
    var overLimitFor by remember { mutableStateOf<Pair<PathLesson, String>?>(null) }
    val onStart: (PathLesson, String) -> Unit = { lesson, unit ->
        val introducesNew = lesson.words.any { it.status == com.onikki.app.data.db.entity.WordStatus.NEW }
        if (introducesNew && state.newToday >= settings.newPerDay) overLimitFor = lesson to unit else viewModel.startLesson(lesson, unit)
    }
    val onReview = viewModel::startReview
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SubScreenHeader(title = "O'rganish", onBack = onBack) {
                Text(text = "⚙", color = colors.text.muted(0.7f), fontSize = 20.sp, modifier = Modifier.clip(CircleShape).clickable { settingsOpen = true }.padding(8.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(if (state.studiedToday) "🔥" else "🩶", "${state.streak} kun", Modifier.weight(1f))
                StatPill("⭐", "${state.todayXp} XP", Modifier.weight(1f))
                StatPill("❤️", "${hearts.hearts}/${com.onikki.app.domain.vocab.Hearts.MAX}", Modifier.weight(1f))
            }
        }
        item {
            val nextIn = com.onikki.app.domain.vocab.Hearts.nextIn(hearts, System.currentTimeMillis())
            Text(
                text = listOfNotNull(
                    if (state.studiedToday) "Bugun mashq qilindi — streak saqlandi" else "Bugun hali mashq yo'q — streakni saqlang",
                    "yangi so'z: ${state.newToday}/${settings.newPerDay}",
                    nextIn?.let { "keyingi jon ${it / 3_600_000} s ${(it / 60_000) % 60} daq" },
                    "jami ${state.totalXp} XP"
                ).joinToString(" · "),
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        item {
            val next = state.next
            val unitName = next?.let { n -> state.units.firstOrNull { it.deck.id == n.deckId }?.deck?.name }.orEmpty()
            BigAction(
                kicker = if (next != null) unitName.uppercase() else "DARS YO'LI",
                title = next?.let { "Davom etish: ${it.title}" } ?: "Hamma lesson tugadi 🎉",
                subtitle = next?.let { l -> l.words.take(4).joinToString(", ") { it.english } + if (l.words.size > 4) "…" else "" }
                    ?: "So'z qo'shsangiz, yangi lessonlar paydo bo'ladi",
                enabled = next != null,
                primary = true
            ) { next?.let { onStart(it, unitName) } }
        }
        item {
            BigAction(
                kicker = "TAKRORLASH",
                title = if (state.due.isEmpty()) "Bugun takrorlash yo'q" else "Takrorlash · ${state.due.size} ta so'z",
                subtitle = if (state.due.isEmpty()) "Muddati kelgan so'zlar shu yerda chiqadi" else state.due.take(5).joinToString(", ") { it.english },
                enabled = state.due.isNotEmpty(),
                primary = false,
                onClick = onReview
            )
        }
        state.units.forEach { unit ->
            item(key = "u-${unit.deck.id}") { UnitHeader(unit) }
            items(unit.lessons, key = { "l-${it.deckId}-${it.index}" }) { lesson ->
                LessonNode(lesson, onClick = { onStart(lesson, unit.deck.name) })
            }
        }
        if (state.isLoaded && state.units.isEmpty()) {
            item { Text(text = "Hali so'z yo'q. So'z yodlash bo'limida so'z qo'shing.", color = colors.text.muted(0.6f), fontFamily = OnIkkiFontFamily) }
        }
    }

    if (settingsOpen) LearnSettingsSheet(settings, onChange = viewModel::updateSettings, onDismiss = { settingsOpen = false })
    overLimitFor?.let { (lesson, unit) ->
        com.onikki.app.ui.components.OnIkkiSheet(title = "Bugungi limit", onDismiss = { overLimitFor = null }) {
            Text(
                text = "Bugun ${state.newToday} ta yangi so'z o'rgandingiz (limit ${settings.newPerDay}). Ko'p yangi so'z birdan yaxshi esda qolmaydi — avval takrorlash qilish yaxshiroq.",
                color = colors.text.muted(0.75f),
                fontSize = 14.sp,
                fontFamily = OnIkkiFontFamily
            )
            if (state.due.isNotEmpty()) BigButton("Takrorlash (${state.due.size})", primary = true) { overLimitFor = null; viewModel.startReview() }
            BigButton("Baribir davom etish", primary = state.due.isEmpty()) { overLimitFor = null; viewModel.startLesson(lesson, unit) }
        }
    }
}

@Composable
private fun LearnSettingsSheet(settings: com.onikki.app.data.local.LearnSettings, onChange: ((com.onikki.app.data.local.LearnSettings) -> com.onikki.app.data.local.LearnSettings) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    com.onikki.app.ui.components.OnIkkiSheet(title = "O'rganish sozlamalari", onDismiss = onDismiss) {
        com.onikki.app.ui.components.SheetFieldLabel("Kuniga yangi so'zlar")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.onikki.app.data.local.LearnSettings.NEW_PER_DAY_OPTIONS.forEach { n ->
                androidx.compose.material3.FilterChip(selected = settings.newPerDay == n, onClick = { onChange { it.copy(newPerDay = n) } }, label = { Text("$n") })
            }
        }
        SwitchRow("Tovush", "To'g'ri / xato javobda qisqa signal", settings.sound) { on -> onChange { it.copy(sound = on) } }
        SwitchRow("Vibratsiya", "Javobdan keyin yengil titrash", settings.vibration) { on -> onChange { it.copy(vibration = on) } }
        Text(
            text = "Jon: darsda har xato bitta jon oladi. Jon tugasa — xatolarni takrorlab tiklang (har to'g'ri javob +1), yoki har 5 soatda bittadan o'zi qo'shiladi. Takrorlashda jon olinmaydi.",
            color = colors.text.muted(0.5f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
            Text(text = subtitle, color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OutOfHearts(hearts: com.onikki.app.domain.vocab.HeartState, canHeal: Boolean, onHeal: () -> Unit, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val nextIn = com.onikki.app.domain.vocab.Hearts.nextIn(hearts, System.currentTimeMillis())
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(text = "💔", fontSize = 72.sp)
        Text(text = "Jonlar tugadi", color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(
            text = "Xatolarni takrorlab jonlarni tiklang — har to'g'ri javob +1 ❤️." +
                (nextIn?.let { " Yoki ${it / 3_600_000} soat ${(it / 60_000) % 60} daqiqadan keyin bitta jon o'zi qo'shiladi." } ?: ""),
            color = colors.text.muted(0.7f),
            fontSize = 15.sp,
            fontFamily = OnIkkiFontFamily,
            textAlign = TextAlign.Center
        )
        if (canHeal) BigButton("❤️ Xatolarni takrorlab tiklash", primary = true, onClick = onHeal)
        BigButton("Keyinroq", primary = !canHeal, onClick = onClose)
    }
}

@Composable
private fun StatPill(icon: String, text: String, modifier: Modifier) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 16.sp)
        Text(text = " $text", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun BigAction(kicker: String, title: String, subtitle: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val bg = if (primary && enabled) colors.accent else colors.surface
    val fg = if (primary && enabled) Color(0xFF10131C) else colors.text
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(BorderStroke(1.dp, if (primary) Color.Transparent else colors.cardBorder), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.6f)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = kicker, color = fg.copy(alpha = 0.7f), style = OnIkkiType.kicker)
        Text(text = title, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(text = subtitle, color = fg.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UnitHeader(unit: PathUnit) {
    val colors = LocalOnIkkiColors.current
    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = unit.deck.name, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
            Text(text = "${unit.done}/${unit.lessons.size}", color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        }
        LinearProgressTrack(progress = unit.done / unit.lessons.size.toFloat(), trackColor = colors.neutral800, progressColor = colors.accent, height = 5.dp)
    }
}

/** A lesson on the path: zig-zag circles like a trail; locked ones are dimmed. */
@Composable
private fun LessonNode(lesson: PathLesson, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val enabled = lesson.state != LessonState.LOCKED
    val offset = when (lesson.index % 4) {
        1 -> 48.dp
        3 -> (-48).dp
        else -> 0.dp
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.padding(start = if (offset > 0.dp) offset * 2 else 0.dp, end = if (offset < 0.dp) -offset * 2 else 0.dp)
                .clip(RoundedCornerShape(40.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(6.dp)
                .alpha(if (enabled) 1f else 0.45f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        when (lesson.state) {
                            LessonState.DONE -> colors.accent
                            LessonState.AVAILABLE -> colors.accent800
                            LessonState.LOCKED -> colors.neutral800
                        }
                    )
                    .border(BorderStroke(3.dp, if (lesson.state == LessonState.AVAILABLE) colors.accent else Color.Transparent), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (lesson.state) {
                        LessonState.DONE -> "✓"
                        LessonState.AVAILABLE -> "▶"
                        LessonState.LOCKED -> "🔒"
                    },
                    color = if (lesson.state == LessonState.DONE) Color(0xFF10131C) else colors.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp).width(150.dp)) {
                Text(text = lesson.title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                Text(
                    text = "${lesson.words.size} so'z" + (lesson.result?.let { " · ${it.accuracy}%" } ?: ""),
                    color = colors.text.muted(0.55f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
    }
}

// ---------------------------------------------------------------- lesson

@Composable
private fun LessonRunner(s: LessonSession, hearts: Int, settings: com.onikki.app.data.local.LearnSettings, viewModel: LearnViewModel) {
    val colors = LocalOnIkkiColors.current
    val speaker = rememberSpeaker()
    val fx = rememberFeedbackFx()
    LaunchedEffect(s.feedback) { s.feedback?.let { fx.play(it.verdict, settings.sound, settings.vibration) } }
    val exercise = s.current ?: return
    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✕",
                color = colors.text.muted(0.7f),
                fontSize = 22.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = viewModel::close).padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                LinearProgressTrack(progress = s.progress, trackColor = colors.neutral800, progressColor = colors.habitAccent, height = 12.dp)
            }
            when (s.kind) {
                is SessionKind.Path -> Text(text = "❤️ $hearts", color = colors.warmAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                SessionKind.Heal -> Text(text = "❤️ $hearts/${com.onikki.app.domain.vocab.Hearts.MAX}", color = colors.warmAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                SessionKind.Review -> Text(text = "⭐ ${s.xp}", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // Each exercise gets fresh state.
            key(s.index, exercise) {
                ExerciseView(exercise, speaker) { answers -> viewModel.answer(answers) }
            }
        }
        s.feedback?.let { fb ->
            AnswerPanel(fb.verdict, fb.rightAnswer, onContinue = viewModel::advance, xp = fb.xp, combo = fb.combo)
        }
    }
}

@Composable
private fun LessonResult(s: LessonSession, streak: Int, onDone: () -> Unit, onPractise: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val summary = s.summary ?: return
    val title = when (val k = s.kind) {
        is SessionKind.Path -> "${k.lesson.title} tugadi!"
        SessionKind.Review -> "Takrorlash tugadi!"
        SessionKind.Heal -> "Jonlar tiklandi!"
    }
    // A small celebration: the trophy pops in and the XP counts up.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (shown) 1f else 0.3f,
        androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f),
        label = "trophy"
    )
    val xpShown by androidx.compose.animation.core.animateIntAsState(if (shown) summary.xp else 0, androidx.compose.animation.core.tween(900), label = "xp")
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when {
                s.kind == SessionKind.Heal -> "❤️"
                summary.accuracy == 100 -> "🌟"
                summary.accuracy >= 80 -> "🏆"
                else -> "💪"
            },
            fontSize = 72.sp,
            modifier = Modifier.padding(top = 30.dp).graphicsLayer(scaleX = scale, scaleY = scale)
        )
        Text(text = title, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
        Text(
            text = when {
                summary.accuracy == 100 -> "Bironta ham xato yo'q — zo'r!"
                summary.accuracy >= 80 -> "Juda yaxshi natija"
                summary.accuracy >= 50 -> "Yaxshi, xatolarni takrorlab mustahkamlang"
                else -> "Qiyin bo'ldi — takrorlash yordam beradi"
            },
            color = colors.text.muted(0.65f),
            fontSize = 14.sp,
            fontFamily = OnIkkiFontFamily
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ResultTile("Aniqlik", "${summary.accuracy}%", Modifier.weight(1f))
            ResultTile("Vaqt", "%d:%02d".format(summary.seconds / 60, summary.seconds % 60), Modifier.weight(1f))
            ResultTile("XP", "+$xpShown", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔥", fontSize = 28.sp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = "$streak kun ketma-ket", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(
                    text = if (summary.streakExtended) "Streak bugun uzaytirildi!" else "Bugungi mashq hisobga olindi",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            if (summary.bestCombo >= 3) {
                Box(modifier = Modifier.weight(1f))
                Text(text = "eng uzun seriya: ${summary.bestCombo}", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        if (summary.mistakes.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Xato qilingan so'zlar", color = colors.warmAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                summary.mistakes.forEach { w ->
                    Text(text = "${w.english} — ${Exercises.primary(w.uzbek)}", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        }
        BigButton("Davom etish", primary = true, onClick = onDone)
        if (summary.mistakes.isNotEmpty()) BigButton("Xatolarni takrorlash", primary = false, onClick = onPractise)
    }
}

@Composable
private fun ResultTile(label: String, value: String, modifier: Modifier) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = colors.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(text = label, color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun BigButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) colors.accent else colors.surface)
            .border(BorderStroke(1.dp, if (primary) Color.Transparent else colors.cardBorder), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (primary) Color(0xFF10131C) else colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
    }
}
