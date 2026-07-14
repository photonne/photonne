package com.photonne.app.ui.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.photonne.app.ui.theme.MemoryCardShape

/**
 * The look of a memory: keepsake corners, cover photo, a gradient that keeps the
 * caption legible on bright covers, and the caption itself.
 *
 * Shared by the timeline strip's story pager and the Recuerdos section on
 * purpose. They drifted before — the section used a plain Card with square
 * corners and a flat scrim, so the keepsake language evaporated the moment you
 * tapped "Ver todo". One face, two callers, no drift.
 *
 * Callers add their own motion and chrome through [imageModifier] and [overlay];
 * this composable stays still.
 */
@Composable
fun MemoryCardFace(
    coverUrl: String?,
    contentDescription: String?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    onCoverLoaded: () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(MemoryCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) onCoverLoaded()
                },
                modifier = Modifier.fillMaxSize().then(imageModifier),
            )
        }
        // Bottom gradient so the white caption keeps contrast on light covers.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.0f),
                            Color.Black.copy(alpha = 0.55f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        overlay()
    }
}
