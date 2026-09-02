package com.photonne.app

import android.app.Application
import com.photonne.app.data.devicebackup.reconcileBackupSchedule
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.di.commonModule
import com.photonne.app.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PhotonneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PhotonneApplication)
            modules(
                commonModule(
                    PhotonneAppConfig(
                        apiBaseUrl = BuildConfig.API_BASE_URL.takeIf { it.isNotBlank() },
                        useFakeMemories = false
                    )
                ),
                platformModule()
            )
        }
        // Arm the background backup on EVERY process start — including the ones
        // WorkManager or a broadcast spawns with no UI. Scheduling used to live
        // only in the backup ViewModel's init, which meant nothing rearmed the
        // periodic work until the user opened the app.
        reconcileBackupSchedule()
    }
}
