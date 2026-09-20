package com.photonne.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import com.photonne.app.resources.notifications_filter_description
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.NotificationDto
import com.photonne.app.data.models.NotificationKind
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.resources.Res
import com.photonne.app.resources.notifications_action_mark_all_read
import com.photonne.app.resources.notifications_empty_subtitle
import com.photonne.app.resources.notifications_empty_title
import com.photonne.app.resources.notifications_empty_unread_title
import com.photonne.app.resources.notifications_filter_all
import com.photonne.app.resources.notifications_filter_unread
import com.photonne.app.resources.notifications_time_days_ago
import com.photonne.app.resources.notifications_time_hours_ago
import com.photonne.app.resources.notifications_time_just_now
import com.photonne.app.resources.notifications_time_minutes_ago
import com.photonne.app.resources.notifications_total
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.util.PlatformVerticalScrollbar
import com.photonne.app.ui.theme.ListRowsSkeleton

@Composable
fun NotificationsScreen(
    title: String,
    onBack: () -> Unit,
    canMarkAllRead: Boolean,
    onMarkAllRead: () -> Unit,
    viewModel: NotificationsViewModel,
    onNavigate: (String) -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    // LazyColumn ancla el scroll a la clave del primer item visible: cuando una
    // recarga antepone notificaciones nuevas, quedarían fuera de pantalla por
    // arriba. Los items solo cambian por acciones explícitas (entrar, refrescar,
    // paginar, filtrar), así que volver arriba es siempre lo esperado.
    val firstItemId = state.items.firstOrNull()?.id
    LaunchedEffect(firstItemId) {
        if (firstItemId != null) listState.scrollToItem(0)
    }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Con contenido en pantalla el banner vive arriba del todo: sin la
        // reserva quedaba detrás del cromo flotante.
        if (state.items.isNotEmpty()) {
            ErrorBanner(
                error = state.error,
                modifier = Modifier
                    .padding(top = reservedTop)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        PhotonneRefreshableScreen(
            indicatorTopPadding = reservedTop,
            isRefreshing = state.isLoading && state.items.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            when {
                state.isLoading && state.items.isEmpty() ->
                    ListRowsSkeleton(contentPadding = PaddingValues(top = reservedTop), thumbnailSize = 28.dp)
                // Una primera carga fallida no es una bandeja vacía: antes caía
                // en la rama de vacío y decía "total: 0".
                state.error != null && state.items.isEmpty() ->
                    com.photonne.app.ui.error.FullScreenError(
                        error = state.error,
                        onRetry = viewModel::refresh,
                        modifier = Modifier.padding(top = reservedTop)
                    )
                state.isEmpty -> {
                    val title = if (state.unreadOnly)
                        stringResource(Res.string.notifications_empty_unread_title)
                    else
                        stringResource(Res.string.notifications_empty_title)
                    EmptyState(
                        icon = Icons.Outlined.NotificationsNone,
                        title = title,
                        subtitle = stringResource(Res.string.notifications_empty_subtitle)
                    )
                }
                else -> {
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
                        }
                    }
                    LaunchedEffect(listState) {
                        snapshotFlow { shouldLoadMore }
                            .distinctUntilChanged()
                            .filter { it }
                            .collect { viewModel.loadMore() }
                    }
                    LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp + reservedTop,
                        bottom = 24.dp + floatingNavBarReservedHeight()
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(state.items, key = { it.id }) { notification ->
                        // Una fila leída y sin actionUrl no hace nada: mejor
                        // que tampoco parezca tocable.
                        val actionable = notification.actionUrl != null ||
                            !notification.isRead
                        NotificationRow(
                            notification = notification,
                            onClick = if (actionable) {
                                {
                                    viewModel.markRead(notification.id)
                                    notification.actionUrl?.let { onNavigate(it) }
                                }
                            } else null
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    if (state.isAppending) {
                        item("appending") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    item("total") {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(
                                Res.string.notifications_total,
                                state.totalCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }
                }
            }
        }
    }
        PlatformVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
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
            actions = {
                NotificationsFilterMenu(
                    unreadOnly = state.unreadOnly,
                    unreadCount = state.unreadCount,
                    onSelect = viewModel::setUnreadOnly
                )
                IconButton(onClick = onMarkAllRead, enabled = canMarkAllRead) {
                    Icon(
                        Icons.Outlined.DoneAll,
                        contentDescription = stringResource(
                            Res.string.notifications_action_mark_all_read
                        )
                    )
                }
            }
        )
    }
}

/**
 * Filtro Todas / No leídas movido a la cápsula de acciones del cromo flotante:
 * un icono de filtro que despliega las dos opciones (marca la activa), en vez de
 * una fila fija de chips debajo de la barra.
 */
@Composable
private fun NotificationsFilterMenu(
    unreadOnly: Boolean,
    unreadCount: Int,
    onSelect: (Boolean) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Outlined.FilterList,
                contentDescription = stringResource(Res.string.notifications_filter_description),
                tint = if (unreadOnly) MaterialTheme.colorScheme.primary
                else LocalContentColor.current
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.notifications_filter_all)) },
                leadingIcon = {
                    if (!unreadOnly) Icon(Icons.Outlined.Check, contentDescription = null)
                },
                onClick = { open = false; onSelect(false) }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (unreadCount > 0)
                            "${stringResource(Res.string.notifications_filter_unread)} ($unreadCount)"
                        else stringResource(Res.string.notifications_filter_unread)
                    )
                },
                leadingIcon = {
                    if (unreadOnly) Icon(Icons.Outlined.Check, contentDescription = null)
                },
                onClick = { open = false; onSelect(true) }
            )
        }
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationDto,
    onClick: (() -> Unit)?
) {
    val (icon, tint) = iconFor(notification.type)
    val rowBackground = if (notification.isRead) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                color = if (notification.isRead)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            if (notification.message.isNotBlank()) {
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatRelativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!notification.isRead) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun iconFor(type: Int): Pair<ImageVector, Color> = when (type) {
    NotificationKind.JobCompleted ->
        Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
    NotificationKind.JobFailed ->
        Icons.Filled.Error to MaterialTheme.colorScheme.error
    NotificationKind.ShareViewed ->
        Icons.Filled.Visibility to MaterialTheme.colorScheme.tertiary
    NotificationKind.SharedAssetsDeleted ->
        Icons.Filled.DeleteSweep to MaterialTheme.colorScheme.secondary
    else ->
        Icons.Filled.Notifications to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun formatRelativeTime(instant: Instant): String {
    val now = Clock.System.now()
    val diff = now - instant
    val minutes = diff.inWholeMinutes
    val hours = diff.inWholeHours
    val days = diff.inWholeDays
    return when {
        minutes < 1 -> stringResource(Res.string.notifications_time_just_now)
        minutes < 60 -> stringResource(Res.string.notifications_time_minutes_ago, minutes)
        hours < 24 -> stringResource(Res.string.notifications_time_hours_ago, hours)
        days < 7 -> stringResource(Res.string.notifications_time_days_ago, days)
        else -> formatAbsoluteDate(instant)
    }
}

private fun formatAbsoluteDate(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val month = local.monthNumber.toString().padStart(2, '0')
    val year = local.year.toString()
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$day/$month/$year $hour:$minute"
}
