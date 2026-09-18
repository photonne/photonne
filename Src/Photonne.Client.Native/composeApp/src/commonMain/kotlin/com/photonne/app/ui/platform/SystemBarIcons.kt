package com.photonne.app.ui.platform

import androidx.compose.runtime.Composable

/**
 * Ajusta el contraste de los iconos de las barras del sistema (estado y
 * navegación) al fondo que la app pinta debajo. Con `enableEdgeToEdge()` la
 * app dibuja tras las barras, pero nadie decía si los iconos debían ser
 * claros u oscuros: con tema claro del sistema y tema oscuro en la app (o el
 * visor a pantalla completa) quedaban iconos oscuros sobre fondo oscuro.
 *
 * [darkBackground] = true significa "el fondo bajo las barras es oscuro",
 * así que los iconos deben ser claros. Cada plataforma lo aplica con su API;
 * escritorio no tiene barras del sistema y es un no-op.
 */
@Composable
expect fun SyncSystemBarIcons(darkBackground: Boolean)
