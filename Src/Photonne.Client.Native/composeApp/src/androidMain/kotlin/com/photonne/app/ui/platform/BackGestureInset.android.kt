package com.photonne.app.ui.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `systemGestures` es el inset que el sistema declara para sus propios gestos
 * de borde. Con navegación por botones vale 0 y el carril puede pegarse al
 * borde; con navegación por gestos ronda los 20-24 dp.
 *
 * El mínimo de 16 dp cubre los fabricantes que reportan 0 aunque la navegación
 * por gestos esté activa: perder 16 dp de carril es barato comparado con que
 * cada barrido dispare un "atrás".
 */
@Composable
actual fun backGestureEdgeInset(): Dp {
    val layoutDirection = LocalLayoutDirection.current
    val reported = WindowInsets.systemGestures.asPaddingValues()
        .calculateLeftPadding(layoutDirection)
    return if (reported <= 0.dp) 0.dp else maxOf(reported, 16.dp)
}
