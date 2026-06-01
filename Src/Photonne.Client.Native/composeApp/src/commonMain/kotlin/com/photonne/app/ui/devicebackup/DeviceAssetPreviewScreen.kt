package com.photonne.app.ui.devicebackup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import com.photonne.app.ui.platform.OrientationController
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

private const val PAGER_DISABLE_THRESHOLD = 1.05f

/**
 * Full-screen preview for any file in the device folder. Works directly off
 * the local URI so non-synced files render without round-tripping to the
 * server. Synced files surface a "Details" action that hands off to the real
 * [com.photonne.app.ui.asset.AssetDetailScreen] for full metadata flows.
 *
 * Mirrors the main viewer's video behaviour: a single tap toggles the chrome
 * for immersive viewing, the player is letterboxed below the top bar, a video
 * relaxes the app's portrait lock (rotate to watch landscape), and a fullscreen
 * button hides the chrome and forces landscape.
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
        // Defensive — caller should gate on a non-empty list, but if something
        // races (e.g. free-up-space empties the grid while the user is opening
        // it) just bail rather than crash the pager.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, entries.size - 1)
    ) { entries.size }

    var currentScale by remember { mutableStateOf(1f) }
    var chromeVisible by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    var topChromeHeightPx by remember { mutableStateOf(0) }
    val topChromeHeight = with(density) { topChromeHeightPx.toDp() }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { currentScale = 1f }
    }

    val currentEntry = entries.getOrNull(pagerState.currentPage)
    val syncedCurrent = currentEntry?.takeIf {
        it.syncState is DeviceMediaSyncState.Synced
    }

    // Orientation: portrait everywhere, but a playable video relaxes it — rotate
    // freely while watching, force landscape from the fullscreen button (chrome
    // hidden), snap back to portrait when leaving the video or the screen.
    val isVideoOnScreen =
        currentEntry?.media?.type == DeviceMediaType.Video && isVideoPlaybackSupported
    LaunchedEffect(isVideoOnScreen, chromeVisible) {
        when {
            isVideoOnScreen && !chromeVisible -> OrientationController.forceLandscape()
            isVideoOnScreen -> OrientationController.allowAutoRotate()
            else -> OrientationController.lockPortrait()
        }
    }
    DisposableEffect(Unit) {
        onDispose { OrientationController.lockPortrait() }
    }

    val chromeAlpha by animateFloatAsState(
        targetValue = if (chromeVisible) 1f else 0f,
        label = "deviceChromeAlpha"
    )

    Scaffold(containerColor = Color.Black) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= PAGER_DISABLE_THRESHOLD,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val entry = entries[page]
                val isCurrent = page == pagerState.currentPage
                DeviceAssetPage(
                    entry = entry,
                    thumbnailModel = thumbnailModel,
                    isCurrent = isCurrent,
                    onScaleChange = { newScale -> if (isCurrent) currentScale = newScale },
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onVideoControlsVisibilityChanged = { chromeVisible = it },
                    videoTopInset = if (chromeVisible) topChromeHeight else 0.dp
                )
            }

            if (chromeAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeAlpha }
                        .onSizeChanged { topChromeHeightPx = it.height }
                ) {
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
                            if (isVideoOnScreen) {
                                IconButton(onClick = { chromeVisible = false }) {
                                    Icon(
                                        Icons.Filled.Fullscreen,
                                        contentDescription = "Pantalla completa"
                                    )
                                }
                            }
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
            }
        }
    }
}

@Composable
private fun DeviceAssetPage(
    entry: DeviceBackupEntry,
    thumbnailModel: (com.photonne.app.data.devicebackup.DeviceMedia) -> String,
    isCurrent: Boolean,
    onScaleChange: (Float) -> Unit,
    onToggleChrome: () -> Unit,
    onVideoControlsVisibilityChanged: (Boolean) -> Unit,
    videoTopInset: Dp
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
                    onControlsVisibilityChanged = onVideoControlsVisibilityChanged,
                    modifier = Modifier.fillMaxSize().padding(top = videoTopInset)
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
                    onScaleChange = onScaleChange,
                    onTap = onToggleChrome
                )
            }
        }
    }
}
