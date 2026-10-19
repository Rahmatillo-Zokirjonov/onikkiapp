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
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.local.QuestionType
import com.onikki.app.domain.vocab.BlockerChallenge
import com.onikki.app.domain.vocab.Exercise
import com.onikki.app.domain.vocab.Srs
import com.onikki.app.ui.learn.AnswerPanel
import com.onikki.app.ui.learn.ExerciseView
import com.onikki.app.ui.learn.WordAnswer
import com.onikki.app.ui.learn.rememberFeedbackFx
import com.onikki.app.ui.learn.rightAnswerOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import java.time.LocalDate
import kotlin.random.Random
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

/**
 * What the user must do to get in: [needed] right answers on [words] (the learning words, due ones
 * first — see BlockerChallenge), or, when there are no words, type [phrase].
 */
data class ChallengeSpec(
    val words: List<VocabWord>,
    val needed: Int,
    val type: QuestionType,
    val phrase: String?,
    val graceMinutes: Int
)

/**
 * The unlock challenge. Words: right answers open the app, at least one typed; a miss shows the right
 * answer and the next question takes another word. Every answer goes into the learning schedule.
 * Phrase: type it exactly.
 */
@Composable
fun ChallengeScreen(
    appName: String,
    spec: ChallengeSpec,
    onAnswer: (WordAnswer, Exercise) -> Unit,
    onPassed: () -> Unit,
    onGiveUp: () -> Unit
) {
    BackHandler(onBack = onGiveUp)
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OnIkkiCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(18.dp), gap = 12.dp) {
            Text(text = "$appName ni ochish uchun".uppercase(), color = colors.accent, style = OnIkkiType.kicker)
            if (spec.words.isNotEmpty()) {
                LearnQuiz(spec, onAnswer, onPassed)
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
private fun LearnQuiz(spec: ChallengeSpec, onAnswer: (WordAnswer, Exercise) -> Unit, onPassed: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val speaker = rememberSpeaker()
    val fx = rememberFeedbackFx()
    val context = androidx.compose.ui.platform.LocalContext.current
    val learnSettings = remember { com.onikki.app.data.local.LearnPrefs(context).settings.value }
    val challenge = remember { BlockerChallenge(spec.words, LocalDate.now(), spec.needed.coerceAtLeast(1), spec.type, Random(System.nanoTime())) }
    var exercise by remember { mutableStateOf(challenge.next()) }
    var round by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<Triple<Verdict, String?, Int>?>(null) }
    val current = exercise
    if (current == null) {
        LaunchedEffect(Unit) { onPassed() }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${challenge.solved} / ${challenge.needed} to'g'ri",
            color = colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f)
        )
        if (!challenge.typedDone) Text(text = "✍ kamida 1 tasi yozib", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
    }
    LinearProgressTrack(progress = challenge.solved / challenge.needed.toFloat(), trackColor = colors.neutral800, progressColor = colors.habitAccent)
    key(round) {
        ExerciseView(current, speaker) { answers ->
            val a = answers.firstOrNull() ?: return@ExerciseView
            challenge.answer(current, a.verdict)
            onAnswer(a, current)
            fx.play(a.verdict, learnSettings.sound, learnSettings.vibration)
            val grade = Srs.gradeFor(a.verdict, a.usedHint)
            feedback = Triple(a.verdict, rightAnswerOf(current), Srs.xpFor(grade, current.type.hard))
        }
    }
    feedback?.let { (verdict, right, xp) ->
        AnswerPanel(
            verdict = verdict,
            rightAnswer = right,
            xp = xp,
            onContinue = {
                if (challenge.passed) onPassed()
                else {
                    feedback = null
                    exercise = challenge.next()
                    round++
                }
            }
        )
        if (verdict == Verdict.WRONG) {
            Text(text = "Keyingi savol boshqa so'zdan bo'ladi", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
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
