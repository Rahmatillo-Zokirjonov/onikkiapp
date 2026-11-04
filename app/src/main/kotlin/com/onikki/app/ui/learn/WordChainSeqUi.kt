package com.onikki.app.ui.learn

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.SeqDirection
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker

private val LENGTHS = listOf(5, 10, 20, Int.MAX_VALUE)

/** "Ketma-ketlik" settings on the chain screen. */
@Composable
internal fun SeqCard(s: ChainState, viewModel: WordChainViewModel) {
    val colors = LocalOnIkkiColors.current
    val c by viewModel.seqConfig.collectAsState()
    val words = s.learnedWords
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "KETMA-KETLIK · ${words.size} TA SO'Z", color = colors.accent, style = OnIkkiType.kicker)
        Text(text = "Zanjirni istalgan joydan oldinga yoki orqaga tartib bilan ayting", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        if (words.size < 2) {
            Text(text = "Kamida 2 ta so'z kerak — 2-kundan ochiladi.", color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(SeqDirection.FORWARD to "Oldinga ➡", SeqDirection.BACKWARD to "⬅ Orqaga", SeqDirection.MIXED to "🔀 Aralash").forEach { (d, label) ->
                FilterChip(selected = c.direction == d, onClick = { viewModel.setSeqConfig { it.copy(direction = d) } }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Nechta:", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            LENGTHS.forEach { n ->
                FilterChip(selected = c.length == n, onClick = { viewModel.setSeqConfig { it.copy(length = n) } }, label = { Text(if (n == Int.MAX_VALUE) "Hammasi" else "$n") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = c.start == null, onClick = { viewModel.setSeqConfig { it.copy(start = null) } }, label = { Text("Tasodifiy joydan") })
            FilterChip(selected = c.start != null, onClick = { viewModel.setSeqConfig { it.copy(start = it.start ?: 0) } }, label = { Text("O'zim tanlayman") })
        }
        c.start?.let { start ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.background).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Stepper("−") { viewModel.setSeqConfig { it.copy(start = (start - 1).coerceAtLeast(0)) } }
                Text(
                    text = "${start + 1}-so'zdan: ${words[start].english}",
                    color = colors.text,
                    fontSize = 15.sp,
                    fontFamily = OnIkkiFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Stepper("+") { viewModel.setSeqConfig { it.copy(start = (start + 1).coerceAtMost(words.size - 1)) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !c.voice, onClick = { viewModel.setSeqConfig { it.copy(voice = false) } }, label = { Text("⌨ Yozib") })
            FilterChip(selected = c.voice, onClick = { viewModel.setSeqConfig { it.copy(voice = true) } }, label = { Text("🎤 Ovozda aytib") })
        }
        if (s.weakLinks > 0) {
            Text(text = "Zaif joylar: ${s.weakLinks} ta — ular ko'proq so'raladi", color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        OnIkkiButton(text = "Boshlash", onClick = viewModel::startSeq, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
    }
}

@Composable
private fun Stepper(sign: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Text(text = sign, color = colors.accent, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 2.dp))
}

/** Saying the chain: the last word is shown, the next (or previous) one is typed or said. */
@Composable
internal fun SeqScreen(s: SeqSession, speaker: Speaker, mic: SpeechInput, viewModel: WordChainViewModel) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    if (s.finished) { SeqResult(s, viewModel); return }
    val arrow = if (s.backward) "⬅ Orqaga" else "Oldinga ➡"
    val promptIndex = if (s.step == 0) s.anchor else s.targets[s.step - 1]
    val targetIndex = s.targets[s.step]
    val current = s.current ?: return
    val useVoice = s.voice && mic.available
    val listen = { mic.start { viewModel.seqHeard(it) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) listen() }
    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { mic.cancel(); viewModel.closeSeq() }.padding(horizontal = 10.dp, vertical = 4.dp))
            Text(text = "$arrow · ${s.step + 1}/${s.targets.size}", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Text(text = "✓ ${s.right}", color = colors.accent, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
        }
        LinearProgressTrack(progress = s.step / s.targets.size.toFloat(), trackColor = colors.neutral800, progressColor = colors.accent, height = 6.dp, modifier = Modifier.padding(horizontal = 18.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The words said so far, so the chain is felt as a chain.
            val trail = (listOf(s.anchor) + s.targets.take(s.step)).takeLast(4).dropLast(1)
            if (trail.isNotEmpty()) {
                Text(
                    text = trail.joinToString(if (s.backward) " ← " else " → ") { s.words[it].english },
                    color = colors.text.muted(0.45f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.heroBorder), RoundedCornerShape(22.dp))
                    .clickable(enabled = speaker.ready) { speaker.speak(s.prompt.english) }.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "${promptIndex + 1}-O'RIN", color = colors.text.muted(0.5f), style = OnIkkiType.kicker)
                Text(text = "${s.prompt.english} 🔊", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                Text(text = Exercises.primary(s.prompt.uzbek), color = colors.accent, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            }
            Text(
                text = if (s.backward) "Undan OLDINGI so'z (${targetIndex + 1}-o'rin)?" else "Undan KEYINGI so'z (${targetIndex + 1}-o'rin)?",
                color = colors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OnIkkiFontFamily
            )
            s.last?.let { a ->
                val w = s.words[a.target]
                Text(
                    text = when {
                        a.ok && a.verdict == Verdict.ALMOST -> "✓ Tartib to'g'ri. Imlosi: ${w.english}"
                        a.ok -> "✓ To'g'ri: ${w.english}"
                        a.verdict != Verdict.WRONG -> "✓ ${w.english} (yordam bilan)"
                        else -> "✗ To'g'risi: ${w.english}" + (s.heard?.takeIf { it.isNotBlank() }?.let { " · eshitildi: \"$it\"" } ?: "")
                    },
                    color = if (a.ok) colors.accent else colors.warmAccent,
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    textAlign = TextAlign.Center
                )
            }
            if (s.hinted) {
                Text(
                    text = "💡 ${Exercises.primary(current.uzbek)} · ${current.english.first()}… (${current.english.length} harf)",
                    color = colors.habitAccent,
                    fontSize = 15.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            if (useVoice) {
                Text(
                    text = if (mic.listening) mic.partial.ifBlank { "Eshityapman…" } else "🎤 ni bosib ayting — bir nechta so'zni ketma-ket aytsangiz ham bo'ladi",
                    color = colors.text.muted(0.6f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    textAlign = TextAlign.Center
                )
                mic.error?.let { Text(text = it, color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
                Box(
                    modifier = Modifier.size(76.dp).clip(CircleShape).background(if (mic.listening) colors.warmAccent else colors.accent).clickable {
                        if (mic.listening) mic.stop()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    contentAlignment = Alignment.Center
                ) { Text(text = if (mic.listening) "■" else "🎤", color = Color(0xFF10131C), fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            } else {
                if (s.voice) Text(text = "Telefonda ovozni tanish yo'q — yozib javob bering", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                OutlinedTextField(
                    value = s.input,
                    onValueChange = viewModel::seqType,
                    singleLine = true,
                    placeholder = { Text("inglizcha so'z") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.seqSubmit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!useVoice) OnIkkiButton(text = "Tekshirish", onClick = viewModel::seqSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnIkkiButton(text = "💡 Yordam", onClick = viewModel::seqHint, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                OnIkkiButton(text = "Bilmayman", onClick = { mic.cancel(); viewModel.seqGiveUp() }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SeqResult(s: SeqSession, viewModel: WordChainViewModel) {
    val colors = LocalOnIkkiColors.current
    val total = s.targets.size
    val misses = s.answers.withIndex().filter { !it.value.ok }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = if (misses.isEmpty()) "🌟" else if (s.right * 2 >= total) "🔗" else "💪", fontSize = 64.sp, modifier = Modifier.padding(top = 28.dp))
        Text(text = "$total tadan ${s.right} tasi to'g'ri", color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(
            text = (if (s.backward) "Orqaga" else "Oldinga") + ": ${s.anchor + 1}-so'zdan ${s.targets.last() + 1}-so'zgacha",
            color = colors.text.muted(0.6f),
            fontSize = 14.sp,
            fontFamily = OnIkkiFontFamily
        )
        if (misses.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Adashgan joylar (ular keyin ko'proq so'raladi)", color = colors.warmAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                misses.forEach { (i, a) ->
                    val from = s.words[if (i == 0) s.anchor else s.targets[i - 1]]
                    val to = s.words[a.target]
                    Text(
                        text = "${from.english} ${if (s.backward) "←" else "→"} ${to.english}" + (a.given.takeIf { it.isNotBlank() && a.verdict == Verdict.WRONG }?.let { "   (siz: $it)" } ?: ""),
                        color = colors.text,
                        fontSize = 15.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
        }
        OnIkkiButton(text = "Yana bir marta", onClick = viewModel::startSeq, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        OnIkkiButton(text = "Yopish", onClick = viewModel::closeSeq, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
    }
}

/** Replacing a chain word: the user's own, or one not met yet. */
@Composable
internal fun EditSheet(e: ChainEdit, viewModel: WordChainViewModel) {
    val colors = LocalOnIkkiColors.current
    var english by rememberSaveable(e.position) { mutableStateOf("") }
    var uzbek by rememberSaveable(e.position) { mutableStateOf("") }
    var example by rememberSaveable(e.position) { mutableStateOf("") }
    OnIkkiSheet(title = "${e.position + 1}-so'zni almashtirish", onDismiss = viewModel::closeEdit) {
        Text(text = "Hozir: ${e.old.english} — ${Exercises.primary(e.old.uzbek)}", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = "Yangi so'z aynan shu o'ringa tushadi, boshqa so'zlar siljimaydi. Eski so'z zanjirga qaytmaydi.",
            color = colors.text.muted(0.55f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        SheetFieldLabel("Inglizcha")
        OutlinedTextField(value = english, onValueChange = { english = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SheetFieldLabel("O'zbekcha")
        OutlinedTextField(value = uzbek, onValueChange = { uzbek = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SheetFieldLabel("Misol gap (ixtiyoriy)")
        OutlinedTextField(value = example, onValueChange = { example = it.take(140) }, modifier = Modifier.fillMaxWidth())
        SheetErrorText(e.error)
        OnIkkiButton(text = "O'z so'zimni qo'yish", onClick = { viewModel.saveOwn(english, uzbek, example) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp))
        OnIkkiButton(text = "Boshqa so'z ber (ilova tanlaydi)", onClick = viewModel::autoReplace, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
    }
}
