package com.photonne.app.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.SentShareLink
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.my_links_empty_subtitle
import com.photonne.app.resources.my_links_empty_title
import com.photonne.app.resources.share_action_copy
import com.photonne.app.resources.share_action_edit
import com.photonne.app.resources.share_action_revoke
import com.photonne.app.resources.share_attribute_expiry_format
import com.photonne.app.resources.share_attribute_max_views_format
import com.photonne.app.resources.share_attribute_no_downloads
import com.photonne.app.resources.share_attribute_password
import com.photonne.app.resources.share_attribute_views_format
import com.photonne.app.resources.share_link_fallback_title
import com.photonne.app.resources.share_revoke_confirm_message
import com.photonne.app.resources.share_revoke_confirm_title
import com.photonne.app.ui.theme.EmptyState as SharedEmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Mis enlaces" (More → Mis enlaces): lists every public share link the user has created —
 * for albums *and* for individual assets — and lets them act on the link itself (copy /
 * edit settings / revoke) without first opening whatever it points at. See [SentSharesViewModel].
 *
 * Lives under More rather than Álbumes because a share link isn't an album: it has its own
 * shape, its own actions, and it isn't always about an album at all.
 */
@Composable
fun MyLinksScreen(modifier: Modifier = Modifier) {
    val viewModel: SentSharesViewModel = koinViewModel()
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    var editing by remember { mutableStateOf<SentShareLink?>(null) }
    var revoking by remember { mutableStateOf<SentShareLink?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isLoading && state.links.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.links.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.error?.userMessage != null && state.links.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            state.error?.userMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                state.links.isEmpty() -> SharedEmptyState(
                    icon = Icons.Filled.Share,
                    title = stringResource(Res.string.my_links_empty_title),
                    subtitle = stringResource(Res.string.my_links_empty_subtitle)
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.links, key = { it.token }) { link ->
                        MyLinkRow(
                            link = link,
                            baseUrl = apiBaseUrl,
                            // Fall back to building the URL from the token when the server
                            // doesn't send shareUrl (older builds of /api/share/sent), so
                            // copy never yields an empty string.
                            onCopy = {
                                val url = link.shareUrl.ifBlank {
                                    resolveUrl("/share/${link.token}", apiBaseUrl)
                                }
                                clipboard.setText(AnnotatedString(url))
                            },
                            onEdit = { editing = link },
                            onRevoke = { revoking = link }
                        )
                    }
                }
            }
        }
    }

    editing?.let { link ->
        EditShareDialog(
            link = link.toAlbumShareLink(),
            isSubmitting = state.isMutating,
            errorMessage = state.error?.userMessage,
            onDismiss = {
                editing = null
                viewModel.clearError()
            },
            onConfirm = { expiresAt, password, allowDownload, maxViews ->
                viewModel.editLink(
                    token = link.token,
                    expiresAt = expiresAt,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews
                )
                editing = null
            }
        )
    }

    revoking?.let { link ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(Res.string.share_revoke_confirm_title)) },
            text = { Text(stringResource(Res.string.share_revoke_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revoke(link.token)
                    revoking = null
                }) {
                    Text(
                        stringResource(Res.string.share_action_revoke),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { revoking = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun MyLinkRow(
    link: SentShareLink,
    baseUrl: String,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val cover = link.thumbnailUrl?.let { resolveUrl(it, baseUrl) }
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = link.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.title ?: stringResource(Res.string.share_link_fallback_title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            val passwordAttr = stringResource(Res.string.share_attribute_password)
            val noDownloadsAttr = stringResource(Res.string.share_attribute_no_downloads)
            val viewsAttr = stringResource(Res.string.share_attribute_views_format, link.viewCount)
            val expiryPrefix = stringResource(Res.string.share_attribute_expiry_format, "").trim()
            val maxViewsAttr = stringResource(
                Res.string.share_attribute_max_views_format,
                link.maxViews ?: 0
            )
            val attrs = buildList {
                if (link.hasPassword) add(passwordAttr)
                link.expiresAt?.let { add("$expiryPrefix ${formatDate(it)}") }
                link.maxViews?.let { add(maxViewsAttr) }
                add(viewsAttr)
                if (!link.allowDownload) add(noDownloadsAttr)
            }
            Text(
                text = attrs.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.share_action_copy))
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.share_action_edit))
        }
        IconButton(onClick = onRevoke) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(Res.string.share_action_revoke),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatDate(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private fun resolveUrl(url: String, baseUrl: String): String {
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        return url
    }
    val sep = if (url.startsWith("/")) "" else "/"
    return "$baseUrl$sep$url"
}
