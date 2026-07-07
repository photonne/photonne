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
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

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
    // One pool shared by the Ktor API/image client AND the ExoPlayer video
    // data source, so a single eviction reaches every socket. The 30 s
    // keep-alive (default is 5 min) is the second half of the half-open-socket
    // defence: a connection left idle while the phone slept — almost certainly
    // killed by the server/NAT, with no network-change event to trigger
    // evictAll() — has already aged out of the pool on wake, so the next
    // request dials fresh instead of reusing a dead socket.
    single { ConnectionPool(5, 30, TimeUnit.SECONDS) }
    single<HttpClientEngine> {
        // A WiFi↔cellular switch leaves OkHttp's keep-alive sockets bound to
        // the now-dead interface. Reusing one surfaces as "unable to resolve
        // host" or an indefinite hang that only an app restart clears. We evict
        // the shared pool on every network transition so the next request dials
        // a fresh connection on the live network.
        val pool = get<ConnectionPool>()
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
    // ExoPlayer's HTTP data source. Shares the pool above (so network-change
    // eviction and the short idle keep-alive reach video too) and adds finite
    // connect/read timeouts so a half-open socket fails fast and the player can
    // re-dial — instead of a clip that simply never loads until the app is
    // killed and reopened.
    single {
        OkHttpClient.Builder()
            .connectionPool(get<ConnectionPool>())
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    // Purga proactiva de sockets al volver a primer plano (ForegroundRecovery).
    // Reutiliza EL MISMO pool compartido que ya se evicta en cambio de red, así
    // que un keep-alive muerto tras la suspensión del móvil se cierra antes de
    // que el primer request lo reutilice. evictAll() solo cierra conexiones
    // ociosas: una subida de backup en curso no lo es y sobrevive.
    single<com.photonne.app.data.api.ConnectionRecycler> {
        val pool = get<ConnectionPool>()
        object : com.photonne.app.data.api.ConnectionRecycler {
            override fun recycle() = pool.evictAll()
        }
    }
    single { AssetSharing(androidContext()) }
    single { com.photonne.app.data.devicebackup.DeviceGallery(androidContext()) }
}
