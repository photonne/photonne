package com.photonne.app.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ancho máximo de una columna de lectura o de formulario (punto 50). En un
 * teléfono no cambia nada; en tablet o escritorio evita campos y párrafos de
 * borde a borde, que a partir de ~720 dp se leen peor y quedan huérfanos en
 * la esquina.
 */
val ContentMaxWidth: Dp = 720.dp

/**
 * Centra el contenido y lo limita a [maxWidth]. Va DESPUÉS del scroll y del
 * `hazeSource` (que deben cubrir todo el ancho) y ANTES del padding interior.
 */
fun Modifier.contentWidth(maxWidth: Dp = ContentMaxWidth): Modifier =
    this.fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
