package com.onikki.app.ui.learn

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.domain.vocab.ChainPath
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.PathStep
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker

/** The learning path's middle steps (choosing, matching, listening); cards and the check have their own screens. */
@Composable
internal fun ChainPathScreen(p: PathState, speaker: Speaker, viewModel: ChainPathViewModel) {
    val colors = LocalOnIkkiColors.current
    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        PathHeader(p, onClose = viewModel::close)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (p.step) {
                PathStep.CHOOSE -> ChooseStep(p, speaker, viewModel)
                PathStep.MATCH -> MatchStep(p, viewModel)
                PathStep.LISTEN -> ListenStep(p, speaker, viewModel)
                else -> Unit
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp)) {
            when (p.step) {
                PathStep.CHOOSE -> if (p.picked != null) OnIkkiButton(text = "Davom etish", onClick = viewModel::nextChoice, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                PathStep.MATCH -> if (p.matchDone) OnIkkiButton(text = "Keyingi qadam →", onClick = viewModel::next, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                PathStep.LISTEN -> if (p.verdict == null) OnIkkiButton(text = "Tekshirish", onClick = viewModel::checkListen, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                else OnIkkiButton(text = "Davom etish", onClick = viewModel::nextListen, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                else -> Unit
            }
        }
    }
}

/** ✕, the day and the five steps with the current one lit. */
@Composable
internal fun PathHeader(p: PathState, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(text = "📚 O'rganish · ${p.day}-kun", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "${p.step.ordinal + 1}/${PathStep.entries.size}: ${p.step.label}", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PathStep.entries.forEach { st ->
                Box(
                    modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (st.ordinal <= p.step.ordinal) colors.accent else colors.neutral800)
                )
            }
        }
    }
}

@Composable
private fun ChooseStep(p: PathState, speaker: Speaker, viewModel: ChainPathViewModel) {
    val colors = LocalOnIkkiColors.current
    val q = p.choice ?: return
    Text(text = "Qaysi so'z shu ma'noni beradi?", color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
    Text(text = Exercises.primary(q.answer.uzbek), color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
    q.options.forEach { o ->
        val picked = p.picked
        val right = ChainPath.isRight(q, o)
        val tint = when {
            picked == null -> colors.cardBorder
            right -> colors.accent
            o == picked -> colors.warmAccent
            else -> colors.cardBorder
        }
        Text(
            text = o + if (picked != null && right) "  ✓" else if (o == picked) "  ✗" else "",
            color = colors.text,
            fontSize = 18.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (picked != null && (right || o == picked)) tint.copy(alpha = 0.15f) else colors.surface)
                .border(BorderStroke(1.5.dp, tint), RoundedCornerShape(16.dp))
                .clickable(enabled = picked == null) { viewModel.pick(o); if (right) speaker.speak(o) }.padding(16.dp)
        )
    }
    if (p.picked != null && !ChainPath.isRight(q, p.picked)) {
        Text(text = "To'g'risi: ${q.answer.english} — bu savol oxirida yana keladi", color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
    }
    Text(text = "Qoldi: ${p.choices.size}", color = colors.text.muted(0.45f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
}

@Composable
private fun MatchStep(p: PathState, viewModel: ChainPathViewModel) {
    val colors = LocalOnIkkiColors.current
    Text(text = "Inglizcha so'zni, keyin uning ma'nosini bosing", color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.matchWords.forEachIndexed { i, w ->
                MatchTile(w.english, done = i in p.matched, selected = p.selected == i, wrong = p.wrongPair?.first == i) { viewModel.selectLeft(i) }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.matchOrder.forEach { i ->
                MatchTile(Exercises.primary(p.matchWords[i].uzbek), done = i in p.matched, selected = false, wrong = p.wrongPair?.second == i) { viewModel.selectRight(i) }
            }
        }
    }
    if (p.matchDone) Text(text = "✓ Hammasi juftlandi" + if (p.mistakes > 0) " · xatolar: ${p.mistakes}" else "", color = colors.accent, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
}

@Composable
private fun MatchTile(text: String, done: Boolean, selected: Boolean, wrong: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val tint = when {
        done -> colors.accent
        wrong -> colors.warmAccent
        selected -> colors.habitAccent
        else -> colors.cardBorder
    }
    Text(
        text = text,
        color = if (done) colors.text.muted(0.4f) else colors.text,
        fontSize = 15.sp,
        fontFamily = OnIkkiFontFamily,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (done || selected || wrong) tint.copy(alpha = 0.14f) else colors.surface)
            .border(BorderStroke(1.5.dp, tint), RoundedCornerShape(14.dp))
            .clickable(enabled = !done, onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp)
    )
}

@Composable
private fun ListenStep(p: PathState, speaker: Speaker, viewModel: ChainPathViewModel) {
    val colors = LocalOnIkkiColors.current
    val w = p.heard ?: return
    // Each new word is read out once by itself.
    LaunchedEffect(w.english, p.listen.size) { if (speaker.ready) speaker.speak(w.english) }
    Text(text = "Eshiting va inglizcha yozing", color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(colors.accent).clickable(enabled = speaker.ready) { speaker.speak(w.english) },
            contentAlignment = Alignment.Center
        ) { Text(text = "🔊", fontSize = 30.sp) }
        Text(
            text = "🐢 sekin",
            color = colors.text,
            fontSize = 15.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(colors.surface).clickable(enabled = speaker.ready) { speaker.speakSlow(w.english) }.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
    OutlinedTextField(
        value = p.input,
        onValueChange = viewModel::type,
        singleLine = true,
        placeholder = { Text("eshitgan so'zingiz") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (p.verdict == null) viewModel.checkListen() else viewModel.nextListen() }),
        modifier = Modifier.fillMaxWidth()
    )
    p.verdict?.let { v ->
        Text(
            text = when (v) {
                Verdict.CORRECT -> "✓ To'g'ri: ${w.english} — ${Exercises.primary(w.uzbek)}"
                Verdict.ALMOST -> "✓ Deyarli. To'g'ri yozilishi: ${w.english}"
                Verdict.WRONG -> "✗ To'g'risi: ${w.english} — oxirida yana keladi"
            },
            color = if (v == Verdict.WRONG) colors.warmAccent else colors.accent,
            fontSize = 15.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
    Text(text = "Qoldi: ${p.listen.size}", color = colors.text.muted(0.45f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
}

/** Between steps: what comes next, so the path feels like a path. */
@Composable
internal fun PathIntro(p: PathState, onStart: () -> Unit, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        PathHeader(p, onClose)
        Column(
            modifier = Modifier.weight(1f).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "📚", fontSize = 56.sp)
            Text(text = "${p.words.size} ta yangi so'z", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            Text(text = p.words.joinToString(", ") { it.english }, color = colors.accent, fontSize = 15.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
            PathStep.entries.forEach { st ->
                Text(text = "${st.ordinal + 1}. ${st.label}", color = colors.text.muted(0.65f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            }
            Text(text = "Oson qadamlardan qiyiniga: avval tanib olasiz, keyin o'zingiz yozasiz.", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp)) {
            OnIkkiButton(text = "Boshlash", onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        }
    }
}
