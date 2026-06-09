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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
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
    single<HttpClientEngine> {
        // A WiFi↔cellular switch leaves OkHttp's keep-alive sockets bound to
        // the now-dead interface. Reusing one surfaces as "unable to resolve
        // host" or an indefinite hang that only an app restart clears. We hold
        // our own ConnectionPool and evict it on every network transition so
        // the next request dials a fresh connection on the live network.
        val pool = ConnectionPool()
        val monitor = get<NetworkMonitor>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            // drop(1): skip the replayed startup tick; only react to real
            // transitions (network gained/lost/capabilities changed).
            monitor.changes.drop(1).collect { pool.evictAll() }
        }
        OkHttp.create {
            config {
                connectionPool(pool)
                // Re-route off a stale pooled connection automatically; the
                // default is already true but we pin it so a future OkHttp
                // bump can't silently disable our recovery path.
                retryOnConnectionFailure(true)
            }
        }
    }
    single { AssetSharing(androidContext()) }
    single { com.photonne.app.data.devicebackup.DeviceGallery(androidContext()) }
}
