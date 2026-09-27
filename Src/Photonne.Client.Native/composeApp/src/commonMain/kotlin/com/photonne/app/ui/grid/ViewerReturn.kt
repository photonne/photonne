package com.photonne.app.ui.grid

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.photonne.app.ui.theme.LocalCurrentDetailAssetId

/**
 * Qué rejilla abrió el visor, para que al cerrarlo sea ESA la que enseña la
 * última foto vista.
 *
 * Antes `currentDetailAssetId` solo alimentaba el morph: si en el visor se
 * pasaba a una foto que estaba fuera de pantalla, al cerrar la miniatura no
 * existía, el morph no tenía a dónde volver y la rejilla seguía donde se dejó.
 *
 * Cada rejilla se identifica con un token propio ([rememberViewerReturnOrigin])
 * y lo marca al tocar una celda. Mientras el visor está abierto (y durante su
 * cierre), la rejilla de origen sigue a la foto del visor por debajo — está
 * tapada, así que el salto no se ve — y cuando el visor se cierra la miniatura
 * ya está en pantalla y el morph vuelve a su sitio. Solo la rejilla de origen
 * se mueve: el timeline de debajo de un álbum no salta por una foto que
 * también contiene.
 */
@Stable
class ViewerReturnState {
    var origin: Any? by mutableStateOf(null)
        private set

    fun markOrigin(token: Any) {
        origin = token
    }

    /** Visor cerrado del todo: un visor abierto después desde fuera de una
     *  rejilla (subida, notificación…) no debe mover la última rejilla. */
    fun clear() {
        origin = null
    }
}

/** `null` fuera de la app (previews): las rejillas no hacen nada. */
val LocalViewerReturn = staticCompositionLocalOf<ViewerReturnState?> { null }

@Composable
fun rememberViewerReturnOrigin(): Any = remember { Any() }

/** Marca a [token] como la rejilla que abre el visor. */
@Composable
fun rememberMarkViewerOrigin(token: Any): () -> Unit {
    val state = LocalViewerReturn.current
    return remember(state, token) { { state?.markOrigin(token) } }
}

/**
 * Lleva la foto del visor a pantalla en una [LazyGridState]. [indexOf] da el
 * índice de LazyGrid del asset (con cabeceras incluidas) o -1.
 */
@Composable
fun ViewerReturnScrollEffect(
    token: Any,
    gridState: LazyGridState,
    indexOf: (assetId: String) -> Int,
    vararg keys: Any?,
) {
    ViewerReturnEffect(token, keys) { assetId ->
        val index = indexOf(assetId)
        if (index < 0) return@ViewerReturnEffect
        val info = gridState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
        val top = info.viewportStartOffset + info.beforeContentPadding
        val bottom = info.viewportEndOffset - info.afterContentPadding
        if (visible != null && visible.offset.y >= top &&
            visible.offset.y + visible.size.height <= bottom
        ) return@ViewerReturnEffect
        gridState.scrollToItem(index)
        centerItem(gridState, gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.let { it.offset.y to it.size.height },
            gridState.layoutInfo.viewportStartOffset, gridState.layoutInfo.viewportEndOffset)
    }
}

/** Igual que el de rejilla, para listas de filas (el timeline). */
@Composable
fun ViewerReturnScrollEffect(
    token: Any,
    listState: LazyListState,
    indexOf: (assetId: String) -> Int,
    vararg keys: Any?,
) {
    ViewerReturnEffect(token, keys) { assetId ->
        val index = indexOf(assetId)
        if (index < 0) return@ViewerReturnEffect
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
        val top = info.viewportStartOffset + info.beforeContentPadding
        val bottom = info.viewportEndOffset - info.afterContentPadding
        if (visible != null && visible.offset >= top &&
            visible.offset + visible.size <= bottom
        ) return@ViewerReturnEffect
        listState.scrollToItem(index)
        centerItem(listState, listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.let { it.offset to it.size },
            listState.layoutInfo.viewportStartOffset, listState.layoutInfo.viewportEndOffset)
    }
}

@Composable
private fun ViewerReturnEffect(
    token: Any,
    keys: Array<out Any?>,
    ensureVisible: suspend (assetId: String) -> Unit,
) {
    val currentId = LocalCurrentDetailAssetId.current
    val isOrigin = LocalViewerReturn.current?.origin === token
    val latestEnsure by rememberUpdatedState(ensureVisible)
    LaunchedEffect(currentId, isOrigin, *keys) {
        if (currentId == null || !isOrigin) return@LaunchedEffect
        runCatching { latestEnsure(currentId) }
    }
}

/** Tras el scrollToItem la celda queda arriba (bajo el cromo): se centra. */
private suspend fun centerItem(
    state: ScrollableState,
    item: Pair<Int, Int>?,
    viewportStart: Int,
    viewportEnd: Int,
) {
    val (offset, size) = item ?: return
    val delta = offset + size / 2f - (viewportStart + viewportEnd) / 2f
    if (delta != 0f) state.scrollBy(delta)
}
