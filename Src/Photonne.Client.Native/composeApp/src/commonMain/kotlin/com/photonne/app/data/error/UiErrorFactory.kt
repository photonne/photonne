package com.photonne.app.data.error

import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.version.AppVersionStore

/**
 * Construye [UiError]s rellenando el contexto que vive en stores globales
 * (URL base del servidor, versión cacheada). Los ViewModels lo inyectan y
 * lo usan en sus `runCatching { … }.onFailure { error -> … }` en lugar de
 * stringificar la excepción a mano.
 */
class UiErrorFactory(
    private val urlStore: ServerUrlStore,
    private val versionStore: AppVersionStore,
) {
    fun from(throwable: Throwable, fallback: String): UiError =
        throwable.toUiError(
            fallback = fallback,
            serverBaseUrl = runCatching { urlStore.requireBaseUrl() }.getOrNull(),
            serverVersion = versionStore.serverVersion.value,
        )
}
