package com.photonne.app.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * iOS separa el "impacto" (algo ha pasado) de la "selección" (has movido el
 * cursor un paso), y usar el generador correcto es lo que hace que un barrido
 * se sienta como el selector de una rueda y no como una ráfaga de golpes.
 *
 * Los generadores se recuerdan en vez de crearse por evento: instanciarlos en
 * cada tic pierde el estado interno que los mantiene calientes, y se vuelve al
 * retardo de arranque que [prepare] existe para evitar.
 */
private class IosHaptics(
    private val impact: UIImpactFeedbackGenerator,
    private val soft: UIImpactFeedbackGenerator,
    private val selection: UISelectionFeedbackGenerator
) : PhotonneHaptics {

    override fun perform(event: HapticEvent) {
        when (event) {
            HapticEvent.SelectionStart -> impact.impactOccurred()
            HapticEvent.CellCrossed -> selection.selectionChanged()
            HapticEvent.SelectionEnd -> soft.impactOccurred()
        }
    }

    override fun prepare() {
        impact.prepare()
        soft.prepare()
        selection.prepare()
    }
}

@Composable
actual fun rememberPhotonneHaptics(): PhotonneHaptics {
    val haptics = remember {
        IosHaptics(
            impact = UIImpactFeedbackGenerator(
                UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            ),
            soft = UIImpactFeedbackGenerator(
                UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            ),
            selection = UISelectionFeedbackGenerator()
        )
    }
    // Una primera preparación al montar deja el motor listo para el long-press
    // inicial, que es justo el tic que peor se nota si llega tarde.
    DisposableEffect(haptics) {
        haptics.prepare()
        onDispose { }
    }
    return haptics
}
