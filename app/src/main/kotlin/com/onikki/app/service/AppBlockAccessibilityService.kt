package com.onikki.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.ScreenTimeRepository
import com.onikki.app.ui.blocked.BlockedScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private const val RELAUNCH_DEBOUNCE_MS = 2_000L

/**
 * Detects the foreground app (TYPE_WINDOW_STATE_CHANGED) and launches
 * [BlockedScreenActivity] on top of it when [ScreenTimeRepository.resolveBlockReason]
 * says it should be blocked (TZ 3.6). Requires the user to enable this service by
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
        val app = application as OnIkkiApplication
        repository = ScreenTimeRepository(
            context = applicationContext,
            appUsageDao = app.database.appUsageDao(),
            appLimitDao = app.database.appLimitDao(),
            dailyReviewDao = app.database.dailyReviewDao()
        )
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

        serviceScope.launch {
            val reason = repository.resolveBlockReason(packageName, LocalDateTime.now()) ?: return@launch
            val now = System.currentTimeMillis()
            val recentlyLaunchedSamePackage =
                packageName == lastLaunchedPackage && (now - lastLaunchedAtMillis) < RELAUNCH_DEBOUNCE_MS
            if (recentlyLaunchedSamePackage) return@launch

            lastLaunchedPackage = packageName
            lastLaunchedAtMillis = now
            startActivity(BlockedScreenActivity.createIntent(applicationContext, packageName, reason))
        }
    }

    override fun onInterrupt() {
        // Required override; nothing to clean up.
    }
}
