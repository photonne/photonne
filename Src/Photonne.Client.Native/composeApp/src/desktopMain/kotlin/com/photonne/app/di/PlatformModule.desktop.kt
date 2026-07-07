package com.photonne.app.di

import com.photonne.app.data.api.DesktopNetworkMonitor
import com.photonne.app.data.api.NetworkMonitor
import com.photonne.app.ui.actions.AssetSharing
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import org.koin.dsl.module
import java.util.prefs.Preferences

actual fun platformModule() = module {
    single<NetworkMonitor> { DesktopNetworkMonitor() }
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("com/photonne/app")) }
    single<HttpClientEngine> { CIO.create() }
    single<com.photonne.app.data.api.ConnectionRecycler> {
        object : com.photonne.app.data.api.ConnectionRecycler {
            override fun recycle() {}
        }
    }
    single { AssetSharing() }
    single { com.photonne.app.data.devicebackup.DeviceGallery() }
}
