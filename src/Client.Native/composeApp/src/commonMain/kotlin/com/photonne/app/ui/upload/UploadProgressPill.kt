package com.photonne.app.ui.upload

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.upload_pill_pending
import com.photonne.app.ui.theme.ChromeElevation
import com.photonne.app.ui.theme.PillShape
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Floating "Subiendo N archivos" pill while the manual upload queue works and
 * the user is elsewhere: the queue keeps running after leaving Subir, and
 * without this nothing said so. Tapping it goes back to Subir.
 */
@Composable
fun UploadProgressPill(
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingCount <= 0) return
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = ChromeElevation.bar
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
            Text(
                pluralStringResource(Res.plurals.upload_pill_pending, pendingCount, pendingCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Spacing.md)
            )
        }
    }
}
