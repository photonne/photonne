package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable

/**
 * iOS picker is a stub for v1: bridging PHPickerViewController from
 * Compose Multiplatform needs a UIViewController interop layer that
 * we'll add in a follow-up. The lambda throws [MediaPickerUnavailable]
 * so the screen surfaces a localized "not supported on iOS yet" banner.
 */
@Composable
actual fun rememberMediaPicker(
    @Suppress("UNUSED_PARAMETER") onPicked: (List<PickedFile>) -> Unit
): () -> Unit = {
    throw MediaPickerUnavailable("Photo picking on iOS is not implemented yet")
}
