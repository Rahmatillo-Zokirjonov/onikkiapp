package com.onikki.app.ui.blocked

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.local.ChallengeMode
import com.onikki.app.data.local.ChallengeSettingsStore
import com.onikki.app.data.repository.AppGate
import com.onikki.app.data.repository.BlockOverrides
import com.onikki.app.data.repository.BlockReason
import com.onikki.app.domain.screentime.VocabChallenge
import com.onikki.app.ui.theme.OnIkkiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Shown on top of a blocked app, or of an app that asks for the word challenge on open. Launched by
 * [com.onikki.app.service.AppBlockAccessibilityService]. A separate Activity in its own task, so that
 * finishing it after a passed challenge drops the user straight back into the app they opened.
 */
class BlockedScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val gate = intent.readGate()
        if (packageName == null || gate == null) {
            finish()
            return
        }
        val appName = resolveAppName(packageName) ?: packageName
        val app = application as OnIkkiApplication
        val settingsStore = ChallengeSettingsStore(this)

        setContent {
            OnIkkiTheme {
                var challenge by remember { mutableStateOf<ChallengeSpec?>(null) }
                // An app that simply asks for the challenge on open goes straight to it.
                LaunchedEffect(Unit) {
                    if (gate is AppGate.ChallengeRequired) challenge = loadChallenge(app, settingsStore)
                }
                val spec = challenge
                if (spec != null) {
                    ChallengeScreen(
                        appName = appName,
                        spec = spec,
                        onAnswer = { wordId, correct ->
                            lifecycleScope.launch {
                                app.database.vocabWordDao().recordAnswer(
                                    wordId, if (correct) 1 else 0, if (correct) 0 else 1, System.currentTimeMillis()
                                )
                            }
                        },
                        onPassed = {
                            if (spec.graceMinutes <= 0) BlockOverrides.grantSession(packageName)
                            else BlockOverrides.grant(packageName, spec.graceMinutes * 60_000L)
                            returnToApp(packageName)
                        },
                        onGiveUp = { goHome() }
                    )
                } else if (gate is AppGate.Blocked) {
                    BlockedScreen(
                        appName = appName,
                        reason = gate.reason,
                        strict = gate.strict,
                        canChallenge = gate.canChallenge,
                        onClose = { goHome() },
                        onStartChallenge = { lifecycleScope.launch { challenge = loadChallenge(app, settingsStore) } }
                    )
                }
            }
        }
    }

    /**
     * Re-shown for the same app (it came back on top) → keep the screen as is, so a half-done challenge
     * isn't reset. A different app or a different verdict → rebuild for the new one.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val samePackage = intent.getStringExtra(EXTRA_PACKAGE) == this.intent.getStringExtra(EXTRA_PACKAGE)
        val sameGate = intent.readGate() == this.intent.readGate()
        if (!samePackage || !sameGate) {
            setIntent(intent)
            recreate()
        }
    }

    private suspend fun loadChallenge(app: OnIkkiApplication, store: ChallengeSettingsStore): ChallengeSpec {
        val settings = store.settings.first()
        val words = app.database.vocabWordDao().getAll()
        // No words yet → the typed phrase still works as a challenge.
        return if (settings.mode == ChallengeMode.WORDS && words.isNotEmpty()) {
            ChallengeSpec(
                questions = VocabChallenge.pickQuestions(words, settings.wordCount.coerceAtMost(words.size), settings.direction),
                phrase = null,
                graceMinutes = settings.graceMinutes
            )
        } else {
            ChallengeSpec(questions = emptyList(), phrase = settings.phrase, graceMinutes = settings.graceMinutes)
        }
    }

    /** Back to the app the user was opening (its existing task, not a fresh start). */
    private fun returnToApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            runCatching { startActivity(launch) }
        }
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun resolveAppName(packageName: String): String? = try {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        private const val EXTRA_PACKAGE = "package_name"
        private const val EXTRA_GATE = "gate"
        private const val EXTRA_REASON_TYPE = "reason_type"
        private const val EXTRA_REASON_DETAIL = "reason_detail"
        private const val EXTRA_REASON_DETAIL2 = "reason_detail2"
        private const val EXTRA_STRICT = "strict"
        private const val EXTRA_CAN_CHALLENGE = "can_challenge"

        fun createIntent(context: Context, packageName: String, gate: AppGate): Intent =
            Intent(context, BlockedScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(EXTRA_PACKAGE, packageName)
                when (gate) {
                    AppGate.Allowed -> Unit
                    AppGate.ChallengeRequired -> putExtra(EXTRA_GATE, "CHALLENGE")
                    is AppGate.Blocked -> {
                        putExtra(EXTRA_GATE, "BLOCKED")
                        putExtra(EXTRA_STRICT, gate.strict)
                        putExtra(EXTRA_CAN_CHALLENGE, gate.canChallenge)
                        when (val reason = gate.reason) {
                            BlockReason.LimitReached -> putExtra(EXTRA_REASON_TYPE, "LIMIT")
                            is BlockReason.Schedule -> {
                                putExtra(EXTRA_REASON_TYPE, "SCHEDULE")
                                putExtra(EXTRA_REASON_DETAIL, reason.start.toString())
                                putExtra(EXTRA_REASON_DETAIL2, reason.end.toString())
                            }
                            is BlockReason.Zone -> {
                                putExtra(EXTRA_REASON_TYPE, "ZONE")
                                putExtra(EXTRA_REASON_DETAIL, reason.zoneName)
                            }
                            is BlockReason.PrayerTime -> {
                                putExtra(EXTRA_REASON_TYPE, "PRAYER")
                                putExtra(EXTRA_REASON_DETAIL, reason.prayerName)
                            }
                        }
                    }
                }
            }

        private fun Intent.readGate(): AppGate? = when (getStringExtra(EXTRA_GATE)) {
            "CHALLENGE" -> AppGate.ChallengeRequired
            "BLOCKED" -> {
                val detail = getStringExtra(EXTRA_REASON_DETAIL).orEmpty()
                val reason = when (getStringExtra(EXTRA_REASON_TYPE)) {
                    "SCHEDULE" -> BlockReason.Schedule(
                        runCatching { LocalTime.parse(detail) }.getOrDefault(LocalTime.MIDNIGHT),
                        runCatching { LocalTime.parse(getStringExtra(EXTRA_REASON_DETAIL2)) }.getOrDefault(LocalTime.MIDNIGHT)
                    )
                    "ZONE" -> BlockReason.Zone(detail)
                    "PRAYER" -> BlockReason.PrayerTime(detail)
                    else -> BlockReason.LimitReached
                }
                AppGate.Blocked(reason, getBooleanExtra(EXTRA_STRICT, false), getBooleanExtra(EXTRA_CAN_CHALLENGE, false))
            }
            else -> null
        }
    }
}
