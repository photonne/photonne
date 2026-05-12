package com.photonne.app.ui.theme

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * OutlinedTextField defaults to ~56dp tall; primary action buttons that sit
 * underneath form fields look skinny next to them, and an inline
 * CircularProgressIndicator (which is 40dp by default) overflows the button.
 * Apply this modifier to buttons so they line up with the fields above.
 */
val ActionButtonHeight = 56.dp

fun Modifier.actionButtonHeight(): Modifier = heightIn(min = ActionButtonHeight)

/** Loading indicator sized to sit comfortably inside an [actionButtonHeight] button. */
@Composable
fun ButtonLoadingIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        strokeWidth = 2.5.dp
    )
}
