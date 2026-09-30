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

    private var prefs: SharedPreferences? = null
    private val cache = mutableMapOf<String, Long>()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val now = System.currentTimeMillis()
        synchronized(cache) {
            p.all.forEach { (pkg, until) -> if (until is Long && until > now) cache[pkg] = until }
        }
        // Drop expired entries so the file doesn't grow forever.
        p.edit().apply { p.all.forEach { (k, v) -> if (v !is Long || v <= now) remove(k) } }.apply()
    }

    fun grant(packageName: String, durationMs: Long = DEFAULT_GRACE_MS) {
        val until = System.currentTimeMillis() + durationMs
        synchronized(cache) { cache[packageName] = until }
        prefs?.edit()?.putLong(packageName, until)?.apply()
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
