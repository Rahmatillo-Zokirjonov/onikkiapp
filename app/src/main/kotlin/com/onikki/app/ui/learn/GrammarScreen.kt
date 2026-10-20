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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.domain.grammar.Grammar
import com.onikki.app.domain.grammar.GrammarTopic
import com.onikki.app.domain.grammar.GrammarBank
import com.onikki.app.domain.grammar.GrammarExercise
import com.onikki.app.domain.grammar.GrammarKind
import com.onikki.app.domain.grammar.TopicProgress
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker
import com.onikki.app.ui.vocabulary.rememberSpeaker

/** O'rganish → Grammatika: topics by level; a topic opens its explanation. */
@Composable
fun GrammarRoute(suggested: String, onBack: () -> Unit) {
    val viewModel: GrammarViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val progress by viewModel.progress.collectAsState()
    val session by viewModel.session.collectAsState()
    val context = LocalContext.current
    val prefs = remember { LearnPrefs(context) }
    var level by rememberSaveable { mutableStateOf(prefs.grammarLevel() ?: suggested) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val speaker = rememberSpeaker()
    val open = openId?.let(Grammar::byId)
    val s = session
    if (s != null) {
        BackHandler(onBack = viewModel::close)
        GrammarPractice(s, Grammar.byId(s.topicId), progress[s.topicId], viewModel)
    } else if (open != null) {
        BackHandler { openId = null }
        TopicScreen(open, progress[open.id], speaker, onPractice = { viewModel.start(open.id) }, onBack = { openId = null })
    } else {
        BackHandler(onBack = onBack)
        TopicList(level, suggested, progress, onLevel = { level = it; prefs.setGrammarLevel(it) }, onOpen = { openId = it.id }, onBack = onBack)
    }
}

@Composable
private fun TopicList(level: String, suggested: String, progress: Map<String, TopicProgress>, onLevel: (String) -> Unit, onOpen: (GrammarTopic) -> Unit, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val topics = Grammar.forLevel(level)
    val mastered = topics.count { progress[it.id]?.mastered == true }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubScreenHeader(title = "Grammatika", onBack = onBack)
        Text(
            text = "Har bir mavzuda qisqa o'zbekcha tushuntirish, qoida va misollar. ★ — so'zlaringizga qarab tavsiya qilingan daraja.",
            color = colors.text.muted(0.6f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Grammar.LEVELS.forEach { l ->
                FilterChip(selected = level == l, onClick = { onLevel(l) }, label = { Text(if (l == suggested) "$l ★" else l) })
            }
        }
        Text(
            text = "$level: $mastered/${topics.size} mavzu o'zlashtirildi · o'zlashtirish = oxirgi ${GrammarBank.WINDOW} ta javobda ${GrammarBank.MASTERY}%+",
            color = colors.text.muted(0.5f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        topics.forEachIndexed { i, t ->
            val pr = progress[t.id]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(18.dp))
                    .clickable { onOpen(t) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (pr?.mastered == true) colors.accent else colors.accent800), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (pr?.mastered == true) "✓" else "${i + 1}",
                        color = if (pr?.mastered == true) Color(0xFF10131C) else colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OnIkkiFontFamily
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(text = t.title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                    Text(text = t.titleUz, color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                }
                if (pr != null) {
                    Text(
                        text = if (pr.mastered) "${pr.recentAverage}%" else "${pr.counted}/${GrammarBank.WINDOW} · ${pr.recentAverage}%",
                        color = if (pr.mastered) colors.accent else colors.text.muted(0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OnIkkiFontFamily
                    )
                } else Text(text = "›", color = colors.text.muted(0.4f), fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun TopicScreen(t: GrammarTopic, progress: TopicProgress?, speaker: Speaker, onPractice: () -> Unit, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubScreenHeader(title = t.level, onBack = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = t.title, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            Text(text = t.titleUz, color = colors.text.muted(0.6f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
        }
        // The pattern at a glance.
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "QOIDA", color = colors.accent, style = OnIkkiType.kicker)
            Text(text = t.formula, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            t.explanation.lines().filter { it.isNotBlank() }.forEach { line ->
                val bullet = line.trimStart().startsWith("•")
                Row {
                    if (bullet) Text(text = "•  ", color = colors.accent, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                    Text(
                        text = line.trim().removePrefix("•").trim(),
                        color = colors.text.muted(if (bullet) 0.9f else 0.8f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
        }
        Text(text = "MISOLLAR", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
        t.examples.forEach { e ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).clickable(enabled = speaker.ready) { speaker.speak(e.english) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = e.english, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
                    Text(text = e.uzbek, color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
                Text(text = "🔊", fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp).alpha(if (speaker.ready) 1f else 0.3f))
            }
        }
        ProgressCard(progress)
        com.onikki.app.ui.components.OnIkkiButton(
            text = "Mashq qilish (${GrammarBank.SESSION} ta)",
            onClick = onPractice,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        )
        Text(
            text = "4 xil mashq: test, bo'sh joy, xatoni tuzatish, gapni o'zgartirish.",
            color = colors.text.muted(0.45f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun ProgressCard(p: TopicProgress?) {
    val colors = LocalOnIkkiColors.current
    val counted = p?.counted ?: 0
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (p?.mastered == true) "✓ O'zlashtirildi" else "O'zlashtirish",
                color = if (p?.mastered == true) colors.accent else colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            Text(text = "${p?.recentAverage ?: 0}%", color = colors.text.muted(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        }
        com.onikki.app.ui.components.LinearProgressTrack(
            progress = counted / GrammarBank.WINDOW.toFloat(),
            trackColor = colors.neutral800,
            progressColor = if ((p?.recentAverage ?: 0) >= GrammarBank.MASTERY) colors.accent else colors.habitAccent,
            height = 8.dp
        )
        Text(
            text = when {
                p == null -> "Hali mashq qilinmagan. Oxirgi ${GrammarBank.WINDOW} ta javobda ${GrammarBank.MASTERY}% va undan yuqori — mavzu o'zlashtiriladi."
                p.mastered -> "Oxirgi ${GrammarBank.WINDOW} ta javob o'rtachasi ${p.recentAverage}%. Vaqti-vaqti bilan takrorlab turing."
                counted < GrammarBank.WINDOW -> "$counted/${GrammarBank.WINDOW} ta javob · yana ${GrammarBank.WINDOW - counted} ta javob kerak, o'rtacha ${GrammarBank.MASTERY}%+"
                else -> "Oxirgi ${GrammarBank.WINDOW} ta javobda ${p.recentAverage}% — ${GrammarBank.MASTERY}% ga yetkazing"
            },
            color = colors.text.muted(0.55f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

// ---------------------------------------------------------------- practice

@Composable
private fun GrammarPractice(s: GrammarSession, topic: GrammarTopic?, progress: TopicProgress?, viewModel: GrammarViewModel) {
    val colors = LocalOnIkkiColors.current
    if (s.done) {
        GrammarResult(s, topic, progress, onAgain = { viewModel.start(s.topicId) }, onClose = viewModel::close)
        return
    }
    val ex = s.current ?: return
    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = viewModel::close).padding(horizontal = 10.dp, vertical = 4.dp))
            Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                com.onikki.app.ui.components.LinearProgressTrack(progress = s.index / s.exercises.size.toFloat(), trackColor = colors.neutral800, progressColor = colors.habitAccent, height = 12.dp)
            }
            Text(text = "⭐ ${s.xp}", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = (topic?.title ?: "") + " · " + ex.kind.label, color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            androidx.compose.runtime.key(s.index, ex) { GrammarExerciseView(ex, s.verdict, onAnswer = viewModel::answer) }
        }
        s.verdict?.let { v ->
            AnswerPanel(
                verdict = v,
                rightAnswer = listOfNotNull(if (v != com.onikki.app.domain.vocab.Verdict.CORRECT) ex.rightAnswer else null, ex.why.ifBlank { null }?.let { "💡 $it" }).joinToString("\n"),
                onContinue = viewModel::next,
                xp = GrammarBank.xp(v)
            )
        }
    }
}

@Composable
private fun GrammarExerciseView(ex: GrammarExercise, verdict: com.onikki.app.domain.vocab.Verdict?, onAnswer: (String, Int) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var picked by remember(ex) { mutableStateOf(-1) }
    var text by remember(ex) { mutableStateOf(if (ex.kind == GrammarKind.FIX) ex.prompt else "") }
    val answered = verdict != null
    when (ex.kind) {
        GrammarKind.CHOICE -> {
            SentenceCard(ex.prompt, title = "To'g'ri variantni tanlang")
            ex.options.forEachIndexed { i, o ->
                val bg = when {
                    !answered -> colors.surface
                    i == ex.answer -> colors.accent.copy(alpha = 0.25f)
                    i == picked -> colors.warmAccent.copy(alpha = 0.25f)
                    else -> colors.surface
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(16.dp))
                        .clickable(enabled = !answered) { picked = i; onAnswer("", i) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) { Text(text = o, color = colors.text, fontSize = 17.sp, fontFamily = OnIkkiFontFamily) }
            }
        }
        else -> {
            when (ex.kind) {
                GrammarKind.GAP -> SentenceCard(ex.prompt, title = "Bo'sh joyni to'ldiring" + (ex.hint?.let { "  ($it)" } ?: ""))
                GrammarKind.FIX -> SentenceCard(ex.prompt, title = "Gapdagi xatoni tuzating")
                else -> SentenceCard(ex.prompt, title = ex.hint ?: "Gapni o'zgartiring")
            }
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { if (!answered) text = it },
                label = { Text(if (ex.kind == GrammarKind.GAP) "Javob" else "To'g'ri gap") },
                singleLine = ex.kind == GrammarKind.GAP,
                minLines = if (ex.kind == GrammarKind.GAP) 1 else 2,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = if (ex.kind == GrammarKind.GAP) androidx.compose.ui.text.input.KeyboardCapitalization.None else androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (!answered) {
                com.onikki.app.ui.components.OnIkkiButton(
                    text = "Tekshirish",
                    onClick = { if (text.isNotBlank()) onAnswer(text, -1) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                )
            }
        }
    }
}

@Composable
private fun SentenceCard(text: String, title: String) {
    val colors = LocalOnIkkiColors.current
    Text(text = title, color = colors.text.muted(0.65f), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
    Text(
        text = text,
        color = colors.text,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(18.dp)
    )
}

@Composable
private fun GrammarResult(s: GrammarSession, topic: GrammarTopic?, progress: TopicProgress?, onAgain: () -> Unit, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = if (progress?.mastered == true) "🏅" else if (s.accuracy >= GrammarBank.MASTERY) "🌟" else "💪", fontSize = 68.sp, modifier = Modifier.padding(top = 30.dp))
        Text(text = topic?.title ?: "Grammatika", color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(
            text = "Bu raund: ${s.accuracy}% · +${s.xp} XP",
            color = colors.text.muted(0.7f),
            fontSize = 16.sp,
            fontFamily = OnIkkiFontFamily
        )
        ProgressCard(progress)
        com.onikki.app.ui.components.OnIkkiButton(text = "Yana mashq qilish", onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        com.onikki.app.ui.components.OnIkkiButton(
            text = "Mavzuga qaytish",
            onClick = onClose,
            variant = com.onikki.app.ui.components.OnIkkiButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        )
    }
}
