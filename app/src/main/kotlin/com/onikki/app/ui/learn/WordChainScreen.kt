package com.onikki.app.ui.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.domain.vocab.ChainDay
import com.onikki.app.domain.vocab.ChainDayState
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.domain.vocab.WordChain
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker
import com.onikki.app.ui.vocabulary.rememberSpeaker

private val MONTHS = listOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr")
private fun uzDate(d: java.time.LocalDate) = "${d.dayOfMonth}-${MONTHS[d.monthValue - 1]}"

/** Study Skills "so'z zanjiri": day n brings n new words, until 15 January. */
@Composable
fun WordChainRoute(onBack: () -> Unit) {
    val viewModel: WordChainViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val check by viewModel.check.collectAsState()
    val seq by viewModel.seq.collectAsState()
    val edit by viewModel.edit.collectAsState()
    val speaker = rememberSpeaker()
    val mic = rememberSpeechInput()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val c = check
    val q = seq
    if (q != null) {
        BackHandler { mic.cancel(); viewModel.closeSeq() }
        SeqScreen(q, speaker, mic, viewModel)
    } else if (c != null) {
        BackHandler(onBack = viewModel::closeCheck)
        ChainCheckScreen(c, speaker, viewModel)
    } else {
        BackHandler(onBack = onBack)
        ChainHome(state, speaker, viewModel, onBack)
        edit?.let { EditSheet(it, viewModel) }
    }
}

/** A word to learn: English (tap to hear), meaning, example; ✎ replaces it. */
@Composable
private fun WordRow(i: Int, w: ImportedWord, speaker: Speaker, onEdit: (() -> Unit)? = null) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background).clickable(enabled = speaker.ready) { speaker.speak(w.english) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "${i + 1}.", color = colors.text.muted(0.45f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.width(30.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = w.english, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                Text(text = "  ${Exercises.primary(w.uzbek)}", color = colors.accent, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            w.example?.let { Text(text = it, color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily) }
        }
        Text(text = "🔊", fontSize = 15.sp)
        if (onEdit != null) {
            Text(text = "✎", color = colors.text.muted(0.6f), fontSize = 17.sp, modifier = Modifier.padding(start = 6.dp).clip(CircleShape).clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun ChainHome(s: ChainState, speaker: Speaker, viewModel: WordChainViewModel, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    var openDay by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader(title = "So'z zanjiri", onBack = onBack) }
        item {
            Text(
                text = "Study Skills topshirig'i: har kuni yangi so'zlar bittaga ko'payadi — 1-kun 1 ta, 2-kun 2 ta, 3-kun 3 ta … ${uzDate(WordChain.END)}gacha. Har kuni o'sha kunning so'zlarini yodlab, tartib bilan yozasiz.",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        // Today.
        item {
            val day = s.todayDay
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.heroBorder), RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "BUGUN · ${s.today}-KUN / ${s.days.size}", color = colors.accent, style = OnIkkiType.kicker, modifier = Modifier.weight(1f))
                    Text(text = "🔥 ${s.streak} kun", color = colors.text.muted(0.7f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
                if (day == null) {
                    Text(text = if (s.finishedAll) "Zanjir tugadi! 🎉" else "Zanjir ${uzDate(s.start)} boshlanadi", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                } else {
                    Text(
                        text = "Bugun ${day.number} ta yangi so'z" + if (day.state == ChainDayState.DONE) " ✓" else "",
                        color = colors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OnIkkiFontFamily
                    )
                    Text(text = "Jami ${day.total} ta so'z · bajarilgan kunlarda ${s.learned} ta", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    LinearProgressTrack(progress = s.done.size / s.days.size.coerceAtLeast(1).toFloat(), trackColor = colors.neutral800, progressColor = colors.accent, height = 6.dp)
                    day.words.forEachIndexed { i, w -> WordRow(i, w, speaker) { viewModel.startEdit(WordChain.totalUpTo(day.number - 1) + i) } }
                    if (!day.complete) MoreWordsNote(day)
                    OnIkkiButton(
                        text = if (day.state == ChainDayState.DONE) "Yana bir bor yozib ko'rish" else "Yodladim — yozib tekshirish",
                        onClick = { viewModel.startCheck(day.number) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    )
                }
            }
        }
        item { SeqCard(s, viewModel) }
        // Start date (if the task began on another day).
        item {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Boshlangan sana: ${uzDate(s.start)}", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                Text(text = "−", color = colors.accent, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { viewModel.shiftStart(-1) }.padding(horizontal = 12.dp, vertical = 2.dp))
                Text(text = "+", color = colors.accent, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { viewModel.shiftStart(1) }.padding(horizontal = 12.dp, vertical = 2.dp))
            }
        }
        s.shortDay?.let { d ->
            item {
                Text(
                    text = "ℹ Ilovadagi so'zlar ${d - 1}-kungacha yetadi (${uzDate(s.start.plusDays((d - 2).toLong()))}). Keyingi kunlar uchun so'z qo'shish imkoniyati keyingi yangilanishda.",
                    color = colors.text.muted(0.55f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        // The table.
        item { Text(text = "JADVAL · qatorni bossangiz so'zlari ochiladi", color = colors.text.muted(0.55f), style = OnIkkiType.kicker, modifier = Modifier.padding(top = 6.dp)) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                listOf("Kun" to 0.6f, "Sana" to 1.2f, "Yangi" to 0.9f, "Jami" to 0.9f, "Holat" to 0.9f).forEach { (h, w) ->
                    Text(text = h, color = colors.text.muted(0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(w))
                }
            }
        }
        items(s.days, key = { it.number }) { d ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TableRow(d, open = openDay == d.number) { openDay = if (openDay == d.number) null else d.number }
                if (openDay == d.number) {
                    d.words.forEachIndexed { i, w -> WordRow(i, w, speaker) { viewModel.startEdit(WordChain.totalUpTo(d.number - 1) + i) } }
                    if (!d.complete) MoreWordsNote(d)
                    if (d.state != ChainDayState.FUTURE && d.words.isNotEmpty()) {
                        OnIkkiButton(
                            text = if (d.state == ChainDayState.DONE) "${d.number}-kunni qayta yozish" else "${d.number}-kunni yozib tekshirish",
                            onClick = { viewModel.startCheck(d.number) },
                            variant = OnIkkiButtonVariant.SECONDARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreWordsNote(d: ChainDay) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = "Bu kunga ${d.number} ta so'z kerak, ilovada ${d.words.size} tasi qoldi.",
        color = colors.warmAccent,
        fontSize = 12.sp,
        fontFamily = OnIkkiFontFamily
    )
}

@Composable
private fun TableRow(d: ChainDay, open: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val bg = if (d.state == ChainDayState.TODAY || open) colors.accent.copy(alpha = 0.16f) else colors.surface
    val faded = d.state == ChainDayState.FUTURE
    val tint = if (faded) colors.text.muted(0.45f) else colors.text
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "${d.number}", color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(0.6f))
        Text(text = uzDate(d.date), color = colors.text.muted(if (faded) 0.4f else 0.65f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1.2f))
        Text(text = "+${d.number}", color = if (faded) tint else colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(0.9f))
        Text(text = "${d.total}", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(0.9f))
        Text(
            text = when (d.state) {
                ChainDayState.DONE -> "✓"
                ChainDayState.TODAY -> "● bugun"
                ChainDayState.MISSED -> "✗"
                ChainDayState.FUTURE -> ""
            },
            color = when (d.state) {
                ChainDayState.DONE -> colors.accent
                ChainDayState.TODAY -> colors.habitAccent
                ChainDayState.MISSED -> colors.warmAccent
                ChainDayState.FUTURE -> colors.text
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ChainCheckScreen(c: ChainCheck, speaker: Speaker, viewModel: WordChainViewModel) {
    val colors = LocalOnIkkiColors.current
    val passed = c.checked && WordChain.passed(c.verdicts) && c.hinted.isEmpty()
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = viewModel::closeCheck).padding(horizontal = 10.dp, vertical = 4.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(text = "${c.day}-kun · ${c.words.size} ta so'z", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "O'zbekchasiga qarab inglizchasini tartib bilan yozing", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (c.checked) {
                item {
                    Text(
                        text = when {
                            passed -> "✓ Hammasi to'g'ri! ${c.day}-kun bajarildi."
                            WordChain.passed(c.verdicts) -> "Hammasi to'g'ri, lekin yordam (💡) ishlatildi — yana bir bor yordamsiz yozing."
                            else -> "${c.right}/${c.words.size} to'g'ri. Xatolarni ko'rib, qayta urinib ko'ring."
                        },
                        color = if (passed) colors.accent else colors.warmAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(14.dp)
                    )
                }
            }
            itemsIndexed(c.words) { i, w ->
                val v = c.verdicts.getOrNull(i)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "${i + 1}.", color = colors.text.muted(0.5f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.width(30.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = Exercises.primary(w.uzbek), color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                        OutlinedTextField(
                            value = c.inputs[i],
                            onValueChange = { viewModel.type(i, it) },
                            singleLine = true,
                            isError = v != null && v != Verdict.CORRECT,
                            placeholder = { if (i in c.hinted) Text(w.english.first() + "…" + " (${w.english.length} harf)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = if (i == c.words.lastIndex) ImeAction.Done else ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (v != null && v != Verdict.CORRECT) {
                            Text(text = "To'g'risi: ${w.english}", color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                        }
                    }
                    when {
                        v == Verdict.CORRECT -> Text(text = "✓", color = colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        v != null -> Text(text = "🔊", fontSize = 18.sp, modifier = Modifier.clip(CircleShape).clickable(enabled = speaker.ready) { speaker.speak(w.english) }.padding(4.dp))
                        i !in c.hinted -> Text(text = "💡", fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { viewModel.hint(i) }.padding(4.dp))
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !c.checked -> OnIkkiButton(text = "Tekshirish", onClick = viewModel::submit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                passed -> OnIkkiButton(text = "Tayyor", onClick = viewModel::closeCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                else -> {
                    OnIkkiButton(text = "Qayta urinish", onClick = viewModel::retry, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                    OnIkkiButton(text = "Yopish", onClick = viewModel::closeCheck, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
            Text(
                text = "💡 — birinchi harfi va harflar sonini ko'rsatadi, lekin unda kun hisobga olinmaydi. Imlo aniq bo'lishi kerak.",
                color = colors.text.muted(0.45f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}
