package com.photonne.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado única de la app. Antes cada pantalla elegía su `.dp` a ojo
 * (el margen horizontal convivía en 10/12/14/16/20/24), lo que hacía que dos
 * listas hermanas no alinearan igual. Todo margen/separación nuevo debe salir de
 * aquí; `screenHorizontal` es el margen lateral canónico de una pantalla.
 */
object Spacing {
    /** 2.dp — pelo entre elementos casi pegados (icono+texto de un chip). */
    val xxs: Dp = 2.dp

    /** 4.dp — separación mínima dentro de un mismo grupo. */
    val xs: Dp = 4.dp

    /** 8.dp — separación compacta entre elementos relacionados. */
    val sm: Dp = 8.dp

    /** 12.dp — separación media (entre filas de una tarjeta). */
    val md: Dp = 12.dp

    /** 16.dp — separación estándar; margen lateral por defecto de una pantalla. */
    val lg: Dp = 16.dp

    /** 24.dp — separación amplia entre secciones. */
    val xl: Dp = 24.dp

    /** 32.dp — separación entre bloques mayores / respiración de cabeceras. */
    val xxl: Dp = 32.dp

    /** Margen horizontal canónico del contenido de una pantalla. */
    val screenHorizontal: Dp = lg
}
