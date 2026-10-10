package com.photonne.app.ui.selection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Resultado de una acción en bloque repetida elemento a elemento. */
data class BulkOutcome(
    val succeeded: List<String>,
    val failed: List<String>,
) {
    val total: Int get() = succeeded.size + failed.size
}

/**
 * Repite una llamada de un solo elemento sobre [ids] con [concurrency]
 * peticiones a la vez (como el favorito en bloque): el servidor no tiene
 * endpoints masivos para álbumes ni carpetas. Un fallo no corta el lote; se
 * cuenta y se sigue. Una cancelación sí se propaga.
 */
suspend fun runBulk(
    ids: List<String>,
    concurrency: Int = 4,
    action: suspend (String) -> Unit,
): BulkOutcome {
    if (ids.isEmpty()) return BulkOutcome(emptyList(), emptyList())
    val gate = Semaphore(concurrency.coerceAtLeast(1))
    val results = coroutineScope {
        ids.map { id ->
            async {
                gate.withPermit {
                    try {
                        action(id)
                        id to true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        id to false
                    }
                }
            }
        }.awaitAll()
    }
    return BulkOutcome(
        succeeded = results.filter { it.second }.map { it.first },
        failed = results.filterNot { it.second }.map { it.first }
    )
}
