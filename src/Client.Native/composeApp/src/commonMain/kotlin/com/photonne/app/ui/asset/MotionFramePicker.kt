package com.photonne.app.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.asset_action_pick_frame
import com.photonne.app.resources.motion_frames_hint
import com.photonne.app.resources.motion_frames_load_error
import com.photonne.app.resources.motion_frames_next
import com.photonne.app.resources.motion_frames_position
import com.photonne.app.resources.motion_frames_previous
import com.photonne.app.resources.motion_frames_save
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PrimaryActionButton
import com.photonne.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Thumbnails drawn in the filmstrip, spread evenly over the clip. */
private const val FilmstripThumbs = 10

/**
 * "Elegir fotograma": the motion photo's clip frame by frame, to keep the
 * clean one as a new photo next to the original (Samsung Gallery style).
 *
 * The frames are JPEGs the server decodes with ffmpeg, not a video player, so
 * the same screen works on Android, iOS and desktop. Once the count arrives
 * every frame is prefetched to Coil's disk cache one at a time, starting next
 * to the current one, so stepping and scrubbing stop waiting on the network.
 * While a frame loads the previous one stays on screen.
 *
 * Drawn over the viewer (it fills the whole screen and swallows touches so
 * nothing reaches the pager below); the host closes it on Back.
 */
@Composable
internal fun MotionFramePicker(
    assetId: String,
    baseUrl: String,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    repository: AssetDetailRepository = koinInject(),
    errorFactory: UiErrorFactory = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val platformContext = LocalPlatformContext.current
    var frameCount by remember(assetId) { mutableStateOf<Int?>(null) }
    var loadFailed by remember(assetId) { mutableStateOf(false) }
    var loadTick by remember(assetId) { mutableIntStateOf(0) }
    var index by remember(assetId) { mutableIntStateOf(0) }
    var saving by remember(assetId) { mutableStateOf(false) }
    var saveError by remember(assetId) { mutableStateOf<UiError?>(null) }
    // Last frame actually drawn: placeholder for the next one so stepping
    // never flashes an empty screen.
    var shownKey by remember(assetId) { mutableStateOf<MemoryCache.Key?>(null) }

    fun frameUrl(i: Int) = "$baseUrl/api/assets/$assetId/motion/frames/$i"

    LaunchedEffect(assetId, loadTick) {
        runCatching { repository.getMotionFrameCount(assetId) }
            .onSuccess { count ->
                // The still sits around the middle of the clip on both iPhone
                // and Samsung, so that's where the picker starts.
                index = count / 2
                frameCount = count
            }
            .onFailure { loadFailed = true }
    }

    // Prefetch to disk only: ~90 decoded full-screen frames would flush the
    // memory cache the grid behind relies on.
    LaunchedEffect(frameCount) {
        val count = frameCount ?: return@LaunchedEffect
        val start = index
        val loader = SingletonImageLoader.get(platformContext)
        (0 until count).sortedBy { abs(it - start) }.forEach { i ->
            loader.execute(
                ImageRequest.Builder(platformContext)
                    .data(frameUrl(i))
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs)
        ) {
            IconButton(onClick = onClose) {
                Icon(PhotonneIcons.Close, contentDescription = stringResource(Res.string.action_close), tint = Color.White)
            }
            Text(
                stringResource(Res.string.asset_action_pick_frame),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val count = frameCount
            when {
                loadFailed -> ErrorBanner(
                    error = UiError(stringResource(Res.string.motion_frames_load_error)),
                    onRetry = { loadFailed = false; loadTick++ },
                    modifier = Modifier.padding(Spacing.lg).clip(MaterialTheme.shapes.small)
                )
                count == null -> CircularProgressIndicator(color = Color.White)
                else -> AsyncImage(
                    model = ImageRequest.Builder(platformContext)
                        .data(frameUrl(index))
                        .placeholderMemoryCacheKey(shownKey)
                        .crossfade(false)
                        .build(),
                    contentDescription = stringResource(Res.string.motion_frames_position, index + 1, count),
                    contentScale = ContentScale.Fit,
                    onSuccess = { shownKey = it.result.memoryCacheKey },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val count = frameCount
        if (count != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Filmstrip(
                    frameCount = count,
                    index = index,
                    frameUrl = ::frameUrl,
                    onIndexChange = { index = it }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { index-- }, enabled = index > 0) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = stringResource(Res.string.motion_frames_previous),
                            tint = if (index > 0) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                    Text(
                        stringResource(Res.string.motion_frames_position, index + 1, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(160.dp)
                    )
                    IconButton(onClick = { index++ }, enabled = index < count - 1) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.motion_frames_next),
                            tint = if (index < count - 1) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
                Text(
                    stringResource(Res.string.motion_frames_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                saveError?.let { error ->
                    ErrorBanner(
                        error = error,
                        onDismiss = { saveError = null },
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                    )
                }
                PrimaryActionButton(
                    label = stringResource(Res.string.motion_frames_save),
                    isLoading = saving,
                    onClick = {
                        saveError = null
                        saving = true
                        scope.launch {
                            runCatching { repository.saveMotionFrame(assetId, index) }
                                .onSuccess { onSaved() }
                                .onFailure { saveError = errorFactory.from(it, "No se pudo guardar el fotograma") }
                            saving = false
                        }
                    }
                )
            }
        }
    }
}

/**
 * Strip of evenly spaced thumbnails with a framed cursor on the current frame.
 * Tapping or dragging anywhere on it jumps to the frame under the finger; the
 * arrows below handle the single steps.
 */
@Composable
private fun Filmstrip(
    frameCount: Int,
    index: Int,
    frameUrl: (Int) -> String,
    onIndexChange: (Int) -> Unit,
) {
    val thumbs = remember(frameCount) {
        val n = minOf(FilmstripThumbs, frameCount)
        List(n) { i -> if (n == 1) 0 else i * (frameCount - 1) / (n - 1) }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(frameCount) {
                fun at(x: Float) =
                    ((x / size.width) * (frameCount - 1)).roundToInt().coerceIn(0, frameCount - 1)
                detectTapGestures { onIndexChange(at(it.x)) }
            }
            .pointerInput(frameCount) {
                fun at(x: Float) =
                    ((x / size.width) * (frameCount - 1)).roundToInt().coerceIn(0, frameCount - 1)
                detectHorizontalDragGestures(
                    onDragStart = { onIndexChange(at(it.x)) },
                    onHorizontalDrag = { change, _ -> onIndexChange(at(change.position.x)) }
                )
            }
    ) {
        Row(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
            thumbs.forEach { i ->
                AsyncImage(
                    model = frameUrl(i),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        // The cursor shows the current frame itself, framed, like Samsung's;
        // the last one drawn stays while the next loads.
        var cursorKey by remember(frameCount) { mutableStateOf<MemoryCache.Key?>(null) }
        val cursorWidth = 40.dp
        val travel = maxWidth - cursorWidth
        val fraction = if (frameCount > 1) index.toFloat() / (frameCount - 1) else 0f
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(frameUrl(index))
                .placeholderMemoryCacheKey(cursorKey)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { cursorKey = it.result.memoryCacheKey },
            modifier = Modifier
                .offset(x = travel * fraction)
                .width(cursorWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, Color.White, RoundedCornerShape(6.dp))
        )
    }
}
