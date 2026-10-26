package com.photonne.app.data.actions

import com.photonne.app.data.api.PhotonneApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the server keeps a trash at all (`TrashSettings.Enabled`, a global
 * setting any signed-in user may read). With it off, the server's "move to
 * trash" deletes for good (AssetsEndpoint), so the client must not promise
 * "Movidas a la papelera · Deshacer": it asks for an explicit "Eliminar
 * definitivamente" instead and offers no undo.
 *
 * Optimistic default (enabled): it's the server's own default, and a failed
 * read must not turn every delete into a scary dialog.
 */
class ServerTrashPolicy(private val api: PhotonneApi) {

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _retentionDays = MutableStateFlow<Int?>(null)

    /**
     * Days an item stays before the nightly cleanup purges it; null when
     * nothing purges it by age (trash off, the cleanup task off, or 0 days).
     * Pessimistic default (null): a wrong "se elimina en 3 días" is worse
     * than saying nothing.
     */
    val retentionDays: StateFlow<Int?> = _retentionDays.asStateFlow()

    /** Re-reads the setting; keeps the last known value when the call fails. */
    suspend fun refresh() {
        runCatching { api.adminGetSetting(KEY).value }
            .onSuccess { value -> _enabled.value = !value.trim().equals("false", ignoreCase = true) }
    }

    /** Re-reads the retention (and whether the cleanup runs at all). */
    suspend fun refreshRetention() {
        runCatching {
            val cleanup = api.adminGetSetting(CLEANUP_KEY).value.trim().equals("true", ignoreCase = true)
            val days = api.adminGetSetting(RETENTION_KEY).value.trim().ifEmpty { "30" }.toIntOrNull() ?: 30
            if (cleanup && _enabled.value && days > 0) days else null
        }.onSuccess { _retentionDays.value = it }
    }

    private companion object {
        const val KEY = "TrashSettings.Enabled"
        const val RETENTION_KEY = "TrashSettings.RetentionDays"
        // Retention only applies when the nightly cleanup runs (TrashCleanupEndpoint).
        const val CLEANUP_KEY = "NightlyTaskSettings.TrashCleanup.Enabled"
    }
}
