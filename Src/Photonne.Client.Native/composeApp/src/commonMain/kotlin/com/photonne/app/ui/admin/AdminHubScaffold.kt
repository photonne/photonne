package com.photonne.app.ui.admin

import com.photonne.app.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A single tappable entry in a hub list (Ajustes / Sistema). */
data class AdminHubEntry(
    val key: String,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector
)

/** Shared scaffold used by the Ajustes and Sistema hub screens to render
 *  a vertically scrolling list of large entry rows. Draws its own floating
 *  subscreen chrome so both hubs match the rest of the app. Kept generic so
 *  any hub can grow without adding more layout boilerplate. */
@Composable
fun AdminHubList(
    title: String,
    onBack: () -> Unit,
    entries: List<AdminHubEntry>,
    onClick: (String) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    AdminPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
        page {
            entries.forEach { entry ->
                HubEntryRow(entry = entry, onClick = { onClick(entry.key) })
            }
        }
    }
}

@Composable
private fun HubEntryRow(entry: AdminHubEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                entry.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(contentAlignment = Alignment.CenterEnd) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
