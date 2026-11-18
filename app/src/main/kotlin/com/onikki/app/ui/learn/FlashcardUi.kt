package com.onikki.app.ui.learn

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.FlashCard
import com.onikki.app.domain.vocab.FlashDirection
import com.onikki.app.domain.vocab.FlashGrade
import com.onikki.app.domain.vocab.FlashSession
import com.onikki.app.domain.vocab.Flashcards
import com.onikki.app.domain.vocab.ImportedWord
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

/** O'rganish → Flashcard: the dictionary's words as flip cards. */
@Composable
fun FlashcardRoute(onBack: () -> Unit) {
    val viewModel: FlashcardViewModel = viewModel()
    val session by viewModel.session.collectAsState()
    val speaker = rememberSpeaker()
    LaunchedEffect(Unit) { viewModel.loadSetup() }
    val s = session
    if (s != null) {
        BackHandler(onBack = viewModel::close)
        FlashcardScreen(s, speaker, viewModel)
    } else {
        BackHandler(onBack = onBack)
        VocabFlashSetup(viewModel, onBack)
    }
}

@Composable
private fun VocabFlashSetup(viewModel: FlashcardViewModel, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val setup by viewModel.setup.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubScreenHeader(title = "Flashcard", onBack = onBack)
        Text(
            text = "Kartani bosing — aylanadi. O'zingizni tekshirib, ❌ Bilmadim / 🤔 Qiyin / ✅ Bildim ni tanlang. Bilmagan so'z bir necha kartadan keyin yana chiqadi; javoblar takrorlash jadvaliga ham yoziladi.",
            color = colors.text.muted(0.6f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        DirectionChips(viewModel)
        if (setup.decks.size > 1) {
            Text(text = "TO'PLAM", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = setup.deckId == null, onClick = { viewModel.loadSetup(null) }, label = { Text("Hammasi") })
                setup.decks.forEach { d -> FilterChip(selected = setup.deckId == d.id, onClick = { viewModel.loadSetup(d.id) }, label = { Text(d.name) }) }
            }
        }
        FlashSource.entries.forEach { src ->
            val n = setup.counts[src] ?: 0
            SourceButton(
                title = src.label,
                subtitle = when (src) {
                    FlashSource.DUE -> if (n == 0) "Hozir takrorlash kerak bo'lgan so'z yo'q" else "$n ta so'z — unutilish arafasidagilar"
                    FlashSource.WEAK -> if (n == 0) "Qiyin so'z topilmadi" else "$n ta — ko'p adashilgan va kartada bilinmaganlar"
                    FlashSource.ALL -> if (n == 0) "Lug'atda so'z yo'q" else "$n ta — eng kam bilinganlaridan boshlab"
                },
                enabled = n > 0
            ) { viewModel.startVocab(src) }
        }
        LeitnerSummary(setup.boxes)
    }
}

@Composable
private fun DirectionChips(viewModel: FlashcardViewModel) {
    val direction by viewModel.direction.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FlashDirection.entries.forEach { d -> FilterChip(selected = direction == d, onClick = { viewModel.setDirection(d) }, label = { Text(d.label) }) }
    }
}

@Composable
private fun SourceButton(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)
            .border(BorderStroke(1.dp, if (enabled) colors.accent700 else colors.cardBorder), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "🃏 $title", color = if (enabled) colors.text else colors.text.muted(0.45f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        Text(text = subtitle, color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
    }
}

/** How many cards sit in each Leitner box. */
@Composable
private fun LeitnerSummary(boxes: Map<String, Int>) {
    if (boxes.isEmpty()) return
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "LEITNER QUTILARI", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..Flashcards.MAX_BOX).forEach { b ->
                val n = boxes.values.count { it == b }
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.background).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "$n", color = if (b <= 2) colors.warmAccent else colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                    Text(text = "$b-quti", color = colors.text.muted(0.5f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        }
        Text(text = "1-qutidagilar eng ko'p chiqadi; har \"Bildim\" so'zni bir quti yuqoriga, \"Bilmadim\" 1-qutiga qaytaradi.", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
    }
}

/** "So'z zanjiri" → flashcards of today's words, the whole chain or the weak ones. */
@Composable
internal fun ChainFlashCard(s: ChainState, viewModel: FlashcardViewModel) {
    val colors = LocalOnIkkiColors.current
    val today = s.todayDay?.words.orEmpty()
    val learned = s.learnedWords
    val review = s.reviewWords
    val reviewed by viewModel.reviewedToday.collectAsState()
    // Re-read when the boxes change, so the weak count follows the answers.
    val boxes by viewModel.boxes.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "🃏 FLASHCARD", color = colors.accent, style = OnIkkiType.kicker)
        Text(text = "So'zlarni kartalar bilan yodlang", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        DirectionChips(viewModel)
        listOf(
            ChainFlash.TODAY to "Bugungi yangi so'zlar",
            ChainFlash.REVIEW to (if (reviewed) "✓ Takrorlash bajarildi (1, 3, 7 kun oldingi)" else "🔁 Takrorlash: 1, 3 va 7 kun oldingi so'zlar"),
            ChainFlash.CHAIN_ORDER to "Butun zanjir — tartib bilan",
            ChainFlash.CHAIN_MIXED to "Butun zanjir — aralash",
            ChainFlash.WEAK to "Qiyin so'zlar"
        ).forEach { (kind, title) ->
            val n = remember(kind, boxes, today, learned, review) { viewModel.chainCount(kind, today, learned, review) }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.background)
                    .clickable(enabled = n > 0) { viewModel.startChain(kind, today, learned, review) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = if (n > 0) colors.text else colors.text.muted(0.4f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                Text(text = "$n ta ›", color = if (n > 0) colors.accent else colors.text.muted(0.4f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
        }
    }
}

/** The run: one flip card at a time, graded by the user. */
@Composable
internal fun FlashcardScreen(s: FlashSession, speaker: Speaker, viewModel: FlashcardViewModel, onFinished: (() -> Unit)? = null) {
    val colors = LocalOnIkkiColors.current
    val boxes by viewModel.boxes.collectAsState()
    if (s.finished && onFinished != null) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    if (s.finished) { FlashResult(s, speaker, viewModel); return }
    val card = s.current ?: return
    val uzFirst = s.currentUzFirst
    var editing by androidx.compose.runtime.saveable.rememberSaveable(card.key) { androidx.compose.runtime.mutableStateOf(false) }
    // The English side is read out when it shows.
    LaunchedEffect(card.key, s.flipped, s.done) {
        if (speaker.ready && (uzFirst == s.flipped)) speaker.speak(card.english)
    }
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = viewModel::close).padding(horizontal = 10.dp, vertical = 4.dp))
            Text(text = "Flashcard · ${s.done}/${s.total}", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Text(text = "✓ ${s.firstTry.size}", color = colors.accent, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
        }
        LinearProgressTrack(progress = s.done / s.total.coerceAtLeast(1).toFloat(), trackColor = colors.neutral800, progressColor = colors.accent, height = 6.dp, modifier = Modifier.padding(horizontal = 18.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FlipCard(card, uzFirst, s.flipped, boxes[card.key] ?: 0, speaker, viewModel::flip)
            if (s.flipped && card.wordId != null) {
                Text(
                    text = if (card.mnemonic == null) "✎ Eslab qolish uchun o'z eslatmangizni yozing" else "✎ Eslatmani o'zgartirish",
                    color = colors.habitAccent,
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).clickable { editing = true }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (editing) MnemonicDialog(card, onSave = { viewModel.saveMnemonic(it); editing = false }, onDismiss = { editing = false })
            Text(
                text = if (s.flipped) "O'zingizni tekshiring: bildingizmi?" else "Eslab ko'ring, keyin kartani bosing",
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp)) {
            if (!s.flipped) {
                OnIkkiButton(text = "Aylantirish", onClick = viewModel::flip, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradeButton("❌", "Bilmadim", colors.warmAccent, Modifier.weight(1f)) { viewModel.grade(FlashGrade.AGAIN) }
                    GradeButton("🤔", "Qiyin", colors.habitAccent, Modifier.weight(1f)) { viewModel.grade(FlashGrade.HARD) }
                    GradeButton("✅", "Bildim", colors.accent, Modifier.weight(1f)) { viewModel.grade(FlashGrade.GOOD) }
                }
            }
        }
    }
}

@Composable
private fun MnemonicDialog(card: FlashCard, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by androidx.compose.runtime.saveable.rememberSaveable(card.key) { androidx.compose.runtime.mutableStateOf(card.mnemonic.orEmpty()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${card.english} — eslatma") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "So'zni nima bilan bog'lasangiz eslab qolasiz? Tovush, rasm yoki hazil — masalan: achieve → \"a, chiv-chiv erishdi!\"",
                    fontSize = 13.sp
                )
                androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it.take(160) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(text) }) { Text("Saqlash") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Bekor") } }
    )
}

@Composable
private fun GradeButton(icon: String, label: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.14f)).border(BorderStroke(1.dp, tint.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 20.sp)
        Text(text = label, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun FlipCard(card: FlashCard, uzFirst: Boolean, flipped: Boolean, box: Int, speaker: Speaker, onFlip: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, animationSpec = tween(380), label = "flip")
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp)
            .graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }
            .clip(RoundedCornerShape(26.dp)).background(colors.surface)
            .border(BorderStroke(1.5.dp, if (rotation > 90f) colors.accent else colors.heroBorder), RoundedCornerShape(26.dp))
            .clickable(onClick = onFlip).padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (box > 0) Text(text = "📦 $box", color = colors.text.muted(0.45f), fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { rotationY = if (rotation > 90f) 180f else 0f })
        if (rotation <= 90f) {
            // Front: the word to recall from.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uzFirst) {
                    Text(text = card.uzbek, color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                    Text(text = "inglizchasi qanday?", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                } else {
                    Text(text = card.english, color = colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                    SpeakChip(card.english, speaker)
                    Text(text = "ma'nosi nima?", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        } else {
            // Back (drawn turned, so it reads the right way round).
            Column(
                modifier = Modifier.graphicsLayer { rotationY = 180f },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = card.english, color = colors.accent, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                SpeakChip(card.english, speaker)
                Text(text = card.uzbek, color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                card.example?.let {
                    Text(text = it, color = colors.text.muted(0.6f), fontSize = 14.sp, fontStyle = FontStyle.Italic, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                }
                card.mnemonic?.let {
                    Text(text = "💡 $it", color = colors.habitAccent, fontSize = 15.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SpeakChip(text: String, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = "🔊 tinglash",
        color = colors.text.muted(0.7f),
        fontSize = 13.sp,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(colors.background).clickable(enabled = speaker.ready) { speaker.speak(text) }.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun FlashResult(s: FlashSession, speaker: Speaker, viewModel: FlashcardViewModel) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = if (s.struggled.isEmpty()) "🌟" else "🃏", fontSize = 64.sp, modifier = Modifier.padding(top = 28.dp))
        Text(text = "${s.total} ta kartadan ${s.firstTry.size} tasini birinchi urinishda bildingiz", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
        if (s.struggled.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Qiyin bo'lganlar (bosib eshiting)", color = colors.warmAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                s.struggled.forEach { c ->
                    Text(
                        text = "🔊 ${c.english} — ${Exercises.primary(c.uzbek)}",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = speaker.ready) { speaker.speak(c.english) }.padding(vertical = 4.dp)
                    )
                }
            }
            OnIkkiButton(text = "Qiyinlarini qayta (${s.struggled.size})", onClick = { viewModel.again(onlyStruggled = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        }
        OnIkkiButton(text = "Yana bir bor hammasini", onClick = { viewModel.again(onlyStruggled = false) }, variant = if (s.struggled.isEmpty()) OnIkkiButtonVariant.PRIMARY else OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        OnIkkiButton(text = "Yopish", onClick = viewModel::close, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
    }
}
