package com.onikki.app.ui.blocked

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.onikki.app.data.repository.BlockOverrides
import com.onikki.app.data.repository.BlockReason
import com.onikki.app.ui.theme.OnIkkiTheme

/**
 * Full-screen block, launched by [com.onikki.app.service.AppBlockAccessibilityService] on top
 * of whatever app triggered it — this is a separate Activity (not a NavHost route) because the
 * blocked app, not On ikki, is the foreground app at that moment.
 */
class BlockedScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageName == null) {
            finish()
            return
        }
        val reason = when (intent.getStringExtra(EXTRA_REASON_TYPE)) {
            "WORK_HOURS" -> BlockReason.WorkHours
            "PRAYER" -> BlockReason.PrayerTime(intent.getStringExtra(EXTRA_REASON_DETAIL) ?: "")
            else -> BlockReason.LimitReached
        }
        val appName = resolveAppName(packageName) ?: packageName

        setContent {
            OnIkkiTheme {
                BlockedScreen(
                    appName = appName,
                    reason = reason,
                    onClose = { goHome() },
                    onEmergencyUnlock = {
                        BlockOverrides.grant(packageName)
                        finish()
                    }
                )
            }
        }
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
        private const val EXTRA_REASON_TYPE = "reason_type"
        private const val EXTRA_REASON_DETAIL = "reason_detail"

        fun createIntent(context: Context, packageName: String, reason: BlockReason): Intent {
            val type: String
            val detail: String?
            when (reason) {
                BlockReason.LimitReached -> {
                    type = "LIMIT"
                    detail = null
                }
                BlockReason.WorkHours -> {
                    type = "WORK_HOURS"
                    detail = null
                }
                is BlockReason.PrayerTime -> {
                    type = "PRAYER"
                    detail = reason.prayerName
                }
            }
            return Intent(context, BlockedScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_REASON_TYPE, type)
                putExtra(EXTRA_REASON_DETAIL, detail)
            }
        }
    }
}
