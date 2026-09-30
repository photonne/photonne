package com.photonne.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Icon-only badge for grid cards, where a labelled [MetaBadge] wouldn't fit over
 * the thumbnail. The scrim keeps it legible against arbitrary cover art, so it
 * uses the photo-overlay tokens (`scrimMedium` / `onScrim`) rather than surface
 * colours — the badge sits on a photo, not on a surface. Also the video and
 * Live Photo glyph of the photo grid.
 */
@Composable
fun OverlayIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = PhotonneColors.scrimMedium,
                shape = PillShape
            )
            .padding(Spacing.xs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = PhotonneColors.onScrim,
            modifier = Modifier.size(14.dp)
        )
    }
}
