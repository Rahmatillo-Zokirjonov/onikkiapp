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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.domain.vocab.FamilyQuestion
import com.onikki.app.domain.vocab.PairQuestion
import com.onikki.app.domain.vocab.Spelling
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.domain.vocab.WordFamilies
import com.onikki.app.domain.vocab.WordFamily
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker
import com.onikki.app.ui.vocabulary.rememberSpeaker

/** A family word with its root part in the accent colour and the ending highlighted ("danger" + "ous"). */
@Composable
fun familyWord(root: String, word: String) = buildAnnotatedString {
    val colors = LocalOnIkkiColors.current
    val (stem, ending) = WordFamilies.split(root, word)
    withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) { append(stem) }
    withStyle(SpanStyle(color = colors.warmAccent, fontWeight = FontWeight.Bold)) { append(ending) }
}

/** The family chips shown on a word's card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyChips(family: WordFamily, current: String, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "So'z oilasi", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(family.root to family.rootUz) + family.members.map { it.word to it.uzbek }).forEach { (w, uz) ->
                val here = w.equals(current, ignoreCase = true)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (here) colors.accent.copy(alpha = 0.15f) else colors.background)
                        .border(BorderStroke(1.dp, if (here) colors.accent else colors.cardBorder), RoundedCornerShape(12.dp))
                        .clickable(enabled = speaker.ready) { speaker.speak(w) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = familyWord(family.root, w), fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                    Text(text = uz, color = colors.text.muted(0.55f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- confusing pairs

@Composable
fun ConfusingPairsRoute(onBack: () -> Unit) {
    val vm: WordPracticeViewModel = viewModel()
    val round by vm.pairs.collectAsState()
    val custom by vm.customPairs.collectAsState()
    val speaker = rememberSpeaker()
    var adding by remember { mutableStateOf(false) }
    val colors = LocalOnIkkiColors.current
    val r = round
    if (r != null) {
        BackHandler(onBack = vm::closePairs)
        PairRound(r, speaker, vm)
        return
    }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubScreenHeader(title = "Chalkash juftlar", onBack = onBack)
        Text(text = "Bo'sh joyli gap va 2 ta variant — to'g'risini tanlang, keyin nega shunday ekanini o'qing.", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        OnIkkiButton(text = "Mashq qilish (10 ta)", onClick = vm::startPairs, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
        OnIkkiButton(text = "＋ O'z juftimni qo'shish", onClick = { adding = true }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
        com.onikki.app.domain.vocab.ConfusingPairs.PAIRS.forEach { p ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "${p.first} / ${p.second}", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                Text(text = "${p.firstUz} / ${p.secondUz}", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                Text(text = p.rule, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        if (custom.isNotEmpty()) Text(text = "MENING JUFTLARIM", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
        custom.forEach { p ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${p.first} / ${p.second}", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                    Text(text = p.sentence, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                }
                Text(text = "✕", color = colors.text.muted(0.5f), fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { vm.deletePair(p) }.padding(6.dp))
            }
        }
    }
    if (adding) AddPairSheet(onDismiss = { adding = false }, onSave = vm::addPair)
}

@Composable
private fun PairRound(r: WordRound<PairQuestion>, speaker: Speaker, vm: WordPracticeViewModel) {
    val colors = LocalOnIkkiColors.current
    if (r.done) {
        RoundResult("Chalkash juftlar", r.right, r.questions.size, onAgain = vm::startPairs, onClose = vm::closePairs)
        return
    }
    val q = r.current ?: return
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        RoundHeader(r.index, r.questions.size, onClose = vm::closePairs)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = "To'g'ri so'zni tanlang", color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            Text(
                text = if (r.verdict == null) q.sentence else q.sentence.replace("___", q.right),
                color = colors.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 30.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(20.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { i, o ->
                    val bg = when {
                        r.verdict == null -> colors.surface
                        i == q.answer -> colors.accent.copy(alpha = 0.25f)
                        i == r.picked -> colors.warmAccent.copy(alpha = 0.25f)
                        else -> colors.surface
                    }
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = 72.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                            .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(18.dp))
                            .clickable(enabled = r.verdict == null) { vm.pickPair(i); speaker.speak(q.sentence.replace("___", q.right)) }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = o, color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                        if (r.verdict != null) Text(text = if (i == 0) q.pair.firstUz else q.pair.secondUz, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                    }
                }
            }
            if (r.verdict != null) {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = if (r.verdict == Verdict.CORRECT) "✓ To'g'ri!" else "✗ To'g'risi: ${q.right}", color = if (r.verdict == Verdict.CORRECT) colors.accent else colors.warmAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                    if (q.why.isNotBlank()) Text(text = "💡 ${q.why}", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                    if (q.pair.rule.isNotBlank() && q.pair.rule != q.why) Text(text = q.pair.rule, color = colors.text.muted(0.65f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        }
        if (r.verdict != null) {
            Box(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp)) {
                OnIkkiButton(text = "Davom etish", onClick = vm::nextPair, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
            }
        }
    }
}

@Composable
private fun AddPairSheet(onDismiss: () -> Unit, onSave: (String, String, String, String, String, Int, String) -> Boolean) {
    val colors = LocalOnIkkiColors.current
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var firstUz by remember { mutableStateOf("") }
    var secondUz by remember { mutableStateOf("") }
    var sentence by remember { mutableStateOf("") }
    var answer by remember { mutableIntStateOf(0) }
    var why by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OnIkkiSheet(title = "Yangi juft", onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text("1-so'z") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = second, onValueChange = { second = it }, label = { Text("2-so'z") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = firstUz, onValueChange = { firstUz = it }, label = { Text("ma'nosi") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = secondUz, onValueChange = { secondUz = it }, label = { Text("ma'nosi") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = sentence, onValueChange = { sentence = it; error = null }, label = { Text("Gap, bo'sh joy ___ bilan") }, modifier = Modifier.fillMaxWidth())
        Text(text = "Qaysi so'z to'g'ri?", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = answer == 0, onClick = { answer = 0 }, label = { Text(first.ifBlank { "1-so'z" }) })
            FilterChip(selected = answer == 1, onClick = { answer = 1 }, label = { Text(second.ifBlank { "2-so'z" }) })
        }
        OutlinedTextField(value = why, onValueChange = { why = it }, label = { Text("Nega? (qisqa izoh, ixtiyoriy)") }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(text = it, color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
        OnIkkiButton(
            text = "Saqlash",
            onClick = {
                if (onSave(first, second, firstUz, secondUz, sentence, answer, why)) onDismiss()
                else error = "Ikkala so'zni yozing va gapda ___ bo'lsin"
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------- word families

@Composable
fun WordFamiliesRoute(onBack: () -> Unit) {
    val vm: WordPracticeViewModel = viewModel()
    val round by vm.families.collectAsState()
    val speaker = rememberSpeaker()
    val colors = LocalOnIkkiColors.current
    val r = round
    if (r != null) {
        BackHandler(onBack = vm::closeFamilies)
        FamilyRound(r, speaker, vm)
        return
    }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubScreenHeader(title = "So'z oilalari", onBack = onBack)
        Text(text = "Ildiz so'z va gap beriladi — gapga mos shaklini yozasiz: This road is ___. (danger) → dangerous", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        OnIkkiButton(text = "Mashq qilish (10 ta)", onClick = vm::startFamilies, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
        WordFamilies.ALL.forEach { fam ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp)) {
                FamilyChips(fam, current = "", speaker = speaker)
            }
        }
        Text(text = "O'z so'zingizni oilaga qo'shish: So'z yodlash → so'zni tahrirlash → \"Ildiz so'z\".", color = colors.text.muted(0.45f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun FamilyRound(r: WordRound<FamilyQuestion>, speaker: Speaker, vm: WordPracticeViewModel) {
    val colors = LocalOnIkkiColors.current
    if (r.done) {
        RoundResult("So'z oilalari", r.right, r.questions.size, onAgain = vm::startFamilies, onClose = vm::closeFamilies)
        return
    }
    val q = r.current ?: return
    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        RoundHeader(r.index, r.questions.size, onClose = vm::closeFamilies)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = "To'g'ri shaklini yozing", color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (r.verdict == null) q.member.sentence else q.member.sentence.replace("___", q.member.word),
                    color = colors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 30.sp,
                    fontFamily = OnIkkiFontFamily
                )
                Text(text = "(${q.family.root} — ${q.family.rootUz})", color = colors.accent, fontSize = 16.sp, fontFamily = OnIkkiFontFamily)
            }
            OutlinedTextField(
                value = r.typed,
                onValueChange = vm::typeFamily,
                label = { Text("Shakli") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.checkFamily() }),
                modifier = Modifier.fillMaxWidth()
            )
            if (r.verdict == null) {
                OnIkkiButton(text = "Tekshirish", onClick = vm::checkFamily, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
            } else {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = if (r.verdict == Verdict.CORRECT) "✓ To'g'ri!" else "✗ Noto'g'ri", color = if (r.verdict == Verdict.CORRECT) colors.accent else colors.warmAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                    if (r.verdict != Verdict.CORRECT) DiffRow(Spelling.diff(r.typed, q.member.word), size = 22)
                    Text(text = familyWord(q.family.root, q.member.word), fontSize = 26.sp, fontFamily = OnIkkiFontFamily)
                    Text(text = q.member.uzbek, color = colors.text.muted(0.65f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                    FamilyChips(q.family, q.member.word, speaker)
                }
                OnIkkiButton(text = "Davom etish", onClick = vm::nextFamily, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- shared

@Composable
private fun RoundHeader(index: Int, size: Int, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp))
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            com.onikki.app.ui.components.LinearProgressTrack(progress = index / size.coerceAtLeast(1).toFloat(), trackColor = colors.neutral800, progressColor = colors.habitAccent, height = 12.dp)
        }
        Text(text = "${index + 1}/$size", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun RoundResult(title: String, right: Int, total: Int, onAgain: () -> Unit, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val pct = if (total == 0) 0 else right * 100 / total
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(text = if (pct >= 90) "🌟" else if (pct >= 70) "🏆" else "💪", fontSize = 68.sp)
        Text(text = title, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(text = "$right/$total to'g'ri · $pct%", color = colors.text.muted(0.7f), fontSize = 17.sp, fontFamily = OnIkkiFontFamily)
        OnIkkiButton(text = "Yana mashq", onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        OnIkkiButton(text = "Yopish", onClick = onClose, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
    }
}
