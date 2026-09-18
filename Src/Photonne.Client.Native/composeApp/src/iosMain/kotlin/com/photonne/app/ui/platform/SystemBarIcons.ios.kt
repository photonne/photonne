package com.photonne.app.ui.platform

import androidx.compose.runtime.Composable

/**
 * iOS gestiona el estilo de la barra de estado vía `UIStatusBarStyle` del
 * view controller anfitrión; cambiarlo desde Compose requiere tocar el
 * `ComposeUIViewController` en iosApp. Pendiente de revisar en dispositivo
 * (punto 48 del roadmap); de momento no-op.
 */
@Composable
actual fun SyncSystemBarIcons(darkBackground: Boolean) = Unit
