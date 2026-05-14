package com.photonne.app.di

import com.photonne.app.ui.actions.AssetSharing
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual fun platformModule() = module {
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<HttpClientEngine> { Darwin.create() }
    single { AssetSharing() }
    single { com.photonne.app.data.devicebackup.DeviceGallery() }
}
