package com.photonne.app.data.devicelibrary

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDeviceMediaTrasher(
    onResult: (trashed: Boolean) -> Unit
): (uris: List<String>) -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        currentOnResult.value(result.resultCode == Activity.RESULT_OK)
    }
    return remember(launcher, context) {
        trasher@{ uris ->
            val parsed = uris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            if (parsed.isEmpty()) {
                currentOnResult.value(false)
                return@trasher
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // System trash (30 days) behind the system's own consent
                // dialog; the launcher result says whether the user agreed.
                val request = MediaStore.createTrashRequest(
                    context.contentResolver, parsed, true
                )
                launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                // Android 10-: no system trash and no system consent dialog.
                // The contract promises the OS confirms (callers fire this
                // straight from a tap, stacking no dialog of their own), so
                // deleting here would PERMANENTLY destroy a photo on a single
                // accidental tap. Refuse instead of destroying.
                currentOnResult.value(false)
            }
        }
    }
}

@Composable
actual fun rememberDeviceMediaDeleter(
    onResult: (deleted: Boolean) -> Unit
): (uris: List<String>) -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        currentOnResult.value(result.resultCode == Activity.RESULT_OK)
    }
    return remember(launcher, context) {
        deleter@{ uris ->
            val parsed = uris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            if (parsed.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // Pre-R has no consent flow — refuse rather than destroy
                // silently (same stance as the trasher).
                currentOnResult.value(false)
                return@deleter
            }
            val request = MediaStore.createDeleteRequest(context.contentResolver, parsed)
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }
}
