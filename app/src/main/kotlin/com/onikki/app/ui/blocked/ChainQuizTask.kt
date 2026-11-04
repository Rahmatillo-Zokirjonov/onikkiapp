package com.onikki.app.ui.blocked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.domain.vocab.ChainQuestion
import com.onikki.app.domain.vocab.ChainQuestionKind
import com.onikki.app.domain.vocab.ChainQuiz
import com.onikki.app.domain.vocab.ChainSequence
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import kotlin.random.Random

/** [needed] right answers on the order of the chain; a miss shows the answer, the next question is new. */
@Composable
internal fun ChainQuizTask(words: List<ImportedWord>, needed: Int, onPassed: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    val prefs = remember { LearnPrefs(context) }
    val quiz = remember { ChainQuiz(words, needed.coerceAtLeast(1), prefs.chainWeak(), Random(System.nanoTime())) }
    var question by remember { mutableStateOf(quiz.next()) }
    var input by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var solved by remember { mutableIntStateOf(0) }
    val q = question
    if (q == null) {
        LaunchedEffect(Unit) { onPassed() }
        return
    }

    fun check() {
        if (input.isBlank() || verdict != null) return
        val v = quiz.judge(input, q)
        quiz.answer(q, v)?.let { key -> prefs.setChainWeak(ChainSequence.updateWeak(prefs.chainWeak(), key, missed = v == Verdict.WRONG)) }
        verdict = v
        solved = quiz.solved
    }

    fun proceed() {
        if (quiz.passed) { onPassed(); return }
        question = quiz.next()
        input = ""
        verdict = null
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "🔗 Zanjir: $solved / ${quiz.needed} to'g'ri", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
        Text(text = "${words.size} ta so'z", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
    }
    LinearProgressTrack(progress = solved / quiz.needed.toFloat(), trackColor = colors.neutral800, progressColor = colors.habitAccent)
    QuestionText(q, quiz)
    OutlinedTextField(
        value = input,
        onValueChange = { if (verdict == null) input = it.take(40) },
        singleLine = true,
        placeholder = { Text(if (q.numeric) "o'rin raqami" else "inglizcha so'z") },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (q.numeric) KeyboardType.Number else KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { if (verdict == null) check() else proceed() }),
        modifier = Modifier.fillMaxWidth()
    )
    val v = verdict
    if (v == null) {
        OnIkkiButton(text = "Tekshirish", onClick = { check() }, modifier = Modifier.fillMaxWidth())
    } else {
        Text(
            text = when (v) {
                Verdict.CORRECT -> "✓ To'g'ri!"
                Verdict.ALMOST -> "✓ Tartib to'g'ri. Imlosi: ${quiz.answerText(q)}"
                Verdict.WRONG -> "✗ To'g'risi: ${quiz.answerText(q)}"
            },
            color = if (v == Verdict.WRONG) colors.warmAccent else colors.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background).padding(12.dp)
        )
        OnIkkiButton(text = if (quiz.passed) "Ochish" else "Davom etish", onClick = { proceed() }, modifier = Modifier.fillMaxWidth())
        if (v == Verdict.WRONG) Text(text = "Keyingi savol boshqa joydan bo'ladi", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun QuestionText(q: ChainQuestion, quiz: ChainQuiz) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.background).padding(14.dp)
    ) {
        val given = q.given?.let(quiz::word)
        when (q.kind) {
            ChainQuestionKind.NEXT, ChainQuestionKind.PREVIOUS -> {
                Text(text = "\"${given?.english}\" dan ${if (q.kind == ChainQuestionKind.NEXT) "KEYIN" else "OLDIN"} qaysi so'z turadi?", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "${(q.given ?: 0) + 1}-o'rin · ${given?.let { Exercises.primary(it.uzbek) }}", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
            ChainQuestionKind.AT_POSITION -> {
                Text(text = "Zanjirda ${q.target + 1}-o'rinda qaysi so'z turibdi?", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "${ChainQuiz.dayOf(q.target)}-kun so'zlaridan", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
            ChainQuestionKind.POSITION_OF -> {
                Text(text = "\"${given?.english}\" zanjirda nechanchi o'rinda?", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "${given?.let { Exercises.primary(it.uzbek) }} · raqam bilan yozing", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }
    }
}
