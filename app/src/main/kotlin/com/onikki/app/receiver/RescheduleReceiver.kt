package com.onikki.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onikki.app.domain.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarms don't survive a reboot, and exact times go wrong after a clock/timezone change or when the
 * exact-alarm permission flips — re-plan everything on each of those system broadcasts.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler(context).resync()
            } finally {
                pending.finish()
            }
        }
    }
}
