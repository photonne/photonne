package com.photonne.app.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.devicebackup.DeviceFolderRef
import com.photonne.app.data.devicelibrary.DeviceBucket
import com.photonne.app.data.devicelibrary.DeviceLibraryStore
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.backup_bucket_item_count
import com.photonne.app.resources.device_folders_add_backup
import com.photonne.app.resources.device_folders_empty_subtitle
import com.photonne.app.resources.device_folders_empty_title
import com.photonne.app.resources.device_folders_title
import com.photonne.app.resources.timeline_scope_backed_up
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.PhotoGridScrubberOverlay
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.ListRowsSkeleton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * "Mi dispositivo": the device library browsed by system folder (bucket),
 * opened from the entry card at the top of Carpetas. Lists EVERY bucket,
 * always — deliberately blind to the timeline's DeviceLibraryScope, since
 * this is where a folder the timeline hides (WhatsApp under the camera-only
 * default) must remain findable. Android-only in practice: the host never
 * offers the entry where [DeviceLibraryStore.supportsBuckets] is false.
 */
@Composable
fun DeviceFoldersScreen(
    /** Folder-ref uris already in the backup list, for the "Con copia" tag. */
    backedUpUris: Set<String>,
    onAddToBackup: (DeviceFolderRef) -> Unit,
    onOpenBucket: (DeviceBucket) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val store: DeviceLibraryStore = koinInject()
    var buckets by remember { mutableStateOf<List<DeviceBucket>?>(null) }
    LaunchedEffect(Unit) {
        buckets = runCatching { store.listBuckets() }.getOrDefault(emptyList())
    }

    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val reservedTop = subscreenChromeReservedTop()

    Box(modifier = Modifier.fillMaxSize()) {
        val loaded = buckets
        when {
            loaded == null -> ListRowsSkeleton()
            loaded.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Smartphone,
                title = stringResource(Res.string.device_folders_empty_title),
                subtitle = stringResource(Res.string.device_folders_empty_subtitle)
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = 8.dp + reservedTop,
                    bottom = floatingNavBarReservedHeight()
                ),
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
            ) {
                items(loaded, key = { it.id }) { bucket ->
                    DeviceBucketRow(
                        bucket = bucket,
                        isBackedUp = bucket.toFolderRef().uri in backedUpUris,
                        onClick = { onOpenBucket(bucket) },
                        onAddToBackup = { onAddToBackup(bucket.toFolderRef()) }
                    )
                }
            }
        }

        SubscreenFloatingChrome(
            title = stringResource(Res.string.device_folders_title),
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                isScrollInProgress = { listState.isScrollInProgress },
                scrollToTopMinIndex = SCROLL_TO_TOP_MIN_ROW,
                onScrollToTop = { listState.animateScrollToItem(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
}

@Composable
private fun DeviceBucketRow(
    bucket: DeviceBucket,
    isBackedUp: Boolean,
    onClick: () -> Unit,
    onAddToBackup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bucket.latestUri != null) {
                AsyncImage(
                    model = bucket.latestUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                bucket.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(Res.string.backup_bucket_item_count, bucket.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isBackedUp) {
            Text(
                stringResource(Res.string.timeline_scope_backed_up),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        } else {
            IconButton(onClick = onAddToBackup) {
                Icon(
                    Icons.Outlined.CloudUpload,
                    contentDescription = stringResource(Res.string.device_folders_add_backup),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One bucket's contents as a plain local grid — read-only in this first
 * cut: browse, check the sync badges the ledger overlay paints, tap into
 * the shared asset viewer (which handles `device:` items in place).
 */
@Composable
fun DeviceFolderDetailScreen(
    bucket: DeviceBucket,
    onOpenAsset: (items: List<TimelineItem>, index: Int) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val store: DeviceLibraryStore = koinInject()
    val apiBaseUrl = rememberApiBaseUrl()
    var items by remember(bucket.id) { mutableStateOf<List<TimelineItem>?>(null) }
    LaunchedEffect(bucket.id) {
        items = withContext(Dispatchers.Default) {
            runCatching { store.loadBucketItems(bucket.id) }.getOrDefault(emptyList())
        }
    }

    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val reservedTop = subscreenChromeReservedTop()
    val reservedBottom = floatingNavBarReservedHeight()

    Box(modifier = Modifier.fillMaxSize()) {
        val loaded = items
        when {
            loaded == null -> AssetGridSkeleton(
                contentPadding = PaddingValues(top = reservedTop, bottom = reservedBottom)
            )
            loaded.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Smartphone,
                title = stringResource(Res.string.device_folders_empty_title),
                subtitle = stringResource(Res.string.device_folders_empty_subtitle)
            )
            else -> {
                AssetGrid(
                    items = loaded,
                    baseUrl = apiBaseUrl,
                    gridState = gridState,
                    onItemClick = { index -> onOpenAsset(loaded, index) },
                    contentPadding = PaddingValues(top = reservedTop, bottom = reservedBottom),
                    modifier = Modifier.fillMaxWidth().hazeSource(hazeState)
                )
                PhotoGridScrubberOverlay(
                    gridState = gridState,
                    items = loaded,
                    reservedTop = reservedTop,
                    reservedBottom = reservedBottom,
                    selectionActive = false,
                    hazeState = hazeState
                )
            }
        }

        SubscreenFloatingChrome(
            title = bucket.displayName,
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                isScrollInProgress = { gridState.isScrollInProgress },
                scrollToTopMinIndex = SCROLL_TO_TOP_MIN_CELL,
                onScrollToTop = {
                    if (gridState.firstVisibleItemIndex > SCROLL_TO_TOP_SNAP_CELL) {
                        gridState.scrollToItem(SCROLL_TO_TOP_SNAP_CELL)
                    }
                    gridState.animateScrollToItem(0)
                }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange,
            statusBarScrim = true
        )
    }
}

/** Filas / celdas scrolleadas antes de que aparezca la píldora de volver arriba. */
private const val SCROLL_TO_TOP_MIN_ROW = 8
private const val SCROLL_TO_TOP_MIN_CELL = 12

/** Adónde teletransporta el tap antes de animar el último tramo. */
private const val SCROLL_TO_TOP_SNAP_CELL = 48
