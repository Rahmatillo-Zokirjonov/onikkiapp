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
 * Keeps a recent phone location for place-based blocks. Started by the accessibility service (which is
 * alive whenever blocking works at all); network-based fixes every minute are enough to tell "at school"
 * from "at home" without draining the battery like GPS would.
 */
object ZoneLocationTracker {
    private const val MIN_INTERVAL_MS = 60_000L
    private const val MIN_DISTANCE_M = 50f
    /** An older fix can't be trusted to say where the phone is now. */
    private const val MAX_FIX_AGE_MS = 30 * 60_000L

    @Volatile private var latest: Location? = null
    private var listening = false

    private val listener = LocationListener { location -> latest = location }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission
    fun start(context: Context) {
        if (listening || !hasPermission(context)) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val enabled = { provider: String -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .forEach { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let(::offer) }
        // Android 12+: the fused provider blends Wi-Fi, cell and GPS at "balanced" power — it notices leaving
        // a zone reliably without keeping GPS on. Older phones: network fixes plus whatever GPS others request.
        val active = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && enabled(LocationManager.FUSED_PROVIDER)) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.NETWORK_PROVIDER.takeIf(enabled)
        }
        listOfNotNull(active, LocationManager.PASSIVE_PROVIDER).forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(provider, MIN_INTERVAL_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper())
            }
        }
        listening = true
    }

    fun stop(context: Context) {
        if (!listening) return
        context.getSystemService(LocationManager::class.java)?.removeUpdates(listener)
        listening = false
    }

    private fun offer(location: Location) {
        val current = latest
        if (current == null || location.time > current.time) latest = location
    }

    /** Latest fix if it's fresh enough, else null (→ zone rules simply don't apply). */
    fun currentLocation(context: Context): Location? {
        if (!listening) start(context)
        return latest?.takeIf { System.currentTimeMillis() - it.time <= MAX_FIX_AGE_MS }
    }

    /** One fresh fix for "save my current place" (GPS allowed here — the user is waiting for it). */
    @SuppressLint("MissingPermission") // checked by hasPermission
    suspend fun fetchCurrent(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        val fresh = withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    manager.getCurrentLocation(provider, cancel, context.mainExecutor) { cont.resume(it) }
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
        val result = fresh ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        result?.let(::offer)
        return result
    }

    fun zoneContaining(location: Location, zones: List<BlockZone>): BlockZone? = zones.firstOrNull { zone ->
        val result = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, zone.latitude, zone.longitude, result)
        result[0] <= zone.radiusMeters + location.accuracy.coerceAtMost(200f) / 2
    }
}
