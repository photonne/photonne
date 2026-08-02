package com.photonne.app.ui.grid.dragselect

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.selection_rail_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Carril del margen izquierdo: barriéndolo se marcan filas enteras.
 *
 * Es puramente decorativo — el gesto vive en la rejilla, no aquí — pero sin él
 * la función es indescubrible: nadie va a barrer un margen que no se ve.
 *
 * Empieza en [startInset] y no en el borde. En Android con navegación por
 * gestos ese borde ES el gesto de "atrás" del sistema, y no se puede
 * reclamar: `setSystemGestureExclusionRects` está limitado a 200 dp de altura
 * por borde, así que un carril de altura completa sencillamente no es
 * excluible. Se aparta en vez de pelear.
 *
 * Solo aparece con [visible] (o sea, con selección ya activa). Sin selección
 * el carril está inerte y esa primera columna de fotos se sigue abriendo con
 * un tap normal.
 */
@Composable
internal fun BoxScope.RowSelectRail(
    visible: Boolean,
    state: DragSelectState,
    startInset: Dp,
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    reservedTop: Dp = 0.dp,
    reservedBottom: Dp = 0.dp
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "rowSelectRailAlpha"
    )
    if (alpha <= 0.01f) return

    val active = state.isRailActive
    val background by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        label = "rowSelectRailBackground"
    )
    val gripColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        label = "rowSelectRailGrip"
    )
    val hint = stringResource(Res.string.selection_rail_hint)

    Box(
        // Sin pointerInput a propósito: si el carril capturase el gesto, el
        // arrastre dejaría de llegar a la rejilla, que es quien lo resuelve.
        modifier = modifier
            .align(Alignment.CenterStart)
            .padding(start = startInset, top = reservedTop, bottom = reservedBottom)
            .fillMaxHeight()
            .width(width)
            .alpha(alpha)
            .background(background, RoundedCornerShape(percent = 50))
            .semantics { contentDescription = hint },
        contentAlignment = Alignment.Center
    ) {
        // Cuatro trazos verticales centrados: se lee como un asidero y no
        // compite con las miniaturas que tiene al lado.
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(gripColor, RoundedCornerShape(percent = 50))
                )
            }
        }
    }
}
