package com.photonne.app.data.devicesync

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
class DeviceSyncStateStore(private val settings: Settings) {

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

    private companion object {
        const val KEY_FOLDER = "device_sync.folder"
    }
}
