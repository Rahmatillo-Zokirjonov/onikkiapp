package com.onikki.app.ui.vocabulary

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.EnglishDictionary
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------------------------------------------------------------- pronunciation

/** The phone's text-to-speech in English. [ready] stays false if no English voice is installed. */
class Speaker(context: Context) {
    var ready by mutableStateOf(false)
        private set
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(0.9f)
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.setSpeechRate(0.9f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "onikki-word")
    }

    /** Slower, for listening exercises (🐢). */
    fun speakSlow(text: String) {
        if (!ready) return
        tts?.setSpeechRate(0.55f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "onikki-word-slow")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}

@Composable
fun SpeakButton(speaker: Speaker, text: String, big: Boolean = false) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .size(if (big) 64.dp else 36.dp)
            .background(if (speaker.ready) colors.accent800 else colors.neutral800, RoundedCornerShape(50))
            .clickable(enabled = speaker.ready) { speaker.speak(text) },
        contentAlignment = Alignment.Center
    ) { Text(text = "🔊", fontSize = if (big) 26.sp else 16.sp) }
}

// ---------------------------------------------------------------- dictionary

/** The built-in English → Uzbek list, read once from assets. */
object DictionaryStore {
    @Volatile private var dict: EnglishDictionary? = null

    suspend fun get(context: Context): EnglishDictionary = dict ?: withContext(Dispatchers.IO) {
        dict ?: context.applicationContext.assets.open("dict_en_uz.tsv").bufferedReader(Charsets.UTF_8)
            .useLines { EnglishDictionary(it.toList().asSequence()) }
            .also { dict = it }
    }
}

// ---------------------------------------------------------------- letter boxes

enum class BoxState { TYPING, RIGHT, WRONG }

/**
 * One box per letter of [answer]; spaces, apostrophes and hyphens are already filled in, so only
 * letters are typed. The keyboard goes to a hidden field; tapping the boxes brings it back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterBoxInput(
    answer: String,
    typed: String,
    onTyped: (String) -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
    state: BoxState = BoxState.TYPING,
    enabled: Boolean = true
) {
    val colors = LocalOnIkkiColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val slots = remember(answer) { AnswerCheck.slots(answer) }
    val letters = AnswerCheck.letterCount(slots)
    val filled = AnswerCheck.fill(slots, typed)
    val boxSize = when {
        slots.size <= 8 -> 38.dp
        slots.size <= 12 -> 30.dp
        else -> 26.dp
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = typed,
            onValueChange = { raw ->
                if (!enabled) return@BasicTextField
                // Only letters go in; apostrophes and spaces are part of the boxes already.
                onTyped(AnswerCheck.letters(raw).take(letters))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focusRequester)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    runCatching { focusRequester.requestFocus() }
                    keyboard?.show()
                }
        ) {
            var letterIndex = 0
            slots.forEachIndexed { i, slot ->
                val ch = filled[i]
                if (slot.fixed != null) {
                    if (slot.fixed == ' ') {
                        Box(modifier = Modifier.size(width = 10.dp, height = boxSize))
                    } else {
                        Box(modifier = Modifier.size(width = 10.dp, height = boxSize), contentAlignment = Alignment.Center) {
                            Text(text = slot.fixed.toString(), color = colors.text.muted(0.7f), fontSize = 18.sp, fontFamily = OnIkkiFontFamily)
                        }
                    }
                } else {
                    val isCursor = state == BoxState.TYPING && letterIndex == typed.length
                    letterIndex++
                    val border = when (state) {
                        BoxState.RIGHT -> colors.habitAccent
                        BoxState.WRONG -> colors.warmAccent
                        BoxState.TYPING -> if (isCursor) colors.accent else colors.divider
                    }
                    Box(
                        modifier = Modifier
                            .size(boxSize)
                            .background(if (ch != null) colors.surface else Color.Transparent, RoundedCornerShape(8.dp))
                            .border(BorderStroke(if (isCursor) 2.dp else 1.dp, border), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch?.toString().orEmpty(),
                            color = colors.text,
                            fontSize = if (boxSize > 30.dp) 19.sp else 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = OnIkkiFontFamily
                        )
                    }
                }
            }
        }
        Text(
            text = "$letters harf",
            color = colors.text.muted(0.4f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}
