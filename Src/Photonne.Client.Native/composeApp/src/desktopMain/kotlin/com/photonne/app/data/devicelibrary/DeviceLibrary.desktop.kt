package com.photonne.app.data.devicelibrary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.photonne.app.data.devicebackup.DeviceMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Desktop has no system media index — the timeline simply shows no
 * device-local side there (the SAF-style folder backup flow keeps
 * working through its own JFileChooser path).
 */
actual class DeviceLibrary {

    actual val isSupported: Boolean = false

    actual val supportsBuckets: Boolean = false

    actual fun accessState(): DeviceLibraryAccess = DeviceLibraryAccess.Unsupported

    actual suspend fun loadAll(scope: DeviceLibraryScope): List<DeviceMedia> = emptyList()

    actual suspend fun listBuckets(): List<DeviceBucket> = emptyList()

    actual fun changes(): Flow<Unit> = emptyFlow()
}

@Composable
actual fun rememberDeviceLibraryAccessRequester(
    onResult: (DeviceLibraryAccess) -> Unit
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember { { currentOnResult.value(DeviceLibraryAccess.Unsupported) } }
}
