package com.onikki.app.ui.learn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * The phone's speech recognizer in English, as Compose state. [start] listens once; the alternatives
 * arrive in [onResult]. With [continuous], it keeps listening after pauses until [stop], gluing the
 * pieces together — for spoken answers longer than one breath.
 */
class SpeechInput(private val context: Context) {
    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    var listening by mutableStateOf(false)
        private set
    /** What has been understood so far (partial while speaking). */
    var partial by mutableStateOf("")
        private set
    /** Microphone level 0..1 for the animation. */
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var continuous = false
    private var stopped = false
    private var collected = ""
    private var onResult: (List<String>) -> Unit = {}

    fun start(continuous: Boolean = false, onResult: (List<String>) -> Unit) {
        if (!available) { error = "Telefonda ovozni tanish xizmati yo'q (Google ilovasini yangilang)"; return }
        this.continuous = continuous
        this.onResult = onResult
        stopped = false
        collected = ""
        partial = ""
        error = null
        listenOnce()
    }

    private fun listenOnce() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { level = 0f }
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                partial = (collected + " " + p).trim()
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (continuous) {
                    collected = (collected + " " + list.firstOrNull().orEmpty()).trim()
                    partial = collected
                    if (!stopped) { listenOnce(); return }
                    finish(listOf(collected))
                } else finish(list)
            }

            override fun onError(code: Int) {
                // In a long answer, silence just means "keep listening".
                if (continuous && !stopped && (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    listenOnce(); return
                }
                if (continuous && collected.isNotBlank()) { finish(listOf(collected)); return }
                listening = false
                level = 0f
                error = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ovoz eshitilmadi — yana bir bor, balandroq gapiring"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet kerak (ovozni tanish uchun)"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofonga ruxsat bering"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mikrofon band, bir soniyadan keyin urinib ko'ring"
                    else -> "Ovozni tanib bo'lmadi (xato $code)"
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        r.startListening(intent)
    }

    private fun finish(list: List<String>) {
        listening = false
        level = 0f
        onResult(list)
    }

    /** Stops listening; what was heard is still delivered. */
    fun stop() {
        stopped = true
        recognizer?.stopListening()
    }

    fun cancel() {
        stopped = true
        recognizer?.cancel()
        listening = false
        level = 0f
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}

@Composable
fun rememberSpeechInput(): SpeechInput {
    val context = LocalContext.current
    val input = remember { SpeechInput(context) }
    DisposableEffect(input) { onDispose { input.cancel(); input.destroy() } }
    return input
}
