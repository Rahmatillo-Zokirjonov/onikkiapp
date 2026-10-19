package com.onikki.app.ui.learn

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.onikki.app.domain.vocab.Verdict

/**
 * A short tone and a haptic tick after each answer. Uses the system tone generator and the view's
 * haptics, so no sound files and no VIBRATE permission are needed.
 */
class FeedbackFx(private val view: View) {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull()

    fun play(verdict: Verdict, sound: Boolean, vibrate: Boolean) {
        if (sound) {
            runCatching {
                when (verdict) {
                    Verdict.CORRECT -> tone?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                    Verdict.ALMOST -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                    Verdict.WRONG -> tone?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
                }
            }
        }
        if (vibrate) {
            val constant = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && verdict == Verdict.WRONG -> HapticFeedbackConstants.REJECT
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
                verdict == Verdict.WRONG -> HapticFeedbackConstants.LONG_PRESS
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(constant)
        }
    }

    fun release() {
        tone?.release()
    }
}

@Composable
fun rememberFeedbackFx(): FeedbackFx {
    val view = LocalView.current
    val fx = remember(view) { FeedbackFx(view) }
    DisposableEffect(fx) { onDispose { fx.release() } }
    return fx
}
