package com.photonne.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Small icon badge that qualifies a list item — shared, external library,
 * excluded from the timeline. Sits in the metadata row of the list-mode rows in
 * Álbumes y Carpetas; the grid-mode counterpart is [OverlayIconBadge], which
 * needs its own scrim to stay legible over a thumbnail.
 *
 * The label is spoken, not drawn: three labelled chips overflowed the metadata
 * row on a phone, and dropping the text also makes a row read the same in both
 * view modes. [label] must still describe the badge — it is the only thing a
 * screen reader gets.
 *
 * Named `MetaBadge` rather than `Badge` on purpose: `androidx.compose.material3.Badge`
 * is a different thing (a notification count dot) and is imported elsewhere in
 * the app, so the short name would resolve ambiguously.
 */
@Composable
fun MetaBadge(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50)
            )
            .padding(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp)
        )
    }
}
