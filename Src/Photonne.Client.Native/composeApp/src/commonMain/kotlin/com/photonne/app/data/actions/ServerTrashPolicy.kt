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

    /** Re-reads the setting; keeps the last known value when the call fails. */
    suspend fun refresh() {
        runCatching { api.adminGetSetting(KEY).value }
            .onSuccess { value -> _enabled.value = !value.trim().equals("false", ignoreCase = true) }
    }

    private companion object {
        const val KEY = "TrashSettings.Enabled"
    }
}
