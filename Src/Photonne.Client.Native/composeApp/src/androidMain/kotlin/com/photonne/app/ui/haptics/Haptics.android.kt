package com.photonne.app.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Android enruta el feedback por la View anfitriona, que es la que aplica la
 * preferencia del sistema. No se usa `FLAG_IGNORE_VIEW_SETTING` a propósito:
 * si el usuario ha apagado la vibración táctil, aquí también se calla.
 */
private class AndroidHaptics(private val view: View) : PhotonneHaptics {
    override fun perform(event: HapticEvent) {
        view.performHapticFeedback(constantFor(event))
    }

    // Nada que preparar: el motor de Android no tiene el arranque en frío de
    // los UIFeedbackGenerator de iOS.
    override fun prepare() = Unit

    private fun constantFor(event: HapticEvent): Int = when (event) {
        HapticEvent.SelectionStart -> HapticFeedbackConstants.LONG_PRESS
        // SEGMENT_TICK es exactamente el "has cruzado un tope" de los sliders
        // y llegó en API 30; por debajo, CLOCK_TICK es lo más parecido.
        HapticEvent.CellCrossed ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        HapticEvent.SelectionEnd -> HapticFeedbackConstants.CONTEXT_CLICK
    }
}

@Composable
actual fun rememberPhotonneHaptics(): PhotonneHaptics {
    val view = LocalView.current
    return remember(view) { AndroidHaptics(view) }
}
