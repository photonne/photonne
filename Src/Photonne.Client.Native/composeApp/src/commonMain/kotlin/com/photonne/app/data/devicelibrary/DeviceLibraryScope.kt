package com.photonne.app.data.devicelibrary

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Which slice of the device library the timeline shows. A VISIBILITY
 * preference only — deliberately independent from the backup-folder
 * list ("what I want safe" and "what I want to see" are different
 * intents: backing up WhatsApp shouldn't flood the timeline, and the
 * timeline must work before any backup is configured).
 *
 * Only meaningful where the platform indexes media into folders
 * ([DeviceLibrary.supportsBuckets]); iOS/desktop ignore it.
 */
sealed interface DeviceLibraryScope {
    /** The whole library — every app's images and videos. */
    data object All : DeviceLibraryScope

    /**
     * Only what camera apps store: the `DCIM` tree. The whole tree, not
     * just `DCIM/Camera`, so OEM/third-party camera dirs (OpenCamera,
     * `100MEDIA`…) stay visible; the trade-off is that OEMs that put
     * screenshots under `DCIM/Screenshots` (Samsung, Xiaomi) keep those
     * visible too.
     */
    data object CameraOnly : DeviceLibraryScope

    /** An explicit set of MediaStore buckets, as picked by the user. */
    data class Buckets(val bucketIds: Set<String>) : DeviceLibraryScope
}

/**
 * Holds the user's [DeviceLibraryScope] and persists it via the
 * platform [Settings] backend, mirroring [com.photonne.app.data.settings.TimelineZoomStore].
 * Per-device on purpose (bucket ids only mean something on THIS phone),
 * so it never belongs in account settings.
 *
 * Defaults to [DeviceLibraryScope.CameraOnly] — a photos timeline
 * should open on photos, not on every app's cache — and the timeline
 * shows a one-time notice so the narrowing is never silent (see
 * [noticeDismissed]).
 */
class DeviceLibraryScopeStore(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _value = MutableStateFlow(load())
    val value: StateFlow<DeviceLibraryScope> = _value.asStateFlow()

    private val _noticeDismissed =
        MutableStateFlow(settings.getBoolean(KEY_NOTICE_DISMISSED, false))

    /** True once the "showing camera only" notice was acknowledged —
     *  by dismissing it or by opening the scope sheet at all. */
    val noticeDismissed: StateFlow<Boolean> = _noticeDismissed.asStateFlow()

    fun update(scope: DeviceLibraryScope) {
        if (_value.value == scope) return
        when (scope) {
            DeviceLibraryScope.All -> {
                settings.putString(KEY_MODE, MODE_ALL)
                settings.remove(KEY_BUCKETS)
            }
            DeviceLibraryScope.CameraOnly -> {
                settings.putString(KEY_MODE, MODE_CAMERA)
                settings.remove(KEY_BUCKETS)
            }
            is DeviceLibraryScope.Buckets -> {
                settings.putString(KEY_MODE, MODE_BUCKETS)
                settings.putString(KEY_BUCKETS, json.encodeToString(scope.bucketIds.toList()))
            }
        }
        _value.value = scope
    }

    fun dismissNotice() {
        if (_noticeDismissed.value) return
        settings.putBoolean(KEY_NOTICE_DISMISSED, true)
        _noticeDismissed.value = true
    }

    private fun load(): DeviceLibraryScope = when (settings.getStringOrNull(KEY_MODE)) {
        MODE_ALL -> DeviceLibraryScope.All
        MODE_BUCKETS -> settings.getStringOrNull(KEY_BUCKETS)
            ?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            }
            ?.let { DeviceLibraryScope.Buckets(it.toSet()) }
            ?: DeviceLibraryScope.CameraOnly
        else -> DeviceLibraryScope.CameraOnly
    }

    private companion object {
        const val KEY_MODE = "device_library.scope_mode"
        const val KEY_BUCKETS = "device_library.scope_buckets"
        const val KEY_NOTICE_DISMISSED = "device_library.scope_notice_dismissed"

        const val MODE_ALL = "all"
        const val MODE_CAMERA = "camera"
        const val MODE_BUCKETS = "buckets"
    }
}
