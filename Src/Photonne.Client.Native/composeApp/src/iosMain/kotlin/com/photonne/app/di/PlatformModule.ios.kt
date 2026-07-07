package com.photonne.app.di

import com.photonne.app.data.api.IosNetworkMonitor
import com.photonne.app.data.api.NetworkMonitor
import com.photonne.app.ui.actions.AssetSharing
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual fun platformModule() = module {
    single<NetworkMonitor> { IosNetworkMonitor() }
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<HttpClientEngine> { Darwin.create() }
    // No-op: el engine Darwin (URLSession) no expone purga de pool sin recrear
    // el HttpClient (singleton compartido). iOS recupera con el re-probe de
    // ForegroundRecovery + el socket timeout de 30 s del HttpTimeout.
    single<com.photonne.app.data.api.ConnectionRecycler> {
        object : com.photonne.app.data.api.ConnectionRecycler {
            override fun recycle() {}
        }
    }
    single { AssetSharing() }
    single { com.photonne.app.data.devicebackup.DeviceGallery() }
}
