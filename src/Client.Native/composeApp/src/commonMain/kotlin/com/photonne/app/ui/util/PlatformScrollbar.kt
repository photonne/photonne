package com.photonne.app.ui.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Barra de scroll clásica para las listas SIN scrubber propio (Más, listas de
 * álbumes/carpetas, notificaciones). Solo escritorio pinta algo — una
 * VerticalScrollbar de Compose Desktop pegada al borde; en Android/iOS es un
 * no-op, el overscroll y el propio dedo ya cuentan la posición.
 *
 * Colócala como hermana de la lista dentro de un Box, alineada a CenterEnd y
 * con fillMaxHeight.
 */
@Composable
expect fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier)
