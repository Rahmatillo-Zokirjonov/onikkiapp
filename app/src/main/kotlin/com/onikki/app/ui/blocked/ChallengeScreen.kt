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

@Composable
private fun WordQuiz(
    questions: List<ChallengeQuestion>,
    onAnswer: (wordId: Long, correct: Boolean) -> Unit,
    onPassed: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    var queue by remember { mutableStateOf(questions) }
    var solved by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var wrongShown by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val current = queue.firstOrNull()

    LaunchedEffect(current) { runCatching { focus.requestFocus() } }
    if (current == null) {
        LaunchedEffect(Unit) { onPassed() }
        return
    }

    fun submit() {
        if (wrongShown != null) {
            // After seeing the right answer, "Keyingi" moves on; the missed word goes to the back of the line.
            queue = queue.drop(1) + current
            wrongShown = null
            input = ""
            return
        }
        if (input.isBlank()) return
        val correct = VocabChallenge.isCorrect(input, current)
        onAnswer(current.word.id, correct)
        if (correct) {
            solved++
            queue = queue.drop(1)
            input = ""
        } else {
            wrongShown = current.shownAnswer
        }
    }

    Text(
        text = "So'zni ${if (current.toUzbek) "o'zbekchaga" else "inglizchaga"} tarjima qiling",
        color = colors.text.muted(0.6f),
        fontSize = 13.sp,
        fontFamily = OnIkkiFontFamily
    )
    LinearProgressTrack(
        progress = solved / questions.size.toFloat(),
        trackColor = colors.neutral800,
        progressColor = colors.accent
    )
    Text(
        text = current.prompt,
        color = colors.text,
        fontSize = 30.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = OnIkkiFontFamily,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
    OutlinedTextField(
        value = input,
        onValueChange = { if (wrongShown == null) input = it },
        label = { Text(if (current.toUzbek) "O'zbekcha" else "English") },
        singleLine = true,
        isError = wrongShown != null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
    )
    wrongShown?.let { answer ->
        Text(
            text = "Noto'g'ri. To'g'ri javob: $answer",
            color = colors.warmAccent,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$solved / ${questions.size}",
            color = colors.text.muted(0.5f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f)
        )
        OnIkkiButton(text = if (wrongShown != null) "Keyingi" else "Tekshirish", onClick = ::submit)
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
