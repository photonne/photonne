package com.photonne.app.data.devicebackup

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the currently-active device-sync folder so the user
 * doesn't have to re-pick it after every launch.
 *
 * We deliberately don't persist per-file sync verdicts here: those
 * are recomputed from the server's hash index on screen entry. That
 * keeps the local store tiny and means switching servers / accounts
 * doesn't leave us with stale "synced" flags.
 */
class DeviceBackupStateStore(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    fun savedFolder(): DeviceFolderRef? {
        if (!settings.hasKey(KEY_FOLDER)) return null
        val raw = settings.getString(KEY_FOLDER, "")
        if (raw.isEmpty()) return null
        return runCatching { json.decodeFromString<DeviceFolderRef>(raw) }.getOrNull()
    }

    fun saveFolder(folder: DeviceFolderRef) {
        settings.putString(KEY_FOLDER, json.encodeToString(folder))
    }

    fun clearFolder() {
        settings.remove(KEY_FOLDER)
    }

    fun isBackupEnabled(): Boolean = settings.getBoolean(KEY_BACKUP_ENABLED, false)

    fun setBackupEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_BACKUP_ENABLED, enabled)
    }

    // ─── Background sync preferences ─────────────────────────────────────────
    // These drive the platform scheduler (WorkManager / BGTaskScheduler) when
    // the user has opted into automatic backups. Defaults are conservative:
    // off, Wi-Fi-only, charging-only — so enabling auto backup the first time
    // never accidentally eats the user's cellular plan or battery.

    fun isAutoBackupEnabled(): Boolean = settings.getBoolean(KEY_AUTO_BACKUP, false)
    fun setAutoBackupEnabled(enabled: Boolean) =
        settings.putBoolean(KEY_AUTO_BACKUP, enabled)

    fun requireWifi(): Boolean = settings.getBoolean(KEY_REQUIRE_WIFI, true)
    fun setRequireWifi(value: Boolean) = settings.putBoolean(KEY_REQUIRE_WIFI, value)

    fun requireCharging(): Boolean = settings.getBoolean(KEY_REQUIRE_CHARGING, true)
    fun setRequireCharging(value: Boolean) =
        settings.putBoolean(KEY_REQUIRE_CHARGING, value)

    /** Snapshots all background-sync preferences for the scheduler. */
    fun backgroundSyncPreferences(): BackgroundSyncPreferences =
        BackgroundSyncPreferences(
            enabled = isAutoBackupEnabled(),
            requireWifi = requireWifi(),
            requireCharging = requireCharging()
        )

    private companion object {
        const val KEY_FOLDER = "device_backup.folder"
        const val KEY_BACKUP_ENABLED = "device_backup.backup_enabled"
        const val KEY_AUTO_BACKUP = "device_backup.auto_enabled"
        const val KEY_REQUIRE_WIFI = "device_backup.require_wifi"
        const val KEY_REQUIRE_CHARGING = "device_backup.require_charging"
    }
}

/** Immutable snapshot of the user's background-sync configuration. */
data class BackgroundSyncPreferences(
    val enabled: Boolean,
    val requireWifi: Boolean,
    val requireCharging: Boolean
)
