package com.photonne.app.ui.devicebackup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.devicebackup.DeviceMediaSyncState
import com.photonne.app.data.devicebackup.DeviceMediaType
import com.photonne.app.resources.Res
import com.photonne.app.resources.device_backup_preview_back
import com.photonne.app.resources.device_backup_preview_open_detail
import com.photonne.app.resources.device_backup_preview_video_unsupported
import com.photonne.app.ui.asset.VideoPlayer
import com.photonne.app.ui.asset.ZoomablePagerImage
import com.photonne.app.ui.asset.isVideoPlaybackSupported
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

private const val PAGER_DISABLE_THRESHOLD = 1.05f

/**
 * Full-screen preview for any file in the device folder. Works
 * directly off the local URI so non-synced files render without
 * round-tripping to the server. Synced files surface a "Details"
 * action that hands off to the real [AssetDetailScreen] for full
 * metadata / favorite / archive flows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAssetPreviewScreen(
    entries: List<DeviceBackupEntry>,
    startIndex: Int,
    thumbnailModel: (com.photonne.app.data.devicebackup.DeviceMedia) -> String,
    onBack: () -> Unit,
    onOpenDetail: (DeviceBackupEntry) -> Unit
) {
    if (entries.isEmpty()) {
        // Defensive — caller should gate on a non-empty list, but if
        // something races (e.g. free-up-space empties the grid while
        // the user is opening it) just bail rather than crash the
        // pager.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, entries.size - 1)
    ) { entries.size }

    var currentScale by remember { mutableStateOf(1f) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { currentScale = 1f }
    }

    val currentEntry = entries.getOrNull(pagerState.currentPage)
    val syncedCurrent = currentEntry?.takeIf {
        it.syncState is DeviceMediaSyncState.Synced
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.device_backup_preview_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = currentEntry?.media?.displayName ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                },
                actions = {
                    if (syncedCurrent != null) {
                        IconButton(onClick = { onOpenDetail(syncedCurrent) }) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = stringResource(
                                    Res.string.device_backup_preview_open_detail
                                )
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = currentScale <= PAGER_DISABLE_THRESHOLD,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            val entry = entries[page]
            val isCurrent = page == pagerState.currentPage
            DeviceAssetPage(
                entry = entry,
                thumbnailModel = thumbnailModel,
                isCurrent = isCurrent,
                onScaleChange = { newScale -> if (isCurrent) currentScale = newScale }
            )
        }
    }
}

@Composable
private fun DeviceAssetPage(
    entry: DeviceBackupEntry,
    thumbnailModel: (com.photonne.app.data.devicebackup.DeviceMedia) -> String,
    isCurrent: Boolean,
    onScaleChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val media = entry.media
        val model = thumbnailModel(media)
        when {
            media.type == DeviceMediaType.Video && isVideoPlaybackSupported && isCurrent -> {
                VideoPlayer(
                    url = media.uri,
                    headers = emptyMap(),
                    modifier = Modifier.fillMaxSize()
                )
            }
            media.type == DeviceMediaType.Video -> {
                AsyncImage(
                    model = model,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                if (!isVideoPlaybackSupported && isCurrent) {
                    Text(
                        text = stringResource(Res.string.device_backup_preview_video_unsupported),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            else -> {
                ZoomablePagerImage(
                    model = model,
                    contentDescription = media.displayName,
                    onScaleChange = onScaleChange
                )
            }
        }
    }
}
