package com.onikki.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.rememberSpeaker

/** V1 · V2 · V3 table of every irregular verb in the user's list, with a drill button. */
@Composable
fun IrregularVerbsScreen(
    words: List<VocabWord>,
    onBack: () -> Unit,
    onDrill: (List<VocabWord>) -> Unit,
    onPast: (List<VocabWord>, Boolean) -> Unit,
    sprintBest: Int,
    onSprintDone: (List<Pair<Long, Boolean>>, Int) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val speaker = rememberSpeaker()
    var sprint by rememberSaveable { mutableStateOf(false) }
    if (sprint) {
        androidx.activity.compose.BackHandler { sprint = false }
        VerbSprint(words.filter(Exercises::isIrregular), sprintBest, speaker, onDone = { r, n -> onSprintDone(r, n) }, onClose = { sprint = false })
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    val verbs = remember(words) { words.filter(Exercises::isIrregular).sortedBy { it.english.lowercase() } }
    val shown = remember(verbs, query, level) {
        val q = AnswerCheck.normalize(query)
        verbs.filter { v ->
            (level == null || v.level == level) &&
                (q.isEmpty() || listOfNotNull(v.english, v.pastForm, v.participle, v.uzbek).any { AnswerCheck.normalize(it).contains(q) })
        }
    }
    val learned = verbs.count { it.status == WordStatus.REVIEW || it.status == WordStatus.MASTERED }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SubScreenHeader(title = "Noto'g'ri fe'llar", onBack = onBack) }
        item {
            Text(
                text = "${verbs.size} ta fe'l · $learned tasi o'rganilgan. Qatorni bossangiz — uch shakli ovozda o'qiladi.",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        item {
            OnIkkiButton(
                text = "Mashq qilish (10 ta fe'l)",
                onClick = { onDrill(shown.ifEmpty { verbs }) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnIkkiButton(text = "go → went", onClick = { onPast(shown.ifEmpty { verbs }, false) }, variant = com.onikki.app.ui.components.OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                OnIkkiButton(text = "went → go", onClick = { onPast(shown.ifEmpty { verbs }, true) }, variant = com.onikki.app.ui.components.OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                OnIkkiButton(text = "⏱ 60 s", onClick = { sprint = true }, variant = com.onikki.app.ui.components.OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(0.8f))
            }
        }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Qidirish: go, went, bormoq…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<String?>(null, "A1", "A2", "B1").forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l ?: "Hammasi") }) }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                listOf("V1", "V2", "V3").forEach { Text(text = it, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f)) }
                Box(modifier = Modifier.size(10.dp))
            }
        }
        items(shown, key = { it.id }) { v ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .clickable(enabled = speaker.ready) { speaker.speak(verbLine(v)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = v.english, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                    Text(text = v.pastForm.orEmpty(), color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                    Text(text = v.participle.orEmpty(), color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(
                            when (v.status) {
                                WordStatus.MASTERED -> colors.accent
                                WordStatus.REVIEW -> colors.habitAccent
                                WordStatus.LEARNING -> colors.planAccent
                                WordStatus.NEW -> colors.neutral700
                            }
                        )
                    )
                }
                Text(
                    text = Exercises.primary(v.uzbek) + (v.level?.let { " · $it" } ?: ""),
                    color = colors.text.muted(0.55f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (verbs.isEmpty()) {
            item { Text(text = "Hali fe'l yo'q. So'z qo'shishda \"go (went, gone) | bormoq\" ko'rinishida yozing.", color = colors.text.muted(0.6f), fontFamily = OnIkkiFontFamily) }
        }
    }
}

/**
 * "60 soniya": base forms one after another, type the past form, Enter — right or wrong, the next one
 * comes at once. The best score is kept.
 */
@Composable
private fun VerbSprint(verbs: List<VocabWord>, best: Int, speaker: com.onikki.app.ui.vocabulary.Speaker, onDone: (List<Pair<Long, Boolean>>, Int) -> Unit, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    var started by remember { mutableStateOf(false) }
    var left by remember { androidx.compose.runtime.mutableIntStateOf(60) }
    var order by remember { mutableStateOf(verbs.shuffled()) }
    var index by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Pair<Long, Boolean>>()) }
    var flash by remember { mutableStateOf<Boolean?>(null) }
    var finished by remember { mutableStateOf(false) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val score = results.count { it.second }
    androidx.compose.runtime.LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (left > 0) { kotlinx.coroutines.delay(1000); left-- }
        finished = true
        onDone(results, results.count { it.second })
    }
    androidx.compose.runtime.LaunchedEffect(flash) { if (flash != null) { kotlinx.coroutines.delay(350); flash = null } }
    fun submit() {
        if (!started || finished || order.isEmpty()) return
        val v = order[index % order.size]
        val ok = com.onikki.app.domain.vocab.Spelling.isRight(text, com.onikki.app.domain.screentime.VocabChallenge.splitAnswers(v.pastForm.orEmpty()))
        results = results + (v.id to ok)
        flash = ok
        text = ""
        index++
        if (index % order.size == 0) order = order.shuffled()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubScreenHeader(title = "60 soniya: V1 → V2", onBack = onClose)
        Text(text = "Rekord: ${maxOf(best, if (finished) score else 0)}", color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        Text(text = "0:%02d".format(left), color = if (left <= 10) colors.warmAccent else colors.text, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        when {
            finished -> {
                Text(text = "$score ta to'g'ri", color = colors.accent, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = if (score > best) "🏆 Yangi rekord!" else "${results.size} ta urinish", color = colors.text.muted(0.7f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                val missed = results.filter { !it.second }.mapNotNull { r -> verbs.firstOrNull { it.id == r.first } }.distinctBy { it.id }
                if (missed.isNotEmpty()) {
                    Text(text = "Xato qilinganlar:", color = colors.warmAccent, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                    missed.take(12).forEach { v -> Text(text = "${v.english} → ${v.pastForm}", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily) }
                }
                OnIkkiButton(text = "Yana o'ynash", onClick = { left = 60; index = 0; results = emptyList(); order = verbs.shuffled(); finished = false; started = false }, modifier = Modifier.fillMaxWidth())
            }
            !started -> {
                Text(text = "Asosiy shakl chiqadi — o'tgan shaklini yozib, Enter bosing. Iloji boricha ko'p!", color = colors.text.muted(0.7f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OnIkkiButton(text = "Boshlash", onClick = { started = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
            }
            else -> {
                val v = order[index % order.size]
                Text(
                    text = v.english,
                    color = when (flash) { true -> colors.accent; false -> colors.warmAccent; null -> colors.text },
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OnIkkiFontFamily
                )
                Text(text = Exercises.primary(v.uzbek), color = colors.text.muted(0.6f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    label = { Text("V2") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                OnIkkiButton(text = "Keyingi", onClick = ::submit, modifier = Modifier.fillMaxWidth())
                Text(text = "To'g'ri: $score", color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
            }
        }
    }
}
