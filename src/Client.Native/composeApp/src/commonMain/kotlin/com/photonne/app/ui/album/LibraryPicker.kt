package com.photonne.app.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.timeline.TimelineRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.library_picker_hint
import com.photonne.app.resources.selection_count
import com.photonne.app.ui.error.FullScreenError
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.selection.rememberSelectionSummary
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PrimaryActionButton
import com.photonne.app.ui.theme.Spacing
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The library, page by page, newest first, for choosing photos from inside an
 * album or a folder ("Añadir fotos", "Traer fotos"). Plain state holder so the
 * paging can be tested without a screen.
 */
class LibraryPickerState(
    private val repository: TimelineRepository,
    private val errors: UiErrorFactory,
    private val scope: CoroutineScope,
) {
    var items by mutableStateOf<List<TimelineItem>>(emptyList())
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set
    var hasMore by mutableStateOf(true)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiError?>(null)
        private set
    private var cursor: Instant? = null

    fun loadMore() {
        if (isLoading || !hasMore) return
        isLoading = true
        scope.launch {
            runCatching { repository.loadPage(cursor) }
                .onSuccess { page ->
                    // A photo that moved between pages shows once.
                    val known = items.mapTo(HashSet()) { it.id }
                    items = items + page.items.filter { it.id !in known }
                    cursor = page.nextCursor
                    hasMore = page.hasMore
                    error = null
                }
                .onFailure { error = errors.from(it, "No se pudieron cargar las fotos") }
            isLoading = false
        }
    }

    fun retry() {
        error = null
        loadMore()
    }

    fun toggle(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }
}

/**
 * Full-screen picker over the library. [onConfirm] runs with the chosen ids and
 * returns whether it worked; the picker stays open (with the button busy) until
 * then, and closes itself on success.
 */
@Composable
fun LibraryPicker(
    title: String,
    confirmLabel: @Composable (Int) -> String,
    onDismiss: () -> Unit,
    onConfirm: suspend (List<String>) -> Boolean,
) {
    val repository: TimelineRepository = koinInject()
    val errors: UiErrorFactory = koinInject()
    val scope = rememberCoroutineScope()
    val state = remember { LibraryPickerState(repository, errors, scope) }
    val apiBaseUrl = rememberApiBaseUrl()
    var submitting by remember { mutableStateOf(false) }
    val summary = rememberSelectionSummary(state.items, state.selection)

    LaunchedEffect(state) { state.loadMore() }

    Dialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs, vertical = Spacing.xs)
            ) {
                IconButton(onClick = onDismiss, enabled = !submitting) {
                    Icon(PhotonneIcons.Close, contentDescription = stringResource(Res.string.action_close))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (state.selection.isEmpty()) stringResource(Res.string.library_picker_hint)
                        else listOfNotNull(
                            pluralStringResource(Res.plurals.selection_count, state.selection.size, state.selection.size),
                            summary
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.items.isEmpty() && state.error != null ->
                        FullScreenError(error = state.error, onRetry = state::retry)
                    state.items.isEmpty() -> AssetGridSkeleton()
                    else -> AssetGrid(
                        items = state.items,
                        baseUrl = apiBaseUrl,
                        // Here a tap chooses: there is no viewer to open.
                        onItemClick = { index -> state.items.getOrNull(index)?.let { state.toggle(it.id) } },
                        onItemLongClick = { index -> state.items.getOrNull(index)?.let { state.toggle(it.id) } },
                        selectedIds = state.selection,
                        hasMore = state.hasMore,
                        isAppending = state.isLoading,
                        onLoadMore = state::loadMore,
                        appendFailed = state.error != null,
                        onRetryAppend = state::retry,
                        contentPadding = PaddingValues(bottom = Spacing.md)
                    )
                }
            }

            PrimaryActionButton(
                label = confirmLabel(state.selection.size),
                enabled = state.selection.isNotEmpty(),
                isLoading = submitting,
                onClick = {
                    submitting = true
                    scope.launch {
                        val done = onConfirm(state.selection.toList())
                        submitting = false
                        if (done) onDismiss()
                    }
                },
                modifier = Modifier.padding(Spacing.lg)
            )
        }
    }
}
