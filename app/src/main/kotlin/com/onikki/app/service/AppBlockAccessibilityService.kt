package com.onikki.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.AppGate
import com.onikki.app.data.repository.BlockOverrides
import com.onikki.app.domain.screentime.SessionLeave
import com.onikki.app.data.repository.ScreenTimeRepository
import com.onikki.app.domain.screentime.ZoneLocationTracker
import com.onikki.app.ui.blocked.BlockedScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** A second window from the same app within this time counts as the same opening. */
private const val SETTLE_MS = 600L

/**
 * Detects the foreground app (TYPE_WINDOW_STATE_CHANGED) and launches
 * [BlockedScreenActivity] on top of it when [ScreenTimeRepository.resolveGate]
 * says it's blocked or needs the unlock challenge (TZ 3.6). Requires the user to enable this service by
 * hand in Settings > Accessibility — there is no runtime permission dialog for it.
 *
 * NOT exercised on a real device in this session (no Android toolchain available
 * here) — the shape of this service is standard, but verify the enable flow and
 * event handling on a real device before relying on it.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ScreenTimeRepository

    private var lastLaunchedPackage: String? = null
    private var lastLaunchedAtMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        val app = application as OnIkkiApplication
        repository = ScreenTimeRepository(
            context = applicationContext,
            appUsageDao = app.database.appUsageDao(),
            appLimitDao = app.database.appLimitDao(),
            dailyReviewDao = app.database.dailyReviewDao(),
            blockZoneDao = app.database.blockZoneDao()
        )
        // Place-based blocks need a recent location; the service is the one thing always running.
        ZoneLocationTracker.start(applicationContext)
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        // "Until you leave" unlocks end the moment another real app or the home screen comes up —
        // before this app's own gate is checked, so coming back to the unlocked app asks again.
        if (BlockOverrides.hasSessions() && isLeaving(packageName)) BlockOverrides.endSessionsExcept(packageName)

        // Every event is evaluated to the end — cancelling an earlier evaluation when an app opens a second
        // window (Chrome does, ~20 ms apart) killed an in-flight location fetch and let the app through.
        // Launches for the same app within SETTLE_MS are delayed so the last one lands on top.
        serviceScope.launch {
            val gate = repository.resolveGate(packageName, LocalDateTime.now())
            if (gate == AppGate.Allowed) return@launch
            val sinceLast = System.currentTimeMillis() - lastLaunchedAtMillis
            if (packageName == lastLaunchedPackage && sinceLast < SETTLE_MS) delay(SETTLE_MS - sinceLast)
            lastLaunchedPackage = packageName
            lastLaunchedAtMillis = System.currentTimeMillis()
            startActivity(BlockedScreenActivity.createIntent(applicationContext, packageName, gate))
        }
    }

    private val launchable = mutableMapOf<String, Boolean>()

    private fun isLeaving(packageName: String): Boolean {
        val hasLauncher = launchable.getOrPut(packageName) { packageManager.getLaunchIntentForPackage(packageName) != null }
        return SessionLeave.isLeaving(
            foreground = packageName,
            ownPackage = applicationContext.packageName,
            homePackages = homePackages(),
            keyboardPackages = keyboardPackages(),
            hasLauncherEntry = hasLauncher
        )
    }

    private fun homePackages(): Set<String> = runCatching {
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet()
    }.getOrDefault(emptySet())

    private fun keyboardPackages(): Set<String> = runCatching {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        ZoneLocationTracker.stop(applicationContext)
        super.onDestroy()
    }

    override fun onInterrupt() {
        // Required override; nothing to clean up.
    }

    companion object {
        /**
         * True while Android has this service bound. "Enabled in settings" is not enough: some phones
         * kill it (e.g. after clearing recents) and leave it enabled-but-dead until toggled off and on.
         */
        @Volatile var isConnected: Boolean = false
            private set
    }
}
