package com.photonne.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sin `UINavigationController` en la raíz no hay gesto de borde que esquivar. */
@Composable
actual fun backGestureEdgeInset(): Dp = 0.dp
