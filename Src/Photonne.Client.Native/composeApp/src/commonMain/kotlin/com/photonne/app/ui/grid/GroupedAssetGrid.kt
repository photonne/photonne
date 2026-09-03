package com.photonne.app.ui.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import com.photonne.app.ui.grid.dragselect.DragSelectConfig
import com.photonne.app.ui.grid.dragselect.DragSelectState
import com.photonne.app.ui.grid.dragselect.dragSelectable
import com.photonne.app.ui.grid.dragselect.rememberTimelineDragSelectAdapter
import com.photonne.app.ui.haptics.rememberPhotonneHaptics
import com.photonne.app.ui.selection.GroupSelectionState
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.selectionStateOf
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.timeline.captureLocalDate
import com.photonne.app.resources.Res
import com.photonne.app.resources.selection_group_clear
import com.photonne.app.resources.selection_group_select
import com.photonne.app.resources.timeline_year_count
import com.photonne.app.ui.theme.SkeletonBlock
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Sentinel returned by the server for assets that exist on disk but
 * haven't been indexed yet (timeline endpoint, "Copied" sync status).
 * Multiple such rows can come back in the same page, so the grid must
 * fall back to a path-based key to avoid duplicate-key crashes.
 */
private const val EMPTY_ASSET_ID = "00000000-0000-0000-0000-000000000000"

internal fun assetCellKey(item: TimelineItem, index: Int): String =
    if (item.id.isEmpty() || item.id == EMPTY_ASSET_ID) {
        "u:$index:${item.fullPath}"
    } else item.id

internal sealed interface TimelineEntry {
    /** [count] is shown trailing the title (Year view's per-year total). */
    data class Header(val key: String, val title: String, val count: Int? = null) : TimelineEntry
    data class Cell(val item: TimelineItem, val index: Int) : TimelineEntry

    /**
     * Stand-in for an unloaded bucket (docs/timeline-buckets.md): packs into
     * [count]-many square skeleton cells so the month's scroll height is
     * reserved deterministically before its content arrives.
     */
    data class SkeletonBucket(val bucketKey: String, val count: Int) : TimelineEntry
}

internal fun monthKeyOf(instant: Instant): String {
    val date = instant.captureLocalDate()
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
}

internal fun monthLabelOf(instant: Instant): String {
    val date = instant.captureLocalDate()
    return formatLocalizedMonth(date)
}

internal fun dayKeyOf(instant: Instant): String {
    val date = instant.captureLocalDate()
    val mm = date.monthNumber.toString().padStart(2, '0')
    val dd = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$mm-$dd"
}

internal fun dayLabelOf(instant: Instant): String {
    val date = instant.captureLocalDate()
    return formatLocalizedDay(date)
}

internal fun yearKeyOf(instant: Instant): String =
    instant.captureLocalDate().year.toString()

internal fun yearLabelOf(instant: Instant): String = yearKeyOf(instant)

private fun keyOf(instant: Instant, grouping: TimelineGrouping): String = when (grouping) {
    TimelineGrouping.Year -> yearKeyOf(instant)
    TimelineGrouping.Month -> monthKeyOf(instant)
    TimelineGrouping.Day -> dayKeyOf(instant)
}

private fun labelOf(instant: Instant, grouping: TimelineGrouping): String = when (grouping) {
    TimelineGrouping.Year -> yearLabelOf(instant)
    TimelineGrouping.Month -> monthLabelOf(instant)
    TimelineGrouping.Day -> dayLabelOf(instant)
}

internal fun groupTimelineEntries(
    items: List<TimelineItem>,
    grouping: TimelineGrouping = TimelineGrouping.Month
): List<TimelineEntry> {
    if (items.isEmpty()) return emptyList()
    val out = ArrayList<TimelineEntry>(items.size + 8)
    var lastKey: String? = null
    items.forEachIndexed { index, item ->
        val key = keyOf(item.fileCreatedAt, grouping)
        if (key != lastKey) {
            out += TimelineEntry.Header(
                key = key,
                title = labelOf(item.fileCreatedAt, grouping)
            )
            lastKey = key
        }
        out += TimelineEntry.Cell(item = item, index = index)
    }
    return out
}

/**
 * Merges server timeline items with locally-known device items (both
 * already typed as [TimelineItem] — the latter carry
 * `localUri`/`localThumbnailModel`). The merge:
 *
 * 1. Dedups local items against server items — by established server
 *    assetId first (the identity map's ground truth), then by SHA-256
 *    (when both sides know it) and, as a last resort, by
 *    `(fileName, fileSize)`. This stops device entries from
 *    double-rendering when the server already has the photo.
 * 2. Interleaves the survivors with the server list, preserving
 *    descending `fileCreatedAt` order so click callbacks resolve back
 *    to the right entry via the merged list's index.
 */
internal fun mergeTimelineWithLocal(
    server: List<TimelineItem>,
    local: List<TimelineItem>
): List<TimelineItem> {
    if (local.isEmpty()) return server
    val serverIds = server.mapTo(HashSet()) { it.id }
    val serverChecksums = server.mapNotNullTo(HashSet()) {
        it.checksum?.takeIf { c -> c.isNotBlank() }
    }
    val serverDedupKeys = server.mapTo(HashSet()) { dedupKey(it.fileName, it.fileSize) }
    // Last-resort key for SIZELESS locals (iOS: PhotoKit exposes neither
    // file size nor real filename cheaply, so the two keys above can never
    // match, and an iCloud-offloaded original can't even be hashed for a
    // checksum): capture second + pixel dimensions. Both sides speak the
    // naive-local time frame, so the seconds line up. Only emitted when
    // the dimensions are known — second-plus-null-dims would false-match.
    val serverMoments = server.mapNotNullTo(HashSet()) { momentKey(it) }
    val dedupedLocal = local.filter { item ->
        val assetId = item.localServerAssetId
        if (assetId != null && assetId in serverIds) return@filter false
        val checksum = item.checksum
        if (!checksum.isNullOrBlank() && checksum in serverChecksums) return@filter false
        if (item.fileSize == 0L && momentKey(item)?.let { it in serverMoments } == true) {
            return@filter false
        }
        dedupKey(item.fileName, item.fileSize) !in serverDedupKeys
    }
    if (dedupedLocal.isEmpty()) return server
    if (server.isEmpty()) return dedupedLocal.sortedByDescending { it.fileCreatedAt }
    // Both lists are descending; a manual merge keeps it O(n+m).
    val merged = ArrayList<TimelineItem>(server.size + dedupedLocal.size)
    var i = 0
    var j = 0
    val localSorted = dedupedLocal.sortedByDescending { it.fileCreatedAt }
    while (i < server.size && j < localSorted.size) {
        if (server[i].fileCreatedAt >= localSorted[j].fileCreatedAt) {
            merged += server[i]; i++
        } else {
            merged += localSorted[j]; j++
        }
    }
    while (i < server.size) { merged += server[i]; i++ }
    while (j < localSorted.size) { merged += localSorted[j]; j++ }
    return merged
}

private fun dedupKey(fileName: String, fileSize: Long): String = "$fileName|$fileSize"

/** (capture second, width, height, kind) — null when dimensions are unknown. */
private fun momentKey(item: TimelineItem): String? {
    val width = item.width ?: return null
    val height = item.height ?: return null
    return "${item.fileCreatedAt.epochSeconds}|$width|$height|${item.isVideo}"
}

/**
 * Finds the grid-entry index of the header that matches [target] (or the
 * closest older bucket) for the given [grouping]. Used both by the
 * "jump to date" affordance and to re-anchor the grid when the user
 * switches zoom levels. Returns -1 when the timeline is empty or no
 * matching header exists.
 */
internal fun findEntryIndexForDate(
    entries: List<TimelineEntry>,
    target: LocalDate,
    grouping: TimelineGrouping
): Int {
    if (entries.isEmpty()) return -1
    val targetKey = targetKeyFor(target, grouping)
    var fallback = -1
    entries.forEachIndexed { index, entry ->
        if (entry is TimelineEntry.Header) {
            if (entry.key == targetKey) return index
            // Headers are emitted in newest-first order (timeline descends).
            // We pick the first header whose key is older or equal as a fallback.
            if (fallback == -1 && entry.key <= targetKey) fallback = index
        }
    }
    return fallback
}

private fun targetKeyFor(target: LocalDate, grouping: TimelineGrouping): String {
    val mm = target.monthNumber.toString().padStart(2, '0')
    val dd = target.dayOfMonth.toString().padStart(2, '0')
    return when (grouping) {
        TimelineGrouping.Year -> target.year.toString()
        TimelineGrouping.Month -> "${target.year}-$mm"
        TimelineGrouping.Day -> "${target.year}-$mm-$dd"
    }
}

/** Backwards-compatible Month-grouping shim for the jump-to-date flow. */
internal fun findEntryIndexForMonth(entries: List<TimelineEntry>, target: LocalDate): Int =
    findEntryIndexForDate(entries, target, TimelineGrouping.Month)

/**
 * Renders the pre-packed [rows] as a uniform square grid. Every cell in a
 * row shares the row's height and an equal width weight, so all tiles are
 * the same square size; a partial trailing row reserves empty slots rather
 * than stretching its cells.
 *
 * Month/year headers between groups are emitted via [stickyHeader] so the
 * native sticky behavior takes over — the current section's header pins
 * to the top until the next section's header pushes it up. This avoids
 * the previous overlay approach occluding the first row of each new
 * group.
 *
 * Internal indices stay in the original `items` order — click callbacks
 * resolve against whatever list the caller used to build the entries.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupedAssetGrid(
    rows: List<TimelineRowEntry>,
    baseUrl: String,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    selectedIds: Set<String> = emptySet(),
    onItemLongClick: ((Int) -> Unit)? = null,
    /**
     * Tap on an unloaded month's skeleton area, reported with its bucket
     * key. The Year view uses this to dive into the month — most of the
     * yearly grid is skeletons, so cells alone would leave it half-inert.
     */
    onSkeletonClick: ((bucketKey: String) -> Unit)? = null,
    /**
     * While true, cells skip thumbnail requests entirely (dominant-colour
     * backdrop + badges only) — flipped during scrubber drags so viewport
     * teleports stay cheap.
     */
    suppressThumbnails: Boolean = false,
    /**
     * Disables user scrolling. Flipped off while the zoom-reflow layer is
     * driving the visible transition so the frozen base grid underneath
     * can't be scrolled out from under it.
     */
    userScrollEnabled: Boolean = true,
    cellSpacing: Dp = 2.dp,
    /**
     * Padding around the scrolling content. The immersive timeline reserves a
     * top inset here (status bar + docked bar height) so the first row / memories
     * strip clears the top bar while at rest, yet scrolls fully under it.
     */
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /**
     * Marca o desmarca de golpe todos los assets de un grupo — el checkbox de
     * la banda de mes. `null` la deja sin checkbox (vista Año, donde clicar es
     * navegar y el grupo es una muestra truncada, no el mes entero).
     */
    onSetGroupSelected: ((ids: List<String>, selected: Boolean) -> Unit)? = null,
    /**
     * Arrastre en banda. Igual que en [AssetGrid], cuando llega el long-press
     * deja de ser de la celda y pasa a ser de la rejilla.
     */
    dragSelect: GroupedGridDragSelect? = null,
    header: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null
) {
    // No load-more plumbing here anymore: with the bucket model every month
    // is laid out up front (skeleton or real rows) and content loading is
    // driven by which bucket keys are visible — see TimelineScreen's
    // visibility effect.
    //
    // Rows are emitted as one items(count) interval per header group rather
    // than one item() per row: with the whole library laid out up front,
    // per-row registration made every rows swap re-register thousands of
    // intervals on the main thread.
    val segments = remember(rows) { segmentRows(rows) }
    // Ids que el checkbox de cada cabecera abarca, precalculados junto a los
    // segmentos: recorrer el grupo dentro de la cabecera lo repetiría en cada
    // recomposición, y durante un arrastre eso es una vez por frame.
    val groupIds = remember(segments) { segments.map { selectableIdsOf(it) } }

    val haptics = rememberPhotonneHaptics()
    val dragSelectAdapter = rememberTimelineDragSelectAdapter(
        listState = state,
        rows = rows,
        headerCount = { dragSelect?.headerCount ?: 0 },
        cellSpacing = cellSpacing,
        idAt = { ordinal -> dragSelect?.idAt?.invoke(ordinal) }
    )
    val gridModifier = if (dragSelect != null) {
        modifier.fillMaxSize().dragSelectable(
            state = dragSelect.state,
            adapter = dragSelectAdapter,
            scrollableState = state,
            enabled = dragSelect.enabled,
            selectionActive = selectedIds.isNotEmpty(),
            isSelected = { it in selectedIds },
            onPatch = dragSelect.onPatch,
            haptics = haptics,
            config = dragSelect.config.copy(
                autoScrollTopInset = contentPadding.calculateTopPadding(),
                autoScrollBottomInset = contentPadding.calculateBottomPadding()
            )
        )
    } else {
        modifier.fillMaxSize()
    }

    LazyColumn(
        state = state,
        userScrollEnabled = userScrollEnabled,
        verticalArrangement = Arrangement.spacedBy(cellSpacing),
        contentPadding = contentPadding,
        modifier = gridModifier
    ) {
        header?.invoke(this)
        segments.forEachIndexed { segmentIndex, segment ->
            segment.header?.let { groupHeader ->
                val ids = groupIds[segmentIndex]
                // Inline month/date band — NOT sticky: it scrolls away with the
                // content instead of pinning at the top (so it never overlaps
                // the status-bar icons or permanently eats the top). The
                // scrubber still surfaces the current date while scrolling.
                item(key = "h:${groupHeader.key}", contentType = "date-header") {
                    MonthHeader(
                        title = groupHeader.title,
                        count = groupHeader.count,
                        // Un grupo entero de esqueletos no tiene nada que
                        // seleccionar todavía.
                        groupState = if (ids.isEmpty()) null
                        else selectedIds.selectionStateOf(ids),
                        onToggleGroup = onSetGroupSelected?.takeIf { ids.isNotEmpty() }
                            ?.let { setSelected ->
                                {
                                    setSelected(
                                        ids,
                                        selectedIds.selectionStateOf(ids) !=
                                            GroupSelectionState.All
                                    )
                                }
                            }
                    )
                }
            }
            items(
                count = segment.body.size,
                key = { i -> rowLazyKey(segment.body[i]) }
            ) { i ->
                when (val entry = segment.body[i]) {
                    is TimelineRowEntry.SkeletonRow -> SkeletonCellsRow(
                        entry = entry,
                        spacing = cellSpacing,
                        onClick = onSkeletonClick?.let { handler ->
                            { handler(entry.bucketKey) }
                        }
                    )
                    is TimelineRowEntry.Row -> UniformCellsRow(
                        row = entry.row,
                        baseUrl = baseUrl,
                        spacing = cellSpacing,
                        onItemClick = onItemClick,
                        // Con arrastre en banda el long-press lo posee la
                        // rejilla; si ambos lo escuchan se cancelan.
                        onItemLongClick = onItemLongClick,
                        cellLongClickEnabled = dragSelect == null,
                        selectedIds = selectedIds,
                        loadThumbnails = !suppressThumbnails,
                        // Device-only rows are merged in after the gallery
                        // scan finishes; animateItem fades them in and
                        // slides their neighbours instead of hard-popping.
                        modifier = Modifier.animateItem()
                    )
                    // Headers never enter a segment body.
                    is TimelineRowEntry.Header -> Unit
                }
            }
        }
    }
}

/**
 * Configuración del arrastre en banda para [GroupedAssetGrid].
 *
 * [headerCount] son los ítems que emite el `header` antes de las filas, y
 * [idAt] traduce ordinal global a id devolviendo null en lo que no se puede
 * seleccionar (ítems solo-locales, ordinales aún no cargados).
 */
@Immutable
internal data class GroupedGridDragSelect(
    val state: DragSelectState,
    val onPatch: (SelectionPatch) -> Unit,
    val headerCount: Int,
    val idAt: (ordinal: Int) -> String?,
    val enabled: Boolean = true,
    val config: DragSelectConfig = DragSelectConfig.Default
)

/** One sticky header plus its run of row entries. */
internal data class RowSegment(
    val header: TimelineRowEntry.Header?,
    val body: List<TimelineRowEntry>
)

/** Splits the flat row list into per-header segments (leading rows headerless). */
internal fun segmentRows(rows: List<TimelineRowEntry>): List<RowSegment> {
    val out = ArrayList<RowSegment>()
    var header: TimelineRowEntry.Header? = null
    var body = ArrayList<TimelineRowEntry>()
    fun flush() {
        if (header != null || body.isNotEmpty()) out += RowSegment(header, body)
    }
    rows.forEach { entry ->
        if (entry is TimelineRowEntry.Header) {
            flush()
            header = entry
            body = ArrayList()
        } else {
            body += entry
        }
    }
    flush()
    return out
}

/**
 * Ids de un segmento que el checkbox de su cabecera puede marcar: los assets
 * ya cargados y de servidor. Se dejan fuera los ítems solo-locales (aún sin
 * subir, no participan en las operaciones en bloque del timeline) y las filas
 * de esqueleto, que todavía no tienen contenido.
 */
internal fun selectableIdsOf(segment: RowSegment): List<String> =
    segment.body.asSequence()
        .filterIsInstance<TimelineRowEntry.Row>()
        .flatMap { it.row.cells.asSequence() }
        .map { it.item }
        .filterNot { it.isLocalOnly }
        .map { it.id }
        .toList()

/** Stable LazyColumn key — must stay identical to the keyToBucket scheme. */
internal fun rowLazyKey(entry: TimelineRowEntry): Any = when (entry) {
    is TimelineRowEntry.Row -> {
        val first = entry.row.cells.first()
        "r:${assetCellKey(first.item, first.index)}"
    }
    is TimelineRowEntry.SkeletonRow -> "s:${entry.bucketKey}:${entry.rowIndex}"
    is TimelineRowEntry.Header -> "h:${entry.key}"
}

/**
 * One row of an unloaded bucket: [TimelineRowEntry.SkeletonRow.cellCount]
 * square shimmer tiles; a partial last row leaves its remaining slots empty
 * so cells keep the same size as full rows.
 */
@Composable
private fun SkeletonCellsRow(
    entry: TimelineRowEntry.SkeletonRow,
    spacing: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(entry.rowHeightDp.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        repeat(entry.cellCount) { i ->
            if (i > 0) Spacer(Modifier.width(spacing))
            SkeletonBlock(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                cornerRadius = 0.dp
            )
        }
        val emptySlots = entry.cellsPerRow - entry.cellCount
        if (emptySlots > 0) {
            Spacer(Modifier.weight(emptySlots.toFloat()))
        }
    }
}

/**
 * Inline (non-sticky) date band: an opaque surface strip with the group title.
 * Uniform across server-indexed and on-device groups so the timeline reads
 * cleanly while scrolling; scrolls away with the content like any other row.
 */
@Composable
private fun MonthHeader(
    title: String,
    count: Int? = null,
    groupState: GroupSelectionState? = null,
    onToggleGroup: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .height(56.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp)
        )
        if (count != null) {
            // Year view: the group's TOTAL, so the truncated sample below
            // never reads as "these are all my photos of 2019".
            Text(
                text = stringResource(Res.string.timeline_year_count, formatGroupedCount(count)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 16.dp)
            )
        } else if (groupState != null && onToggleGroup != null) {
            GroupSelectionCheck(
                state = groupState,
                onClick = onToggleGroup,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Spacing.sm)
            )
        }
    }
}

/**
 * Círculo tri-estado al final de la banda de mes: vacío, medio (algunas del
 * grupo seleccionadas) o lleno. Va siempre visible, no solo en modo selección,
 * porque es la única forma de descubrir que un mes entero se puede marcar de
 * una vez — y es también la manera de ENTRAR en selección sin long-press.
 */
@Composable
private fun GroupSelectionCheck(
    state: GroupSelectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = state != GroupSelectionState.None
    val description = stringResource(
        if (state == GroupSelectionState.All) Res.string.selection_group_clear
        else Res.string.selection_group_select
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Checkbox
                contentDescription = description
                toggleableState = when (state) {
                    GroupSelectionState.None -> ToggleableState.Off
                    GroupSelectionState.Partial -> ToggleableState.Indeterminate
                    GroupSelectionState.All -> ToggleableState.On
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.lg)
                .border(
                    width = 2.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                )
                .background(
                    color = if (state == GroupSelectionState.All) {
                        MaterialTheme.colorScheme.primary
                    } else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                GroupSelectionState.All -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(IconSize.sm)
                )
                // Barra central: el "algunas sí" de un checkbox indeterminado,
                // sin recurrir a un TriStateCheckbox cuadrado que rompería la
                // forma circular del check de las celdas.
                GroupSelectionState.Partial -> Box(
                    modifier = Modifier
                        .size(width = 10.dp, height = 2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                GroupSelectionState.None -> Unit
            }
        }
    }
}

/**
 * Locale-neutral thousands grouping with a space ("8 214") — common code
 * has no NumberFormat, and a hardcoded '.' or ',' would be wrong in half
 * the locales.
 */
internal fun formatGroupedCount(n: Int): String {
    val digits = n.toString()
    if (digits.length <= 3) return digits
    val sb = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(' ')
        sb.append(c)
    }
    return sb.toString()
}

@Composable
private fun UniformCellsRow(
    row: TimelineRow,
    baseUrl: String,
    spacing: Dp,
    onItemClick: (Int) -> Unit,
    onItemLongClick: ((Int) -> Unit)?,
    selectedIds: Set<String>,
    modifier: Modifier = Modifier,
    loadThumbnails: Boolean = true,
    cellLongClickEnabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(row.rowHeightDp.dp)
    ) {
        row.cells.forEachIndexed { i, cell ->
            if (i > 0) Spacer(Modifier.width(spacing))
            AssetGridCell(
                asset = cell.item,
                baseUrl = baseUrl,
                onClick = { onItemClick(cell.index) },
                onLongClick = onItemLongClick?.takeIf { cellLongClickEnabled }
                    ?.let { { it(cell.index) } },
                // El clic derecho se queda en la celda: en escritorio es la
                // única entrada a la selección.
                onSecondaryClick = onItemLongClick?.let { { it(cell.index) } },
                isSelected = cell.item.id in selectedIds,
                // Uniform grid: every cell carries equal weight and the row's
                // height equals the cell width, so the tile is square without
                // forcing an aspect ratio here.
                forceSquare = false,
                loadThumbnail = loadThumbnails,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        // A partial trailing row reserves its empty slots (same as the
        // skeleton row) so its cells keep the uniform size instead of
        // stretching to fill the width.
        val emptySlots = row.columns - row.cells.size
        if (emptySlots > 0) {
            Spacer(Modifier.weight(emptySlots.toFloat()))
        }
    }
}

