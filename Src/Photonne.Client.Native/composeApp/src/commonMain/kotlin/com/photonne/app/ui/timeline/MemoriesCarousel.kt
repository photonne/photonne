package com.photonne.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val zone = TimeZone.currentSystemDefault()
    val currentYear = Clock.System.now().toLocalDateTime(zone).date.year
    val groups = groupMemoriesByDay(items, zone, currentYear)
    val oneYear = stringResource(Res.string.timeline_memories_one_year_ago)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.timeline_memories_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(groups, key = { it.cover.id }) { group ->
                val label = if (group.yearsAgo == 1) oneYear
                else stringResource(Res.string.timeline_memories_years_ago, group.yearsAgo)
                MemoryCard(
                    group = group,
                    baseUrl = baseUrl,
                    label = label,
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
    onClick: () -> Unit
) {
    val cover = group.cover
    Box(
        modifier = Modifier
            .size(width = 110.dp, height = 150.dp)
            .clip(RoundedCornerShape(12.dp))
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
        if (group.count > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group.count.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}
