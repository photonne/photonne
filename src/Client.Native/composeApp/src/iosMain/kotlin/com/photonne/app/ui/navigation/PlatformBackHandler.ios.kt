package com.photonne.app.ui.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no hardware back button. The system-wide left-edge swipe
    // belongs to UINavigationController, which we don't use here, so
    // there is nothing to intercept at the Compose layer.
}
