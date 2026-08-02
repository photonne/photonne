package com.photonne.app.ui.organize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import com.photonne.app.data.models.OrganizeSummary
import com.photonne.app.resources.organize_inbox_count_format
import com.photonne.app.ui.grid.formatLocalizedMonth
import kotlinx.datetime.LocalDate
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import com.photonne.app.resources.organize_suggestions_back
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.resources.Res
import com.photonne.app.resources.organize_inbox_empty_subtitle
import com.photonne.app.resources.organize_inbox_empty_title
import com.photonne.app.resources.organize_inbox_header
import com.photonne.app.resources.organize_inbox_title
import com.photonne.app.resources.organize_rule_title
import androidx.compose.foundation.layout.PaddingValues
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.PhotoGridScrubberOverlay
import com.photonne.app.ui.grid.chromeSelectionActive
import com.photonne.app.ui.grid.rememberAssetGridSelectionGestures
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/**
 * "Para organizar" inbox: a paged grid of the assets still sitting under
 * MobileBackup (dropped by automatic backup, not yet filed). Long-press to
 * multi-select; the selection bars (App.kt) drive the move-out action that
 * files them into a folder and removes them from here.
 */
@Composable
fun OrganizeInboxScreen(
    state: OrganizeInboxUiState,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onPickSuggestion: (com.photonne.app.data.models.OrganizeSuggestion) -> Unit = {},
    onSeeAllItems: () -> Unit = {},
    onBackToSuggestions: () -> Unit = {},
    onApplySelection: (SelectionPatch) -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val apiBaseUrl = rememberApiBaseUrl()
    // Fuente de blur del cromo: la rejilla que scrollea por detrás, de la que
    // las cápsulas son HERMANAS — la regla de Haze.
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    // Con una selección activa manda la cápsula sólida del Scaffold, que YA
    // empuja la rejilla hacia abajo: reservar además el hueco del cromo
    // flotante dejaría una banda muerta del doble de alta.
    val gestures = rememberAssetGridSelectionGestures(onApplySelection)
    val chromeFloating = !gestures.chromeSelectionActive(state.isSelectionActive)
    val reservedTop = if (chromeFloating) subscreenChromeReservedTop() else 0.dp

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
        // El cromo envuelve todas las ramas: las de carga / vacío también
        // necesitan su barra (y su botón de volver).
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading && state.items.isEmpty() ->
                    AssetGridSkeleton(
                        contentPadding = PaddingValues(
                            top = reservedTop,
                            bottom = floatingNavBarReservedHeight()
                        )
                    )
                state.error != null && state.items.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .padding(top = reservedTop)
                            .padding(Spacing.lg)
                    ) {
                        ErrorBanner(error = state.error, onRetry = onRefresh)
                    }
                // Los lotes son la puerta de entrada; la rejilla plana queda
                // como salida para lo que no cubran.
                !state.showAllItems && state.suggestions.isNotEmpty() ->
                    SuggestedBatchesList(
                        suggestions = state.suggestions,
                        baseUrl = apiBaseUrl,
                        contentPadding = PaddingValues(
                            top = reservedTop,
                            bottom = floatingNavBarReservedHeight()
                        ),
                        onPick = onPickSuggestion,
                        onSeeAll = onSeeAllItems,
                        header = { InboxHeader(summary = state.summary) },
                    )
                state.isEmpty ->
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(Res.string.organize_inbox_empty_title),
                        subtitle = stringResource(Res.string.organize_inbox_empty_subtitle)
                    )
                else ->
                    AssetGrid(
                        items = state.items,
                        baseUrl = apiBaseUrl,
                        gridState = gridState,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        selectedIds = state.selection,
                        hasMore = state.hasMore,
                        isAppending = state.isAppending,
                        isInitialLoading = state.isInitialLoading,
                        onLoadMore = onLoadMore,
                        dragSelect = gestures.dragSelect,
                        contentPadding = PaddingValues(
                            top = reservedTop,
                            bottom = floatingNavBarReservedHeight()
                        ),
                        modifier = Modifier.fillMaxWidth().hazeSource(hazeState),
                        header = { InboxHeader(summary = state.summary) }
                    )
            }

            if (state.showAllItems || state.suggestions.isEmpty()) PhotoGridScrubberOverlay(
                gridState = gridState,
                items = state.items,
                headerCount = 1,
                reservedTop = reservedTop,
                reservedBottom = floatingNavBarReservedHeight(),
                selectionActive = state.isSelectionActive,
                hazeState = hazeState,
            )

            if (chromeFloating) {
                SubscreenFloatingChrome(
                    title = stringResource(Res.string.organize_inbox_title),
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
                    // Fotos a sangre bajo la status bar: sin scrim el reloj se
                    // pierde sobre según qué miniatura.
                    statusBarScrim = true,
                    actions = {
                        if (state.showAllItems && state.suggestions.isNotEmpty()) {
                            IconButton(onClick = onBackToSuggestions) {
                                Icon(
                                    Icons.Outlined.AutoAwesomeMosaic,
                                    contentDescription = stringResource(
                                        Res.string.organize_suggestions_back
                                    )
                                )
                            }
                        }
                        IconButton(onClick = onOpenRules) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = stringResource(
                                    Res.string.organize_rule_title
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

/** Cells scrolled past before the back-to-top pill appears (~3 rows). */
private const val SCROLL_TO_TOP_MIN_CELL = 12

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_CELL = 48

/**
 * Total real de la bandeja y tramo que cubre.
 *
 * El contador NO sale de `items.size`: la rejilla solo tiene lo paginado, así
 * que ese número subiría solo al hacer scroll. Y el tramo es lo que un contador
 * a secas no puede decir — 1.240 fotos se leen igual siendo el viaje de la
 * semana pasada que cuatro años de atraso, y eso cambia por dónde empiezas.
 */
@Composable
private fun InboxHeader(summary: OrganizeSummary?) {
    val span = remember(summary) { summary?.let { formatCaptureSpan(it.oldest, it.newest) } }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        if (summary != null && summary.count > 0) {
            Text(
                text = stringResource(Res.string.organize_inbox_count_format, summary.count),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = span ?: stringResource(Res.string.organize_inbox_header),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "mar 2024 – ago 2026", o un solo mes cuando todo cae dentro de él.
 *
 * Solo se leen los primeros 7 caracteres ("YYYY-MM") de la marca naive que
 * manda el servidor: mostramos mes y año, así que analizar la hora sería
 * exponerse a un desajuste de formato para tirarla después.
 */
private fun formatCaptureSpan(oldest: String?, newest: String?): String? {
    val from = parseYearMonth(oldest) ?: return null
    val to = parseYearMonth(newest) ?: return null
    val fromLabel = formatLocalizedMonth(from)
    val toLabel = formatLocalizedMonth(to)
    return if (fromLabel == toLabel) fromLabel else "$fromLabel – $toLabel"
}

private fun parseYearMonth(raw: String?): LocalDate? {
    if (raw == null || raw.length < 7) return null
    val year = raw.substring(0, 4).toIntOrNull() ?: return null
    val month = raw.substring(5, 7).toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return LocalDate(year, month, 1)
}
