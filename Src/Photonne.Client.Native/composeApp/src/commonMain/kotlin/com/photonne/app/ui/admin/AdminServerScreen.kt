package com.photonne.app.ui.admin

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.ui.theme.PrimaryActionButton
import com.photonne.app.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_server_checked_at
import com.photonne.app.resources.admin_server_check_error
import com.photonne.app.resources.admin_server_current_version
import com.photonne.app.resources.admin_server_latest_version
import com.photonne.app.resources.admin_server_release_notes
import com.photonne.app.resources.admin_server_check_again
import com.photonne.app.resources.admin_server_release_url
import com.photonne.app.resources.admin_server_up_to_date
import com.photonne.app.resources.admin_server_update_available
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.resources.error_banner_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdminServerScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminServerViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    AdminPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
    when {
        state.isLoading && state.info == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error != null && state.info == null ->
            EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = state.error!!.userMessage,
                actionLabel = stringResource(Res.string.error_banner_retry),
                onAction = { viewModel.load(refresh = true) }
            )
        state.info != null -> {
            val info = state.info!!
            page {
                // A re-check that failed used to look exactly like one that
                // found nothing new: the error only showed with no data at all.
                ErrorBanner(error = state.error, onRetry = { viewModel.load(refresh = true) })
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        AdminKeyValueRow(
                            label = stringResource(Res.string.admin_server_current_version),
                            value = info.currentVersion.ifBlank { "—" }
                        )
                        info.latestVersion?.let { latest ->
                            AdminKeyValueRow(
                                label = stringResource(Res.string.admin_server_latest_version),
                                value = latest
                            )
                        }
                        info.checkedAt?.let { checked ->
                            AdminKeyValueRow(
                                label = stringResource(Res.string.admin_server_checked_at),
                                value = adminDateTime(checked) ?: checked
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
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
                            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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
                            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Text(
                                stringResource(Res.string.admin_server_release_notes),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                PrimaryActionButton(
                    label = stringResource(Res.string.admin_server_check_again),
                    isLoading = state.isLoading,
                    onClick = { viewModel.load(refresh = true) }
                )
            }
        }
    }
    }
}

