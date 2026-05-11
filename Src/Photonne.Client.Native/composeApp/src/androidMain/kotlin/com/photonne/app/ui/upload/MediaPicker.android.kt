package com.photonne.app.ui.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberMediaPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val pendingUris = remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    // PickMultipleVisualMedia caps at the OS-imposed max (typically 100); we
    // never need more in a single batch.
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_SELECTION)
    ) { uris ->
        pendingUris.value = uris
    }

    LaunchedEffect(pendingUris.value) {
        val uris = pendingUris.value
        if (uris.isEmpty()) return@LaunchedEffect
        pendingUris.value = emptyList()
        val resolver = context.contentResolver
        val files = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                runCatching {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val name = queryDisplayName(resolver, uri) ?: "upload"
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    PickedFile(
                        name = name,
                        mimeType = mime,
                        sizeBytes = bytes.size.toLong(),
                        bytes = bytes
                    )
                }.getOrNull()
            }
        }
        onPicked(files)
    }

    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: android.net.Uri): String? {
    return resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}

private const val MAX_SELECTION = 50
