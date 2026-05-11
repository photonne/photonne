package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_trash_action_cleanup
import com.photonne.app.resources.admin_trash_expired
import com.photonne.app.resources.admin_trash_over_quota_bytes
import com.photonne.app.resources.admin_trash_over_quota_users
import com.photonne.app.resources.admin_trash_quota_mb
import com.photonne.app.resources.admin_trash_retention_days
import com.photonne.app.resources.admin_trash_total_bytes
import com.photonne.app.resources.admin_trash_total_items
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdminTrashScreen(viewModel: AdminTrashViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    when {
        state.isLoading && state.stats == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.errorMessage != null && state.stats == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        state.stats != null -> {
            val stats = state.stats!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.statusMessage?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.primary)
                }
                state.errorMessage?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }

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
                        TrashRow(
                            stringResource(Res.string.admin_trash_total_items),
                            stats.totalItems.toString()
                        )
                        TrashRow(
                            stringResource(Res.string.admin_trash_total_bytes),
                            humanBytes(stats.totalBytes)
                        )
                        TrashRow(
                            stringResource(Res.string.admin_trash_expired),
                            stats.expiredItems.toString()
                        )
                        TrashRow(
                            stringResource(Res.string.admin_trash_retention_days),
                            stats.retentionDays.toString()
                        )
                        TrashRow(
                            stringResource(Res.string.admin_trash_quota_mb),
                            stats.maxQuotaMb.toString()
                        )
                        TrashRow(
                            stringResource(Res.string.admin_trash_over_quota_users),
                            stats.overQuotaUsers.toString()
                        )
                        TrashRow(
                            stringResource(Res.string.admin_trash_over_quota_bytes),
                            humanBytes(stats.overQuotaBytes)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isCleaning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(12.dp))
                    }
                    Button(
                        onClick = viewModel::cleanupExpired,
                        enabled = !state.isCleaning && stats.expiredItems > 0
                    ) {
                        Text(stringResource(Res.string.admin_trash_action_cleanup))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TrashRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
