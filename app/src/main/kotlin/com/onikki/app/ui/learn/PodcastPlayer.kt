package com.onikki.app.ui.learn

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.onikki.app.domain.podcast.PodcastLine
import java.util.Locale

/**
 * Reads a podcast line by line with the phone's English voice. Each speaker gets their own pitch,
 * so a dialogue sounds like two people. Pause stops after nothing is lost: play resumes at the line
 * that was being read. State is Compose state, updated on the main thread.
 */
class PodcastPlayer(context: Context) {
    var ready by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    /** The line being read (or the one to resume from). */
    var current by mutableIntStateOf(0)
        private set
    /** Reached the end at least once. */
    var finished by mutableStateOf(false)
        private set
    var slow by mutableStateOf(false)

    private var lines: List<PodcastLine> = emptyList()
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.US)
                ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val i = utteranceId?.removePrefix("pod-")?.toIntOrNull() ?: return
                        main.post { current = i }
                    }

                    override fun onDone(utteranceId: String?) {
                        val i = utteranceId?.removePrefix("pod-")?.toIntOrNull() ?: return
                        if (i == lines.lastIndex) main.post { playing = false; finished = true; current = 0 }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { playing = false }
                    }
                })
            }
        }
    }

    fun load(newLines: List<PodcastLine>) {
        stop()
        lines = newLines
        current = 0
        finished = false
    }

    fun play(from: Int = current) {
        val engine = tts ?: return
        if (!ready || lines.isEmpty()) return
        engine.stop()
        val speakers = lines.mapNotNull { it.speaker }.distinct()
        engine.setSpeechRate(if (slow) 0.65f else 0.9f)
        for (i in from.coerceIn(0, lines.lastIndex)..lines.lastIndex) {
            val line = lines[i]
            // Pitch is taken when a line is queued: first speaker normal, second lower, third higher.
            engine.setPitch(
                when (speakers.indexOf(line.speaker)) {
                    1 -> 0.78f
                    2 -> 1.2f
                    else -> 1.0f
                }
            )
            engine.speak(line.text, TextToSpeech.QUEUE_ADD, null, "pod-$i")
        }
        engine.setPitch(1.0f)
        current = from
        playing = true
    }

    fun pause() {
        tts?.stop()
        playing = false
    }

    fun restart() = play(0)

    fun toggleSlow() {
        slow = !slow
        if (playing) play(current)
    }

    /** Reads just one line (from the transcript or a question's evidence). */
    fun playLine(i: Int) {
        val engine = tts ?: return
        if (!ready || i !in lines.indices) return
        engine.stop()
        engine.setSpeechRate(if (slow) 0.65f else 0.9f)
        engine.setPitch(1.0f)
        engine.speak(lines[i].text, TextToSpeech.QUEUE_FLUSH, null, "one-$i")
        playing = false
    }

    fun stop() {
        tts?.stop()
        playing = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

@Composable
fun rememberPodcastPlayer(): PodcastPlayer {
    val context = LocalContext.current
    val player = remember { PodcastPlayer(context) }
    DisposableEffect(player) { onDispose { player.shutdown() } }
    return player
}
