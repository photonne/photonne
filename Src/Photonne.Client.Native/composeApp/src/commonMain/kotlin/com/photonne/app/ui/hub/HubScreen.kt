package com.photonne.app.ui.hub

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.Person
import com.photonne.app.data.models.StorageInfoDto
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.hub_action_albums
import com.photonne.app.resources.hub_action_map
import com.photonne.app.resources.hub_action_open_library
import com.photonne.app.resources.hub_action_search
import com.photonne.app.resources.hub_action_see_all
import com.photonne.app.resources.hub_action_upload
import com.photonne.app.resources.hub_facet_assets
import com.photonne.app.resources.hub_greeting_afternoon
import com.photonne.app.resources.hub_greeting_evening
import com.photonne.app.resources.hub_greeting_morning
import com.photonne.app.resources.hub_greeting_with_name
import com.photonne.app.resources.hub_person_unnamed
import com.photonne.app.resources.hub_section_explore
import com.photonne.app.resources.hub_section_memories
import com.photonne.app.resources.hub_section_people
import com.photonne.app.resources.hub_section_recents
import com.photonne.app.resources.hub_section_shortcuts
import com.photonne.app.resources.hub_totals_photos
import com.photonne.app.resources.hub_totals_storage
import com.photonne.app.resources.hub_totals_videos
import com.photonne.app.resources.timeline_memories_one_year_ago
import com.photonne.app.resources.timeline_memories_years_ago
import com.photonne.app.ui.theme.LocalCurrentDetailAssetId
import com.photonne.app.ui.theme.LocalSharedTransitionScope
import com.photonne.app.ui.theme.MemoryCardShape
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.theme.SkeletonBlock
import com.photonne.app.ui.theme.SkeletonChip
import com.photonne.app.ui.timeline.MemoryGroup
import com.photonne.app.ui.timeline.groupMemoriesByDay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

private val HubTopGap = 12.dp
private val HubBottomGap = 24.dp
private val HubSectionGap = 20.dp

@Composable
fun HubScreen(
    state: HubUiState,
    baseUrl: String,
    onRefresh: () -> Unit,
    onOpenAsset: (List<TimelineItem>, Int) -> Unit,
    onOpenMemory: (List<TimelineItem>, Int) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    onOpenFacet: (HubFacet) -> Unit
) {
    val scrollState = rememberScrollState()

    if (state.isLoading && !state.attempted) {
        HubSkeleton()
        return
    }

    PhotonneRefreshableScreen(
        isRefreshing = state.isLoading && state.attempted,
        onRefresh = onRefresh
    ) {
    // Column + verticalScroll (instead of LazyColumn) so each section is
    // composed exactly once. The hub has at most ~6 sections, so the
    // perf cost is trivial and it kills the "section pops in" feel
    // LazyColumn caused when a previously off-screen section re-entered
    // composition and re-ran its entry animation.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = HubTopGap, bottom = HubBottomGap),
        verticalArrangement = Arrangement.spacedBy(HubSectionGap)
    ) {
        var stagger = 0

        HubSectionEntry(stagger++) {
            HubGreeting(
                displayName = state.displayName,
                storage = state.storage,
                scrollOffsetPx = scrollState.value
            )
        }

        if (state.memories.isNotEmpty()) {
            HubSectionEntry(stagger++) {
                HubMemoriesStories(
                    memories = state.memories,
                    baseUrl = baseUrl,
                    onOpenMemory = onOpenMemory
                )
            }
        }

        if (state.recents.isNotEmpty()) {
            HubSectionEntry(stagger++) {
                HubRecentsRow(
                    recents = state.recents,
                    baseUrl = baseUrl,
                    onOpenAsset = onOpenAsset
                )
            }
        }

        if (state.people.isNotEmpty()) {
            HubSectionEntry(stagger++) {
                HubPeopleRow(
                    people = state.people,
                    baseUrl = baseUrl,
                    onOpenPerson = onOpenPerson,
                    onOpenPeople = onOpenPeople
                )
            }
        }

        if (state.facets.isNotEmpty()) {
            HubSectionEntry(stagger++) {
                HubExploreGrid(
                    facets = state.facets,
                    baseUrl = baseUrl,
                    onOpenFacet = onOpenFacet
                )
            }
        }

        HubSectionEntry(stagger++) {
            HubShortcuts(
                onOpenLibrary = onOpenLibrary,
                onOpenSearch = onOpenSearch,
                onOpenMap = onOpenMap,
                onOpenAlbums = onOpenAlbums,
                onOpenUpload = onOpenUpload
            )
        }
    }
    }
}

// -------------------------------------------------------------------------
// Section 1 — Greeting + storage chips with parallax
// -------------------------------------------------------------------------

@Composable
private fun HubGreeting(
    displayName: String?,
    storage: StorageInfoDto?,
    scrollOffsetPx: Int
) {
    val parallax = (scrollOffsetPx * 0.4f).coerceAtMost(120f)
    val greetingSlotRes = when (currentGreetingSlot()) {
        GreetingSlot.Morning -> Res.string.hub_greeting_morning
        GreetingSlot.Afternoon -> Res.string.hub_greeting_afternoon
        GreetingSlot.Evening -> Res.string.hub_greeting_evening
    }
    val slotText = stringResource(greetingSlotRes)
    val title = if (displayName.isNullOrBlank()) slotText
    else stringResource(Res.string.hub_greeting_with_name, slotText, displayName)

    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.22f),
                        primary.copy(alpha = 0.06f),
                        surface
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                onDrawBehind {
                    drawRect(brush)
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .graphicsLayer { translationY = -parallax },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (storage != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StoragePill(
                        text = stringResource(
                            Res.string.hub_totals_photos,
                            formatCount(storage.photos)
                        )
                    )
                    StoragePill(
                        text = stringResource(
                            Res.string.hub_totals_videos,
                            formatCount(storage.videos)
                        )
                    )
                    StoragePill(
                        text = stringResource(
                            Res.string.hub_totals_storage,
                            humanBytes(storage.usedBytes)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StoragePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// -------------------------------------------------------------------------
// Section 2 — "Hoy hace…" cinematic stories
// -------------------------------------------------------------------------

private const val StoryDurationMs = 5000L

@Composable
private fun HubMemoriesStories(
    memories: List<TimelineItem>,
    baseUrl: String,
    onOpenMemory: (List<TimelineItem>, Int) -> Unit
) {
    val zone = TimeZone.currentSystemDefault()
    val currentYear = Clock.System.now().toLocalDateTime(zone).date.year
    val groups = remember(memories) { groupMemoriesByDay(memories, zone, currentYear) }
    if (groups.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(Res.string.hub_section_memories),
            actionLabel = null,
            onAction = null
        )
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

// -------------------------------------------------------------------------
// Section 3 — Recents carousel
// -------------------------------------------------------------------------

@Composable
private fun HubRecentsRow(
    recents: List<TimelineItem>,
    baseUrl: String,
    onOpenAsset: (List<TimelineItem>, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(Res.string.hub_section_recents),
            actionLabel = null,
            onAction = null
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(recents, key = { idx, item -> "recent:$idx:${item.id}" }) { idx, item ->
                RecentTile(
                    item = item,
                    baseUrl = baseUrl,
                    onClick = { onOpenAsset(recents, idx) }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecentTile(item: TimelineItem, baseUrl: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val sharedScope = LocalSharedTransitionScope.current
    val currentDetailId = LocalCurrentDetailAssetId.current
    // Same gating as `AssetGridCell` — only register the shared element
    // while the viewer is open (or animating closed) so the Hub's tiles
    // don't churn the SharedTransitionScope on every recompose.
    val thumbnailSharedMod: Modifier = if (sharedScope != null && currentDetailId != null) {
        val sharedKey = remember(item.id) { "asset-${item.id}" }
        with(sharedScope) {
            Modifier.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(key = sharedKey),
                visible = currentDetailId != item.id,
                boundsTransform = { _, _ ->
                    androidx.compose.animation.core.tween(durationMillis = 320)
                }
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(width = 116.dp, height = 116.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .hubPressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        if (item.hasThumbnails) {
            AsyncImage(
                model = "$baseUrl/api/assets/${item.id}/thumbnail?size=Small",
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(thumbnailSharedMod)
            )
        }
    }
}

// -------------------------------------------------------------------------
// Section 4 — People with animated gold ring
// -------------------------------------------------------------------------

@Composable
private fun HubPeopleRow(
    people: List<Person>,
    baseUrl: String,
    onOpenPerson: (Person) -> Unit,
    onOpenPeople: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(Res.string.hub_section_people),
            actionLabel = stringResource(Res.string.hub_action_see_all),
            onAction = onOpenPeople
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(people, key = { it.id }) { person ->
                PersonAvatar(
                    person = person,
                    baseUrl = baseUrl,
                    onClick = { onOpenPerson(person) }
                )
            }
        }
    }
}

@Composable
private fun PersonAvatar(person: Person, baseUrl: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val infinite = rememberInfiniteTransition()
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val unnamed = stringResource(Res.string.hub_person_unnamed)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .hubPressScale(interaction)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            val ringBrush = Brush.sweepGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    MaterialTheme.colorScheme.primary
                )
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ringBrush)
                    .graphicsLayer { rotationZ = angle }
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                person.coverFaceId?.let { faceId ->
                    AsyncImage(
                        model = "$baseUrl/api/faces/$faceId/thumbnail",
                        contentDescription = person.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Text(
            text = (person.name?.takeIf { it.isNotBlank() }) ?: unnamed,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 84.dp)
        )
    }
}

// -------------------------------------------------------------------------
// Section 5 — Explore facets 2-col grid (built from LazyColumn rows)
// -------------------------------------------------------------------------

@Composable
private fun HubExploreGrid(
    facets: List<HubFacet>,
    baseUrl: String,
    onOpenFacet: (HubFacet) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(Res.string.hub_section_explore),
            actionLabel = null,
            onAction = null
        )
        Spacer(modifier = Modifier.height(10.dp))
        val pairs = facets.chunked(2)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            pairs.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { facet ->
                        Box(modifier = Modifier.weight(1f)) {
                            FacetCard(
                                facet = facet,
                                baseUrl = baseUrl,
                                onClick = { onOpenFacet(facet) }
                            )
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FacetCard(facet: HubFacet, baseUrl: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .hubPressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        if (facet.coverHasThumbnail && facet.coverAssetId != null) {
            AsyncImage(
                model = "$baseUrl/api/assets/${facet.coverAssetId}/thumbnail?size=Small",
                contentDescription = facet.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Bottom gradient + label overlay so the text is readable even on
        // images with bright lower halves (sky/snow/beach).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = facet.label.replaceFirstChar { it.uppercase() },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(Res.string.hub_facet_assets, facet.assetCount),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// -------------------------------------------------------------------------
// Section 6 — Quick shortcuts
// -------------------------------------------------------------------------

@Composable
private fun HubShortcuts(
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenUpload: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(Res.string.hub_section_shortcuts),
            actionLabel = null,
            onAction = null
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "All photos" is intentionally the first shortcut: it's the
            // primary navigation target and must always be reachable, even
            // if recents/memories/etc. fail to load.
            ShortcutChip(
                label = stringResource(Res.string.hub_action_open_library),
                icon = Icons.Outlined.PhotoLibrary,
                onClick = onOpenLibrary
            )
            ShortcutChip(
                label = stringResource(Res.string.hub_action_search),
                icon = Icons.Outlined.Search,
                onClick = onOpenSearch
            )
            ShortcutChip(
                label = stringResource(Res.string.hub_action_map),
                icon = Icons.Outlined.Place,
                onClick = onOpenMap
            )
            ShortcutChip(
                label = stringResource(Res.string.hub_action_albums),
                icon = Icons.Outlined.Collections,
                onClick = onOpenAlbums
            )
            ShortcutChip(
                label = stringResource(Res.string.hub_action_upload),
                icon = Icons.Outlined.AddPhotoAlternate,
                onClick = onOpenUpload
            )
        }
    }
}

@Composable
private fun ShortcutChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// -------------------------------------------------------------------------
// Shared section header
// -------------------------------------------------------------------------

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            SuggestionChip(
                onClick = onAction,
                label = {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

// -------------------------------------------------------------------------
// Initial-load skeleton — sketches the same section shapes the hub will
// fill in once data lands, so the layout doesn't jump on first paint.
// -------------------------------------------------------------------------

@Composable
private fun HubSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = HubTopGap, bottom = HubBottomGap),
        verticalArrangement = Arrangement.spacedBy(HubSectionGap)
    ) {
        // Greeting hero
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(160.dp),
            cornerRadius = 24.dp
        )
        // Memories story
        Column(modifier = Modifier.fillMaxWidth()) {
            SkeletonSectionHeader()
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cardWidth = maxWidth - 32.dp
                SkeletonBlock(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(cardWidth * 0.62f),
                    cornerRadius = 24.dp
                )
            }
        }
        // Recents row
        Column(modifier = Modifier.fillMaxWidth()) {
            SkeletonSectionHeader()
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    SkeletonBlock(
                        modifier = Modifier.size(width = 116.dp, height = 116.dp),
                        cornerRadius = 14.dp
                    )
                }
            }
        }
        // People row (circles)
        Column(modifier = Modifier.fillMaxWidth()) {
            SkeletonSectionHeader()
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(5) {
                    SkeletonBlock(
                        modifier = Modifier.size(72.dp),
                        cornerRadius = 36.dp
                    )
                }
            }
        }
        // Explore 2-col grid
        Column(modifier = Modifier.fillMaxWidth()) {
            SkeletonSectionHeader()
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(2) {
                            SkeletonBlock(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.4f),
                                cornerRadius = 18.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonChip(width = 140.dp, height = 18.dp)
    }
}
