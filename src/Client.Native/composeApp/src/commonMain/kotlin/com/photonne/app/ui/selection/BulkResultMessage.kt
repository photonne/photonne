package com.photonne.app.ui.selection

import com.photonne.app.resources.Res
import com.photonne.app.resources.bulk_failed
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Texto del snackbar tras una acción en bloque: "3 álbumes eliminados" si todo
 * fue bien, "2 de 3 eliminados · 1 falló" si no. Se resuelve fuera de
 * composición porque el resultado llega en un callback.
 */
suspend fun bulkResultMessage(
    outcome: BulkOutcome,
    done: PluralStringResource,
    partial: StringResource,
): String {
    val succeeded = outcome.succeeded.size
    val failed = outcome.failed.size
    if (failed == 0) return getPluralString(done, succeeded, succeeded)
    return getString(partial, succeeded, outcome.total) + " · " +
        getPluralString(Res.plurals.bulk_failed, failed, failed)
}
