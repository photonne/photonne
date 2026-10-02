package com.photonne.app.ui.memories

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.ui.theme.PhotonneColors
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.MemoryDetail
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_memories_group_count
import com.photonne.app.resources.explore_section_memories
import com.photonne.app.resources.memories_empty_dated
import com.photonne.app.resources.memories_header_today
import com.photonne.app.resources.memories_header_through_years
import com.photonne.app.resources.memories_section_favorites
import com.photonne.app.resources.memories_section_people
import com.photonne.app.resources.memories_section_places
import com.photonne.app.resources.memories_section_this_month
import com.photonne.app.resources.memories_section_today
import com.photonne.app.resources.memories_section_trips
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.theme.contentWidth
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.CardRowsSkeleton
import com.photonne.app.ui.theme.Spacing

/** Square and 12 dp apart, like a Scenes / Objects tile on a phone, so the
 * three grids of covers read as one family. Three whole cards fit even at 360 dp
 * (16 + 3·100 + 2·12 = 340), with the fourth peeking in to say "this row keeps
 * going". */
internal val RowCardSize = 100.dp

/**
 * Proporción de las tarjetas de Recuerdos: apaisadas, la de la antigua tira de
 * Fotos (alto = 0,62 × ancho). En 4:5 cada una llenaba la pantalla.
 */
private const val BigCardAspect = 1f / 0.62f

/** Tope de ancho de una tarjeta, como la tira: en tablet no crece sin límite. */
private val BigCardMaxWidth = 560.dp

/** Parte del ancho que ocupa cada tarjeta de un carrusel; el resto deja asomar la siguiente. */
private const val CarouselCardFraction = 0.86f

/** Lo que dura una pasada del zoom lento de las tarjetas grandes (ida o vuelta). */
private const val KenBurnsMillis = 14_000

/**
 * Recuerdos: los recorridos en el tiempo, en tres bloques — Hoy, Este mes y
 * "A lo largo de los años" (una persona a través de los años). Los temas
 * (viajes, playa…) viven en Explorar y las parejas, en la ficha de cada persona.
 *
 * Cada bloque es un carrusel de tarjetas grandes y apaisadas (la forma de la
 * antigua tira de Fotos, con su zoom lento) en las que asoma la siguiente: con
 * los bloques apilados en vertical, "A lo largo de los años" quedaba dos o tres
 * pantallas más abajo. Así se ven los tres en poco más de una.
 */
@Composable
fun MemoriesScreen(
    viewModel: MemoryFeedViewModel,
    baseUrl: String,
    onOpenMemory: (MemoryDetail) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    MemoryFeedScaffold(
        title = stringResource(Res.string.explore_section_memories),
        viewModel = viewModel,
        onOpenMemory = onOpenMemory,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        isEmpty = { it.recuerdos.isEmpty() },
        emptyTitle = stringResource(Res.string.memories_empty_dated),
    ) { state, open ->
        val blocks = state.recuerdos.groupBy { recuerdosBlockOf(it) }
        RecuerdosBlock.entries.forEach { block ->
            val memories = blocks[block].orEmpty()
            if (memories.isEmpty()) return@forEach
            item(key = "block:${block.name}") {
                RecuerdosCarousel(
                    title = stringResource(
                        when (block) {
                            RecuerdosBlock.Today -> Res.string.memories_header_today
                            RecuerdosBlock.ThisMonth -> Res.string.memories_section_this_month
                            RecuerdosBlock.ThroughYears -> Res.string.memories_header_through_years
                        }
                    ),
                    memories = memories,
                    // Bajo "A lo largo de los años" basta el nombre; el título
                    // del servidor lo repetiría.
                    useCardLabel = block == RecuerdosBlock.ThroughYears,
                    baseUrl = baseUrl,
                    openingId = state.openingId,
                    onClick = open,
                )
            }
        }
    }
}

@Composable
private fun RecuerdosCarousel(
    title: String,
    memories: List<Memory>,
    useCardLabel: Boolean,
    baseUrl: String,
    openingId: String?,
    onClick: (Memory) -> Unit,
) {
    Column(modifier = Modifier.contentWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = Spacing.lg, end = Spacing.lg, top = 20.dp, bottom = Spacing.sm)
                .semantics { heading() }
        )
        if (memories.size == 1) {
            // Una sola: a todo el ancho, sin carrusel que no lleva a nada.
            val memory = memories.single()
            BigMemoryCard(
                memory = memory,
                title = if (useCardLabel) memory.cardLabel ?: memory.title else memory.title,
                baseUrl = baseUrl,
                isOpening = openingId == memory.id,
                onClick = { onClick(memory) },
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .widthIn(max = BigCardMaxWidth)
                    .fillMaxWidth()
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(memories, key = { "memory:${it.id}" }) { memory ->
                    BigMemoryCard(
                        memory = memory,
                        title = if (useCardLabel) memory.cardLabel ?: memory.title else memory.title,
                        baseUrl = baseUrl,
                        isOpening = openingId == memory.id,
                        onClick = { onClick(memory) },
                        // Asoma la siguiente: dice "desliza" sin decirlo.
                        modifier = Modifier
                            .fillParentMaxWidth(CarouselCardFraction)
                            .widthIn(max = BigCardMaxWidth)
                    )
                }
            }
        }
    }
}

@Composable
internal fun BigMemoryCard(
    memory: Memory,
    baseUrl: String,
    isOpening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = memory.title,
) {
    // Zoom lento de ida y vuelta, leído en la fase de dibujo (graphicsLayer):
    // no recompone la tarjeta en cada fotograma.
    val transition = rememberInfiniteTransition(label = "kenburns")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(KenBurnsMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kenburns-scale"
    )
    MemoryCardFace(
        coverUrl = memory.coverAssetId?.let { "$baseUrl/api/assets/$it/thumbnail?size=Large" },
        contentDescription = memory.title,
        title = title,
        subtitle = memory.subtitle
            ?: stringResource(Res.string.explore_memories_group_count, memory.assetCount),
        imageModifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        modifier = modifier
            .aspectRatio(BigCardAspect)
            .clickable(enabled = !isOpening, onClick = onClick),
    ) {
        if (isOpening) OpeningScrim()
    }
}

/**
 * Andamio común de Recuerdos y Explorar: carga del feed, esqueleto, error,
 * vacío y cromo flotante. [content] pinta la lista con lo que toque a cada una;
 * su `open` abre un recuerdo (es una petición: el feed solo trae la portada).
 */
@Composable
internal fun MemoryFeedScaffold(
    title: String,
    viewModel: MemoryFeedViewModel,
    onOpenMemory: (MemoryDetail) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit,
    isEmpty: (MemoryFeedUiState) -> Boolean,
    emptyTitle: String,
    onRefresh: () -> Unit = viewModel::refresh,
    content: LazyListScope.(MemoryFeedUiState, (Memory) -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = com.photonne.app.ui.main.LocalSnackbarController.current
    // Fuente de blur del cromo: la lista que scrollea por detrás, de la que las
    // cápsulas son HERMANAS — la regla de Haze.
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val reservedTop = subscreenChromeReservedTop()
    LaunchedEffect(Unit) {
        // Un intento fallido no cuenta como cargado: volver a entrar reintenta.
        val blocked = state.attempted && state.error == null
        if (state.rows.isEmpty() && !state.isLoading && !blocked) viewModel.refresh()
    }
    val open: (Memory) -> Unit = { memory ->
        viewModel.open(
            memoryId = memory.id,
            onError = { error -> snackbar?.show(error.userMessage) },
            onLoaded = onOpenMemory
        )
    }

    PhotonneRefreshableScreen(
        indicatorTopPadding = reservedTop,
        isRefreshing = state.isLoading && state.rows.isNotEmpty(),
        onRefresh = onRefresh
    ) {
        // El cromo envuelve todas las ramas: las de carga / error / vacío
        // también necesitan su barra (y su botón de volver).
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.rows.isEmpty() ->
                    CardRowsSkeleton(
                        contentPadding = PaddingValues(top = reservedTop),
                        cardWidth = RowCardSize,
                        cardHeight = RowCardSize,
                        cardSpacing = Spacing.md
                    )

                state.error != null && state.rows.isEmpty() ->
                    com.photonne.app.ui.error.FullScreenError(
                        error = state.error,
                        onRetry = viewModel::refresh,
                        modifier = Modifier.padding(top = reservedTop)
                    )

                isEmpty(state) ->
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = emptyTitle,
                        modifier = Modifier.fillMaxSize(),
                    )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(
                        // Reserva el cromo flotante: el contenido pasa por debajo al
                        // scrollear, pero en reposo lo primero no queda escondido.
                        top = 8.dp + reservedTop,
                        bottom = 24.dp + floatingNavBarReservedHeight()
                    ),
                ) {
                    content(state, open)
                }
            }

            // Sin acciones: una sola cápsula (volver + título).
            SubscreenFloatingChrome(
                title = title,
                onBack = onBack,
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                    firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { listState.isScrollInProgress },
                    scrollToTopMinIndex = 2,
                    onScrollToTop = { listState.animateScrollToItem(0) }
                ),
                hazeState = hazeState,
                onChromeVisibleChange = onChromeVisibleChange
            )
        }
    }
}

/** Título de una fila de tema: el del servidor o, si no mandó, el de la sección. */
@Composable
internal fun MemoryRow.displayTitle(): String =
    title.ifEmpty { sectionId?.let { sectionTitleOf(it) }?.let { stringResource(it) }.orEmpty() }

@Composable
internal fun MemoryThemeRow(
    row: MemoryRow,
    baseUrl: String,
    openingId: String?,
    onClick: (Memory) -> Unit,
    header: String = row.displayTitle(),
) {
    // Every row gets a header one way or the other: a strip of covers with
    // nothing above it says nothing about itself.
    if (header.isNotEmpty()) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, top = 20.dp, bottom = Spacing.sm)
                .semantics { heading() },
        )
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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
internal fun MemoryRowCard(
    memory: Memory,
    baseUrl: String,
    isOpening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(RowCardSize),
) {
    MemoryCardFace(
        coverUrl = memory.coverAssetId
            // Medium basta para una tarjeta de 100x100 dp; Large descargaba
            // el tamaño de visor para una miniatura.
            ?.let { "$baseUrl/api/assets/$it/thumbnail?size=Medium" },
        contentDescription = memory.title,
        // The row already says "Días de playa"; the card says which year. Both
        // strings come from the server — neither is assembled here.
        title = memory.cardLabel ?: memory.title,
        subtitle = null,
        compact = true,
        modifier = modifier.clickable(enabled = !isOpening, onClick = onClick),
    ) {
        // The feed carries a cover, not the photos — opening one is a
        // round-trip, so say so rather than looking dead under the finger.
        if (isOpening) OpeningScrim()
    }
}

@Composable
private fun OpeningScrim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PhotonneColors.scrimLight),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
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
internal fun sectionTitleOf(id: MemorySectionId): StringResource? = when (id) {
    MemorySectionId.Today -> Res.string.memories_section_today
    MemorySectionId.People -> Res.string.memories_section_people
    MemorySectionId.Trips -> Res.string.memories_section_trips
    MemorySectionId.ThisMonth -> Res.string.memories_section_this_month
    MemorySectionId.Favorites -> Res.string.memories_section_favorites
    MemorySectionId.Things -> Res.string.memories_section_places
    MemorySectionId.Other -> null
}
