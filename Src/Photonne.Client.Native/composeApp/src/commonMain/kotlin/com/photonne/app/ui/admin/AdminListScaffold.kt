package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.theme.actionButtonHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.photonne.app.data.error.UiError
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_refresh
import com.photonne.app.resources.error_banner_retry
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.ListRowsSkeleton
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/**
 * Shows [message] once on the app's snackbar and tells the owner it was shown,
 * so a result ("Usuario eliminado") doesn't replay on the next recomposition
 * or the next visit. The one channel for operation results in admin: they used
 * to be a text strip here, a line above a button there, and mostly nothing.
 */
@Composable
fun AdminResultSnackbar(message: String?, onShown: () -> Unit) {
    val snackbar = LocalSnackbarController.current
    LaunchedEffect(message) {
        if (message != null) {
            snackbar?.show(message)
            onShown()
        }
    }
}

/**
 * Shell of every admin list: floating chrome, content padded clear of it and
 * of the floating nav, and the four states drawn the same way everywhere —
 * skeleton rows on the first load, the shared [EmptyState] when there is
 * nothing, the error with a retry when the load failed, and pull-to-refresh
 * plus an [ErrorBanner] on top of the rows when a reload fails with data
 * already on screen (the rows stay: a failed refresh is no reason to blank a
 * list). [resultMessage] goes to the snackbar.
 *
 * Nothing may be drawn above the list: the chrome is opaque, and whatever sat
 * up there (status strips, the scan card) was simply never seen. Pinned
 * content goes in [header], as the first rows.
 */
@Composable
fun AdminListScaffold(
    title: String,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    isLoading: Boolean,
    isEmpty: Boolean,
    error: UiError?,
    onRefresh: () -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    resultMessage: String? = null,
    onResultShown: () -> Unit = {},
    onDismissError: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    AdminResultSnackbar(resultMessage, onResultShown)

    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val padding = PaddingValues(
        start = Spacing.screenHorizontal,
        end = Spacing.screenHorizontal,
        top = Spacing.lg + reservedTop,
        bottom = Spacing.lg + floatingNavBarReservedHeight()
    )
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && isEmpty -> ListRowsSkeleton(contentPadding = padding)
            error != null && isEmpty -> EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = error.userMessage,
                actionLabel = stringResource(Res.string.error_banner_retry),
                onAction = onRefresh
            )
            // An empty state can't be pulled, so it carries the refresh itself.
            isEmpty -> EmptyState(
                icon = emptyIcon,
                title = emptyTitle,
                actionLabel = stringResource(Res.string.action_refresh),
                onAction = onRefresh
            )
            else -> PhotonneRefreshableScreen(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                indicatorTopPadding = reservedTop
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    if (error != null) {
                        item(key = "admin-list-error") {
                            ErrorBanner(error = error, onRetry = onRefresh, onDismiss = onDismissError)
                        }
                    }
                    header?.invoke(this)
                    content()
                }
            }
        }
        SubscreenFloatingChrome(
            title = title,
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                isScrollInProgress = { listState.isScrollInProgress },
                scrollToTopMinIndex = 4,
                onScrollToTop = { listState.animateScrollToItem(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange,
            actions = actions
        )
    }
}

/**
 * Shell of the admin editors (a user, a library): the same scrolling form
 * column as [AdminSettingsForm], plus the one state the editors had no answer
 * for. An editor opened on an id that isn't in the list — the list failed to
 * load, the entity was deleted from the web meanwhile — used to spin forever:
 * now it spins only while the list is actually loading ([isResolving]), and
 * otherwise says it couldn't find it ([notFound]) and offers the reload.
 */
@Composable
fun AdminEditorScaffold(
    title: String,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    isResolving: Boolean,
    notFound: Boolean,
    notFoundMessage: String,
    onRetry: () -> Unit,
    resultMessage: String? = null,
    onResultShown: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    AdminResultSnackbar(resultMessage, onResultShown)

    AdminPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
        when {
            isResolving -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            notFound -> EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = notFoundMessage,
                actionLabel = stringResource(Res.string.error_banner_retry),
                onAction = onRetry
            )
            else -> page(content)
        }
    }
}

/**
 * Shell of every admin page that is a scrolling column rather than a list:
 * the hubs, the server version, the duplicates and backup tasks, the editors.
 * Each of them used to paste the same forty lines — Box, haze, scroll state,
 * the padding that clears the floating chrome and the floating nav, and the
 * chrome wired to that scroll — with the margins drifting between 8, 12 and
 * 16 dp from one copy to the next.
 *
 * [body] gets a `page` function that draws the padded scrolling column, so a
 * caller can show something else instead of it (a spinner, an empty state)
 * and still keep the chrome.
 */
@Composable
fun AdminPageScaffold(
    title: String,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    body: @Composable (page: @Composable (@Composable ColumnScope.() -> Unit) -> Unit) -> Unit
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        body { content ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .hazeSource(hazeState)
                    .padding(
                        start = Spacing.screenHorizontal,
                        end = Spacing.screenHorizontal,
                        top = Spacing.lg + subscreenChromeReservedTop(),
                        bottom = Spacing.lg + floatingNavBarReservedHeight()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content
            )
        }
        SubscreenFloatingChrome(
            title = title,
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { if (scrollState.value > 0) 1 else 0 },
                firstVisibleItemScrollOffset = { scrollState.value },
                isScrollInProgress = { scrollState.isScrollInProgress },
                scrollToTopMinIndex = 1,
                onScrollToTop = { scrollState.animateScrollTo(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
}

/** Label on the left, value on the right: a row of a stats or info card. It
 *  existed three times over (InfoRow, StatRow, TwoColumn), identical. */
@Composable
fun AdminKeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The form's main button: end-aligned, at the shared action height, with the
 * spinner beside it while the request is out. One shape for "Guardar" in the
 * settings forms and "Guardar"/"Crear" in the editors, which used a full-width
 * button with the spinner inside.
 */
@Composable
fun AdminPrimaryActionRow(
    label: String,
    enabled: Boolean,
    isSubmitting: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(Spacing.md))
        }
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.actionButtonHeight()) {
            Text(label)
        }
    }
}
