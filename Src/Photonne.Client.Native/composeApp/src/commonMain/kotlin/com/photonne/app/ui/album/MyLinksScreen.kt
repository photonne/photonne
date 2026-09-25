package com.photonne.app.ui.album

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import com.photonne.app.resources.share_action_share_failed
import com.photonne.app.resources.share_action_share_link
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.photonne.app.resources.share_attribute_upload
import com.photonne.app.resources.share_attribute_uploads_format
import com.photonne.app.resources.share_attribute_views_format
import com.photonne.app.resources.share_link_copied
import com.photonne.app.resources.share_link_fallback_title
import com.photonne.app.resources.share_revoke_confirm_message
import com.photonne.app.resources.share_revoke_confirm_title
import com.photonne.app.ui.theme.EmptyState as SharedEmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.photonne.app.ui.theme.ListRowsSkeleton
import com.photonne.app.ui.theme.Spacing

/**
 * "Mis enlaces" (More → Mis enlaces): lists every public share link the user has created —
 * for albums *and* for individual assets — and lets them act on the link itself (copy /
 * edit settings / revoke) without first opening whatever it points at. See [SentSharesViewModel].
 *
 * Lives under More rather than Álbumes because a share link isn't an album: it has its own
 * shape, its own actions, and it isn't always about an album at all.
 */
@Composable
fun MyLinksScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val viewModel: SentSharesViewModel = koinViewModel()
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbarController.current
    val copiedMessage = stringResource(Res.string.share_link_copied)
    val shareFailedMessage = stringResource(Res.string.share_action_share_failed)
    val sharing: com.photonne.app.ui.actions.AssetSharing = koinInject()
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<SentShareLink?>(null) }
    var revoking by remember { mutableStateOf<SentShareLink?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize()) {
    PhotonneRefreshableScreen(
        indicatorTopPadding = reservedTop,
        isRefreshing = state.isLoading && state.links.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.links.isEmpty() ->
                    ListRowsSkeleton(contentPadding = PaddingValues(top = reservedTop))
                state.error != null && state.links.isEmpty() ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = reservedTop)
                            .padding(Spacing.lg)
                    ) {
                        ErrorBanner(error = state.error, onRetry = viewModel::refresh)
                    }
                state.links.isEmpty() -> SharedEmptyState(
                    icon = Icons.Outlined.Share,
                    title = stringResource(Res.string.my_links_empty_title),
                    subtitle = stringResource(Res.string.my_links_empty_subtitle)
                )
                else -> LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    contentPadding = PaddingValues(
                        top = 8.dp + reservedTop,
                        bottom = 8.dp + floatingNavBarReservedHeight()
                    ),
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                ) {
                    items(state.links, key = { it.token }) { link ->
                        MyLinkRow(
                            modifier = Modifier.animateItem(),
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
                                snackbar?.show(copiedMessage)
                            },
                            onShare = {
                                val url = link.shareUrl.ifBlank {
                                    resolveUrl("/share/${link.token}", apiBaseUrl)
                                }
                                scope.launch {
                                    runCatching { sharing.shareText(url) }
                                        .onFailure { error ->
                                            snackbar?.show(
                                                error.message ?: shareFailedMessage
                                            )
                                        }
                                }
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
            onConfirm = { expiresAt, password, allowDownload, maxViews, allowUpload ->
                // Abierto hasta el resultado: un fallo se enseña aquí mismo.
                viewModel.editLink(
                    token = link.token,
                    expiresAt = expiresAt,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews,
                    allowUpload = allowUpload
                ) {
                    editing = null
                }
            }
        )
    }

    revoking?.let { link ->
        // Diálogo estándar: espera la confirmación del servidor y enseña el
        // fallo inline en lugar de cerrarse con la petición aún en el aire.
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.share_revoke_confirm_title),
            message = stringResource(Res.string.share_revoke_confirm_message),
            confirmLabel = stringResource(Res.string.share_action_revoke),
            isDestructive = true,
            isSubmitting = state.isMutating,
            errorMessage = state.error?.userMessage,
            onDismiss = {
                revoking = null
                viewModel.clearError()
            },
            onConfirm = {
                viewModel.revoke(link.token) { revoking = null }
            }
        )
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
private fun MyLinkRow(
    link: SentShareLink,
    baseUrl: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.small)
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
                    imageVector = Icons.Outlined.Share,
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
            val uploadAttr = stringResource(Res.string.share_attribute_upload)
            val uploadsAttr = stringResource(Res.string.share_attribute_uploads_format, link.uploadCount)
            val attrs = buildList {
                if (link.hasPassword) add(passwordAttr)
                link.expiresAt?.let { add("$expiryPrefix ${formatDate(it)}") }
                link.maxViews?.let { add(maxViewsAttr) }
                add(viewsAttr)
                if (!link.allowDownload) add(noDownloadsAttr)
                if (link.allowUpload) {
                    add(uploadAttr)
                    if (link.uploadCount > 0) add(uploadsAttr)
                }
            }
            Text(
                text = attrs.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(Res.string.share_action_copy)
            )
        }
        IconButton(onClick = onShare) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = stringResource(Res.string.share_action_share_link)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.share_action_edit))
        }
        IconButton(onClick = onRevoke) {
            Icon(
                Icons.Outlined.Delete,
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
