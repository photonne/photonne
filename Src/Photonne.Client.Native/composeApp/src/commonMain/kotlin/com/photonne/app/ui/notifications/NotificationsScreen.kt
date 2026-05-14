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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.NotificationDto
import com.photonne.app.data.models.NotificationKind
import com.photonne.app.resources.Res
import com.photonne.app.resources.notifications_empty_subtitle
import com.photonne.app.resources.notifications_empty_title
import com.photonne.app.resources.notifications_empty_unread_title
import com.photonne.app.resources.notifications_filter_all
import com.photonne.app.resources.notifications_filter_unread
import com.photonne.app.resources.notifications_pagination_next
import com.photonne.app.resources.notifications_pagination_page
import com.photonne.app.resources.notifications_pagination_previous
import com.photonne.app.resources.notifications_time_days_ago
import com.photonne.app.resources.notifications_time_hours_ago
import com.photonne.app.resources.notifications_time_just_now
import com.photonne.app.resources.notifications_time_minutes_ago
import com.photonne.app.resources.notifications_total
import com.photonne.app.ui.theme.EmptyState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterRow(
            unreadOnly = state.unreadOnly,
            unreadCount = state.unreadCount,
            onSelect = viewModel::setUnreadOnly
        )

        state.errorMessage?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        when {
            state.isLoading && state.items.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
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
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(state.items, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = { viewModel.markRead(notification.id) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                if (state.totalPages > 1) {
                    item("pagination") {
                        Spacer(Modifier.size(12.dp))
                        PaginationRow(
                            currentPage = state.page,
                            totalPages = state.totalPages,
                            onPrev = { viewModel.goToPage(state.page - 1) },
                            onNext = { viewModel.goToPage(state.page + 1) }
                        )
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

@Composable
private fun FilterRow(
    unreadOnly: Boolean,
    unreadCount: Int,
    onSelect: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = !unreadOnly,
            onClick = { onSelect(false) },
            label = { Text(stringResource(Res.string.notifications_filter_all)) }
        )
        FilterChip(
            selected = unreadOnly,
            onClick = { onSelect(true) },
            label = {
                if (unreadCount > 0) {
                    Text(
                        "${stringResource(Res.string.notifications_filter_unread)} ($unreadCount)"
                    )
                } else {
                    Text(stringResource(Res.string.notifications_filter_unread))
                }
            }
        )
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationDto,
    onClick: () -> Unit
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
            .clickable(onClick = onClick)
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
private fun PaginationRow(
    currentPage: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrev,
            enabled = currentPage > 1
        ) {
            Text(stringResource(Res.string.notifications_pagination_previous))
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(
                Res.string.notifications_pagination_page,
                currentPage,
                totalPages
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.size(12.dp))
        OutlinedButton(
            onClick = onNext,
            enabled = currentPage < totalPages
        ) {
            Text(stringResource(Res.string.notifications_pagination_next))
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
