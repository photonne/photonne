package com.photonne.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Forma de cápsula: extremos semicirculares sea cual sea el alto. Antes se
 * escribía de cuatro maneras (`RoundedCornerShape(50)`, `(percent = 50)`,
 * `FloatingNavBarShape`, `(24.dp)` en una píldora de 48). Es la de toda píldora,
 * chip y cápsula del cromo; `CircleShape` se queda para círculos de verdad
 * (avatares, checks, botones redondos).
 */
val PillShape: RoundedCornerShape = RoundedCornerShape(percent = 50)

/**
 * Sombras del cromo flotante, en tres escalones. Cuanto más arriba en la pila
 * visual, más sombra: una píldora efímera no debe competir con la barra de la
 * que depende, y la navegación (lo único siempre presente) queda por encima de
 * todo lo demás. Tres y no dos porque la nav y el visor viven sobre foto a pantalla
 * completa y necesitan despegarse más que las cápsulas de una subpantalla, que
 * al llegar arriba se acoplan y pierden la sombra.
 */
object ChromeElevation {
    /** 2.dp — píldoras pequeñas y efímeras: subir, fecha flotante, scrubber, avisos. */
    val pill: Dp = 2.dp

    /** 4.dp — cápsulas de cabecera (subpantallas, álbum, acciones del timeline). */
    val bar: Dp = 4.dp

    /** 6.dp — navegación flotante, barras de selección y cromo del visor. */
    val nav: Dp = 6.dp
}

/**
 * Alto de las barras de progreso lineales. Había 2, 4 y 6 dp sin criterio.
 */
object ProgressHeight {
    /** 4.dp — barra dentro de una fila o de una tarjeta de lista. */
    val inline: Dp = 4.dp

    /** 6.dp — barra protagonista de una tarjeta de estado (backup, tareas, biblioteca). */
    val hero: Dp = 6.dp
}
