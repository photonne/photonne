package com.photonne.app.data.devicebackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.koin.core.context.GlobalContext

/**
 * Re-applies the user's background-sync preferences to WorkManager. Called
 * from `PhotonneApplication.onCreate` (every process start, UI or not) and
 * from [BackupScheduleReceiver], so the periodic backup no longer depends on
 * the user opening the app: after a reboot, an app update or a force-stop,
 * the schedule comes back as soon as the process does.
 *
 * Idempotent — the scheduler reconciles a unique work name, so calling this
 * on every wake never stacks duplicate jobs.
 */
fun reconcileBackupSchedule() {
    val koin = runCatching { GlobalContext.get() }.getOrNull()
    if (koin == null) {
        Log.w("BackupSchedule", "Koin not started; cannot reconcile backup schedule")
        return
    }
    val store: DeviceBackupStateStore = koin.get()
    val scheduler: BackgroundSyncScheduler = koin.get()
    scheduler.apply(store.backgroundSyncPreferences())
}

/**
 * Wakes the process on boot / app update just to run [reconcileBackupSchedule].
 * WorkManager's own RescheduleReceiver usually survives reboots, but it is
 * belt-and-braces against the cases where its database got wiped or an OEM
 * battery manager dropped the jobs — the user's stored preferences are the
 * source of truth, not WorkManager's internal state.
 */
class BackupScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i("BackupSchedule", "Received ${intent.action}; reconciling backup schedule")
                reconcileBackupSchedule()
            }
        }
    }
}
