package com.onikki.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.domain.vocab.CellKind
import com.onikki.app.domain.vocab.DiffCell
import com.onikki.app.domain.vocab.DictationMode
import com.onikki.app.domain.vocab.Exercise
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.SentenceDiff
import com.onikki.app.domain.vocab.Spelling
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.domain.vocab.WordMark
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker

/** How many times a misspelt word is rewritten before moving on. */
const val REWRITES = 3

// ---------------------------------------------------------------- shared pieces

@Composable
private fun PromptCard(content: @Composable () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun Round(icon: String, speaker: Speaker, size: Int = 44, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(colors.accent800).alpha(if (speaker.ready) 1f else 0.4f).clickable(enabled = speaker.ready, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text = icon, fontSize = (size / 2.4).sp) }
}

/** Letters coloured by the diff: right green, wrong red, a left-out letter as a red "_", extra struck through. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiffRow(cells: List<DiffCell>, size: Int = 26) {
    val colors = LocalOnIkkiColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.forEach { c ->
            Text(
                text = if (c.kind == CellKind.MISSING) "_" else c.char.toString(),
                color = if (c.kind == CellKind.OK) colors.accent else colors.warmAccent,
                fontSize = size.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textDecoration = if (c.kind == CellKind.EXTRA) TextDecoration.LineThrough else null
            )
        }
    }
}

/** A dictated sentence, word by word: right green, wrong red (with what was typed), missing as "___". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentenceMarks(marks: List<WordMark>, extra: Int) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            marks.forEach { m ->
                Column {
                    Text(
                        text = m.word,
                        color = if (m.kind == CellKind.OK) colors.accent else colors.warmAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OnIkkiFontFamily
                    )
                    when (m.kind) {
                        CellKind.WRONG -> Text(text = m.typed.orEmpty(), color = colors.text.muted(0.5f), fontSize = 11.sp, textDecoration = TextDecoration.LineThrough, fontFamily = OnIkkiFontFamily)
                        CellKind.MISSING -> Text(text = "tushib qoldi", color = colors.warmAccent.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
                        else -> Unit
                    }
                }
            }
        }
        if (extra > 0) Text(text = "+$extra ta ortiqcha so'z", color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun WriteField(value: String, onValue: (String) -> Unit, label: String, enabled: Boolean, focus: FocusRequester, singleLine: Boolean = true, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (enabled) onValue(it.take(200)) },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(
            // Password-style keyboard: no suggestions or autocorrect that would spell the word for you.
            keyboardType = if (singleLine) KeyboardType.Password else KeyboardType.Text,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
    )
}

/**
 * The strict spelling answer used by spelling, verbs and word dictation: type → check. A miss shows the
 * letter diff and the right word, then it must be written right [REWRITES] times before moving on
 * (it still counts as a miss for the SRS).
 */
@Composable
private fun SpellAnswer(
    answers: List<String>,
    wordId: Long,
    word: VocabWord,
    speaker: Speaker,
    label: String,
    onAnswer: (List<WordAnswer>) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val focus = remember(answers) { FocusRequester() }
    var text by remember(answers) { mutableStateOf("") }
    var firstTry by remember(answers) { mutableStateOf<String?>(null) }
    var rewrites by remember(answers) { mutableIntStateOf(0) }
    var lastRewrite by remember(answers) { mutableStateOf<String?>(null) }
    var done by remember(answers) { mutableStateOf(false) }
    val right = answers.first()
    LaunchedEffect(answers) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (done || text.isBlank()) return
        val ok = Spelling.isRight(text, answers)
        val miss = firstTry
        when {
            miss == null && ok -> { done = true; speaker.speak(right); onAnswer(listOf(WordAnswer(wordId, Verdict.CORRECT))) }
            miss == null -> { firstTry = text; text = ""; speaker.speakSlow(right) }
            ok -> {
                rewrites++
                lastRewrite = null
                text = ""
                if (rewrites >= REWRITES) { done = true; onAnswer(listOf(WordAnswer(wordId, Verdict.WRONG, note = word.mnemonic?.let { "💡 Eslatma: $it" }))) }
            }
            else -> { lastRewrite = text; text = "" }
        }
        runCatching { focus.requestFocus() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        firstTry?.let { miss ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.warmAccent.copy(alpha = 0.10f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Siz yozdingiz:", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                DiffRow(Spelling.diff(miss, Spelling.closest(miss, answers)))
                Text(text = "To'g'risi:", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = right, color = colors.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Round("🔊", speaker, 38) { speaker.speakSlow(right) }
                }
                word.mnemonic?.let { Text(text = "💡 Eslatma: $it", color = colors.habitAccent, fontSize = 14.sp, fontFamily = OnIkkiFontFamily) }
                if (!done) {
                    Text(
                        text = "Endi $REWRITES marta to'g'ri yozing: ${"●".repeat(rewrites)}${"○".repeat(REWRITES - rewrites)}",
                        color = colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OnIkkiFontFamily
                    )
                }
                lastRewrite?.let {
                    Text(text = "Yana xato — qaytadan:", color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    DiffRow(Spelling.diff(it, right), size = 20)
                }
            }
        }
        if (!done) {
            WriteField(text, { text = it }, if (firstTry == null) label else "Qayta yozing (${rewrites + 1}/$REWRITES)", enabled = true, focus = focus, onDone = ::submit)
            OnIkkiButton(text = if (firstTry == null) "Tekshirish" else "Yozdim", onClick = ::submit, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        }
    }
}

// ---------------------------------------------------------------- 1. spelling

@Composable
fun SpellExercise(ex: Exercise.Spell, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseTitle("Inglizchasini yozing")
        PromptCard {
            Text(text = ex.prompt, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            ex.clue?.let { Text(text = it, color = colors.text.muted(0.65f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily) }
        }
        SpellAnswer(ex.answers, ex.word.id, ex.word, speaker, "Inglizcha", onAnswer)
    }
}

// ---------------------------------------------------------------- 2. irregular verbs

@Composable
fun VerbPastExercise(ex: Exercise.VerbPast, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseTitle(if (ex.reverse) "Asosiy shaklini yozing (V1)" else "O'tgan shaklini yozing (V2)")
        PromptCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = ex.prompt + "  →  ?", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Round("🔊", speaker, 38) { speaker.speak(ex.prompt) }
            }
            Text(text = Exercises.primary(ex.word.uzbek), color = colors.accent, fontSize = 16.sp, fontFamily = OnIkkiFontFamily)
            ex.word.example?.let { Text(text = it, color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily) }
        }
        SpellAnswer(ex.answers, ex.word.id, ex.word, speaker, if (ex.reverse) "V1" else "V2", onAnswer)
    }
}

// ---------------------------------------------------------------- 3. dictation

@Composable
fun DictationExercise(ex: Exercise.Dictation, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val word = ex.word.english.trim()
    val heard = when (ex.mode) {
        DictationMode.WORD -> word
        DictationMode.WORD_SENTENCE -> "$word. ${ex.sentence.orEmpty()}"
        DictationMode.SENTENCE -> ex.sentence.orEmpty()
    }
    LaunchedEffect(ex, speaker.ready) { if (speaker.ready) speaker.speak(heard) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseTitle("Eshiting va yozing · ${ex.mode.label}")
        PromptCard {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Round("🔊", speaker, 64) { speaker.speak(heard) }
                Round("🐢", speaker, 52) { speaker.speakSlow(heard) }
                Text(text = if (speaker.ready) "Matn ko'rinmaydi — faqat tinglang" else "Telefonda inglizcha ovoz yo'q", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
            }
        }
        if (ex.mode == DictationMode.WORD) {
            SpellAnswer(ex.answers, ex.word.id, ex.word, speaker, "Eshitgan so'zingiz", onAnswer)
        } else {
            SentenceDictation(ex, speaker, onAnswer)
        }
    }
}

@Composable
private fun SentenceDictation(ex: Exercise.Dictation, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val withWord = ex.mode == DictationMode.WORD_SENTENCE
    val wordFocus = remember(ex) { FocusRequester() }
    val sentenceFocus = remember(ex) { FocusRequester() }
    var wordText by remember(ex) { mutableStateOf("") }
    var sentenceText by remember(ex) { mutableStateOf("") }
    var result by remember(ex) { mutableStateOf<Triple<Boolean?, List<WordMark>, Int>?>(null) }
    val sentence = ex.sentence.orEmpty()
    LaunchedEffect(ex) { runCatching { (if (withWord) wordFocus else sentenceFocus).requestFocus() } }

    fun check() {
        if (result != null || sentenceText.isBlank() || (withWord && wordText.isBlank())) return
        val wordOk = if (withWord) Spelling.isRight(wordText, ex.answers) else null
        val (marks, extra) = SentenceDiff.compare(sentenceText, sentence)
        result = Triple(wordOk, marks, extra)
        val sv = SentenceDiff.verdict(marks, extra)
        val verdict = if (wordOk == false) Verdict.WRONG else sv
        speaker.speak(if (withWord) "${ex.word.english}. $sentence" else sentence)
        onAnswer(listOf(WordAnswer(ex.word.id, verdict, note = ex.word.mnemonic?.takeIf { verdict != Verdict.CORRECT }?.let { "💡 Eslatma: $it" })))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (withWord) WriteField(wordText, { wordText = it }, "So'z", enabled = result == null, focus = wordFocus, onDone = { runCatching { sentenceFocus.requestFocus() } })
        WriteField(sentenceText, { sentenceText = it }, "Gap", enabled = result == null, focus = sentenceFocus, singleLine = false, onDone = ::check)
        val r = result
        if (r == null) {
            OnIkkiButton(text = "Tekshirish", onClick = ::check, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                r.first?.let { ok ->
                    Text(text = if (ok) "So'z to'g'ri" else "So'z:", color = if (ok) colors.accent else colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                    if (!ok) DiffRow(Spelling.diff(wordText, Spelling.closest(wordText, ex.answers)), size = 22)
                }
                Text(text = "Gap:", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                SentenceMarks(r.second, r.third)
            }
        }
    }
}
