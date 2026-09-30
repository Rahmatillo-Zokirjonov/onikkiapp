package com.onikki.app.domain.screentime

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.onikki.app.data.db.entity.BlockZone
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Where the phone is, for place-based blocks. Started by the accessibility service.
 *
 * Lessons from real use (a block that silently stopped working while the user sat still at school):
 * - updates must be periodic, not "after moving 50 m" — a phone that doesn't move got no new fix, its last
 *   fix aged past the freshness limit, and the zone rule stopped applying;
 * - the last fix is persisted, so clearing the app from recents doesn't erase where the phone was;
 * - when the known fix is old at the moment an app opens, a fresh one is requested right then.
 */
object ZoneLocationTracker {
    private const val UPDATE_INTERVAL_MS = 2 * 60_000L
    /** Newer than this: use as is. */
    private const val FRESH_MS = 10 * 60_000L
    /** Older fixes are still used when a fresh one can't be had — a phone that reports nothing new hasn't moved far. */
    private const val USABLE_MS = 6 * 60 * 60_000L
    private const val REFRESH_COOLDOWN_MS = 60_000L
    private const val PREFS = "zone_location"

    @Volatile private var latest: Location? = null
    @Volatile private var lastRefreshAttempt = 0L
    private var listening = false

    private var listener: LocationListener? = null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission
    fun start(context: Context) {
        restore(context)
        if (listening || !hasPermission(context)) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .forEach { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let { offer(it, context) } }
        // Periodic (distance 0) at balanced power: fused on Android 12+ blends Wi-Fi/cell/GPS; network elsewhere.
        val appContext = context.applicationContext
        val l = LocationListener { offer(it, appContext) }
        listener = l
        listOfNotNull(activeProvider(manager), LocationManager.PASSIVE_PROVIDER).forEach { provider ->
            runCatching { manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, 0f, l, Looper.getMainLooper()) }
        }
        listening = true
    }

    fun stop(context: Context) {
        if (!listening) return
        listener?.let { context.getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        listener = null
        listening = false
    }

    private fun activeProvider(manager: LocationManager): String? {
        val enabled = { p: String -> runCatching { manager.isProviderEnabled(p) }.getOrDefault(false) }
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && enabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            enabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            enabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
    }

    private fun offer(location: Location, context: Context?) {
        val current = latest
        if (current != null && location.time <= current.time) return
        latest = location
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("lat", location.latitude.toString())
            ?.putString("lon", location.longitude.toString())
            ?.putFloat("acc", location.accuracy)
            ?.putLong("time", location.time)
            ?.apply()
    }

    private fun restore(context: Context) {
        if (latest != null) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return
        latest = Location("saved").apply {
            latitude = lat
            longitude = lon
            accuracy = p.getFloat("acc", 100f)
            time = p.getLong("time", 0L)
        }
    }

    /**
     * The location to judge zone rules by, at the moment an app opens: the tracked fix if fresh; otherwise
     * a quick new fix (at most once a minute); otherwise the last known fix up to [USABLE_MS] old.
     */
    suspend fun locationForZones(context: Context): Location? {
        if (!listening) start(context)
        val now = System.currentTimeMillis()
        latest?.takeIf { now - it.time <= FRESH_MS }?.let { return it }
        if (now - lastRefreshAttempt >= REFRESH_COOLDOWN_MS) {
            val fix = try {
                quickFix(context)
            } finally {
                // Only a finished attempt starts the cooldown — a cancelled one must not block the next.
                lastRefreshAttempt = System.currentTimeMillis()
            }
            fix?.let { return it }
        }
        return latest?.takeIf { now - it.time <= USABLE_MS }
    }

    /**
     * A fix for the moment an app is opened: balanced power first (≤ 4 s); if that yields nothing — indoors
     * without Wi-Fi location, or no network location at all — GPS (≤ 6 s), then the providers' last known.
     */
    @SuppressLint("MissingPermission")
    private suspend fun quickFix(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val balanced = activeProvider(manager)
        val gpsOn = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val fix = balanced?.let { requestSingle(context, manager, it, 4_000L) }
            ?: (if (gpsOn && balanced != LocationManager.GPS_PROVIDER) requestSingle(context, manager, LocationManager.GPS_PROVIDER, 6_000L) else null)
            ?: listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            ?: return null
        offer(fix, context)
        return fix
    }

    /** One fresh fix for "save my current place" (GPS allowed here — the user is waiting for it). */
    @SuppressLint("MissingPermission") // checked by hasPermission
    suspend fun fetchCurrent(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        val result = requestSingle(context, manager, provider, 20_000L)
            ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        result?.let { offer(it, context) }
        return result
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingle(context: Context, manager: LocationManager, provider: String, timeoutMs: Long): Location? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    manager.getCurrentLocation(provider, cancel, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
                } else {
                    val once = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }
                    }
                    cont.invokeOnCancellation { manager.removeUpdates(once) }
                    manager.requestLocationUpdates(provider, 0L, 0f, once, Looper.getMainLooper())
                }
            }
        }

    fun zoneContaining(location: Location, zones: List<BlockZone>): BlockZone? = zones.firstOrNull { zone ->
        val result = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, zone.latitude, zone.longitude, result)
        result[0] <= zone.radiusMeters + location.accuracy.coerceAtMost(200f) / 2
    }
}
