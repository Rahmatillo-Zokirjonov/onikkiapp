package com.onikki.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.onikki.app.data.local.OnboardingStore
import com.onikki.app.ui.navigation.OnIkkiNavHost
import com.onikki.app.ui.notes.NoteDeepLink
import com.onikki.app.ui.onboarding.OnboardingRoute
import com.onikki.app.ui.theme.OnIkkiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh launch — after rotation the extra is still in the intent but was already handled.
        if (savedInstanceState == null) handleOpenNote(intent)
        setContent {
            OnIkkiTheme {
                val onboardingStore = remember { OnboardingStore(applicationContext) }
                val isOnboardingComplete by onboardingStore.isComplete.collectAsState(initial = null)
                when (isOnboardingComplete) {
                    null -> Unit
                    false -> OnboardingRoute()
                    true -> OnIkkiNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenNote(intent)
    }

    private fun handleOpenNote(intent: Intent?) {
        val noteId = intent?.getLongExtra(EXTRA_OPEN_NOTE_ID, 0L) ?: 0L
        if (noteId > 0) NoteDeepLink.request(noteId)
    }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
    }
}
