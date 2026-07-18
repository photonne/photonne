package com.photonne.app.ui.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.MemoryDetail
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_memories_empty
import com.photonne.app.resources.explore_section_memories
import com.photonne.app.resources.memories_section_favorites
import com.photonne.app.resources.memories_section_people
import com.photonne.app.resources.memories_section_places
import com.photonne.app.resources.memories_section_this_month
import com.photonne.app.resources.memories_section_today
import com.photonne.app.resources.memories_section_trips
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Wide enough to read a cover, narrow enough that the next one peeks in and
 * says "this row keeps going". */
private val RowCardWidth = 150.dp
private val RowCardHeight = 190.dp

/**
 * The Recuerdos section: every generated memory, as one row per theme.
 *
 * It used to be fifty full-width cards in a column, ordered by the server's
 * score. The themes were already there — "Días de playa de 2024" and "Días de
 * playa de 2021" say so — but they were scattered down the scroll, so getting
 * back to one you saw yesterday was luck. Now the theme is the row, its years run
 * across it, and the rows keep their order between runs.
 */
@Composable
fun MemoriesScreen(
    viewModel: MemoryFeedViewModel,
    baseUrl: String,
    onOpenMemory: (MemoryDetail) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    // Fuente de blur del cromo: la lista que scrollea por detrás, de la que las
    // cápsulas son HERMANAS — la regla de Haze.
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val reservedTop = subscreenChromeReservedTop()
    LaunchedEffect(Unit) {
        if (state.rows.isEmpty() && !state.isLoading && !state.attempted) viewModel.refresh()
    }

    PhotonneRefreshableScreen(
        isRefreshing = state.isLoading && state.rows.isNotEmpty(),
        onRefresh = viewModel::refresh
    ) {
        // El cromo envuelve todas las ramas: las de carga / error / vacío
        // también necesitan su barra (y su botón de volver).
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.rows.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.error != null && state.rows.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize().padding(top = reservedTop).padding(24.dp)) {
                        com.photonne.app.ui.error.ErrorBanner(error = state.error)
                    }

                state.rows.isEmpty() ->
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(Res.string.explore_memories_empty),
                        modifier = Modifier.fillMaxSize(),
                    )

                // The top padding is the screen's own: a row's breathing room comes
                // from its header, so a headerless row would otherwise sit its cards
                // flush against the title bar.
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    contentPadding = PaddingValues(
                        // Reserva el cromo flotante: las filas pasan por debajo al
                        // scrollear, pero en reposo la primera no queda escondida.
                        top = 8.dp + reservedTop,
                        bottom = 24.dp + floatingNavBarReservedHeight()
                    ),
                ) {
                    items(
                        items = state.rows,
                        key = { row -> "row:${row.key}" },
                    ) { row ->
                        MemoryThemeRow(
                            row = row,
                            baseUrl = baseUrl,
                            openingId = state.openingId,
                            onClick = { memory -> viewModel.open(memory.id, onOpenMemory) },
                        )
                    }
                }
            }

            // Sin acciones: una sola cápsula (volver + título).
            SubscreenFloatingChrome(
                title = stringResource(Res.string.explore_section_memories),
                onBack = onBack,
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                    firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { listState.isScrollInProgress },
                    // Filas de tema, no celdas: cada una ocupa media pantalla.
                    scrollToTopMinIndex = 2,
                    onScrollToTop = { listState.animateScrollToItem(0) }
                ),
                hazeState = hazeState,
                onChromeVisibleChange = onChromeVisibleChange
            )
        }
    }
}

@Composable
private fun MemoryThemeRow(
    row: MemoryRow,
    baseUrl: String,
    openingId: String?,
    onClick: (Memory) -> Unit,
) {
    // The server's theme title, or — when it didn't send one — this build's name
    // for the section the cards fall in. Every row gets a header one way or the
    // other: a strip of covers with nothing above it says nothing about itself.
    val header = row.title.ifEmpty {
        row.sectionId?.let { sectionTitleOf(it) }?.let { stringResource(it) }.orEmpty()
    }
    if (header.isNotEmpty()) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = row.memories, key = { memory -> "memory:${memory.id}" }) { memory ->
            MemoryRowCard(
                memory = memory,
                baseUrl = baseUrl,
                isOpening = openingId == memory.id,
                onClick = { onClick(memory) },
            )
        }
    }
}

@Composable
private fun MemoryRowCard(
    memory: Memory,
    baseUrl: String,
    isOpening: Boolean,
    onClick: () -> Unit,
) {
    MemoryCardFace(
        coverUrl = memory.coverAssetId
            ?.let { "$baseUrl/api/assets/$it/thumbnail?size=Large" },
        contentDescription = memory.title,
        // The row already says "Días de playa"; the card says which year. Both
        // strings come from the server — neither is assembled here.
        title = memory.cardLabel ?: memory.title,
        subtitle = null,
        modifier = Modifier
            .width(RowCardWidth)
            .height(RowCardHeight)
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

/**
 * This build's name for a section, used only when the server sent no theme title.
 * These are the app's own groupings — not the server's catalogue of themes — so
 * naming them here is the one place the client gets to write the copy.
 *
 * Null for [MemorySectionId.Other]: kinds this build has never heard of. There is
 * genuinely nothing to call those.
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
