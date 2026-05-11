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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.photonne.app.resources.admin_server_checked_at
import com.photonne.app.resources.admin_server_check_error
import com.photonne.app.resources.admin_server_current_version
import com.photonne.app.resources.admin_server_latest_version
import com.photonne.app.resources.admin_server_release_notes
import com.photonne.app.resources.admin_server_release_url
import com.photonne.app.resources.admin_server_up_to_date
import com.photonne.app.resources.admin_server_update_available
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdminServerScreen(viewModel: AdminServerViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    when {
        state.isLoading && state.info == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.errorMessage != null && state.info == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        state.info != null -> {
            val info = state.info!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        InfoRow(
                            label = stringResource(Res.string.admin_server_current_version),
                            value = info.currentVersion.ifBlank { "—" }
                        )
                        info.latestVersion?.let { latest ->
                            InfoRow(
                                label = stringResource(Res.string.admin_server_latest_version),
                                value = latest
                            )
                        }
                        info.checkedAt?.let { checked ->
                            InfoRow(
                                label = stringResource(Res.string.admin_server_checked_at),
                                value = isoDateOnly(checked) ?: checked
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                if (info.hasUpdate) Res.string.admin_server_update_available
                                else Res.string.admin_server_up_to_date
                            ),
                            color = if (info.hasUpdate) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        info.checkError?.takeIf { it.isNotBlank() }?.let { err ->
                            Text(
                                stringResource(Res.string.admin_server_check_error, err),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                info.latestReleaseUrl?.takeIf { it.isNotBlank() }?.let { url ->
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
                            Text(
                                stringResource(Res.string.admin_server_release_url),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(url, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                info.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
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
                            Text(
                                stringResource(Res.string.admin_server_release_notes),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
