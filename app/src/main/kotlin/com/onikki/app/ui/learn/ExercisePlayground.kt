package com.onikki.app.ui.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.domain.vocab.Exercise
import com.onikki.app.domain.vocab.ExerciseType
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.rememberSpeaker
import kotlin.random.Random

/**
 * Try every exercise type on real words (stage 2 preview). Nothing is written to the SRS here —
 * lessons (next stage) record answers.
 */
@Composable
fun ExercisePlaygroundRoute(onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val speaker = rememberSpeaker()
    var words by remember { mutableStateOf<List<VocabWord>>(emptyList()) }
    LaunchedEffect(Unit) {
        val all = app.database.vocabWordDao().getAll()
        // Words with an example sentence first, so blanks and sentence building have material.
        words = all.sortedByDescending { it.example != null }
    }
    var type by remember { mutableStateOf(ExerciseType.CARD) }
    var round by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Pair<Verdict, String?>?>(null) }
    val exercise: Exercise? = remember(words, type, round) {
        if (words.size < 4) null else build(type, words, Random(round * 31 + type.ordinal))
    }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SubScreenHeader(title = "Mashqlar (sinov)", onBack = onBack)
            Text(text = "Natijalar saqlanmaydi — bu faqat ko'rib chiqish uchun.", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExerciseType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t; result = null; round++ }, label = { Text(t.label) })
                }
            }
            when {
                words.size < 4 -> Text(text = "Kamida 4 ta so'z kerak", color = colors.warmAccent, fontFamily = OnIkkiFontFamily)
                exercise == null -> Text(text = "Bu tur uchun mos so'z topilmadi", color = colors.text.muted(0.6f), fontFamily = OnIkkiFontFamily)
                else -> Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.runtime.key(exercise) {
                        ExerciseView(exercise, speaker) { answers ->
                            result = if (answers.isEmpty()) Verdict.CORRECT to null
                            else {
                                val worst = answers.maxOf { it.verdict.ordinal }
                                Verdict.entries[worst] to rightAnswerOf(exercise)
                            }
                        }
                    }
                }
            }
        }
        result?.let { (verdict, right) ->
            AnswerPanel(verdict, right, onContinue = { result = null; round++ })
        }
    }
}

private fun build(type: ExerciseType, words: List<VocabWord>, random: Random): Exercise? {
    val withExample = words.filter { it.example != null }
    val pick = (if (type in setOf(ExerciseType.CLOZE_CHOICE, ExerciseType.CLOZE_TYPE, ExerciseType.BUILD)) withExample else words).randomOrNull(random) ?: return null
    return when (type) {
        ExerciseType.CARD -> Exercise.Card(pick)
        ExerciseType.CHOOSE -> Exercises.choose(pick, words, random)
        ExerciseType.LISTEN -> Exercises.listen(pick, words, random)
        ExerciseType.MATCH -> Exercises.match(words.shuffled(random), random)
        ExerciseType.TYPE -> Exercises.typeIn(pick)
        ExerciseType.CLOZE_CHOICE, ExerciseType.CLOZE_TYPE -> Exercises.blank(pick.example!!, pick.english, pick.pastForm)?.let {
            Exercises.cloze(pick, it, words, random, typed = type == ExerciseType.CLOZE_TYPE)
        }
        ExerciseType.BUILD -> Exercises.build(pick, words, random)
    }
}
