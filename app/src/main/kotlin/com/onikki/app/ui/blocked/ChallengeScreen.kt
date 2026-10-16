package com.onikki.app.ui.blocked

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.domain.screentime.ChallengeQuestion
import com.onikki.app.domain.screentime.QuestionKind
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.vocabulary.BoxState
import com.onikki.app.ui.vocabulary.LetterBoxInput
import com.onikki.app.ui.vocabulary.SpeakButton
import com.onikki.app.ui.vocabulary.rememberSpeaker
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onikki.app.domain.screentime.VocabChallenge
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted

/** What the user must do to get in: answer [questions], or (when empty) type [phrase]. */
data class ChallengeSpec(val questions: List<ChallengeQuestion>, val phrase: String?, val graceMinutes: Int)

/**
 * The unlock challenge. Words: each must be answered correctly; a wrong answer shows the right one and
 * the word comes back at the end, so the only way through is actually knowing them. Phrase: type it exactly.
 */
@Composable
fun ChallengeScreen(
    appName: String,
    spec: ChallengeSpec,
    onAnswer: (wordId: Long, correct: Boolean) -> Unit,
    onPassed: () -> Unit,
    onGiveUp: () -> Unit
) {
    BackHandler(onBack = onGiveUp)
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding()
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        OnIkkiCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(20.dp), gap = 12.dp) {
            Text(text = "$appName ni ochish uchun".uppercase(), color = colors.accent, style = OnIkkiType.kicker)
            if (spec.questions.isNotEmpty()) {
                WordQuiz(spec.questions, onAnswer, onPassed)
            } else {
                PhraseTask(spec.phrase.orEmpty(), onPassed)
            }
            Text(
                text = if (spec.graceMinutes <= 0) "To'g'ri bajarsangiz, ilovadan chiqquningizcha ochiq turadi."
                else "To'g'ri bajarsangiz ${spec.graceMinutes} daqiqa ochiq turadi.",
                color = colors.text.muted(0.45f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
            OnIkkiButton(
                text = "Kerak emas, chiqish",
                onClick = onGiveUp,
                variant = OnIkkiButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Where the current question stands after "Tekshirish". */
private sealed interface Outcome {
    data object Asking : Outcome
    data class Almost(val right: String) : Outcome
    data class Wrong(val right: String) : Outcome
}

@Composable
private fun WordQuiz(
    questions: List<ChallengeQuestion>,
    onAnswer: (wordId: Long, correct: Boolean) -> Unit,
    onPassed: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val speaker = rememberSpeaker()
    var queue by remember { mutableStateOf(questions) }
    var solved by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var hints by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<Outcome>(Outcome.Asking) }
    var picked by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val current = queue.firstOrNull()

    if (current == null) {
        LaunchedEffect(Unit) { onPassed() }
        return
    }
    val slots = remember(current) { AnswerCheck.slots(current.primaryAnswer) }
    val letterCount = AnswerCheck.letterCount(slots)
    val answerLetters = remember(current) { AnswerCheck.letters(current.primaryAnswer) }

    LaunchedEffect(current) {
        if (current.kind != QuestionKind.CHOICE) runCatching { focus.requestFocus() }
    }
    // Listening questions say the word as soon as they appear (the voice may need a moment to load).
    LaunchedEffect(current, speaker.ready) {
        if (current.kind == QuestionKind.LISTEN && speaker.ready) speaker.speak(current.primaryAnswer)
    }

    fun next(moveToBack: Boolean) {
        queue = if (moveToBack) queue.drop(1) + current else queue.drop(1)
        if (!moveToBack) solved++
        typed = ""
        hints = 0
        picked = null
        outcome = Outcome.Asking
    }

    fun check(answer: String) {
        when (outcome) {
            is Outcome.Almost -> { next(moveToBack = false); return }
            is Outcome.Wrong -> { next(moveToBack = true); return }
            Outcome.Asking -> Unit
        }
        if (AnswerCheck.letters(answer).isEmpty()) return
        // A word solved with letter hints still passes, but counts as "not known yet" for practice.
        when (VocabChallenge.judge(answer, current)) {
            Verdict.CORRECT -> { onAnswer(current.word.id, hints == 0); next(moveToBack = false) }
            Verdict.ALMOST -> { onAnswer(current.word.id, hints == 0); outcome = Outcome.Almost(current.shownAnswer) }
            Verdict.WRONG -> { onAnswer(current.word.id, false); outcome = Outcome.Wrong(current.shownAnswer) }
        }
    }

    Text(
        text = when (current.kind) {
            QuestionKind.LISTEN -> "Eshiting va inglizcha yozing"
            QuestionKind.CHOICE -> "To'g'ri tarjimani tanlang"
            QuestionKind.TYPE -> "So'zni ${if (current.toUzbek) "o'zbekchaga" else "inglizchaga"} tarjima qiling"
        },
        color = colors.text.muted(0.6f),
        fontSize = 13.sp,
        fontFamily = OnIkkiFontFamily
    )
    LinearProgressTrack(progress = solved / questions.size.toFloat(), trackColor = colors.neutral800, progressColor = colors.accent)

    if (current.kind == QuestionKind.LISTEN) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SpeakButton(speaker, current.primaryAnswer, big = true)
            Text(
                text = if (speaker.ready) "Ma'nosi: ${current.prompt}" else "Ovoz yo'q — ma'nosi: ${current.prompt}",
                color = colors.text.muted(0.55f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(
                text = current.prompt,
                color = colors.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                textAlign = TextAlign.Center
            )
            if (current.toUzbek) {
                Box(modifier = Modifier.padding(start = 10.dp)) { SpeakButton(speaker, current.prompt) }
            }
        }
    }

    if (current.kind == QuestionKind.CHOICE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            current.options.forEach { option ->
                val isRight = AnswerCheck.letters(option) == AnswerCheck.letters(current.primaryAnswer)
                val bg = when {
                    picked == null -> colors.surface
                    isRight -> colors.habitAccent.copy(alpha = 0.25f)
                    option == picked -> colors.warmAccent.copy(alpha = 0.25f)
                    else -> colors.surface
                }
                Text(
                    text = option,
                    color = colors.text,
                    fontSize = 16.sp,
                    fontFamily = OnIkkiFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                        .clickable(enabled = picked == null) {
                            picked = option
                            if (isRight) {
                                onAnswer(current.word.id, true)
                                next(moveToBack = false)
                            } else {
                                onAnswer(current.word.id, false)
                                outcome = Outcome.Wrong(current.shownAnswer)
                            }
                        }
                        .padding(vertical = 13.dp, horizontal = 12.dp)
                )
            }
        }
    } else {
        LetterBoxInput(
            answer = current.primaryAnswer,
            typed = typed,
            onTyped = { if (outcome == Outcome.Asking) typed = it },
            onDone = { check(AnswerCheck.assemble(slots, typed)) },
            focusRequester = focus,
            state = when (outcome) {
                Outcome.Asking -> BoxState.TYPING
                is Outcome.Almost -> BoxState.RIGHT
                is Outcome.Wrong -> BoxState.WRONG
            }
        )
        if (current.answers.size > 1 && outcome == Outcome.Asking) {
            Text(
                text = "Boshqa to'g'ri javoblar ham bor — kataklar birinchisiga mos",
                color = colors.text.muted(0.4f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }

    when (val o = outcome) {
        is Outcome.Almost -> Text(
            text = "Deyarli to'g'ri! To'g'ri yozilishi: ${o.right}",
            color = colors.habitAccent,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        is Outcome.Wrong -> Text(
            text = "Noto'g'ri. To'g'ri javob: ${o.right}",
            color = colors.warmAccent,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        Outcome.Asking -> Unit
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "$solved / ${questions.size}", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
        if (current.kind != QuestionKind.CHOICE && outcome == Outcome.Asking && typed.length < letterCount) {
            // Each hint fills in the next correct letter (the typed part is corrected first).
            OnIkkiButton(
                text = "💡 Harf",
                onClick = {
                    val correctPrefix = typed.zip(answerLetters).takeWhile { (a, b) -> a == b }.size
                    typed = answerLetters.take(correctPrefix + 1)
                    hints++
                },
                variant = OnIkkiButtonVariant.SECONDARY
            )
        }
        if (current.kind != QuestionKind.CHOICE || outcome != Outcome.Asking) {
            OnIkkiButton(
                text = if (outcome == Outcome.Asking) "Tekshirish" else "Keyingi",
                onClick = { check(AnswerCheck.assemble(slots, typed)) }
            )
        }
    }
}

@Composable
private fun PhraseTask(phrase: String, onPassed: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "Quyidagi matnni aynan yozing:", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = "«$phrase»",
            color = colors.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = false },
            isError = error,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = {
                if (VocabChallenge.phraseMatches(input, phrase)) onPassed() else error = true
            }),
            modifier = Modifier.fillMaxWidth()
        )
        if (error) {
            Text(text = "Matn mos kelmadi — qaytadan tekshiring", color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        OnIkkiButton(
            text = "Tekshirish",
            onClick = { if (VocabChallenge.phraseMatches(input, phrase)) onPassed() else error = true },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
