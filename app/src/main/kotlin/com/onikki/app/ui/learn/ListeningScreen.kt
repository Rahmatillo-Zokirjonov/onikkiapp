package com.onikki.app.ui.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.domain.vocab.Listening
import com.onikki.app.domain.vocab.ListeningText
import com.onikki.app.domain.vocab.Piece
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.dayreview.ApiKeySheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker
import com.onikki.app.ui.vocabulary.rememberSpeaker

/** O'rganish → Listening: texts by level, listen first, then fill the gaps. */
@Composable
fun ListeningRoute(onBack: () -> Unit) {
    val viewModel: ListeningViewModel = viewModel()
    val home by viewModel.home.collectAsState()
    val session by viewModel.session.collectAsState()
    val ai by viewModel.aiState.collectAsState()
    val speaker = rememberSpeaker()
    val s = session
    if (s == null) {
        BackHandler(onBack = onBack)
        ListeningHome(home, ai, viewModel, onBack)
    } else {
        BackHandler(onBack = viewModel::close)
        ListeningPlayer(s, speaker, viewModel)
    }
    when (val a = ai) {
        AiWriteState.NeedsKey -> ApiKeySheet(onDismiss = viewModel::dismissAi, onSave = viewModel::saveKey)
        is AiWriteState.Failed -> OnIkkiSheet(title = "AI matn", onDismiss = viewModel::dismissAi) {
            Text(text = a.message, color = LocalOnIkkiColors.current.text.muted(0.75f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            OnIkkiButton(text = "Yopish", onClick = viewModel::dismissAi, modifier = Modifier.fillMaxWidth())
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------- texts

@Composable
private fun ListeningHome(state: ListeningHomeState, ai: AiWriteState, viewModel: ListeningViewModel, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubScreenHeader(title = "Listening", onBack = onBack)
        Text(
            text = "Matnni tinglang, keyin eshitganingiz bo'yicha bo'sh joylarni to'ldiring. Bo'sh joylarga ko'proq o'zingiz o'rganayotgan so'zlar tushadi.",
            color = colors.text.muted(0.6f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Listening.LEVELS.forEach { l ->
                FilterChip(
                    selected = state.level == l,
                    onClick = { viewModel.setLevel(l) },
                    label = { Text(if (l == state.suggested) "$l ★" else l) }
                )
            }
        }
        Text(
            text = "★ — so'zlaringizga qarab tavsiya qilingan daraja" + if (state.doneToday > 0) " · bugun ${state.doneToday} ta matn" else "",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
        state.texts.forEach { t -> TextCard(t, state.best[t.id]) { viewModel.open(t) } }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)), RoundedCornerShape(18.dp))
                .clickable(enabled = ai != AiWriteState.Loading, onClick = viewModel::writeWithAi)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (ai == AiWriteState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                    Text(text = "AI matn yozmoqda…", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "✨ AI ${state.level} darajada yangi matn yozsin", color = colors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                    Text(text = "o'rganayotgan so'zlaringiz bilan · API kalit kerak", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        }
    }
}

@Composable
private fun TextCard(text: ListeningText, best: Int?, onClick: () -> Unit) {
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
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(if (best != null) colors.accent else colors.accent800), contentAlignment = Alignment.Center) {
            Text(text = if (best != null) "✓" else "🎧", color = if (best != null) Color(0xFF10131C) else colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = text.title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
            Text(text = "${text.topic} · ${text.sentences.size} gap", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        best?.let { Text(text = "$it%", color = if (it >= 80) colors.accent else colors.warmAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily) }
    }
}

// ---------------------------------------------------------------- player

@Composable
private fun ListeningPlayer(s: ListenSession, speaker: Speaker, viewModel: ListeningViewModel) {
    val colors = LocalOnIkkiColors.current
    var addFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = viewModel::close).padding(horizontal = 10.dp, vertical = 4.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(text = s.text.title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "${s.text.level} · ${s.text.topic}", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
            if (s.phase == ListenPhase.FILL) {
                FilterChip(selected = s.bankMode, onClick = viewModel::toggleMode, label = { Text(if (s.bankMode) "So'z banki" else "Yozish") })
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlayBar(s.text, speaker)
            when (s.phase) {
                ListenPhase.LISTEN -> ListenStep(s, speaker)
                ListenPhase.FILL, ListenPhase.RESULT -> {
                    if (s.phase == ListenPhase.RESULT) ResultCard(s)
                    GapText(s, speaker, viewModel, onWord = { word, sentence -> addFor = word to sentence })
                    if (s.phase == ListenPhase.RESULT) {
                        Text(text = "Notanish so'zni bossangiz — lug'atingizga qo'shiladi", color = colors.text.muted(0.45f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (s.phase) {
                ListenPhase.LISTEN -> OnIkkiButton(text = "Bo'sh joylarni to'ldirish", onClick = viewModel::startFill, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                ListenPhase.FILL -> {
                    if (s.bankMode) Bank(s, viewModel::pick)
                    OnIkkiButton(
                        text = "Tekshirish (${s.filled}/${s.plan.answers.size})",
                        onClick = { if (s.filled > 0) viewModel.check() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (s.filled > 0) 1f else 0.5f)
                    )
                }
                ListenPhase.RESULT -> {
                    OnIkkiButton(text = "Keyingi matn", onClick = viewModel::next, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Qayta urinish", Modifier.weight(1f), viewModel::retry)
                        SmallButton("Boshqa so'zlar", Modifier.weight(1f), viewModel::newGaps)
                    }
                }
            }
        }
    }
    addFor?.let { (word, sentence) -> AddFromTextSheet(word, sentence, viewModel, onDismiss = { addFor = null }) }
}

@Composable
private fun PlayBar(text: ListeningText, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton("▶", big = true, enabled = speaker.ready) { speaker.speak(text.fullText) }
        RoundButton("🐢", big = false, enabled = speaker.ready) { speaker.speakSlow(text.fullText) }
        Text(
            text = if (speaker.ready) "Butun matn · sekin" else "Telefonda inglizcha ovoz o'rnatilmagan (Sozlamalar → Matnni ovozga)",
            color = colors.text.muted(0.55f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListenStep(s: ListenSession, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    LaunchedEffect(s.text.id, speaker.ready) { if (speaker.ready) speaker.speak(s.text.fullText) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "🎧", fontSize = 54.sp)
        Text(text = "Diqqat bilan tinglang", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(
            text = "Matnni 1–2 marta eshiting. Har bir gapni alohida ham qayta tinglash mumkin. Keyin ${s.plan.answers.size} ta bo'sh joyni to'ldirasiz.",
            color = colors.text.muted(0.65f),
            fontSize = 14.sp,
            fontFamily = OnIkkiFontFamily,
            textAlign = TextAlign.Center
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.text.sentences.forEachIndexed { i, sentence ->
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(colors.accent800).clickable(enabled = speaker.ready) { speaker.speak(sentence) }.padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text(text = "▶ ${i + 1}", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily) }
            }
        }
    }
}

/** A chunk on screen: a word with its trailing spaces/punctuation, or a gap. */
private sealed interface Chunk {
    data class Plain(val text: String) : Chunk
    data class Gap(val index: Int) : Chunk
}

private fun chunks(pieces: List<Piece>): List<Chunk> {
    val out = mutableListOf<Chunk>()
    val plain = StringBuilder()
    fun flush() {
        Regex("\\S+\\s*|\\s+").findAll(plain).forEach { out += Chunk.Plain(it.value) }
        plain.clear()
    }
    pieces.forEach { p ->
        if (p.gap != null) {
            flush()
            out += Chunk.Gap(p.gap)
        } else plain.append(p.text)
    }
    flush()
    return out
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GapText(s: ListenSession, speaker: Speaker, viewModel: ListeningViewModel, onWord: (String, String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val focus = remember(s.plan) { List(s.plan.answers.size) { FocusRequester() } }
    val result = s.phase == ListenPhase.RESULT
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        s.plan.sentences.forEachIndexed { si, pieces ->
            val sentence = s.text.sentences[si]
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "▶",
                    color = colors.accent,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp).clip(CircleShape).background(colors.accent800).clickable(enabled = speaker.ready) { speaker.speak(sentence) }.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                FlowRow(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunks(pieces).forEach { c ->
                        when (c) {
                            is Chunk.Plain -> Text(
                                text = c.text,
                                color = colors.text.muted(0.9f),
                                fontSize = 17.sp,
                                fontFamily = OnIkkiFontFamily,
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .then(
                                        if (result && c.text.any { it.isLetter() }) Modifier.clickable { onWord(c.text.trim().trim { !it.isLetter() && it != '\'' }, sentence) } else Modifier
                                    )
                            )
                            is Chunk.Gap -> GapBox(s, c.index, focus, viewModel, onWord = { onWord(it, sentence) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GapBox(s: ListenSession, i: Int, focus: List<FocusRequester>, viewModel: ListeningViewModel, onWord: (String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val answer = s.plan.answers[i]
    val value = s.inputs[i]
    val minWidth = (answer.length * 11 + 22).coerceAtLeast(52).dp
    if (s.phase == ListenPhase.RESULT) {
        val v = s.verdicts.getOrNull(i) ?: Verdict.WRONG
        val tint = when (v) {
            Verdict.CORRECT -> colors.accent
            Verdict.ALMOST -> colors.habitAccent
            Verdict.WRONG -> colors.warmAccent
        }
        Row(
            modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.15f)).clickable { onWord(answer) }.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (v != Verdict.CORRECT && value.isNotBlank()) {
                Text(text = value, color = colors.text.muted(0.5f), fontSize = 16.sp, textDecoration = TextDecoration.LineThrough, fontFamily = OnIkkiFontFamily)
                Text(text = " ", fontSize = 16.sp)
            }
            Text(text = answer, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        }
        return
    }
    val selected = s.selected == i
    if (s.bankMode) {
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .widthIn(min = minWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(if (value.isNotBlank()) colors.accent800 else colors.neutral800)
                .border(BorderStroke(2.dp, if (selected) colors.accent else Color.Transparent), RoundedCornerShape(8.dp))
                .clickable { viewModel.select(i) }
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = value.ifBlank { "${i + 1}" }, color = if (value.isBlank()) colors.text.muted(0.4f) else colors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        }
    } else {
        BasicTextField(
            value = value,
            onValueChange = { viewModel.setInput(i, it.replace(" ", "")) },
            singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = if (i < focus.lastIndex) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onNext = { focus.getOrNull(i + 1)?.requestFocus() }),
            modifier = Modifier
                .padding(end = 4.dp)
                .widthIn(min = minWidth, max = 220.dp)
                .focusRequester(focus[i])
                .clip(RoundedCornerShape(8.dp))
                .background(colors.neutral800)
                .border(BorderStroke(1.5.dp, if (value.isNotBlank()) colors.accent.copy(alpha = 0.6f) else colors.cardBorder), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text(text = "•".repeat(answer.length.coerceAtMost(10)), color = colors.text.muted(0.3f), fontSize = 15.sp)
                    inner()
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bank(s: ListenSession, onPick: (String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val left = s.bankLeft
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        left.forEach { w ->
            Box(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(colors.background).border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(12.dp)).clickable { onPick(w) }.padding(horizontal = 14.dp, vertical = 9.dp)
            ) { Text(text = w, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily) }
        }
        if (left.isEmpty()) Text(text = "Hamma so'z joyida — tekshiring", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun ResultCard(s: ListenSession) {
    val colors = LocalOnIkkiColors.current
    val right = s.verdicts.count { it == Verdict.CORRECT }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = if (s.accuracy == 100) "🌟" else if (s.accuracy >= 70) "🏆" else "💪", fontSize = 34.sp)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = "$right/${s.verdicts.size} to'g'ri · ${s.accuracy}%", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            Text(
                text = when {
                    s.accuracy == 100 -> "Hammasini eshitib topdingiz!"
                    s.accuracy >= 70 -> "Yaxshi! Xatolarni qayta tinglab ko'ring"
                    else -> "Gaplarni ▶ bilan alohida tinglab, qayta urinib ko'ring"
                },
                color = colors.text.muted(0.65f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Text(text = "+${s.xp} XP", color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun AddFromTextSheet(word: String, sentence: String, viewModel: ListeningViewModel, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val entry = remember(word) { viewModel.lookup(word) }
    var uzbek by remember(word) { mutableStateOf(entry?.uzbek.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    val inList = remember(word) { viewModel.isInList(word) }
    OnIkkiSheet(title = word, onDismiss = onDismiss) {
        Text(text = sentence, color = colors.text.muted(0.7f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        if (inList) {
            Text(text = "Bu so'z ro'yxatingizda bor", color = colors.accent, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        } else {
            OutlinedTextField(value = uzbek, onValueChange = { uzbek = it }, label = { Text("O'zbekcha ma'nosi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (entry == null) Text(text = "Lug'atda topilmadi — ma'nosini o'zingiz yozing", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            message?.let { Text(text = it, color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
            OnIkkiButton(
                text = "Mening so'zlarimga qo'shish",
                onClick = { if (uzbek.isNotBlank() && message == null) viewModel.addWord(word, uzbek, sentence) { message = it } },
                modifier = Modifier.fillMaxWidth().alpha(if (uzbek.isNotBlank() && message == null) 1f else 0.5f)
            )
        }
    }
}

@Composable
private fun RoundButton(icon: String, big: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier.size(if (big) 56.dp else 44.dp).clip(CircleShape).background(if (big) colors.accent else colors.accent800).alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text = icon, color = if (big) Color(0xFF10131C) else colors.text, fontSize = if (big) 22.sp else 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SmallButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(14.dp)).border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text = text, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily) }
}
