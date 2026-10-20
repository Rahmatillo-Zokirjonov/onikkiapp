package com.onikki.app.ui.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.domain.podcast.Podcast
import com.onikki.app.domain.podcast.PodcastQuestion
import com.onikki.app.domain.podcast.Podcasts
import com.onikki.app.domain.podcast.QuestionKind
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted

/** O'rganish → Podkastlar: 1–3 minute listening, then questions, then "qancha tushundim" in %. */
@Composable
fun PodcastRoute(suggested: String, onBack: () -> Unit) {
    val viewModel: PodcastViewModel = viewModel()
    LaunchedEffect(suggested) { viewModel.setSuggested(suggested) }
    val home by viewModel.home.collectAsState()
    val session by viewModel.session.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    var aiMinutes by rememberSaveable { mutableStateOf(2) }
    val player = rememberPodcastPlayer()
    val s = session
    LaunchedEffect(s?.podcast?.id) { s?.let { player.load(it.podcast.lines) } ?: player.stop() }
    if (s == null) {
        BackHandler(onBack = onBack)
        PodcastList(home, suggested, viewModel, onBack) {
            AiPodcastCard(home.level, aiState, aiMinutes, onMinutes = { aiMinutes = it }, onWrite = { viewModel.writeWithAi(aiMinutes) })
        }
    } else {
        BackHandler { player.stop(); viewModel.close() }
        PodcastSessionScreen(s, player, viewModel)
    }
    when (val a = aiState) {
        PodcastAiState.NeedsKey -> com.onikki.app.ui.dayreview.ApiKeySheet(onDismiss = viewModel::dismissAi, onSave = { viewModel.saveKey(it, aiMinutes) })
        is PodcastAiState.Failed -> com.onikki.app.ui.components.OnIkkiSheet(title = "AI podkast", onDismiss = viewModel::dismissAi) {
            Text(text = a.message, color = LocalOnIkkiColors.current.text.muted(0.75f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            OnIkkiButton(text = "Yopish", onClick = viewModel::dismissAi, modifier = Modifier.fillMaxWidth())
        }
        else -> Unit
    }
}

/** "AI yangi podkast yozsin": pick 1, 2 or 3 minutes; the result is vetted, kept and opened. */
@Composable
private fun AiPodcastCard(level: String, state: PodcastAiState, minutes: Int, onMinutes: (Int) -> Unit, onWrite: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "✨ AI $level darajada yangi podkast yozsin", color = colors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 2, 3).forEach { m -> FilterChip(selected = minutes == m, onClick = { onMinutes(m) }, label = { Text("$m daqiqa") }) }
        }
        if (state is PodcastAiState.Loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                Text(text = "AI ${state.minutes} daqiqalik podkast yozmoqda…", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            }
        } else {
            OnIkkiButton(text = "Yozish", onClick = onWrite, modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = "O'rganayotgan so'zlaringiz qo'shiladi · savollar tekshiriladi · podkast saqlanib qoladi · API kalit kerak",
            color = colors.text.muted(0.5f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun PodcastList(state: PodcastHomeState, suggested: String, viewModel: PodcastViewModel, onBack: () -> Unit, extra: @Composable () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubScreenHeader(title = "Podkastlar", onBack = onBack)
        Text(
            text = "1–3 daqiqalik matnni tinglang, keyin savollarga javob bering — qancha tushunganingiz foizda chiqadi.",
            color = colors.text.muted(0.6f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Podcasts.LEVELS.forEach { l -> FilterChip(selected = state.level == l, onClick = { viewModel.setLevel(l) }, label = { Text(if (l == suggested) "$l ★" else l) }) }
        }
        state.podcasts.forEach { p -> PodcastCard(p, state.best[p.id]) { viewModel.open(p) } }
        extra()
    }
}

@Composable
private fun PodcastCard(p: Podcast, best: Int?, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.accent800), contentAlignment = Alignment.Center) {
            Text(text = "🎙", fontSize = 22.sp)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = p.title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
            Text(
                text = "${p.topic} · ${p.minutes} daqiqa · " + (if (p.isDialogue) "suhbat" else "hikoya") + (if (p.ai) " · AI" else ""),
                color = colors.text.muted(0.55f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        best?.let { Text(text = "$it%", color = if (it >= 80) colors.accent else colors.warmAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily) }
    }
}

// ---------------------------------------------------------------- session

@Composable
private fun PodcastSessionScreen(s: PodcastSession, player: PodcastPlayer, viewModel: PodcastViewModel) {
    val colors = LocalOnIkkiColors.current
    val p = s.podcast
    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "✕",
                color = colors.text.muted(0.7f),
                fontSize = 22.sp,
                modifier = Modifier.clip(CircleShape).clickable { player.stop(); viewModel.close() }.padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(text = p.title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "${p.level} · ${p.minutes} daqiqa · ${p.questions.size} ta savol", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (s.phase) {
                PodcastPhase.LISTEN -> ListenPanel(p, player)
                PodcastPhase.QUESTIONS -> {
                    MiniPlayer(p, player)
                    p.questions.forEachIndexed { i, q -> QuestionCard(i, q, s, viewModel) }
                }
                PodcastPhase.RESULT -> ResultPanel(s, player)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (s.phase) {
                PodcastPhase.LISTEN -> OnIkkiButton(
                    text = if (player.finished) "Savollarga o'tish" else "Savollarga o'tish (tinglab bo'lgach)",
                    onClick = { player.pause(); viewModel.toQuestions() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                )
                PodcastPhase.QUESTIONS -> OnIkkiButton(
                    text = "Natijani ko'rish (${s.answered}/${p.questions.size})",
                    onClick = { if (s.answered > 0) { player.pause(); viewModel.submit() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (s.answered > 0) 1f else 0.5f)
                )
                PodcastPhase.RESULT -> {
                    OnIkkiButton(text = "Keyingi podkast", onClick = { player.stop(); viewModel.next() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                    OnIkkiButton(text = "Qayta tinglash va javob berish", onClick = { player.stop(); viewModel.again() }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ListenPanel(p: Podcast, player: PodcastPlayer) {
    val colors = LocalOnIkkiColors.current
    var showText by rememberSaveable(p.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "🎧", fontSize = 56.sp)
        val speaker = p.lines.getOrNull(player.current)?.speaker
        Text(
            text = when {
                !player.ready -> "Telefonda inglizcha ovoz yo'q (Sozlamalar → Matnni ovozga)"
                player.playing && speaker != null -> "$speaker gapiryapti…"
                player.playing -> "Tinglanmoqda…"
                player.finished -> "Tinglab bo'ldingiz — savollarga o'tishingiz mumkin"
                else -> "Diqqat bilan tinglang. Matn yashirin."
            },
            color = colors.text.muted(0.7f),
            fontSize = 14.sp,
            fontFamily = OnIkkiFontFamily,
            textAlign = TextAlign.Center
        )
        LinearProgressTrack(progress = if (player.finished && !player.playing) 1f else player.current / p.lines.size.coerceAtLeast(1).toFloat(), trackColor = colors.neutral800, progressColor = colors.accent, height = 8.dp)
        Controls(player)
        Text(
            text = if (showText) "Matnni yashirish" else "Matnni ko'rsatish (avval matnsiz tinglang)",
            color = colors.accent,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showText = !showText }.padding(8.dp)
        )
    }
    if (showText) Transcript(p, player, highlight = emptySet())
}

@Composable
private fun Controls(player: PodcastPlayer) {
    val colors = LocalOnIkkiColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon("⏮", 48, colors.accent800, enabled = player.ready) { player.restart() }
        RoundIcon(if (player.playing) "⏸" else "▶", 68, colors.accent, enabled = player.ready, dark = true) { if (player.playing) player.pause() else player.play() }
        RoundIcon("🐢", 48, if (player.slow) colors.habitAccent else colors.accent800, enabled = player.ready) { player.toggleSlow() }
    }
}

@Composable
private fun MiniPlayer(p: Podcast, player: PodcastPlayer) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RoundIcon(if (player.playing) "⏸" else "▶", 44, colors.accent, enabled = player.ready, dark = true) { if (player.playing) player.pause() else player.play() }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Qayta tinglash mumkin", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            LinearProgressTrack(progress = player.current / p.lines.size.coerceAtLeast(1).toFloat(), trackColor = colors.neutral800, progressColor = colors.accent, height = 5.dp)
        }
        RoundIcon("⏮", 40, colors.accent800, enabled = player.ready) { player.restart() }
    }
}

@Composable
private fun QuestionCard(i: Int, q: PodcastQuestion, s: PodcastSession, viewModel: PodcastViewModel) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "${i + 1}. ${q.kind.label}", color = colors.text.muted(0.5f), style = OnIkkiType.kicker)
        Text(text = q.text, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        when (q.kind) {
            QuestionKind.SHORT -> OutlinedTextField(
                value = s.typed[i],
                onValueChange = { viewModel.type(i, it) },
                label = { Text("Javob (inglizcha)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth()
            )
            else -> q.options.forEachIndexed { o, text ->
                val selected = s.picked[i] == o
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.accent.copy(alpha = 0.2f) else colors.background)
                        .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.cardBorder), RoundedCornerShape(14.dp))
                        .clickable { viewModel.pick(i, o) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = when {
                            q.kind == QuestionKind.TRUE_FALSE && o == 0 -> "To'g'ri (True)"
                            q.kind == QuestionKind.TRUE_FALSE -> "Noto'g'ri (False)"
                            else -> text
                        },
                        color = colors.text,
                        fontSize = 15.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(s: PodcastSession, player: PodcastPlayer) {
    val colors = LocalOnIkkiColors.current
    val p = s.podcast
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "${s.percent}%", color = if (s.percent >= 80) colors.accent else if (s.percent >= 50) colors.habitAccent else colors.warmAccent, fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(text = "tushundingiz", color = colors.text, fontSize = 17.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = when {
                s.percent >= 90 -> "Ajoyib! Bu daraja siz uchun oson — keyingisini sinab ko'ring."
                s.percent >= 70 -> "Yaxshi tushundingiz. Xatolarni matndan topib, yana bir tinglang."
                s.percent >= 50 -> "Yarmidan ko'pini tushundingiz. Sekin (🐢) rejimda qayta tinglang."
                else -> "Qiyin bo'ldi — matnni ochib, gapma-gap tinglab chiqing."
            },
            color = colors.text.muted(0.7f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            textAlign = TextAlign.Center
        )
        Text(text = "+${s.xp} XP", color = colors.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
    }
    p.questions.forEachIndexed { i, q ->
        val v = s.verdicts.getOrElse(i) { Verdict.WRONG }
        val tint = when (v) {
            Verdict.CORRECT -> colors.accent
            Verdict.ALMOST -> colors.habitAccent
            Verdict.WRONG -> colors.warmAccent
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = (if (v == Verdict.WRONG) "✗ " else "✓ ") + q.text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
            val yours = if (q.kind == QuestionKind.SHORT) s.typed[i] else q.options.getOrNull(s.picked[i]).orEmpty()
            if (v != Verdict.CORRECT) {
                Text(text = "Sizning javobingiz: ${yours.ifBlank { "—" }}", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                Text(text = "To'g'ri javob: ${q.rightAnswer}", color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
            p.lines.getOrNull(q.line)?.let { line ->
                Text(
                    text = "🔊 Matnda: \"${line.text}\"",
                    color = colors.text.muted(0.55f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { player.playLine(q.line) }.padding(vertical = 2.dp)
                )
            }
        }
    }
    Text(text = "TO'LIQ MATN", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
    Transcript(p, player, highlight = p.questions.indices.filter { s.verdicts.getOrNull(it) != Verdict.CORRECT }.map { p.questions[it].line }.toSet())
}

/** The transcript; tap a line to hear it; [highlight] marks the lines that held missed answers. */
@Composable
private fun Transcript(p: Podcast, player: PodcastPlayer, highlight: Set<Int>) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        p.lines.forEachIndexed { i, line ->
            val now = player.playing && player.current == i
            Text(
                text = (line.speaker?.let { "$it: " } ?: "") + line.text,
                color = when {
                    now -> colors.accent
                    i in highlight -> colors.warmAccent
                    else -> colors.text.muted(0.85f)
                },
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = if (now || i in highlight) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { player.playLine(i) }.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: String, size: Int, bg: Color, enabled: Boolean, dark: Boolean = false, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(bg).alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text = icon, color = if (dark) Color(0xFF10131C) else colors.text, fontSize = (size / 2.6).sp, fontWeight = FontWeight.Bold) }
}
