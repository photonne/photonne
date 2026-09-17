package com.photonne.app.ui.admin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.SharedTrashItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_shared_trash_action_purge
import com.photonne.app.resources.admin_shared_trash_action_restore
import com.photonne.app.resources.admin_shared_trash_deleted_by_format
import com.photonne.app.resources.admin_shared_trash_empty
import com.photonne.app.resources.admin_shared_trash_empty_subtitle
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.resources.admin_shared_trash_load_more
import com.photonne.app.resources.admin_shared_trash_purge_confirm_message
import com.photonne.app.resources.admin_shared_trash_purge_confirm_title
import com.photonne.app.resources.admin_shared_trash_selected_format
import com.photonne.app.resources.admin_shared_trash_unknown_user
import com.photonne.app.ui.library.ConfirmActionDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import org.jetbrains.compose.resources.getString
import com.photonne.app.ui.theme.ListRowsSkeleton
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.error.ErrorBanner
import androidx.compose.material.icons.outlined.CloudOff
import com.photonne.app.resources.error_banner_retry
import com.photonne.app.resources.action_refresh
import com.photonne.app.resources.admin_shared_trash_error_load
import com.photonne.app.resources.admin_shared_trash_error_restore
import com.photonne.app.resources.admin_shared_trash_error_purge
import com.photonne.app.resources.admin_shared_trash_restored_format
import com.photonne.app.resources.admin_shared_trash_purged_format
import org.jetbrains.compose.resources.stringResource

data class AdminSharedTrashUiState(
    val items: List<SharedTrashItem> = emptyList(),
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
    /** Outcome of a restore or purge, for the snackbar. */
    val resultMessage: String? = null,
    val nextCursor: Instant? = null,
    val hasMore: Boolean = false,
    val selection: Set<String> = emptySet(),
    val loaded: Boolean = false,
    val showPurgeConfirm: Boolean = false,
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
    val isEmpty: Boolean get() = loaded && items.isEmpty() && !isLoading
}

/**
 * Admin listing of assets that users deleted from shared folders. Mirrors
 * [AdminStatsViewModel] / [AdminTrashSettingsViewModel] for the load / error /
 * StateFlow plumbing, and pages with the server's `nextCursor` the same way
 * the user-facing `TrashViewModel` does.
 */
class AdminSharedTrashViewModel(
    private val repository: AdminRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminSharedTrashUiState())
    val state: StateFlow<AdminSharedTrashUiState> = _state.asStateFlow()

    fun consumeResult() {
        _state.update { it.copy(resultMessage = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.getSharedTrash(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = page.items,
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            isLoading = false,
                            loaded = true,
                            selection = emptySet()
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, getString(Res.string.admin_shared_trash_error_load))
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isAppending || snapshot.isLoading || !snapshot.hasMore) return
        val cursor = snapshot.nextCursor ?: return
        _state.update { it.copy(isAppending = true) }
        viewModelScope.launch {
            runCatching { repository.getSharedTrash(cursor = cursor) }
                .onSuccess { page ->
                    _state.update { current ->
                        val existing = current.items.mapTo(HashSet()) { item -> item.id }
                        val appended = page.items.filter { item -> item.id !in existing }
                        current.copy(
                            items = current.items + appended,
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isAppending = false,
                            error = errorFactory.from(error, getString(Res.string.admin_shared_trash_error_load))
                        )
                    }
                }
        }
    }

    fun toggleSelection(assetId: String) {
        _state.update {
            val next = it.selection.toMutableSet()
            if (!next.add(assetId)) next.remove(assetId)
            it.copy(selection = next)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun requestPurgeConfirm() {
        if (_state.value.selection.isEmpty()) return
        _state.update { it.copy(showPurgeConfirm = true) }
    }

    fun dismissPurgeConfirm() {
        _state.update { it.copy(showPurgeConfirm = false) }
    }

    fun restoreSelected() {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.restoreSharedTrash(ids) }
                .onSuccess {
                    val done = getString(Res.string.admin_shared_trash_restored_format, ids.size)
                    _state.update { it.copy(isMutating = false, selection = emptySet(), resultMessage = done) }
                    load()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, getString(Res.string.admin_shared_trash_error_restore))
                        )
                    }
                }
        }
    }

    fun purgeSelected() {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, showPurgeConfirm = false) }
        viewModelScope.launch {
            runCatching { repository.purgeSharedTrash(ids) }
                .onSuccess {
                    val done = getString(Res.string.admin_shared_trash_purged_format, ids.size)
                    _state.update { it.copy(isMutating = false, selection = emptySet(), resultMessage = done) }
                    load()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, getString(Res.string.admin_shared_trash_error_purge))
                        )
                    }
                }
        }
    }
}

@Composable
fun AdminSharedTrashScreen(viewModel: AdminSharedTrashViewModel) {
    val state by viewModel.state.collectAsState()
    val baseUrl = rememberApiBaseUrl()
    // Every entry: it loaded once per app session, so what users deleted since
    // only showed up after restarting the app.
    LaunchedEffect(Unit) { viewModel.load() }
    AdminResultSnackbar(state.resultMessage, viewModel::consumeResult)

    if (state.showPurgeConfirm) {
        ConfirmActionDialog(
            title = stringResource(Res.string.admin_shared_trash_purge_confirm_title),
            message = stringResource(Res.string.admin_shared_trash_purge_confirm_message),
            confirmLabel = stringResource(Res.string.admin_shared_trash_action_purge),
            isDestructive = true,
            isSubmitting = state.isMutating,
            onDismiss = viewModel::dismissPurgeConfirm,
            onConfirm = viewModel::purgeSelected
        )
    }

    when {
        state.isLoading && state.items.isEmpty() -> ListRowsSkeleton()
        state.error != null && state.items.isEmpty() -> EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = state.error!!.userMessage,
            actionLabel = stringResource(Res.string.error_banner_retry),
            onAction = viewModel::load
        )
        state.isEmpty -> EmptyState(
            icon = Icons.Outlined.DeleteSweep,
            title = stringResource(Res.string.admin_shared_trash_empty),
            subtitle = stringResource(Res.string.admin_shared_trash_empty_subtitle),
            actionLabel = stringResource(Res.string.action_refresh),
            onAction = viewModel::load
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            if (state.isSelectionActive) {
                SelectionActionBar(
                    count = state.selection.size,
                    isMutating = state.isMutating,
                    onRestore = viewModel::restoreSelected,
                    onPurge = viewModel::requestPurgeConfirm,
                    onClear = viewModel::clearSelection
                )
                HorizontalDivider()
            }
            ErrorBanner(error = state.error, onRetry = viewModel::load, onDismiss = viewModel::dismissError)
            PhotonneRefreshableScreen(
                isRefreshing = state.isLoading,
                onRefresh = viewModel::load,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = Spacing.sm,
                        bottom = Spacing.sm + floatingNavBarReservedHeight()
                    )
                ) {
                    items(state.items, key = { it.id }) { item ->
                        SharedTrashRow(
                            item = item,
                            baseUrl = baseUrl,
                            isSelected = item.id in state.selection,
                            onToggle = { viewModel.toggleSelection(item.id) }
                        )
                    }
                    if (state.hasMore) {
                        item("load-more") {
                            // Pages as the end scrolls in, like the user's own
                            // trash. The button is the way back after a page
                            // that failed.
                            LaunchedEffect(state.nextCursor) { viewModel.loadMore() }
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.error != null && !state.isAppending) {
                                    TextButton(onClick = viewModel::loadMore) {
                                        Text(stringResource(Res.string.admin_shared_trash_load_more))
                                    }
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    isMutating: Boolean,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            stringResource(Res.string.admin_shared_trash_selected_format, count),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (isMutating) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
        OutlinedButton(onClick = onRestore, enabled = !isMutating) {
            Text(stringResource(Res.string.admin_shared_trash_action_restore))
        }
        Button(
            onClick = onPurge,
            enabled = !isMutating,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(stringResource(Res.string.admin_shared_trash_action_purge))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedTrashRow(
    item: SharedTrashItem,
    baseUrl: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onToggle)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        SharedTrashThumbnail(
            item = item,
            baseUrl = baseUrl,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.fileName.ifBlank { item.id },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val user = item.deletedByUsername?.takeIf { it.isNotBlank() }
                ?: stringResource(Res.string.admin_shared_trash_unknown_user)
            Text(
                stringResource(Res.string.admin_shared_trash_deleted_by_format, user),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            sourceFolderLabel(item)?.let { folder ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        folder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item.deletedAt?.let { deletedAt ->
                Text(
                    formatDeletedAt(deletedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SharedTrashThumbnail(
    item: SharedTrashItem,
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    var failed by remember(item.id) { mutableStateOf(false) }
    val model = remember(item.id, item.hasThumbnails, baseUrl) {
        if (item.hasThumbnails && item.id.isNotBlank()) {
            "$baseUrl/api/assets/${item.id}/thumbnail?size=Small"
        } else {
            null
        }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model != null && !failed) {
            AsyncImage(
                model = model,
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        } else {
            Icon(
                imageVector = if (item.type.equals("video", ignoreCase = true)) {
                    Icons.Outlined.Videocam
                } else {
                    Icons.Outlined.Image
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

/** Prefer the server-provided folder name; otherwise derive the last path
 *  segment from `deletedFromPath` so the admin still sees an origin hint. */
private fun sourceFolderLabel(item: SharedTrashItem): String? {
    item.deletedFromFolderName?.takeIf { it.isNotBlank() }?.let { return it }
    val path = item.deletedFromPath?.takeIf { it.isNotBlank() } ?: return null
    val trimmed = path.trimEnd('/', '\\')
    val idx = trimmed.lastIndexOfAny(charArrayOf('/', '\\'))
    return if (idx in 0 until trimmed.lastIndex) trimmed.substring(idx + 1) else trimmed
}

private fun formatDeletedAt(instant: Instant): String = adminDate(instant)
