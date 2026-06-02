package com.photonne.app.ui.timeline

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.hub_section_memories
import com.photonne.app.resources.timeline_memories_one_year_ago
import com.photonne.app.resources.timeline_memories_years_ago
import com.photonne.app.ui.theme.LocalCurrentDetailAssetId
import com.photonne.app.ui.theme.LocalSharedTransitionScope
import com.photonne.app.ui.theme.MemoryCardShape
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

private const val StoryDurationMs = 5000L

/**
 * "Hoy hace…" cinematic memories carousel, pinned above the photo grid on
 * the Fotos timeline. Extracted from the old Hub landing; each page is an
 * on-this-day group that opens the asset viewer at its cover when tapped.
 */
@Composable
fun MemoriesStrip(
    memories: List<TimelineItem>,
    baseUrl: String,
    onOpenMemory: (List<TimelineItem>, Int) -> Unit
) {
    val zone = TimeZone.currentSystemDefault()
    val currentYear = Clock.System.now().toLocalDateTime(zone).date.year
    val groups = remember(memories) { groupMemoriesByDay(memories, zone, currentYear) }
    if (groups.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.hub_section_memories),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        val pagerState = rememberPagerState(pageCount = { groups.size })
        val progress = remember { Animatable(0f) }
        var paused by remember { mutableStateOf(false) }
        val scrollScope = rememberCoroutineScope()

        // Auto-advance: when the pager *settles* on a page, drive progress
        // 0→1 over StoryDurationMs, then scroll to the next page. We key
        // off [settledPage] (not currentPage) so a swipe in progress
        // doesn't re-trigger the timer mid-flight, and we hand the scroll
        // to [scrollScope] so the LaunchedEffect cancelling on key change
        // doesn't leave the pager stranded between two pages.
        LaunchedEffect(pagerState.settledPage, paused, groups.size) {
            progress.snapTo(0f)
            if (paused || groups.size < 2) return@LaunchedEffect
            val animationResult = progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = StoryDurationMs.toInt(), easing = LinearEasing)
            )
            if (animationResult.endReason == AnimationEndReason.Finished) {
                val next = (pagerState.settledPage + 1) % groups.size
                scrollScope.launch { pagerState.animateScrollToPage(next) }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = maxWidth - 32.dp
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardWidth * 0.62f)
            ) { page ->
                val group = groups[page]
                val isActive = pagerState.currentPage == page
                val label = if (group.yearsAgo == 1)
                    stringResource(Res.string.timeline_memories_one_year_ago)
                else
                    stringResource(Res.string.timeline_memories_years_ago, group.yearsAgo)

                StoryCard(
                    group = group,
                    label = label,
                    baseUrl = baseUrl,
                    isActive = isActive,
                    storyProgress = if (isActive) progress.value else 0f,
                    totalStories = groups.size,
                    activeIndex = pagerState.currentPage,
                    onTapHold = { hold -> paused = hold },
                    onClick = { onOpenMemory(group.items, 0) }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StoryCard(
    group: MemoryGroup,
    label: String,
    baseUrl: String,
    isActive: Boolean,
    storyProgress: Float,
    totalStories: Int,
    activeIndex: Int,
    onTapHold: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val cover = group.cover
    val sharedScope = LocalSharedTransitionScope.current
    val currentDetailId = LocalCurrentDetailAssetId.current
    // cover == group.items[0], and tapping the card opens the viewer at
    // index 0 — so the morph key matches when the viewer mounts.
    val coverSharedMod: Modifier = if (sharedScope != null && currentDetailId != null) {
        val sharedKey = remember(cover.id) { "asset-${cover.id}" }
        with(sharedScope) {
            Modifier.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(key = sharedKey),
                visible = currentDetailId != cover.id,
                boundsTransform = { _, _ ->
                    androidx.compose.animation.core.tween(durationMillis = 320)
                }
            )
        }
    } else {
        Modifier
    }
    // Ken Burns: when the slide is the active one, scale lerps 1.0 → 1.10
    // and pans slightly using the same progress driver so the motion lines
    // up exactly with the time the slide is on screen.
    val kenBurnsScale = 1f + (if (isActive) storyProgress else 0f) * 0.10f
    val kenBurnsPan = (if (isActive) storyProgress else 0f) * 24f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MemoryCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(group.cover.id) {
                detectTapGestures(
                    onPress = {
                        onTapHold(true)
                        try {
                            val released = tryAwaitRelease()
                            onTapHold(false)
                            if (released) onClick()
                        } finally {
                            onTapHold(false)
                        }
                    }
                )
            }
    ) {
        if (cover.hasThumbnails) {
            AsyncImage(
                model = "$baseUrl/api/assets/${cover.id}/thumbnail?size=Large",
                contentDescription = cover.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kenBurnsScale
                        scaleY = kenBurnsScale
                        translationX = -kenBurnsPan
                    }
                    .then(coverSharedMod)
            )
        }
        // Bottom gradient so the white label keeps contrast on light covers
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.0f),
                            Color.Black.copy(alpha = 0.55f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        // Top progress segments — one per memory group, current one fills.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(totalStories) { idx ->
                val fill = when {
                    idx < activeIndex -> 1f
                    idx > activeIndex -> 0f
                    else -> storyProgress
                }
                StorySegment(fill = fill, modifier = Modifier.weight(1f))
            }
        }
        // Caption
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            if (group.count > 1) {
                Text(
                    text = "${group.count} fotos",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun StorySegment(fill: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.30f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fill.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(Color.White)
        )
    }
}
