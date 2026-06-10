package com.photonne.app.data.devicebackup

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
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
        clearCachedMedia()
    }

    // ─── Media metadata cache ────────────────────────────────────────────────
    // The last folder scan, cached so the timeline can show device-only photos
    // instantly on the next launch instead of waiting for a full SAF/PhotoKit
    // re-enumeration. Only the [DeviceMedia] metadata is stored (never sync
    // verdicts — those are still recomputed against the server), and it's
    // tagged with the folder URI so a stale scan from a different folder is
    // never served.

    fun cachedMedia(folderUri: String): List<DeviceMedia> {
        val raw = settings.getStringOrNull(KEY_MEDIA_CACHE) ?: return emptyList()
        val cached = runCatching { json.decodeFromString<CachedMedia>(raw) }.getOrNull()
            ?: return emptyList()
        return if (cached.folderUri == folderUri) cached.media else emptyList()
    }

    fun saveCachedMedia(folderUri: String, media: List<DeviceMedia>) {
        settings.putString(KEY_MEDIA_CACHE, json.encodeToString(CachedMedia(folderUri, media)))
    }

    fun clearCachedMedia() {
        settings.remove(KEY_MEDIA_CACHE)
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

    // Turbo widens the upload fan-out (more files at once) for users on fast
    // Wi-Fi draining a big backlog. Off by default — the conservative caps are
    // gentler on battery and the mobile uplink. Applies to manual and
    // background passes alike, so it lives alongside the other prefs.
    fun isTurboEnabled(): Boolean = settings.getBoolean(KEY_TURBO, false)
    fun setTurboEnabled(value: Boolean) = settings.putBoolean(KEY_TURBO, value)

    /** Snapshots all background-sync preferences for the scheduler. */
    fun backgroundSyncPreferences(): BackgroundSyncPreferences =
        BackgroundSyncPreferences(
            enabled = isAutoBackupEnabled(),
            requireWifi = requireWifi(),
            requireCharging = requireCharging(),
            turbo = isTurboEnabled()
        )

    // ─── Last completed pass ─────────────────────────────────────────────────
    // Outcome of the most recent backup pass (manual or background), so the
    // status card can answer "when did this last actually run, and how did it
    // go?" without the user digging through logs.

    fun lastRun(): LastBackupRun? {
        val raw = settings.getStringOrNull(KEY_LAST_RUN) ?: return null
        return runCatching { json.decodeFromString<LastBackupRun>(raw) }.getOrNull()
    }

    fun recordLastRun(run: LastBackupRun) {
        settings.putString(KEY_LAST_RUN, json.encodeToString(run))
    }

    private companion object {
        const val KEY_FOLDER = "device_backup.folder"
        const val KEY_MEDIA_CACHE = "device_backup.media_cache"
        const val KEY_BACKUP_ENABLED = "device_backup.backup_enabled"
        const val KEY_AUTO_BACKUP = "device_backup.auto_enabled"
        const val KEY_REQUIRE_WIFI = "device_backup.require_wifi"
        const val KEY_REQUIRE_CHARGING = "device_backup.require_charging"
        const val KEY_TURBO = "device_backup.turbo"
        const val KEY_LAST_RUN = "device_backup.last_run"
    }
}

/** Outcome of the most recent completed backup pass, manual or background. */
@Serializable
data class LastBackupRun(
    val finishedAtMillis: Long,
    val uploaded: Int,
    val skipped: Int,
    val failed: Int,
    /** True when the pass came from WorkManager/BGTaskScheduler. */
    val background: Boolean
)

/** Persisted device-scan cache: the media list tagged with the folder it
 *  came from, so a scan from a different folder is never served stale. */
@Serializable
private data class CachedMedia(
    val folderUri: String,
    val media: List<DeviceMedia>
)

/** Immutable snapshot of the user's background-sync configuration. */
data class BackgroundSyncPreferences(
    val enabled: Boolean,
    val requireWifi: Boolean,
    val requireCharging: Boolean,
    /** Widen the upload fan-out for faster bulk backups. Independent of
     *  [enabled]: it tunes both manual and background passes. */
    val turbo: Boolean = false
)
