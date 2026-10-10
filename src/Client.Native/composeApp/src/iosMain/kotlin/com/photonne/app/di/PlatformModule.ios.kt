@file:OptIn(
    ExperimentalSettingsImplementation::class,
    ExperimentalSettingsApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.photonne.app.di

import com.photonne.app.data.api.IosNetworkMonitor
import com.photonne.app.data.api.NetworkMonitor
import com.photonne.app.ui.actions.AssetSharing
import com.photonne.app.data.auth.SecretSettingKeys
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSUserDefaults
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService

actual fun platformModule() = module {
    single<NetworkMonitor> { IosNetworkMonitor() }
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    // NSUserDefaults is a plain plist included in device backups, so secrets
    // live in the Keychain instead. AfterFirstUnlock (not the WhenUnlocked
    // default) because the background backup task needs the session while the
    // phone is locked; ThisDeviceOnly keeps the items out of backups.
    single<Settings>(SecureSettings) {
        val keychain = KeychainSettings(
            kSecAttrService to CFBridgingRetain("com.photonne.app"),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        prepareKeychain(defaults = get(), keychain = keychain)
        keychain
    }
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
    single { com.photonne.app.data.devicelibrary.DeviceLibrary() }
}

private const val KEY_KEYCHAIN_READY = "photonne.keychain.ready"

/**
 * Readies the Keychain-backed secure store.
 *
 * Keychain items outlive an uninstall while NSUserDefaults does not, so a
 * missing marker in the defaults means a fresh install: leftovers from a
 * previous install (tokens of a session the user threw away) are wiped first.
 * Then any secret an older version wrote to the defaults is moved over.
 */
private fun prepareKeychain(defaults: Settings, keychain: Settings) {
    if (!defaults.getBoolean(KEY_KEYCHAIN_READY, false)) {
        (SecretSettingKeys.strings + SecretSettingKeys.booleans).forEach(keychain::remove)
        migrateSecretsToKeychain(from = defaults, to = keychain)
        defaults.putBoolean(KEY_KEYCHAIN_READY, true)
    }
}

/**
 * One-shot move of secrets written by earlier versions to NSUserDefaults: the
 * values are copied into the Keychain (unless it already has them) and always
 * removed from the plist, so no plaintext copy survives the upgrade.
 */
private fun migrateSecretsToKeychain(from: Settings, to: Settings) {
    for (key in SecretSettingKeys.strings) {
        if (!from.hasKey(key)) continue
        val value = from.getString(key, "")
        if (!to.hasKey(key) && value.isNotEmpty()) to.putString(key, value)
        from.remove(key)
    }
    for (key in SecretSettingKeys.booleans) {
        if (!from.hasKey(key)) continue
        if (!to.hasKey(key)) to.putBoolean(key, from.getBoolean(key, false))
        from.remove(key)
    }
}
