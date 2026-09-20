package com.photonne.app.ui.platform

import androidx.compose.runtime.Composable

/**
 * Mantiene la pantalla encendida mientras [enabled] sea true (punto 27): el
 * pase automático de fotos no genera toques y el sistema apagaba la pantalla
 * a mitad. Android lo hace con `keepScreenOn` en la vista raíz; iOS con el
 * `idleTimerDisabled` de la aplicación; escritorio no lo necesita. Se
 * restaura al salir de composición o al pasar a false.
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)
