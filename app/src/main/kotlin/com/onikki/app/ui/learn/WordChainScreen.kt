package com.onikki.app.ui.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.domain.vocab.ChainDay
import com.onikki.app.domain.vocab.ChainDayState
import com.onikki.app.domain.vocab.Exercises
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
import java.time.format.DateTimeFormatter

private val MONTHS = listOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr")
private fun uzDate(d: java.time.LocalDate) = "${d.dayOfMonth}-${MONTHS[d.monthValue - 1]}"

/** Study Skills "so'z zanjiri": a new word every day, the whole chain recited in order. */
@Composable
fun WordChainRoute(onBack: () -> Unit) {
    val viewModel: WordChainViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val check by viewModel.check.collectAsState()
    val speaker = rememberSpeaker()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val c = check
    if (c != null) {
        BackHandler(onBack = viewModel::closeCheck)
        ChainCheckScreen(c, speaker, viewModel)
    } else {
        BackHandler(onBack = onBack)
        ChainHome(state, speaker, viewModel, onBack)
    }
}

@Composable
private fun ChainHome(s: ChainState, speaker: Speaker, viewModel: WordChainViewModel, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    var studyOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader(title = "So'z zanjiri", onBack = onBack) }
        item {
            Text(
                text = "Study Skills topshirig'i: har kuni 1 ta yangi so'z, va har kuni butun zanjirni boshidan tartib bilan aytasiz. ${uzDate(WordChain.END)}gacha.",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        // Today.
        item {
            val day = s.todayDay
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.heroBorder), RoundedCornerShape(22.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "BUGUN · ${s.today}-KUN / ${s.days.size}", color = colors.accent, style = OnIkkiType.kicker, modifier = Modifier.weight(1f))
                    Text(text = "🔥 ${s.streak} kun", color = colors.text.muted(0.7f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
                if (day == null) {
                    Text(text = if (s.finishedAll) "Zanjir tugadi! 🎉" else "Zanjir ${uzDate(s.start)} boshlanadi", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                } else {
                    Text(text = "Yangi so'z:", color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = day.word.english, color = colors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                        Text(text = "🔊", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(enabled = speaker.ready) { speaker.speak(day.word.english) }.padding(6.dp))
                    }
                    Text(text = day.word.uzbek, color = colors.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                    day.word.example?.let { Text(text = it, color = colors.text.muted(0.7f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily) }
                    LinearProgressTrack(progress = s.done.size / s.days.size.coerceAtLeast(1).toFloat(), trackColor = colors.neutral800, progressColor = colors.accent, height = 6.dp)
                    Text(
                        text = if (day.state == ChainDayState.DONE) "✓ Bugungi zanjir aytildi (${s.today} ta so'z)" else "Bugun ${s.today} ta so'zni tartib bilan ayting",
                        color = if (day.state == ChainDayState.DONE) colors.accent else colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = OnIkkiFontFamily
                    )
                    OnIkkiButton(text = if (day.state == ChainDayState.DONE) "Yana bir bor aytish" else "Zanjirni aytib berish", onClick = viewModel::startCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                    OnIkkiButton(
                        text = if (studyOpen) "Zanjirni yopish" else "Zanjirni ko'rib takrorlash (${s.today} ta)",
                        onClick = { studyOpen = !studyOpen },
                        variant = OnIkkiButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (studyOpen) {
            itemsIndexed(WordChain.chain(s.today)) { i, w ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).clickable(enabled = speaker.ready) { speaker.speak(w.english) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${i + 1}.", color = colors.text.muted(0.5f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.width(36.dp))
                    Text(text = w.english, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                    Text(text = Exercises.primary(w.uzbek), color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
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
        // The table.
        item {
            Text(text = "JADVAL", color = colors.text.muted(0.55f), style = OnIkkiType.kicker, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                listOf("Kun" to 0.6f, "Sana" to 1.1f, "Yangi so'z" to 1.6f, "Ma'nosi" to 1.6f, "Jami" to 0.6f, "" to 0.4f).forEach { (h, w) ->
                    Text(text = h, color = colors.text.muted(0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(w))
                }
            }
        }
        itemsIndexed(s.days, key = { _, d -> d.number }) { _, d -> TableRow(d, speaker) }
    }
}

@Composable
private fun TableRow(d: ChainDay, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    val bg = when (d.state) {
        ChainDayState.TODAY -> colors.accent.copy(alpha = 0.16f)
        else -> colors.surface
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).clickable(enabled = speaker.ready && d.state != ChainDayState.FUTURE) { speaker.speak(d.word.english) }.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val faded = d.state == ChainDayState.FUTURE
        val tint = if (faded) colors.text.muted(0.45f) else colors.text
        Text(text = "${d.number}", color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(0.6f))
        Text(text = uzDate(d.date), color = colors.text.muted(if (faded) 0.4f else 0.65f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1.1f))
        Text(text = d.word.english, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.6f))
        Text(text = Exercises.primary(d.word.uzbek), color = colors.text.muted(if (faded) 0.4f else 0.65f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.6f))
        Text(text = "${d.number}", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(0.6f))
        Text(
            text = when (d.state) {
                ChainDayState.DONE -> "✓"
                ChainDayState.TODAY -> "●"
                ChainDayState.MISSED -> "✗"
                ChainDayState.FUTURE -> ""
            },
            color = when (d.state) {
                ChainDayState.DONE -> colors.accent
                ChainDayState.TODAY -> colors.habitAccent
                ChainDayState.MISSED -> colors.warmAccent
                ChainDayState.FUTURE -> colors.text
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.4f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ChainCheckScreen(c: ChainCheck, speaker: Speaker, viewModel: WordChainViewModel) {
    val colors = LocalOnIkkiColors.current
    val passed = c.checked && WordChain.passed(c.verdicts) && c.hinted.isEmpty()
    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = viewModel::closeCheck).padding(horizontal = 10.dp, vertical = 4.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(text = "${c.day}-kun zanjiri", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "${c.chain.size} ta so'zni boshidan tartib bilan yozing", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
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
                            passed -> "✓ Zanjir to'g'ri! ${c.day}-kun bajarildi."
                            WordChain.passed(c.verdicts) -> "Hammasi to'g'ri, lekin yordam (💡) ishlatildi — yana bir bor yordamsiz ayting."
                            else -> "${c.right}/${c.chain.size} to'g'ri. Xatolarni ko'rib, qayta urinib ko'ring."
                        },
                        color = if (passed) colors.accent else colors.warmAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(14.dp)
                    )
                }
            }
            itemsIndexed(c.chain) { i, w ->
                val v = c.verdicts.getOrNull(i)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "${i + 1}.", color = colors.text.muted(0.5f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.width(32.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = c.inputs[i],
                            onValueChange = { viewModel.type(i, it) },
                            singleLine = true,
                            isError = v != null && v != Verdict.CORRECT,
                            placeholder = { if (i in c.hinted) Text(Exercises.primary(w.uzbek)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = if (i == c.chain.lastIndex) ImeAction.Done else ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (v != null && v != Verdict.CORRECT) {
                            Text(text = "To'g'risi: ${w.english} — ${Exercises.primary(w.uzbek)}", color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                        } else if (i in c.hinted && !c.checked) {
                            Text(text = "💡 ${Exercises.primary(w.uzbek)}", color = colors.habitAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
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
                text = "💡 — o'zbekchasini ko'rsatadi, lekin unda kun hisobga olinmaydi. Kichik-katta harf farqi yo'q, imlo aniq bo'lishi kerak.",
                color = colors.text.muted(0.45f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}
