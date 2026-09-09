package com.onikki.app.data.repository

/**
 * In-memory "favqulodda ochish" (emergency unlock) grace periods, per package.
 * Lives only for the app process's lifetime by design — a killed/restarted
 * process re-evaluates blocking from scratch, which is the safer default.
 */
object BlockOverrides {
    private const val DEFAULT_GRACE_MS = 10 * 60 * 1000L
    private val grantedUntil = mutableMapOf<String, Long>()

    fun grant(packageName: String, durationMs: Long = DEFAULT_GRACE_MS) {
        grantedUntil[packageName] = System.currentTimeMillis() + durationMs
    }

    fun isActive(packageName: String): Boolean =
        (grantedUntil[packageName] ?: 0L) > System.currentTimeMillis()
}
