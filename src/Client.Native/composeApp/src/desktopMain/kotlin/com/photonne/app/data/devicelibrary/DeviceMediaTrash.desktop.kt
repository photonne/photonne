package com.photonne.app.data.devicelibrary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** No device library on desktop — always reports false. */
@Composable
actual fun rememberDeviceMediaTrasher(
    onResult: (trashed: Boolean) -> Unit
): (uris: List<String>) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember { { _ -> currentOnResult.value(false) } }
}

/** No device library on desktop — always reports false. */
@Composable
actual fun rememberDeviceMediaDeleter(
    onResult: (deleted: Boolean) -> Unit
): (uris: List<String>) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember { { _ -> currentOnResult.value(false) } }
}
