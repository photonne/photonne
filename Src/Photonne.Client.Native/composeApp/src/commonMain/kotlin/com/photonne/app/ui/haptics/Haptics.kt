package com.photonne.app.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Feedback háptico. La app no tenía ninguno: ni al entrar en selección, ni al
 * cruzar celdas arrastrando, ni al soltar. Sin él, el arrastre en banda se
 * siente roto — no hay forma de saber que una celda ha entrado sin mirarla, y
 * el dedo tapa justo lo que estás seleccionando.
 *
 * No se usa `LocalHapticFeedback` de Compose: solo expone `LongPress` y
 * `TextHandleMove`, sin un tic ligero para el cruce de celda, y su traducción
 * en iOS y escritorio es opaca.
 */
enum class HapticEvent {
    /** Se entra en modo selección: el golpe fuerte del long-press. */
    SelectionStart,

    /** La banda cubre una celda más. Tic corto y seco. */
    CellCrossed,

    /** Se levanta el dedo con la selección hecha. */
    SelectionEnd
}

@Stable
interface PhotonneHaptics {
    fun perform(event: HapticEvent)

    /**
     * Aviso de que va a haber tics inmediatamente. En iOS los generadores
     * tardan ~200 ms en despertar del todo, así que sin esta llamada al
     * empezar el gesto el primer tic llega tarde y se siente desacompasado.
     */
    fun prepare()
}

@Composable
expect fun rememberPhotonneHaptics(): PhotonneHaptics

/**
 * Estrangula los tics de cruce de celda.
 *
 * Un arrastre rápido — y más aún con el auto-scroll a tope — cruza celdas más
 * deprisa de lo que el motor de vibración puede resolver, y el resultado es un
 * zumbido continuo en vez de una serie de tics. Esto los espacia lo justo para
 * que se sigan contando de uno en uno.
 */
@Stable
class HapticThrottle(
    private val haptics: PhotonneHaptics,
    private val minIntervalMillis: Long = 25L
) {
    private var lastMillis = 0L

    fun tick(event: HapticEvent, nowMillis: Long, minIntervalMillis: Long = this.minIntervalMillis) {
        if (lastMillis != 0L && nowMillis - lastMillis < minIntervalMillis) return
        lastMillis = nowMillis
        haptics.perform(event)
    }

    fun reset() {
        lastMillis = 0L
    }
}
