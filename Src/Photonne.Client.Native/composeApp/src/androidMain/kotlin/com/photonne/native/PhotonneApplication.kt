package com.photonne.native

import android.app.Application
import com.photonne.native.di.PhotonneAppConfig
import com.photonne.native.di.commonModule
import com.photonne.native.di.platformModule
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
