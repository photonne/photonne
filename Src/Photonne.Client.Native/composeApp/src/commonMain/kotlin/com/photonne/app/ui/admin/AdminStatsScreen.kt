package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.AdminUserUsage
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_stats_per_user
import com.photonne.app.resources.admin_stats_per_user_breakdown
import com.photonne.app.resources.admin_stats_total_photos
import com.photonne.app.resources.admin_stats_total_storage
import com.photonne.app.resources.admin_stats_total_videos
import com.photonne.app.resources.storage_label_photos
import com.photonne.app.resources.storage_label_videos
import com.photonne.app.ui.charts.ChartLegend
import com.photonne.app.ui.charts.DonutChart
import com.photonne.app.ui.charts.DonutSlice
import com.photonne.app.ui.charts.LegendItem
import com.photonne.app.ui.charts.StackedBar
import com.photonne.app.ui.charts.StackedSegment
import com.photonne.app.ui.charts.TopNBars
import com.photonne.app.ui.charts.TopNEntry
import com.photonne.app.ui.charts.rememberChartPalette
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdminStatsScreen(viewModel: AdminStatsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    when {
        state.isLoading && state.data == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error?.userMessage != null && state.data == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error?.userMessage!!, color = MaterialTheme.colorScheme.error)
            }
        state.data != null -> {
            val data = state.data!!
            // The API returns totalBytes but no global photo/video byte split,
            // so we sum it across users — same denominators the per-user view
            // shows, so the donut's photo:video ratio matches what an admin
            // would expect from the breakdown below.
            val totalPhotoBytes = remember(data.users) {
                data.users.sumOf { it.photoBytes }
            }
            val totalVideoBytes = remember(data.users) {
                data.users.sumOf { it.videoBytes }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + floatingNavBarReservedHeight()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    TotalsOverviewCard(
                        totalPhotos = data.totalPhotos,
                        totalVideos = data.totalVideos,
                        totalBytes = data.totalBytes,
                        photoBytes = totalPhotoBytes,
                        videoBytes = totalVideoBytes
                    )
                }
                if (data.users.size >= 2) {
                    item {
                        TopUsersCard(users = data.users)
                    }
                }
                item {
                    Text(
                        stringResource(Res.string.admin_stats_per_user),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                items(data.users, key = { it.userId }) { usage ->
                    UserUsageCard(usage)
                }
                if (data.users.isEmpty()) {
                    item {
                        Text(
                            stringResource(Res.string.admin_stats_per_user_breakdown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsOverviewCard(
    totalPhotos: Int,
    totalVideos: Int,
    totalBytes: Long,
    photoBytes: Long,
    videoBytes: Long
) {
    val palette = rememberChartPalette()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DonutChart(
                slices = listOf(
                    DonutSlice(photoBytes.toFloat().coerceAtLeast(0f), palette.photos),
                    DonutSlice(videoBytes.toFloat().coerceAtLeast(0f), palette.videos)
                ),
                modifier = Modifier.size(120.dp),
                strokeWidth = 14.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = humanBytes(totalBytes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatRow(
                    label = stringResource(Res.string.admin_stats_total_photos),
                    value = totalPhotos.toString()
                )
                StatRow(
                    label = stringResource(Res.string.admin_stats_total_videos),
                    value = totalVideos.toString()
                )
                StatRow(
                    label = stringResource(Res.string.admin_stats_total_storage),
                    value = humanBytes(totalBytes)
                )
                ChartLegend(
                    items = listOf(
                        LegendItem(palette.photos, stringResource(Res.string.storage_label_photos)),
                        LegendItem(palette.videos, stringResource(Res.string.storage_label_videos))
                    )
                )
            }
        }
    }
}

@Composable
private fun TopUsersCard(users: List<AdminUserUsage>) {
    val palette = rememberChartPalette()
    val top = remember(users) {
        users
            .sortedByDescending { it.photoBytes + it.videoBytes }
            .take(10)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(Res.string.admin_stats_per_user),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            TopNBars(
                entries = top.map { usage ->
                    val total = usage.photoBytes + usage.videoBytes
                    TopNEntry(
                        label = usage.displayName,
                        value = total.toFloat().coerceAtLeast(0f),
                        displayValue = humanBytes(total),
                        color = palette.photos
                    )
                }
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun UserUsageCard(usage: AdminUserUsage) {
    val palette = rememberChartPalette()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(usage.displayName, style = MaterialTheme.typography.titleMedium)
            usage.email?.takeIf { it.isNotBlank() }?.let { email ->
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StackedBar(
                segments = listOf(
                    StackedSegment(usage.photoBytes.toFloat().coerceAtLeast(0f), palette.photos),
                    StackedSegment(usage.videoBytes.toFloat().coerceAtLeast(0f), palette.videos)
                ),
                trackColor = MaterialTheme.colorScheme.surface,
                barHeight = 8.dp
            )
            StatRow(
                label = stringResource(Res.string.admin_stats_total_photos),
                value = "${usage.photos} · ${humanBytes(usage.photoBytes)}"
            )
            StatRow(
                label = stringResource(Res.string.admin_stats_total_videos),
                value = "${usage.videos} · ${humanBytes(usage.videoBytes)}"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            StatRow(
                label = stringResource(Res.string.admin_stats_total_storage),
                value = humanBytes(usage.photoBytes + usage.videoBytes)
            )
        }
    }
}
