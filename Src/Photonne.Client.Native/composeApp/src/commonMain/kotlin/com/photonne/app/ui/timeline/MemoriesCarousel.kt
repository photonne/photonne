package com.photonne.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_memories_one_year_ago
import com.photonne.app.resources.timeline_memories_section
import com.photonne.app.resources.timeline_memories_years_ago
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

data class MemoryGroup(
    val items: List<TimelineItem>,
    val yearsAgo: Int
) {
    val cover: TimelineItem get() = items.first()
    val count: Int get() = items.size
}

internal fun groupMemoriesByDay(
    items: List<TimelineItem>,
    zone: TimeZone,
    currentYear: Int
): List<MemoryGroup> {
    if (items.isEmpty()) return emptyList()
    return items
        .groupBy {
            val date = it.fileCreatedAt.toLocalDateTime(zone).date
            Triple(date.year, date.monthNumber, date.dayOfMonth)
        }
        .map { (key, group) ->
            val (year, _, _) = key
            val yearsAgo = (currentYear - year).coerceAtLeast(1)
            MemoryGroup(items = group, yearsAgo = yearsAgo)
        }
}

@Composable
fun MemoriesCarousel(
    items: List<TimelineItem>,
    baseUrl: String,
    onGroupClick: (List<TimelineItem>) -> Unit,
    modifier: Modifier = Modifier,
    collapseFraction: Float = 0f
) {
    if (items.isEmpty()) return
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val zone = TimeZone.currentSystemDefault()
    val currentYear = Clock.System.now().toLocalDateTime(zone).date.year
    val groups = groupMemoriesByDay(items, zone, currentYear)
    val oneYear = stringResource(Res.string.timeline_memories_one_year_ago)

    val cardWidth = lerp(110.dp, 40.dp, fraction)
    val cardHeight = lerp(150.dp, 60.dp, fraction)
    val cornerRadius = lerp(12.dp, 6.dp, fraction)
    // Fade the section title and the per-card overlays out faster than the
    // size collapse so the compact carousel doesn't look cluttered while it's
    // still mid-shrink.
    val titleProgress = (fraction * 2f).coerceIn(0f, 1f)
    val titleAlpha = 1f - titleProgress
    val titleHeight = lerp(36.dp, 0.dp, titleProgress)
    val overlayAlpha = (1f - fraction * 1.8f).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleHeight)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (titleAlpha > 0f) {
                Text(
                    text = stringResource(Res.string.timeline_memories_section),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.alpha(titleAlpha)
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Memory covers can share an empty UUID when the server hasn't
            // yet assigned a stable id; prefix with the carousel index so
            // duplicates don't collide and crash the LazyRow.
            itemsIndexed(groups, key = { idx, group -> "memory:$idx:${group.cover.id}" }) { _, group ->
                val label = if (group.yearsAgo == 1) oneYear
                else stringResource(Res.string.timeline_memories_years_ago, group.yearsAgo)
                MemoryCard(
                    group = group,
                    baseUrl = baseUrl,
                    label = label,
                    width = cardWidth,
                    height = cardHeight,
                    cornerRadius = cornerRadius,
                    overlayAlpha = overlayAlpha,
                    onClick = { onGroupClick(group.items) }
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(
    group: MemoryGroup,
    baseUrl: String,
    label: String,
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    overlayAlpha: Float,
    onClick: () -> Unit
) {
    val cover = group.cover
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (cover.hasThumbnails) {
            AsyncImage(
                model = "$baseUrl/api/assets/${cover.id}/thumbnail?size=Small",
                contentDescription = cover.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (group.count > 1 && overlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f * overlayAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group.count.toString(),
                    color = Color.White.copy(alpha = overlayAlpha),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (overlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f * overlayAlpha))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = overlayAlpha),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}
