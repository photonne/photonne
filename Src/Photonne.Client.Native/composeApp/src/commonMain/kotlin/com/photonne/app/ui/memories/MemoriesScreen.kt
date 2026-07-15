package com.photonne.app.ui.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.MemoryDetail
import com.photonne.app.data.models.MemoryKind
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_memories_empty
import com.photonne.app.resources.memories_section_favorites
import com.photonne.app.resources.memories_section_people
import com.photonne.app.resources.memories_section_places
import com.photonne.app.resources.memories_section_this_month
import com.photonne.app.resources.memories_section_today
import com.photonne.app.resources.memories_section_trips
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Recuerdos section: every generated memory, not just today's anniversaries.
 *
 * This is what makes the timeline strip's "Ver todo" honest. Before, it opened a
 * vertical list of the exact same groups the strip was already paging through —
 * same ViewModel, same grouping, no extra content.
 */
@Composable
fun MemoriesScreen(
    viewModel: MemoryFeedViewModel,
    baseUrl: String,
    onOpenMemory: (MemoryDetail) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        if (state.sections.isEmpty() && !state.isLoading && !state.attempted) viewModel.refresh()
    }

    PhotonneRefreshableScreen(
        isRefreshing = state.isLoading && state.sections.isNotEmpty(),
        onRefresh = viewModel::refresh
    ) {
        when {
            state.isLoading && state.sections.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.error?.userMessage != null && state.sections.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error?.userMessage!!, color = MaterialTheme.colorScheme.error)
                }

            state.sections.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.explore_memories_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                for (section in state.sections) {
                    sectionTitleOf(section.id)?.let { header ->
                        item(key = "header:${section.id.name}") {
                            SectionHeader(stringResource(header))
                        }
                    }
                    items(
                        items = section.memories,
                        key = { memory -> "memory:${memory.id}" }
                    ) { memory ->
                        MemoryCard(
                            memory = memory,
                            baseUrl = baseUrl,
                            isOpening = state.openingId == memory.id,
                            onClick = { viewModel.open(memory.id, onOpenMemory) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun MemoryCard(
    memory: Memory,
    baseUrl: String,
    isOpening: Boolean,
    onClick: () -> Unit,
) {
    // Same proportions as the strip's story card, so a memory looks like itself
    // whichever surface you meet it on.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val cardHeight = maxWidth * 0.62f
        MemoryCardFace(
            coverUrl = memory.coverAssetId
                ?.let { "$baseUrl/api/assets/$it/thumbnail?size=Large" },
            contentDescription = memory.title,
            title = memory.title,
            subtitle = memory.subtitle,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clickable(enabled = !isOpening, onClick = onClick),
        ) {
            // The feed carries a cover, not the photos — opening one is a
            // round-trip, so say so rather than looking dead under the finger.
            if (isOpening) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

/**
 * Header for a section. Returns the resource rather than the resolved string:
 * this is called from LazyListScope, which isn't a composable context.
 *
 * Null for [MemorySectionId.Other] — those are kinds this build has never heard
 * of, and inventing a heading for them would be worse than letting the server's
 * own titles speak.
 */
private fun sectionTitleOf(id: MemorySectionId): StringResource? = when (id) {
    MemorySectionId.Today -> Res.string.memories_section_today
    MemorySectionId.People -> Res.string.memories_section_people
    MemorySectionId.Trips -> Res.string.memories_section_trips
    MemorySectionId.ThisMonth -> Res.string.memories_section_this_month
    MemorySectionId.Favorites -> Res.string.memories_section_favorites
    MemorySectionId.Things -> Res.string.memories_section_places
    MemorySectionId.Other -> null
}
