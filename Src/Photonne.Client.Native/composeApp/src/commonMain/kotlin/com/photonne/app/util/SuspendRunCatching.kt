package com.photonne.app.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspend work: a [CancellationException] is rethrown instead
 * of being wrapped as a failure. Otherwise a load cancelled because a newer one
 * replaced it would report an error and clear loading flags that now belong to
 * the newer load.
 */
suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
