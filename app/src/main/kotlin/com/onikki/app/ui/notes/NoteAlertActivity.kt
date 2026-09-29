package com.onikki.app.ui.notes

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.onikki.app.MainActivity
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Note
import com.onikki.app.domain.notifications.NOTE_SNOOZE_MINUTES
import com.onikki.app.domain.notifications.NoteReminderNotifier
import com.onikki.app.domain.notifications.snoozeNoteReminder
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiTheme
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * The "Juda muhim" note alert: an alarm-style screen shown over the lock screen or on top of whatever
 * app is open. It's a separate Activity (like BlockedScreenActivity) because On ikki usually isn't the
 * foreground app when it fires. The ringing lives in the notification, so every exit cancels it.
 */
class NoteAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0)
        val dao = (application as OnIkkiApplication).database.noteDao()

        setContent {
            OnIkkiTheme {
                var note by remember { mutableStateOf<Note?>(null) }
                LaunchedEffect(noteId) {
                    note = dao.findById(noteId)
                    if (note == null) dismiss(noteId)
                }
                BackHandler { dismiss(noteId) }
                note?.let { loaded ->
                    NoteAlertScreen(
                        note = loaded,
                        onDismiss = { dismiss(noteId) },
                        onSnooze = {
                            lifecycleScope.launch {
                                NoteReminderNotifier.cancel(this@NoteAlertActivity, noteId)
                                snoozeNoteReminder(this@NoteAlertActivity, noteId)
                                finish()
                            }
                        },
                        onOpen = {
                            NoteReminderNotifier.cancel(this, noteId)
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun dismiss(noteId: Long) {
        NoteReminderNotifier.cancel(this, noteId)
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_NOTE_ID = "note_id"

        fun intent(context: Context, noteId: Long): Intent =
            Intent(context, NoteAlertActivity::class.java)
                .putExtra(EXTRA_NOTE_ID, noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun NoteAlertScreen(note: Note, onDismiss: () -> Unit, onSnooze: () -> Unit, onOpen: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val time = note.remindAt?.toLocalTime() ?: LocalTime.now()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = "JUDA MUHIM ESLATMA", color = colors.warmAccent, style = OnIkkiType.kicker)
            Text(
                text = "%02d:%02d".format(time.hour, time.minute),
                color = colors.text,
                fontSize = 56.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
            Text(
                text = note.displayTitle(),
                color = colors.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
            val body = if (note.title.isBlank()) note.content.trim().substringAfter('\n', "").trim() else note.content.trim()
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    color = colors.text.muted(0.7f),
                    fontSize = 15.sp,
                    fontFamily = OnIkkiFontFamily,
                    lineHeight = 22.sp,
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnIkkiButton(text = "Ko'rdim", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OnIkkiButton(
                    text = "$NOTE_SNOOZE_MINUTES daqiqadan keyin",
                    onClick = onSnooze,
                    variant = OnIkkiButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                OnIkkiButton(
                    text = "Qaydni ochish",
                    onClick = onOpen,
                    variant = OnIkkiButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
