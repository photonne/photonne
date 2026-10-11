package com.photonne.app.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_retry
import com.photonne.app.resources.load_more_failed
import com.photonne.app.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

/**
 * Asks for the next page when the grid nears its end.
 *
 * The flags are read through [rememberUpdatedState]: the old triggers captured
 * them in a `remember(keys) { derivedStateOf }` that the long-lived
 * `LaunchedEffect` never saw replaced, so they kept the values of the first
 * composition. A page that failed then never fired again.
 *
 * It fires once per [items] list: a failed page leaves the list untouched, and
 * firing again for the same list would retry in a loop on screens that don't
 * show the failure. The retry goes through [LoadMoreErrorFooter] instead; a
 * successful page, a refresh or a new search bring a new list and re-arm it.
 */
@Composable
fun LoadMoreEffect(
    gridState: LazyGridState,
    threshold: Int,
    canLoadMore: Boolean,
    items: List<*>,
    onLoadMore: () -> Unit,
) {
    val currentCanLoadMore by rememberUpdatedState(canLoadMore)
    val currentItems by rememberUpdatedState(items)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val nearEnd by remember(gridState, threshold) {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - threshold
        }
    }
    LaunchedEffect(gridState) {
        var firedFor: List<*>? = null
        snapshotFlow { nearEnd && currentCanLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (currentItems !== firedFor) {
                    firedFor = currentItems
                    currentOnLoadMore()
                }
            }
    }
}

/**
 * Pie de una lista paginada cuya página siguiente falló. Sin él el error solo
 * se veía con la lista vacía: con contenido, el scroll se paraba sin más.
 */
@Composable
fun LoadMoreErrorFooter(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.load_more_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = " · ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}
