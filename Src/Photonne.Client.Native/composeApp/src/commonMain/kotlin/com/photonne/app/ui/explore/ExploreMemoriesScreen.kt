package com.photonne.app.ui.explore

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_memories_empty
import com.photonne.app.resources.explore_memories_group_count
import com.photonne.app.resources.timeline_memories_one_year_ago
import com.photonne.app.resources.timeline_memories_years_ago
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.timeline.MemoriesViewModel
import com.photonne.app.ui.timeline.MemoryGroup
import com.photonne.app.ui.timeline.groupMemoriesByDay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExploreMemoriesScreen(
    viewModel: MemoriesViewModel,
    baseUrl: String,
    onGroupClick: (List<TimelineItem>) -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        if (state.items.isEmpty() && !state.isLoading && !state.attempted) viewModel.refresh()
    }

    val zone = remember { TimeZone.currentSystemDefault() }
    val currentYear = remember { Clock.System.now().toLocalDateTime(zone).date.year }
    val groups = remember(state.items) { groupMemoriesByDay(state.items, currentYear) }

    PhotonneRefreshableScreen(
        isRefreshing = state.isLoading && state.items.isNotEmpty(),
        onRefresh = viewModel::refresh
    ) {
        when {
            state.isLoading && state.items.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error?.userMessage != null && state.items.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error?.userMessage!!, color = MaterialTheme.colorScheme.error)
                }
            groups.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.explore_memories_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cover ids can repeat across groups when an asset belongs to two
                // anniversaries on the same calendar day, so the index disambiguates.
                items(
                    items = groups,
                    key = { group -> "memory-group:${group.yearsAgo}:${group.cover.id}" }
                ) { group ->
                    MemoryGroupRow(
                        group = group,
                        baseUrl = baseUrl,
                        onClick = { onGroupClick(group.items) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryGroupRow(
    group: MemoryGroup,
    baseUrl: String,
    onClick: () -> Unit
) {
    val label = if (group.yearsAgo == 1)
        stringResource(Res.string.timeline_memories_one_year_ago)
    else
        stringResource(Res.string.timeline_memories_years_ago, group.yearsAgo)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(0.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val cover = group.cover
                if (cover.hasThumbnails) {
                    AsyncImage(
                        model = "$baseUrl/api/assets/${cover.id}/thumbnail?size=Large",
                        contentDescription = cover.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                if (group.count > 1) {
                    Text(
                        text = stringResource(Res.string.explore_memories_group_count, group.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
            if (group.count > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = group.count.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
