package com.onikki.app.ui.learn

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.Exercise
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.BoxState
import com.onikki.app.ui.vocabulary.LetterBoxInput
import com.onikki.app.ui.vocabulary.Speaker

/** How one word was answered inside an exercise (Match reports five). */
data class WordAnswer(
    val wordId: Long,
    val verdict: Verdict,
    val usedHint: Boolean = false,
    /** Shown in the answer panel instead of the fixed right answer (the AI's correction of an own sentence). */
    val note: String? = null
)

/** Checks an own sentence with the AI; null = no key / offline / failed, so the offline check is used. */
typealias SentenceChecker = suspend (VocabWord, String) -> com.onikki.app.domain.vocab.SentenceVerdict?

/**
 * One exercise. It takes the answer, shows right/wrong on its own controls, then calls [onAnswer] once;
 * the lesson shows the feedback panel and moves on. A card has nothing to grade — it calls [onAnswer] with
 * an empty list when the user is ready.
 */
@Composable
fun ExerciseView(exercise: Exercise, speaker: Speaker, checkSentence: SentenceChecker? = null, onAnswer: (List<WordAnswer>) -> Unit) {
    when (exercise) {
        is Exercise.Card -> CardExercise(exercise.word, speaker, onDone = { onAnswer(emptyList()) })
        is Exercise.Choose -> OptionsExercise(
            title = "Inglizchasini tanlang",
            prompt = { BigPrompt(exercise.prompt) },
            options = exercise.options,
            answer = exercise.answer,
            onPicked = { verdict -> onAnswer(listOf(WordAnswer(exercise.word.id, verdict))) },
            onRevealed = { speaker.speak(exercise.word.english) }
        )
        is Exercise.Listen -> OptionsExercise(
            title = "Eshiting va ma'nosini tanlang",
            prompt = { ListenPrompt(exercise.spoken, speaker) },
            options = exercise.options,
            answer = exercise.answer,
            onPicked = { verdict -> onAnswer(listOf(WordAnswer(exercise.word.id, verdict))) },
            onRevealed = {}
        )
        is Exercise.Match -> MatchExercise(exercise, speaker, onDone = onAnswer)
        is Exercise.TypeIn -> TypeExercise(exercise, speaker, onAnswer)
        is Exercise.Cloze -> ClozeExercise(exercise, speaker, onAnswer)
        is Exercise.Build -> BuildExercise(exercise, speaker, onAnswer)
        is Exercise.FormChoice -> OptionsExercise(
            title = "${exercise.form.label} shaklini tanlang · ${exercise.form.hint}",
            prompt = { VerbPrompt(exercise.word, speaker) },
            options = exercise.options,
            answer = exercise.answer,
            onPicked = { verdict -> onAnswer(listOf(WordAnswer(exercise.word.id, verdict))) },
            onRevealed = { speaker.speak(verbLine(exercise.word)) }
        )
        is Exercise.Forms -> FormsExercise(exercise, speaker, onAnswer)
        is Exercise.Spell -> SpellExercise(exercise, speaker, onAnswer)
        is Exercise.VerbPast -> VerbPastExercise(exercise, speaker, onAnswer)
        is Exercise.Dictation -> DictationExercise(exercise, speaker, onAnswer)
        is Exercise.Translate -> TranslateExercise(exercise, speaker, onAnswer)
        is Exercise.Compose -> ComposeExercise(exercise, speaker, checkSentence, onAnswer)
        is Exercise.Dictate -> TypedAnswer(
            title = "Eshiting va yozing",
            prompt = { ListenPrompt(exercise.word.english, speaker) },
            answer = exercise.answers.first(),
            answers = exercise.answers,
            wordId = exercise.word.id,
            onAfter = {},
            onAnswer = onAnswer
        )
    }
}

/** "go, went, gone" — read aloud after a verb exercise. */
fun verbLine(word: VocabWord): String =
    listOfNotNull(word.english.trim(), word.pastForm?.let(Exercises::primary), word.participle?.let(Exercises::primary)).joinToString(", ")

@Composable
private fun VerbPrompt(word: VocabWord, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(vertical = 18.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = word.english, color = colors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            SpeakerButton(speaker, word.english, size = 38)
        }
        Text(text = Exercises.primary(word.uzbek), color = colors.text.muted(0.65f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
    }
}

/** An irregular verb: V2 and V3 into letter boxes, checked together. */
@Composable
private fun FormsExercise(ex: Exercise.Forms, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val focusPast = remember(ex) { FocusRequester() }
    val focusPart = remember(ex) { FocusRequester() }
    var past by remember(ex) { mutableStateOf("") }
    var part by remember(ex) { mutableStateOf("") }
    var verdicts by remember(ex) { mutableStateOf<Pair<Verdict, Verdict>?>(null) }
    val pastAnswer = ex.past.first()
    val partAnswer = ex.participle.first()
    val pastSlots = remember(ex) { AnswerCheck.slots(pastAnswer) }
    val partSlots = remember(ex) { AnswerCheck.slots(partAnswer) }
    LaunchedEffect(ex) { runCatching { focusPast.requestFocus() } }

    fun check() {
        if (verdicts != null || (past.isEmpty() && part.isEmpty())) return
        val v = Exercises.checkForms(AnswerCheck.assemble(pastSlots, past), AnswerCheck.assemble(partSlots, part), ex)
        verdicts = v
        speaker.speak(verbLine(ex.word))
        onAnswer(listOf(WordAnswer(ex.word.id, Exercises.combine(v.first, v.second))))
    }

    fun stateOf(v: Verdict?) = when (v) {
        null -> BoxState.TYPING
        Verdict.WRONG -> BoxState.WRONG
        else -> BoxState.RIGHT
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseTitle("Fe'lning 2- va 3-shaklini yozing")
        VerbPrompt(ex.word, speaker)
        Text(text = "V2 · Past Simple", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        LetterBoxInput(
            answer = pastAnswer,
            typed = past,
            onTyped = { if (verdicts == null) past = it },
            onDone = { runCatching { focusPart.requestFocus() } },
            focusRequester = focusPast,
            state = stateOf(verdicts?.first)
        )
        Text(text = "V3 · Past Participle", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        LetterBoxInput(
            answer = partAnswer,
            typed = part,
            onTyped = { if (verdicts == null) part = it },
            onDone = ::check,
            focusRequester = focusPart,
            state = stateOf(verdicts?.second)
        )
        if (verdicts == null) {
            OnIkkiButton(text = "Tekshirish", onClick = ::check, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        }
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
fun ExerciseTitle(text: String) {
    val colors = LocalOnIkkiColors.current
    Text(text = text, color = colors.text.muted(0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
}

@Composable
private fun BigPrompt(text: String) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = text,
        color = colors.text,
        fontSize = 30.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = OnIkkiFontFamily,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
    )
}

@Composable
private fun SpeakerButton(speaker: Speaker, text: String, size: Int = 44, slow: Boolean = false) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (speaker.ready) colors.accent800 else colors.neutral800)
            .clickable(enabled = speaker.ready) { if (slow) speaker.speakSlow(text) else speaker.speak(text) },
        contentAlignment = Alignment.Center
    ) { Text(text = if (slow) "🐢" else "🔊", fontSize = (size * 0.45f).sp) }
}

@Composable
private fun ListenPrompt(text: String, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    LaunchedEffect(text, speaker.ready) { if (speaker.ready) speaker.speak(text) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakerButton(speaker, text, size = 84)
        SpeakerButton(speaker, text, size = 52, slow = true)
    }
    if (!speaker.ready) {
        // No English voice on the phone: still answerable, just read instead of heard.
        Text(text = "Ovoz yo'q — so'z: $text", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** A tall, full-width answer button; after answering it turns green (right) or red (picked but wrong). */
@Composable
private fun OptionButton(text: String, state: OptionState, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val bg by animateColorAsState(
        when (state) {
            OptionState.IDLE -> colors.surface
            OptionState.RIGHT -> colors.habitAccent.copy(alpha = 0.25f)
            OptionState.WRONG -> colors.warmAccent.copy(alpha = 0.25f)
            OptionState.SELECTED -> colors.accent800
        },
        label = "option"
    )
    val border = when (state) {
        OptionState.IDLE -> colors.cardBorder
        OptionState.RIGHT -> colors.habitAccent
        OptionState.WRONG -> colors.warmAccent
        OptionState.SELECTED -> colors.accent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(BorderStroke(if (state == OptionState.IDLE) 1.dp else 2.dp, border), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
    }
}

private enum class OptionState { IDLE, SELECTED, RIGHT, WRONG }

// ---------------------------------------------------------------- 1. card

@Composable
fun CardExercise(word: VocabWord, speaker: Speaker, onDone: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    LaunchedEffect(word.id, speaker.ready) { if (speaker.ready) speaker.speak(word.english) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ExerciseTitle(if (word.status == com.onikki.app.data.db.entity.WordStatus.NEW) "Yangi so'z" else "Eslab qoling")
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.heroBorder), RoundedCornerShape(24.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = word.english, color = colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                SpeakerButton(speaker, word.english)
            }
            val chips = listOfNotNull(
                word.partOfSpeech,
                when {
                    Exercises.isIrregular(word) -> "${word.english} · ${word.pastForm} · ${word.participle}"
                    word.pastForm != null -> "o'tgan zamon: ${word.pastForm}"
                    else -> null
                },
                word.level
            )
            if (chips.isNotEmpty()) {
                Text(text = chips.joinToString("  ·  "), color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
            Text(text = word.uzbek, color = colors.text.muted(0.85f), fontSize = 20.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
            word.mnemonic?.takeIf { it.isNotBlank() }?.let {
                Text(text = "💡 $it", color = colors.habitAccent, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
            }
            val family = com.onikki.app.domain.vocab.WordFamilies.of(word.english, word.familyRoot)
            if (family != null) {
                FamilyChips(family, word.english, speaker)
            } else word.familyRoot?.takeIf { it.isNotBlank() }?.let { root ->
                Text(text = familyWord(root, word.english), fontSize = 18.sp, fontFamily = OnIkkiFontFamily)
                Text(text = "ildiz: $root", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
            word.example?.let { example ->
                Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height1().background(colors.divider))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = highlighted(example, word), color = colors.text.muted(0.85f), fontSize = 16.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                    SpeakerButton(speaker, example, size = 36)
                }
            }
        }
        OnIkkiButton(text = "Tushundim", onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
    }
}

private fun Modifier.height1(): Modifier = this.then(Modifier.heightIn(min = 1.dp, max = 1.dp))

/** The example sentence with the word (in whatever form it appears) in bold gold. */
@Composable
private fun highlighted(sentence: String, word: VocabWord) = buildAnnotatedString {
    val colors = LocalOnIkkiColors.current
    val blank = Exercises.blank(sentence, word.english, word.pastForm, word.participle)
    if (blank == null) append(sentence) else {
        append(blank.before)
        withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) { append(blank.answer) }
        append(blank.after)
    }
}

// ---------------------------------------------------------------- 2/3. choose & listen

@Composable
private fun OptionsExercise(
    title: String,
    prompt: @Composable () -> Unit,
    options: List<String>,
    answer: Int,
    onPicked: (Verdict) -> Unit,
    onRevealed: () -> Unit
) {
    var picked by remember(options) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExerciseTitle(title)
        prompt()
        options.forEachIndexed { i, text ->
            val state = when {
                picked == null -> OptionState.IDLE
                i == answer -> OptionState.RIGHT
                i == picked -> OptionState.WRONG
                else -> OptionState.IDLE
            }
            OptionButton(text, state, enabled = picked == null) {
                picked = i
                onRevealed()
                onPicked(Exercises.checkChoice(i, answer))
            }
        }
    }
}

// ---------------------------------------------------------------- 4. match

@Composable
private fun MatchExercise(ex: Exercise.Match, speaker: Speaker, onDone: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val byId = remember(ex) { ex.words.associateBy { it.id } }
    var leftPick by remember(ex) { mutableStateOf<Long?>(null) }
    var rightPick by remember(ex) { mutableStateOf<Long?>(null) }
    val matched = remember(ex) { mutableStateListOf<Long>() }
    val missed = remember(ex) { mutableStateListOf<Long>() }
    var flashWrong by remember(ex) { mutableStateOf<Pair<Long, Long>?>(null) }

    fun tryPair(l: Long?, r: Long?) {
        if (l == null || r == null) return
        if (l == r) {
            matched += l
            speaker.speak(byId.getValue(l).english)
            if (matched.size == ex.words.size) {
                onDone(ex.words.map { WordAnswer(it.id, if (it.id in missed) Verdict.ALMOST else Verdict.CORRECT) })
            }
        } else {
            // A wrong pair counts against the English word that was picked.
            if (l !in missed) missed += l
            flashWrong = l to r
        }
        leftPick = null
        rightPick = null
    }
    LaunchedEffect(flashWrong) {
        if (flashWrong != null) {
            kotlinx.coroutines.delay(450)
            flashWrong = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExerciseTitle("Juftlarini toping")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ex.left.forEach { id ->
                    MatchTile(
                        text = byId.getValue(id).english,
                        done = id in matched,
                        selected = leftPick == id,
                        wrong = flashWrong?.first == id
                    ) { leftPick = id; tryPair(id, rightPick) }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ex.right.forEach { id ->
                    MatchTile(
                        text = Exercises.primary(byId.getValue(id).uzbek),
                        done = id in matched,
                        selected = rightPick == id,
                        wrong = flashWrong?.second == id
                    ) { rightPick = id; tryPair(leftPick, id) }
                }
            }
        }
        Text(text = "${matched.size} / ${ex.words.size}", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun MatchTile(text: String, done: Boolean, selected: Boolean, wrong: Boolean, onClick: () -> Unit) {
    val state = when {
        done -> OptionState.RIGHT
        wrong -> OptionState.WRONG
        selected -> OptionState.SELECTED
        else -> OptionState.IDLE
    }
    Box(modifier = Modifier.alpha(if (done) 0.55f else 1f)) {
        OptionButton(text, state, enabled = !done, onClick = onClick)
    }
}

// ---------------------------------------------------------------- 5. type

@Composable
private fun TypeExercise(ex: Exercise.TypeIn, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    TypedAnswer(
        title = "Inglizcha yozing",
        prompt = { BigPrompt(ex.prompt) },
        answer = ex.answers.first(),
        answers = ex.answers,
        wordId = ex.word.id,
        onAfter = { speaker.speak(ex.word.english) },
        onAnswer = onAnswer
    )
}

/** Letter boxes + 💡 hint + Tekshirish; shared by typing and the typed blank. */
@Composable
private fun TypedAnswer(
    title: String,
    prompt: @Composable () -> Unit,
    answer: String,
    answers: List<String>,
    wordId: Long,
    onAfter: () -> Unit,
    onAnswer: (List<WordAnswer>) -> Unit
) {
    val focus = remember { FocusRequester() }
    var typed by remember(answer) { mutableStateOf("") }
    var hints by remember(answer) { mutableIntStateOf(0) }
    var verdict by remember(answer) { mutableStateOf<Verdict?>(null) }
    val slots = remember(answer) { AnswerCheck.slots(answer) }
    val letters = remember(answer) { AnswerCheck.letters(answer) }
    LaunchedEffect(answer) { runCatching { focus.requestFocus() } }

    fun check() {
        if (verdict != null || typed.isEmpty()) return
        val v = Exercises.checkTyped(AnswerCheck.assemble(slots, typed), answers)
        verdict = v
        onAfter()
        onAnswer(listOf(WordAnswer(wordId, v, usedHint = hints > 0)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseTitle(title)
        prompt()
        LetterBoxInput(
            answer = answer,
            typed = typed,
            onTyped = { if (verdict == null) typed = it },
            onDone = ::check,
            focusRequester = focus,
            state = when (verdict) {
                null -> BoxState.TYPING
                Verdict.WRONG -> BoxState.WRONG
                else -> BoxState.RIGHT
            }
        )
        if (verdict == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OnIkkiButton(
                    text = "💡 Harf",
                    onClick = {
                        val ok = typed.zip(letters).takeWhile { (a, b) -> a == b }.size
                        if (ok < letters.length) {
                            typed = letters.take(ok + 1)
                            hints++
                        }
                    },
                    variant = OnIkkiButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp)
                )
                OnIkkiButton(text = "Tekshirish", onClick = ::check, modifier = Modifier.weight(2f).heightIn(min = 54.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- 6. blank

@Composable
private fun ClozeExercise(ex: Exercise.Cloze, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var filled by remember(ex) { mutableStateOf<String?>(null) }
    val sentence = ex.before + ex.answer + ex.after
    val prompt: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(
                text = buildAnnotatedString {
                    append(ex.before)
                    withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) { append(filled ?: "_____") }
                    append(ex.after)
                },
                color = colors.text,
                fontSize = 21.sp,
                fontFamily = OnIkkiFontFamily,
                textAlign = TextAlign.Center
            )
            Text(text = "(${Exercises.primary(ex.word.uzbek)})", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        }
    }
    if (ex.options.isEmpty()) {
        TypedAnswer(
            title = "Bo'sh joyga so'zni yozing",
            prompt = prompt,
            answer = ex.answer,
            answers = listOf(ex.answer),
            wordId = ex.word.id,
            onAfter = { filled = ex.answer; speaker.speak(sentence) },
            onAnswer = onAnswer
        )
    } else {
        val answerIndex = ex.options.indexOfFirst { it.equals(ex.answer, ignoreCase = true) }
        OptionsExercise(
            title = "Bo'sh joyni to'ldiring",
            prompt = prompt,
            options = ex.options,
            answer = answerIndex,
            onPicked = { v -> filled = ex.answer; onAnswer(listOf(WordAnswer(ex.word.id, v))) },
            onRevealed = { speaker.speak(sentence) }
        )
    }
}

// ---------------------------------------------------------------- 7. build

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildExercise(ex: Exercise.Build, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    // Bank indices, so repeated words ("I ... I") stay distinct.
    val chosen = remember(ex) { mutableStateListOf<Int>() }
    var verdict by remember(ex) { mutableStateOf<Verdict?>(null) }
    val translated = ex.translation != null
    LaunchedEffect(ex, speaker.ready) { if (speaker.ready && !translated) speaker.speak(ex.sentence) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseTitle(if (translated) "Inglizchaga tarjima qiling" else "Gapni tinglang va yig'ing")
        if (translated) {
            UzbekSentence(ex.translation!!)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeakerButton(speaker, ex.sentence, size = 56)
            SpeakerButton(speaker, ex.sentence, size = 44, slow = true)
            Text(
                text = "Kalit so'z: ${ex.word.english} — ${Exercises.primary(ex.word.uzbek)}",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
        }
        // Answer line.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.background)
                .border(
                    BorderStroke(
                        if (verdict == null) 1.dp else 2.dp,
                        when (verdict) {
                            null -> colors.divider
                            Verdict.WRONG -> colors.warmAccent
                            else -> colors.habitAccent
                        }
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            chosen.forEach { i -> WordChip(ex.bank[i], enabled = verdict == null) { chosen.remove(i) } }
        }
        // Word bank.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ex.bank.forEachIndexed { i, word ->
                val used = i in chosen
                Box(modifier = Modifier.alpha(if (used) 0.25f else 1f)) {
                    WordChip(word, enabled = !used && verdict == null) { chosen += i }
                }
            }
        }
        if (verdict == null) {
            OnIkkiButton(
                text = "Tekshirish",
                onClick = {
                    if (chosen.isEmpty()) return@OnIkkiButton
                    val v = Exercises.checkBuild(chosen.map { ex.bank[it] }, ex.tokens)
                    verdict = v
                    if (translated) speaker.speak(ex.sentence)
                    onAnswer(listOf(WordAnswer(ex.word.id, v)))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            )
        }
    }
}

@Composable
private fun UzbekSentence(text: String) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = text,
        color = colors.text,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(18.dp)
    )
}

/** Uzbek sentence → type it in English; one slipped word counts as "deyarli". */
@Composable
private fun TranslateExercise(ex: Exercise.Translate, speaker: Speaker, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var text by remember(ex) { mutableStateOf("") }
    var verdict by remember(ex) { mutableStateOf<Verdict?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseTitle("Gapni inglizcha yozing")
        UzbekSentence(ex.uzbek)
        Text(text = "Kalit so'z: ${ex.word.english}", color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { if (verdict == null) text = it },
            label = { Text("English") },
            minLines = 2,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )
        if (verdict == null) {
            OnIkkiButton(
                text = "Tekshirish",
                onClick = {
                    if (text.isBlank()) return@OnIkkiButton
                    val v = com.onikki.app.domain.vocab.SentenceCheck.compare(text, ex.answer)
                    verdict = v
                    speaker.speak(ex.answer)
                    onAnswer(listOf(WordAnswer(ex.word.id, v)))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            )
        }
    }
}

/** The word in an own sentence: the AI checks grammar and meaning when it can, otherwise only that the word is used. */
@Composable
private fun ComposeExercise(ex: Exercise.Compose, speaker: Speaker, checkSentence: SentenceChecker?, onAnswer: (List<WordAnswer>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var text by remember(ex) { mutableStateOf("") }
    var checking by remember(ex) { mutableStateOf(false) }
    var done by remember(ex) { mutableStateOf(false) }
    var error by remember(ex) { mutableStateOf<String?>(null) }

    fun finish(result: com.onikki.app.domain.vocab.SentenceVerdict) {
        done = true
        checking = false
        val note = listOfNotNull(result.corrected?.let { "✔ $it" }, result.note.ifBlank { null }).joinToString("\n").ifBlank { null }
        onAnswer(listOf(WordAnswer(ex.word.id, result.verdict, note = note)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseTitle("Shu so'z bilan o'zingiz gap tuzing")
        VerbPrompt(ex.word, speaker)
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { if (!done && !checking) { text = it; error = null } },
            label = { Text("Inglizcha gapingiz") },
            minLines = 2,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (checkSentence != null) "AI grammatika va ma'noni tekshiradi (kalit bo'lsa). Kalitsiz — so'z ishlatilgani tekshiriladi." else "So'z gapda ishlatilgani tekshiriladi.",
            color = colors.text.muted(0.5f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        error?.let { Text(text = it, color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
        if (!done) {
            if (checking) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
                    Text(text = "Tekshirilmoqda…", color = colors.text.muted(0.7f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                }
            } else {
                OnIkkiButton(
                    text = "Tekshirish",
                    onClick = {
                        val sentence = text.trim()
                        if (sentence.isEmpty()) return@OnIkkiButton
                        // The obvious misses are caught at once, without spending an AI call.
                        val quick = com.onikki.app.domain.vocab.SentenceCheck.offline(sentence, ex.word)
                        if (quick.verdict == Verdict.WRONG || checkSentence == null) {
                            if (quick.verdict == Verdict.WRONG) { error = quick.note; return@OnIkkiButton }
                            finish(quick)
                            return@OnIkkiButton
                        }
                        checking = true
                        scope.launch { finish(checkSentence(ex.word, sentence) ?: quick) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                )
                OnIkkiButton(
                    text = "Bilmayman — misolni ko'rsat",
                    onClick = {
                        finish(com.onikki.app.domain.vocab.SentenceVerdict(Verdict.WRONG, ex.word.example, ex.word.exampleUz ?: "Misol gapni o'qing va keyingi safar o'zingiz tuzing."))
                    },
                    variant = OnIkkiButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WordChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = text,
        color = colors.text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

// ---------------------------------------------------------------- feedback

/**
 * The panel under an answered exercise: green "To'g'ri!", amber "Deyarli to'g'ri" (with the right spelling)
 * or red "Noto'g'ri" with the right answer, and a big Davom etish.
 */
@Composable
fun AnswerPanel(verdict: Verdict, rightAnswer: String?, onContinue: () -> Unit, xp: Int = 0, combo: Int = 0, extra: (@Composable () -> Unit)? = null) {
    val colors = LocalOnIkkiColors.current
    val tint = when (verdict) {
        Verdict.CORRECT -> colors.habitAccent
        Verdict.ALMOST -> colors.accent
        Verdict.WRONG -> colors.warmAccent
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(tint.copy(alpha = 0.16f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (verdict) {
                    Verdict.CORRECT -> "✓ To'g'ri!"
                    Verdict.ALMOST -> "≈ Deyarli to'g'ri"
                    Verdict.WRONG -> "✗ Noto'g'ri"
                },
                color = tint,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            if (com.onikki.app.domain.vocab.Combo.shouldShow(combo)) {
                Text(text = "🔥 $combo ketma-ket", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.padding(end = 10.dp))
            }
            if (xp > 0) Text(text = "+$xp XP", color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        }
        if (verdict != Verdict.CORRECT && rightAnswer != null) {
            Text(text = "To'g'ri javob: $rightAnswer", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
        }
        extra?.invoke()
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(16.dp)).background(tint).clickable(onClick = onContinue),
            contentAlignment = Alignment.Center
        ) { Text(text = "Davom etish", color = Color(0xFF10131C), fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily) }
    }
}

/** What to show as "To'g'ri javob" for an exercise. */
fun rightAnswerOf(exercise: Exercise): String? = when (exercise) {
    is Exercise.Card -> null
    is Exercise.Choose -> exercise.options[exercise.answer]
    is Exercise.Listen -> "${exercise.spoken} — ${exercise.options[exercise.answer]}"
    is Exercise.Match -> null
    is Exercise.TypeIn -> exercise.answers.joinToString(" / ")
    is Exercise.Cloze -> exercise.before + exercise.answer + exercise.after
    is Exercise.Build -> exercise.sentence
    is Exercise.FormChoice -> verbLine(exercise.word).replace(", ", " — ")
    is Exercise.Forms -> verbLine(exercise.word).replace(", ", " — ")
    is Exercise.Dictate -> "${exercise.word.english} — ${Exercises.primary(exercise.word.uzbek)}"
    is Exercise.Spell -> exercise.answers.joinToString(" / ")
    is Exercise.VerbPast -> "${if (exercise.reverse) exercise.prompt + " → " + exercise.answers.joinToString(" / ") else exercise.word.english + " → " + exercise.answers.joinToString(" / ")}"
    is Exercise.Dictation -> if (exercise.mode == com.onikki.app.domain.vocab.DictationMode.WORD) exercise.word.english else listOfNotNull(exercise.word.english.takeIf { exercise.mode == com.onikki.app.domain.vocab.DictationMode.WORD_SENTENCE }, exercise.sentence).joinToString(" · ")
    is Exercise.Translate -> exercise.answer
    is Exercise.Compose -> exercise.word.example
}

/** Kicker style re-export for screens in this package. */
@Composable
fun LearnKicker(text: String, color: Color = LocalOnIkkiColors.current.accent) = Text(text = text, color = color, style = OnIkkiType.kicker)
