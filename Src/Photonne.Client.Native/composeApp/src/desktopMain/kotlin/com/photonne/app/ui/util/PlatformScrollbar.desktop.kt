package com.photonne.app.ui.util

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier
    )
}
