package com.photonne.app.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Sin motor táctil: escritorio se queda sin feedback y no pasa nada. */
private object NoHaptics : PhotonneHaptics {
    override fun perform(event: HapticEvent) = Unit
    override fun prepare() = Unit
}

@Composable
actual fun rememberPhotonneHaptics(): PhotonneHaptics = remember { NoHaptics }
