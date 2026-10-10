package com.photonne.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) UIApplication.sharedApplication.setIdleTimerDisabled(true)
        onDispose { if (enabled) UIApplication.sharedApplication.setIdleTimerDisabled(false) }
    }
}
