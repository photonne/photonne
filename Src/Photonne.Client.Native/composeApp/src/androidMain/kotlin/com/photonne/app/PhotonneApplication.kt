package com.photonne.app

import android.app.Application
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
                commonModule(PhotonneAppConfig(apiBaseUrl = BuildConfig.API_BASE_URL)),
                platformModule()
            )
        }
    }
}
