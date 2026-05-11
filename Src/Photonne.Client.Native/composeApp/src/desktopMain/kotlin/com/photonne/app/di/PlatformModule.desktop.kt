package com.photonne.app.di

import com.photonne.app.ui.actions.AssetSharing
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import org.koin.dsl.module
import java.util.prefs.Preferences

actual fun platformModule() = module {
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("com/photonne/app")) }
    single<HttpClientEngine> { CIO.create() }
    single { AssetSharing() }
}
