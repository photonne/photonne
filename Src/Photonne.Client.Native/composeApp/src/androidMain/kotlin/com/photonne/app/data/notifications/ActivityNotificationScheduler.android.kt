package com.photonne.app.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * Un PeriodicWorkRequest propio y no el worker del backup: el backup solo se
 * programa con el backup activado (y a menudo exige Wi-Fi y carga), y los
 * avisos de actividad tienen que llegar igual sin él.
 */
class ActivityNotificationSchedulerAndroid(
    private val appContext: Context
) : ActivityNotificationScheduler {

    override val isSupported: Boolean get() = true

    override fun apply(enabled: Boolean) {
        val workManager = WorkManager.getInstance(appContext)
        if (!enabled) {
            workManager.cancelUniqueWork(ActivityNotificationWorker.UNIQUE_WORK_NAME)
            return
        }
        // 15 min es el mínimo de WorkManager; el sistema puede agruparlo.
        val request = PeriodicWorkRequestBuilder<ActivityNotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        // KEEP: reconciliar en cada arranque no debe reiniciar la cuenta atrás.
        workManager.enqueueUniquePeriodicWork(
            ActivityNotificationWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.i("ActivityNotifications", "Scheduled periodic activity check")
    }
}

actual fun createActivityNotificationScheduler(): ActivityNotificationScheduler {
    val context: Context = GlobalContext.get().get()
    return ActivityNotificationSchedulerAndroid(context)
}
