package com.photonne.app.ui.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    // Táctil: sin barra — el gesto y el overscroll ya sitúan al usuario.
}
