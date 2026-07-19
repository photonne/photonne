package com.photonne.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.photonne.app.data.models.StorageInfoDto
import com.photonne.app.data.models.StorageLibraryUsageDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.storage_count_size_format
import com.photonne.app.resources.storage_label_photos
import com.photonne.app.resources.storage_label_total
import com.photonne.app.resources.storage_label_videos
import com.photonne.app.resources.storage_legend_free
import com.photonne.app.resources.storage_legend_used
import com.photonne.app.resources.storage_no_quota
import com.photonne.app.resources.storage_remaining_format
import com.photonne.app.resources.storage_section_libraries
import com.photonne.app.resources.storage_section_overview
import com.photonne.app.resources.storage_section_personal
import com.photonne.app.resources.storage_used_percent_format
import com.photonne.app.resources.storage_used_unbounded_format
import com.photonne.app.ui.charts.ChartLegend
import com.photonne.app.ui.charts.DonutChart
import com.photonne.app.ui.charts.DonutSlice
import com.photonne.app.ui.charts.LegendItem
import com.photonne.app.ui.charts.StackedBar
import com.photonne.app.ui.charts.StackedSegment
import com.photonne.app.ui.charts.rememberChartPalette
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountStorageScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AccountStorageViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize()) {
    when {
        state.isLoading && state.info == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error?.userMessage != null && state.info == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error?.userMessage!!, color = MaterialTheme.colorScheme.error)
            }
        state.info != null -> {
            val info = state.info!!
            val percentInt = state.usagePercent?.let { (it * 100f).toInt().coerceIn(0, 100) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + reservedTop, bottom = 16.dp + floatingNavBarReservedHeight()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { SectionHeader(stringResource(Res.string.storage_section_overview)) }
                item { OverviewCard(info, state.usagePercent, percentInt) }

                item { SectionHeader(stringResource(Res.string.storage_section_personal)) }
                item { PersonalBreakdownCard(info) }

                if (info.libraries.isNotEmpty()) {
                    item { SectionHeader(stringResource(Res.string.storage_section_libraries)) }
                    items(info.libraries, key = { it.id }) { lib -> LibraryCard(lib) }
                }
            }
        }
    }
        SubscreenFloatingChrome(
            title = title,
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                isScrollInProgress = { listState.isScrollInProgress },
                scrollToTopMinIndex = 4,
                onScrollToTop = { listState.animateScrollToItem(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun OverviewCard(info: StorageInfoDto, percentFraction: Float?, percentInt: Int?) {
    val palette = rememberChartPalette()
    val usedHuman = humanReadableBytes(info.usedBytes)
    val quota = info.quotaBytes

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
            // Left: a donut showing used vs free for a quota'd account, or
            // photos vs videos when storage is unbounded. The center label
            // mirrors the donut's "story" (percent vs total bytes).
            val slices: List<DonutSlice>
            val centerPrimary: String
            val centerSecondary: String?
            if (quota != null && percentInt != null) {
                val remaining = (quota - info.usedBytes).coerceAtLeast(0L)
                slices = listOf(
                    DonutSlice(info.usedBytes.toFloat().coerceAtLeast(0f), palette.photos),
                    DonutSlice(remaining.toFloat().coerceAtLeast(0f), palette.free)
                )
                centerPrimary = "$percentInt%"
                centerSecondary = humanReadableBytes(quota)
            } else {
                slices = listOf(
                    DonutSlice(info.photoBytes.toFloat().coerceAtLeast(0f), palette.photos),
                    DonutSlice(info.videoBytes.toFloat().coerceAtLeast(0f), palette.videos)
                )
                centerPrimary = usedHuman
                centerSecondary = null
            }
            DonutChart(
                slices = slices,
                modifier = Modifier.size(120.dp),
                strokeWidth = 14.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = centerPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    centerSecondary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (quota != null && percentInt != null) {
                    Text(
                        text = stringResource(
                            Res.string.storage_used_percent_format,
                            usedHuman,
                            humanReadableBytes(quota),
                            percentInt
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val remaining = (quota - info.usedBytes).coerceAtLeast(0L)
                    Text(
                        stringResource(
                            Res.string.storage_remaining_format,
                            humanReadableBytes(remaining)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChartLegend(
                        items = listOf(
                            LegendItem(palette.photos, stringResource(Res.string.storage_legend_used)),
                            LegendItem(palette.free, stringResource(Res.string.storage_legend_free))
                        )
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.storage_used_unbounded_format, usedHuman),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(Res.string.storage_no_quota),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun PersonalBreakdownCard(info: StorageInfoDto) {
    BreakdownCard(
        photos = info.personalPhotos,
        videos = info.personalVideos,
        photoBytes = info.personalPhotoBytes,
        videoBytes = info.personalVideoBytes
    )
}

@Composable
private fun LibraryCard(lib: StorageLibraryUsageDto) {
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
            Text(lib.name, style = MaterialTheme.typography.titleMedium)
            BreakdownChart(lib.photoBytes, lib.videoBytes)
            BreakdownRows(
                photos = lib.photos,
                videos = lib.videos,
                photoBytes = lib.photoBytes,
                videoBytes = lib.videoBytes
            )
        }
    }
}

@Composable
private fun BreakdownCard(photos: Int, videos: Int, photoBytes: Long, videoBytes: Long) {
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
            BreakdownChart(photoBytes, videoBytes)
            BreakdownRows(photos, videos, photoBytes, videoBytes)
        }
    }
}

@Composable
private fun BreakdownChart(photoBytes: Long, videoBytes: Long) {
    val palette = rememberChartPalette()
    StackedBar(
        segments = listOf(
            StackedSegment(photoBytes.toFloat().coerceAtLeast(0f), palette.photos),
            StackedSegment(videoBytes.toFloat().coerceAtLeast(0f), palette.videos)
        ),
        trackColor = MaterialTheme.colorScheme.surface,
        barHeight = 10.dp
    )
}

@Composable
private fun BreakdownRows(photos: Int, videos: Int, photoBytes: Long, videoBytes: Long) {
    StorageRow(
        label = stringResource(Res.string.storage_label_photos),
        value = stringResource(
            Res.string.storage_count_size_format,
            photos,
            humanReadableBytes(photoBytes)
        )
    )
    StorageRow(
        label = stringResource(Res.string.storage_label_videos),
        value = stringResource(
            Res.string.storage_count_size_format,
            videos,
            humanReadableBytes(videoBytes)
        )
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    StorageRow(
        label = stringResource(Res.string.storage_label_total),
        value = humanReadableBytes(photoBytes + videoBytes)
    )
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** 1024-based with two decimals up to TB; mirrors what the web client renders. */
private fun humanReadableBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = ((value * 100).toLong()).toDouble() / 100.0
    return "$formatted ${units[unitIndex]}"
}
