package com.onikki.app.domain.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.onikki.app.domain.screentime.UsageStatsProvider
import com.onikki.app.service.AppBlockAccessibilityService

/** Status checks for the three manually-granted permissions from TZ 3.6/section 7 + Onboarding. */
object PermissionChecker {

    fun hasUsageAccess(context: Context): Boolean = UsageStatsProvider(context).hasUsageAccess()

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${AppBlockAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    /** "Boshqa ilovalar ustidan ko'rsatish" — lets a "Juda muhim" note alert open over whatever app is in use. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Android 14 can withhold full-screen alerts (used on the lock screen); earlier versions always allow them. */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
    }

    /** Off the battery-optimisation list: aggressive OEM task killers leave the app (and its services) alone. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
}
