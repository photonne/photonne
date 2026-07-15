package com.photonne.app.ui.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.album_hero_photos
import com.photonne.app.ui.grid.AssetGrid
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A memory's assets, already loaded. Both surfaces reach the same screen from
 * different places: the feed pays a round-trip for them, the timeline strip
 * already had them in hand — so this carries the photos rather than an id, and
 * needs no ViewModel of its own.
 *
 * [title] and [subtitle] arrive rendered (from the server for the feed, from the
 * strip's own "hace N años" label): they are displayed, never rebuilt.
 */
data class MemoryDetailContext(
    val title: String,
    val subtitle: String?,
    val coverAssetId: String?,
    val items: List<TimelineItem>,
)

/**
 * A memory opened like an album: cover on top, grid below.
 *
 * Before this, tapping a memory dropped you straight into the viewer. That reads
 * fine for five photos of "en este día", but a memory is usually a collection —
 * "Martina a lo largo de los años" spans decades — and the viewer made you page
 * through it blind, with no way to see the whole thing or jump to one photo.
 *
 * Not [com.photonne.app.ui.album.AlbumDetailScreen]: that one is bound to an
 * AlbumSummary it fetches by id, and to actions a memory has no answer for
 * (share, edit, members, cover). The pieces underneath are the same, though.
 */
@Composable
fun MemoryDetailScreen(
    memory: MemoryDetailContext,
    baseUrl: String,
    onItemClick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // Opaque: this draws over the tab that opened it, and a transparent
    // background would let the timeline show through the grid's gaps.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AssetGrid(
            // The server's order is curated (index 0 is the cover), so the grid
            // shows it as-is — grouping by date would throw that away.
            items = memory.items,
            baseUrl = baseUrl,
            onItemClick = onItemClick,
            contentPadding = PaddingValues(bottom = 24.dp),
            header = {
                MemoryHero(
                    memory = memory,
                    baseUrl = baseUrl,
                    onBack = onBack,
                )
            },
        )
    }
}

@Composable
private fun MemoryHero(
    memory: MemoryDetailContext,
    baseUrl: String,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                )
        ) {
            // The card you just tapped becomes the cover: same face, same
            // proportions, so the memory still looks like itself.
            val coverHeight = maxWidth * 0.62f
            MemoryCardFace(
                coverUrl = memory.coverAssetId
                    ?.let { "$baseUrl/api/assets/$it/thumbnail?size=Large" },
                contentDescription = memory.title,
                title = memory.title,
                subtitle = memory.subtitle,
                modifier = Modifier.fillMaxWidth().height(coverHeight),
            ) {
                // No top bar: the back button rides the cover, so the photo keeps
                // the full height. Its own scrim carries it over bright covers —
                // the card's gradient only darkens the bottom.
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(Res.string.action_close),
                        tint = Color.White,
                    )
                }
            }
        }

        Text(
            text = pluralStringResource(
                Res.plurals.album_hero_photos,
                memory.items.size,
                memory.items.size,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )
    }
}
