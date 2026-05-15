package com.photonne.app.di

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.photonne.app.data.api.AndroidNetworkMonitor
import com.photonne.app.data.api.NetworkMonitor
import com.photonne.app.ui.actions.AssetSharing
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() = module {
    single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
    single<Settings> {
        val context = androidContext()
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            "photonne_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        SharedPreferencesSettings(prefs)
    }
    single<HttpClientEngine> { OkHttp.create() }
    single { AssetSharing(androidContext()) }
    single { com.photonne.app.data.devicebackup.DeviceGallery(androidContext()) }
}
