package com.photonne.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tamaños de icono canónicos. La auditoría encontró 27 tamaños distintos para el
 * mismo tipo de icono (el meta inline aparecía a 12/14/16/18/20 según pantalla).
 * Todo `Icon` debe elegir uno de estos escalones en vez de un `.dp` suelto.
 */
object IconSize {
    /** 12.dp — icono diminuto de metadato dentro de un chip/badge. */
    val xs: Dp = 12.dp

    /** 16.dp — icono inline junto a texto (leading de una fila de lista). */
    val sm: Dp = 16.dp

    /** 20.dp — icono inline destacado / iconos de meta más visibles. */
    val md: Dp = 20.dp

    /** 24.dp — icono de acción estándar (botones de la barra, iconos táctiles). */
    val lg: Dp = 24.dp

    /** 32.dp — icono grande de cabecera / avatar pequeño. */
    val xl: Dp = 32.dp
}
