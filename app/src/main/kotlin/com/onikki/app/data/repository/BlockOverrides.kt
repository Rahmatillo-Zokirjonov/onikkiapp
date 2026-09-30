package com.onikki.app.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * "Opened by passing the challenge" grace periods, per package, persisted.
 *
 * They used to live only in memory, which broke on real phones: clearing the app from recents, or the
 * OEM killing the background process (Xiaomi/Samsung do it often), restarted the accessibility service
 * with an empty map — so an app unlocked for 15 minutes got blocked again on the next open. Now the
 * expiry time is written to disk and survives process death. Must be [init]ed from Application.onCreate.
 */
object BlockOverrides {
    private const val PREFS = "block_overrides"
    private const val DEFAULT_GRACE_MS = 10 * 60 * 1000L
    private const val SESSION_PREFS = "block_sessions"
    /** Safety cap for "until you leave" — never open for longer than this even if leaving went unnoticed. */
    private const val SESSION_MAX_MS = 3 * 60 * 60 * 1000L
    /** Returning to the app right after the challenge can flash the home screen; that isn't leaving. */
    private const val SESSION_SETTLE_MS = 2_000L

    private var prefs: SharedPreferences? = null
    private var sessionPrefs: SharedPreferences? = null
    private val cache = mutableMapOf<String, Long>()
    /** Packages unlocked "until you leave" (their expiry in [cache] is only the safety cap). */
    private val sessions = mutableSetOf<String>()
    private val sessionStartedAt = mutableMapOf<String, Long>()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val s = context.applicationContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        sessionPrefs = s
        synchronized(cache) { sessions += s.all.keys }
        val now = System.currentTimeMillis()
        synchronized(cache) {
            p.all.forEach { (pkg, until) -> if (until is Long && until > now) cache[pkg] = until }
        }
        // Drop expired entries so the file doesn't grow forever.
        p.edit().apply { p.all.forEach { (k, v) -> if (v !is Long || v <= now) remove(k) } }.apply()
    }

    fun grant(packageName: String, durationMs: Long = DEFAULT_GRACE_MS) {
        val until = System.currentTimeMillis() + durationMs
        synchronized(cache) {
            cache[packageName] = until
            sessions -= packageName
        }
        prefs?.edit()?.putLong(packageName, until)?.apply()
        sessionPrefs?.edit()?.remove(packageName)?.apply()
    }

    /** Open [packageName] only until the user switches to another app or the home screen. */
    fun grantSession(packageName: String) {
        val until = System.currentTimeMillis() + SESSION_MAX_MS
        synchronized(cache) {
            cache[packageName] = until
            sessions += packageName
            sessionStartedAt[packageName] = System.currentTimeMillis()
        }
        prefs?.edit()?.putLong(packageName, until)?.apply()
        sessionPrefs?.edit()?.putBoolean(packageName, true)?.apply()
    }

    fun hasSessions(): Boolean = synchronized(cache) { sessions.isNotEmpty() }

    fun isSession(packageName: String): Boolean = synchronized(cache) { packageName in sessions }

    /** The user moved to [foreground]: every "until you leave" unlock for another app ends now. */
    fun endSessionsExcept(foreground: String) {
        val ended = synchronized(cache) {
            val now = System.currentTimeMillis()
            val gone = sessions.filter { it != foreground && now - (sessionStartedAt[it] ?: 0L) >= SESSION_SETTLE_MS }
            gone.forEach { cache.remove(it); sessions.remove(it); sessionStartedAt.remove(it) }
            gone
        }
        if (ended.isEmpty()) return
        prefs?.edit()?.apply { ended.forEach { remove(it) } }?.apply()
        sessionPrefs?.edit()?.apply { ended.forEach { remove(it) } }?.apply()
    }

    fun isActive(packageName: String): Boolean {
        val until = synchronized(cache) { cache[packageName] } ?: prefs?.getLong(packageName, 0L) ?: 0L
        return until > System.currentTimeMillis()
    }

    /** Minutes left of the grace (for the UI), 0 when none. */
    fun minutesLeft(packageName: String): Long {
        val until = synchronized(cache) { cache[packageName] } ?: prefs?.getLong(packageName, 0L) ?: 0L
        return ((until - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
    }
}
