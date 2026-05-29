package com.photonne.app.ui.navigation

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform-level "back" affordance — Android's hardware /
 * gesture back button — and routes it into the shared in-app back action.
 * No-op on iOS and desktop, where there is no equivalent system gesture.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
