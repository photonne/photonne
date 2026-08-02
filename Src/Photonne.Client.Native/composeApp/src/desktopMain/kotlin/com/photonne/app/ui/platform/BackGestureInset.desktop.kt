package com.photonne.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Escritorio no tiene gestos de borde del sistema. */
@Composable
actual fun backGestureEdgeInset(): Dp = 0.dp
