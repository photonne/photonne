package com.photonne.app.ui.admin

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.AdminUserUsage
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_stats_coverage_indexed
import com.photonne.app.resources.admin_stats_coverage_never
import com.photonne.app.resources.admin_stats_coverage_offline
import com.photonne.app.resources.admin_stats_coverage_show_list
import com.photonne.app.resources.admin_stats_coverage_summary
import com.photonne.app.resources.admin_stats_coverage_summary_complete
import com.photonne.app.resources.admin_stats_coverage_summary_partial
import com.photonne.app.resources.admin_stats_coverage_title
import com.photonne.app.resources.admin_stats_coverage_total
import com.photonne.app.resources.admin_stats_coverage_truncated
import com.photonne.app.resources.admin_stats_coverage_unindexed
import com.photonne.app.resources.admin_stats_coverage_unsupported
import com.photonne.app.resources.admin_stats_coverage_verified_at
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import com.photonne.app.resources.admin_stats_per_user_list
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.format.humanBytes

@Composable
fun AdminStatsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminStatsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val data = state.data
    // The API returns totalBytes but no global photo/video byte split,
    // so we sum it across users — same denominators the per-user view
    // shows, so the donut's photo:video ratio matches what an admin
    // would expect from the breakdown below.
    val totalPhotoBytes = remember(data?.users) { data?.users?.sumOf { it.photoBytes } ?: 0L }
    val totalVideoBytes = remember(data?.users) { data?.users?.sumOf { it.videoBytes } ?: 0L }

    AdminListScaffold(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        isLoading = state.isLoading,
        isEmpty = data == null,
        error = state.error,
        onRefresh = viewModel::load,
        emptyIcon = Icons.Outlined.BarChart,
        emptyTitle = stringResource(Res.string.admin_stats_per_user_breakdown),
        onDismissError = viewModel::dismissError,
    ) {
        if (data == null) return@AdminListScaffold
        item {
            TotalsOverviewCard(
                totalPhotos = data.totalPhotos,
                totalVideos = data.totalVideos,
                totalBytes = data.totalBytes,
                photoBytes = totalPhotoBytes,
                videoBytes = totalVideoBytes
            )
        }
        state.coverage?.let { coverage ->
            item {
                IndexingCoverageCard(coverage)
            }
        }
        if (data.users.size >= 2) {
            item {
                TopUsersCard(users = data.users)
            }
        }
        item {
            SettingSectionHeader(stringResource(Res.string.admin_stats_per_user_list), divider = false)
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
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DonutChart(
                slices = listOf(
                    DonutSlice(photoBytes.toFloat().coerceAtLeast(0f), palette.photos),
                    DonutSlice(videoBytes.toFloat().coerceAtLeast(0f), palette.videos)
                ),
                modifier = Modifier.size(120.dp),
                strokeWidth = 14.dp,
                description = stringResource(Res.string.storage_label_photos) + " " + humanBytes(photoBytes) +
                    ", " + stringResource(Res.string.storage_label_videos) + " " + humanBytes(videoBytes)
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
                AdminKeyValueRow(
                    label = stringResource(Res.string.admin_stats_total_photos),
                    value = formatCount(totalPhotos)
                )
                AdminKeyValueRow(
                    label = stringResource(Res.string.admin_stats_total_videos),
                    value = formatCount(totalVideos)
                )
                AdminKeyValueRow(
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
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
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

/**
 * The last indexing-coverage verification: files on disk vs. indexed, with the
 * offending paths one tap away when anything is left unindexed. Data comes
 * from the persisted snapshot, so it also says WHEN it was verified.
 */
@Composable
private fun IndexingCoverageCard(coverage: com.photonne.app.data.api.AdminIndexingCoverageResponse) {
    var showPaths by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(
                stringResource(Res.string.admin_stats_coverage_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(Spacing.sm))

            if (!coverage.hasResult) {
                Text(
                    stringResource(Res.string.admin_stats_coverage_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            AdminKeyValueRow(stringResource(Res.string.admin_stats_coverage_total), formatCount(coverage.totalFiles))
            AdminKeyValueRow(stringResource(Res.string.admin_stats_coverage_indexed), formatCount(coverage.indexed))
            AdminKeyValueRow(stringResource(Res.string.admin_stats_coverage_unsupported), formatCount(coverage.unsupported))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.admin_stats_coverage_unindexed),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatCount(coverage.unindexed),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (coverage.unindexed > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                )
            }

            // Bottom line so nobody has to do the math: covered indexables over
            // total indexables. Unsupported files are left out of the ratio —
            // they can never be indexed, so they'd only dilute the verdict.
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.sm))
            val indexable = coverage.indexed + coverage.unindexed
            val complete = coverage.unindexed == 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.admin_stats_coverage_summary),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                val tenths = coverage.indexed * 1000L / indexable.coerceAtLeast(1)
                Text(
                    text = when {
                        complete -> "100 %"
                        tenths % 10 == 0L -> "${tenths / 10} %"
                        else -> "${tenths / 10},${tenths % 10} %"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (complete) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = if (complete)
                    stringResource(Res.string.admin_stats_coverage_summary_complete, indexable)
                else
                    stringResource(Res.string.admin_stats_coverage_summary_partial, coverage.indexed, indexable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (coverage.offlineLibraries > 0) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(Res.string.admin_stats_coverage_offline, coverage.offlineLibraries),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            coverage.verifiedAtUtc?.let { verified ->
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(
                        Res.string.admin_stats_coverage_verified_at,
                        adminDateTime(verified) ?: verified
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (coverage.unindexedPaths.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(Res.string.admin_stats_coverage_show_list, coverage.unindexed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPaths = !showPaths }
                )
                if (showPaths) {
                    Spacer(Modifier.height(Spacing.xs))
                    coverage.unindexedPaths.forEach { path ->
                        Text(
                            path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (coverage.unindexedTruncated) {
                        Text(
                            stringResource(
                                Res.string.admin_stats_coverage_truncated,
                                coverage.unindexedPaths.size
                            ),
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
private fun UserUsageCard(usage: AdminUserUsage) {
    val palette = rememberChartPalette()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
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
                barHeight = 8.dp,
                description = stringResource(Res.string.storage_label_photos) + " " + humanBytes(usage.photoBytes) +
                    ", " + stringResource(Res.string.storage_label_videos) + " " + humanBytes(usage.videoBytes)
            )
            AdminKeyValueRow(
                label = stringResource(Res.string.admin_stats_total_photos),
                value = "${usage.photos} · ${humanBytes(usage.photoBytes)}"
            )
            AdminKeyValueRow(
                label = stringResource(Res.string.admin_stats_total_videos),
                value = "${usage.videos} · ${humanBytes(usage.videoBytes)}"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xxs))
            AdminKeyValueRow(
                label = stringResource(Res.string.admin_stats_total_storage),
                value = humanBytes(usage.photoBytes + usage.videoBytes)
            )
        }
    }
}
