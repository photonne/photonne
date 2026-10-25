package com.photonne.app.ui.album

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.RemovedAlbumAsset
import com.photonne.app.resources.Res
import com.photonne.app.resources.album_action_removed_recent
import com.photonne.app.resources.album_removed_by
import com.photonne.app.resources.album_removed_count
import com.photonne.app.resources.album_removed_empty
import com.photonne.app.resources.album_removed_hint
import com.photonne.app.resources.album_removed_load_failed
import com.photonne.app.resources.album_restore
import com.photonne.app.resources.album_restore_selected
import com.photonne.app.resources.album_restored
import com.photonne.app.resources.action_retry
import com.photonne.app.resources.notifications_time_days_ago
import com.photonne.app.resources.notifications_time_hours_ago
import com.photonne.app.resources.notifications_time_just_now
import com.photonne.app.resources.notifications_time_minutes_ago
import com.photonne.app.ui.image.AssetThumbnailImage
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.util.getPluralString
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Quitadas recientemente": lo que se ha quitado del álbum en los últimos 30
 * días, agrupado por la acción que lo quitó (cuándo y quién), para devolverlo
 * a su sitio entero o foto a foto. Es lo que rescata una eliminación que pasó
 * sin que se viera el Deshacer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemovedPhotosSheet(
    viewModel: AlbumDetailViewModel,
    onDismiss: () -> Unit,
    /** Algunas han vuelto: la lista de álbumes ajusta su contador. */
    onRestored: (count: Int) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val baseUrl = rememberApiBaseUrl()
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    var removals by remember { mutableStateOf<List<RemovedAlbumAsset>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        failed = false
        viewModel.loadRemoved()
            .onSuccess { removals = it }
            .onFailure { failed = true }
    }

    fun restore(ids: List<String>) {
        if (ids.isEmpty() || busy) return
        busy = true
        viewModel.restoreRemoved(ids) { restored, error ->
            busy = false
            if (error != null) {
                snackbar?.show(error.userMessage)
                return@restoreRemoved
            }
            val back = restored.toSet()
            removals = removals?.filterNot { it.asset.id in back }
            selected = selected - back
            if (back.isNotEmpty()) {
                onRestored(back.size)
                scope.launch {
                    snackbar?.show(getPluralString(Res.plurals.album_restored, back.size, back.size))
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SheetHeader(
                title = stringResource(Res.string.album_action_removed_recent),
                subtitle = stringResource(Res.string.album_removed_hint)
            )
            val current = removals
            when {
                failed -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(Res.string.album_removed_load_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { attempt++ }) {
                        Text(stringResource(Res.string.action_retry))
                    }
                }
                current == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                current.isEmpty() -> Text(
                    stringResource(Res.string.album_removed_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        items(groupRemovals(current), key = { it.items.first().asset.id }) { group ->
                            RemovalGroupSection(
                                group = group,
                                baseUrl = baseUrl,
                                selected = selected,
                                busy = busy,
                                onToggle = { id ->
                                    selected = if (id in selected) selected - id else selected + id
                                },
                                onRestoreGroup = { restore(group.items.map { it.asset.id }) }
                            )
                        }
                    }
                    Button(
                        onClick = { restore(selected.toList()) },
                        enabled = !busy && selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            pluralStringResource(
                                Res.plurals.album_restore_selected,
                                selected.size,
                                selected.size
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemovalGroupSection(
    group: RemovalGroup,
    baseUrl: String,
    selected: Set<String>,
    busy: Boolean,
    onToggle: (String) -> Unit,
    onRestoreGroup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val whenText = removalTime(group.removedAt)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.removedBy?.let {
                        stringResource(Res.string.album_removed_by, whenText, it)
                    } ?: whenText,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = pluralStringResource(
                        Res.plurals.album_removed_count,
                        group.items.size,
                        group.items.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRestoreGroup, enabled = !busy) {
                Text(stringResource(Res.string.album_restore))
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            group.items.forEach { removal ->
                val isSelected = removal.asset.id in selected
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isSelected) Modifier.border(
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.Checkbox) { onToggle(removal.asset.id) }
                ) {
                    AssetThumbnailImage(
                        item = removal.asset,
                        baseUrl = baseUrl,
                        size = "Small",
                        modifier = Modifier.matchParentSize()
                    )
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                        ) {
                            Icon(
                                PhotonneIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Hace 5 min", "Hace 3h", "Hace 2d"; más de una semana, la fecha. */
@Composable
private fun removalTime(instant: Instant): String {
    val diff = Clock.System.now() - instant
    return when {
        diff.inWholeMinutes < 1 -> stringResource(Res.string.notifications_time_just_now)
        diff.inWholeMinutes < 60 ->
            stringResource(Res.string.notifications_time_minutes_ago, diff.inWholeMinutes)
        diff.inWholeHours < 24 ->
            stringResource(Res.string.notifications_time_hours_ago, diff.inWholeHours)
        diff.inWholeDays < 7 ->
            stringResource(Res.string.notifications_time_days_ago, diff.inWholeDays)
        else -> {
            val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val day = local.dayOfMonth.toString().padStart(2, '0')
            val month = local.monthNumber.toString().padStart(2, '0')
            "$day/$month/${local.year}"
        }
    }
}
