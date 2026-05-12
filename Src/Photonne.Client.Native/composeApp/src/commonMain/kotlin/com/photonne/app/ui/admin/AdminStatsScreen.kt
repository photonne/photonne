package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
        state.errorMessage != null && state.data == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        state.data != null -> {
            val data = state.data!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatRow(
                                label = stringResource(Res.string.admin_stats_total_photos),
                                value = data.totalPhotos.toString()
                            )
                            StatRow(
                                label = stringResource(Res.string.admin_stats_total_videos),
                                value = data.totalVideos.toString()
                            )
                            StatRow(
                                label = stringResource(Res.string.admin_stats_total_storage),
                                value = humanBytes(data.totalBytes)
                            )
                        }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(usage.displayName, style = MaterialTheme.typography.titleMedium)
            usage.email?.takeIf { it.isNotBlank() }?.let { email ->
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            StatRow(
                label = stringResource(Res.string.admin_stats_total_photos),
                value = "${usage.photos} · ${humanBytes(usage.photoBytes)}"
            )
            StatRow(
                label = stringResource(Res.string.admin_stats_total_videos),
                value = "${usage.videos} · ${humanBytes(usage.videoBytes)}"
            )
            StatRow(
                label = stringResource(Res.string.admin_stats_total_storage),
                value = humanBytes(usage.photoBytes + usage.videoBytes)
            )
        }
    }
}
