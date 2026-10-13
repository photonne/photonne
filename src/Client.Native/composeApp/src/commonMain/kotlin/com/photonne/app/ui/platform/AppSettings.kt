package com.photonne.app.ui.platform

import androidx.compose.runtime.Composable

/**
 * Opens this app's page in the system settings, where its permissions are.
 * Once a permission was denied, iOS never shows its prompt again and Android
 * stops showing it after a second denial: that page is the only way back.
 * Desktop has no such page and is a no-op.
 */
@Composable
expect fun rememberAppSettingsOpener(): () -> Unit
