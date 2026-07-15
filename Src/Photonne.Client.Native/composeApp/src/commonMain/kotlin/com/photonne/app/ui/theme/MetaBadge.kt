package com.photonne.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Small icon+label chip that qualifies a list item — shared, external library,
 * excluded from the timeline. Sits in the metadata row of the list-mode rows in
 * Álbumes and Carpetas; the grid-mode counterpart is [OverlayIconBadge], which
 * drops the label to fit over a thumbnail.
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
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
