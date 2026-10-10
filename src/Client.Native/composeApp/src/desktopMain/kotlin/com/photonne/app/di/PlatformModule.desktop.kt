package com.photonne.app.di

import com.photonne.app.data.api.DesktopNetworkMonitor
import com.photonne.app.data.api.NetworkMonitor
import com.photonne.app.data.settings.DesktopSecureSettings
import com.photonne.app.ui.actions.AssetSharing
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import org.koin.dsl.module

actual fun platformModule() = module {
    single<NetworkMonitor> { DesktopNetworkMonitor() }
    // Cifrado en reposo (tokens, "recordarme"), con migración desde el
    // java.util.prefs en claro que se usaba antes.
    single<Settings> { DesktopSecureSettings.createDefault() }
    single<Settings>(SecureSettings) { get<Settings>() }
    single<HttpClientEngine> { CIO.create() }
    single<com.photonne.app.data.api.ConnectionRecycler> {
        object : com.photonne.app.data.api.ConnectionRecycler {
            override fun recycle() {}
        }
    }
    single { AssetSharing() }
    single { com.photonne.app.data.devicebackup.DeviceGallery() }
    single { com.photonne.app.data.devicelibrary.DeviceLibrary() }
}
