package com.photonne.app.ui.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_memories_group_count
import com.photonne.app.resources.memories_strip_see_all
import com.photonne.app.resources.memories_strip_title
import com.photonne.app.resources.timeline_memories_one_year_ago
import com.photonne.app.resources.timeline_memories_years_ago
import com.photonne.app.ui.memories.MemoryCardFace
import com.photonne.app.ui.memories.MemoryDetailContext
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

private const val StoryDurationMs = 5000L

/**
 * Cinematic memories carousel, pinned above the photo grid on the Fotos
 * timeline. Each page is an on-this-day group that opens as an album when
 * tapped — same destination the Recuerdos section reaches, so a memory behaves
 * the same wherever you meet it.
 *
 * Deliberately shows ONLY today's anniversaries, live from /api/assets/memories:
 * it's the teaser, and [onSeeAll] leads to the Recuerdos section, which holds
 * everything else (people, trips, favourites). That split is what makes the
 * header's chevron worth following — it used to open a screen showing these very
 * same groups in a list.
 */
@Composable
fun MemoriesStrip(
    memories: List<TimelineItem>,
    baseUrl: String,
    onOpenMemory: (MemoryDetailContext) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    val zone = TimeZone.currentSystemDefault()
    val currentYear = Clock.System.now().toLocalDateTime(zone).date.year
    val groups = remember(memories) { groupMemoriesByDay(memories, currentYear) }
    if (groups.isEmpty()) return

    // The chevron is decorative; the whole title row carries the label instead,
    // so screen readers announce one button rather than an unlabelled icon.
    val seeAllLabel = stringResource(Res.string.memories_strip_see_all)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)) {
        // Title + chevron are one tap target, sized to their content and pinned
        // left. Deliberately NOT a full-width clickable row: the scrubber lives
        // against the right edge (see TimelineScrubber), and a header hit area
        // reaching that far would fight it for the same touches. The chevron is
        // the app's idiom for "there's more inside" (FoldersListScreen,
        // AccountSettingsScreen), which the old TextButton never was.
        Row(
            modifier = Modifier
                .padding(start = 16.dp)
                .then(
                    if (onSeeAll != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onSeeAll)
                            .semantics {
                                role = Role.Button
                                contentDescription = seeAllLabel
                            }
                    } else Modifier
                )
                // 12dp around a titleMedium clears Material's 48dp minimum target.
                // A bare clickable doesn't apply minimumInteractiveComponentSize
                // the way TextButton did, so the padding has to earn it.
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.memories_strip_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (onSeeAll != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        val pagerState = rememberPagerState(pageCount = { groups.size })
        val progress = remember { Animatable(0f) }
        var paused by remember { mutableStateOf(false) }
        val scrollScope = rememberCoroutineScope()

        // Gate the Ken Burns + auto-advance timer on the on-screen cover
        // having loaded, so the 5s timer never starts while the image is
        // still fetching (which would make the cover pop in already
        // mid-zoom). Keying off the load state means the scale always
        // starts from 1.0.
        val loadedCovers = remember { mutableStateMapOf<String, Boolean>() }
        val activeCoverId = groups.getOrNull(pagerState.currentPage)?.cover?.id
        val activeCoverLoaded = activeCoverId != null && loadedCovers[activeCoverId] == true

        // Auto-advance: once the current page's cover has loaded, drive
        // progress 0→1 over StoryDurationMs, then scroll to the next page.
        // We key off [currentPage] (not settledPage) so the zoom resets and
        // restarts from 1.0 the instant the page changes — i.e. mid-scroll,
        // before the incoming cover reaches centre — making it one
        // continuous motion instead of "lands, sits still, then zooms".
        // The scroll runs in [scrollScope] so this effect cancelling on key
        // change doesn't leave the pager stranded between two pages.
        LaunchedEffect(pagerState.currentPage, paused, groups.size, activeCoverLoaded) {
            progress.snapTo(0f)
            if (paused || groups.size < 2 || !activeCoverLoaded) return@LaunchedEffect
            val animationResult = progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = StoryDurationMs.toInt(), easing = LinearEasing)
            )
            if (animationResult.endReason == AnimationEndReason.Finished) {
                val next = (pagerState.currentPage + 1) % groups.size
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
                // Ken Burns drives off currentPage — the same key that resets
                // [progress] to 0 — so the incoming page reads progress 0 the
                // moment it becomes current (no stale ≈1.0 value, no jump) and
                // zooms continuously as it slides in.
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
                    onCoverLoaded = { loadedCovers[group.cover.id] = true },
                    // The strip's own label is the memory's title: these groups
                    // are computed here, not by the server, so there's nothing
                    // else to name them with.
                    onClick = {
                        onOpenMemory(
                            MemoryDetailContext(
                                title = label,
                                subtitle = null,
                                coverAssetId = group.cover.id,
                                items = group.items,
                            )
                        )
                    }
                )
            }
        }
    }
}

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
    onCoverLoaded: () -> Unit,
    onClick: () -> Unit
) {
    val cover = group.cover
    // Ken Burns: when the slide is the active one, scale lerps 1.0 → 1.10
    // and pans slightly using the same progress driver so the motion lines
    // up exactly with the time the slide is on screen.
    val kenBurnsScale = 1f + (if (isActive) storyProgress else 0f) * 0.10f
    val kenBurnsPan = (if (isActive) storyProgress else 0f) * 24f

    // Cover art, gradient, keepsake corners and caption come from the shared
    // face — the same one the Recuerdos section uses. Only the motion and the
    // story chrome are the strip's own.
    MemoryCardFace(
        coverUrl = if (cover.hasThumbnails)
            "$baseUrl/api/assets/${cover.id}/thumbnail?size=Large" else null,
        contentDescription = cover.fileName,
        title = label,
        subtitle = if (group.count > 1)
            stringResource(Res.string.explore_memories_group_count, group.count) else null,
        modifier = Modifier
            .fillMaxSize()
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
            },
        imageModifier = Modifier
            .graphicsLayer {
                scaleX = kenBurnsScale
                scaleY = kenBurnsScale
                translationX = -kenBurnsPan
            },
        onCoverLoaded = onCoverLoaded,
    ) {
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
