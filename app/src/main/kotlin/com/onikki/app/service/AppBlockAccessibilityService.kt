package com.onikki.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.AppGate
import com.onikki.app.data.repository.ScreenTimeRepository
import com.onikki.app.domain.screentime.ZoneLocationTracker
import com.onikki.app.ui.blocked.BlockedScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
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
    private var pendingLaunch: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
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

        // Coalesce bursts: an app often opens a second window right after the first (splash → main,
        // Chrome's first-run screen), which would land on top of the block screen. Every event from
        // the blocked app re-shows it; a burst is collapsed into one launch after it settles.
        pendingLaunch?.cancel()
        pendingLaunch = serviceScope.launch {
            val gate = repository.resolveGate(packageName, LocalDateTime.now())
            if (gate == AppGate.Allowed) return@launch
            val sinceLast = System.currentTimeMillis() - lastLaunchedAtMillis
            if (packageName == lastLaunchedPackage && sinceLast < SETTLE_MS) delay(SETTLE_MS - sinceLast)
            lastLaunchedPackage = packageName
            lastLaunchedAtMillis = System.currentTimeMillis()
            startActivity(BlockedScreenActivity.createIntent(applicationContext, packageName, gate))
        }
    }

    override fun onDestroy() {
        ZoneLocationTracker.stop(applicationContext)
        super.onDestroy()
    }

    override fun onInterrupt() {
        // Required override; nothing to clean up.
    }
}
