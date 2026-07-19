package com.photonne.app.ui.organize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.MoveOutcome
import com.photonne.app.resources.Res
import com.photonne.app.resources.organize_move_action_count
import com.photonne.app.resources.organize_move_by_year_desc
import com.photonne.app.resources.organize_move_by_year_label
import com.photonne.app.resources.organize_move_destination_label
import com.photonne.app.resources.organize_move_destination_placeholder
import com.photonne.app.resources.organize_move_no_matches
import com.photonne.app.resources.organize_move_pick_destination
import com.photonne.app.resources.organize_rule_intro
import com.photonne.app.resources.organize_rule_year_split_label
import com.photonne.app.ui.album.smart.RuleConditionsEditor
import com.photonne.app.ui.folder.FolderPickerDialog
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Para organizar → Mover por condiciones": build a condition rule (reusing the
 * smart-album condition builder), preview how many MobileBackup-pending assets
 * match, pick a destination folder, and file them all there in one shot. The
 * move is server-resolved and irreversible (physical file move), so it's gated
 * behind a confirmation.
 *
 * The screen has no top bar of its own — the host (App.kt) provides it, matching
 * the inbox — so it lays out as a scrollable body plus a pinned "Mover" bar.
 *
 * @param destinations writable move-destination folders (from the folders VM).
 * @param onMoved receives the moved count; the caller refreshes the inbox badge
 *   and navigates back.
 */
@Composable
fun OrganizeRuleScreen(
    title: String,
    onBack: () -> Unit,
    destinations: List<FolderSummary>,
    onMoved: (Int) -> Unit,
    viewModel: OrganizeRuleViewModel = koinViewModel(),
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val state by viewModel.state.collectAsState()
    val pickers by viewModel.pickers.collectAsState()
    val baseUrl = rememberApiBaseUrl()

    var showFolderPicker by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<MoveOutcome?>(null) }

    // The VM instance is reused across navigations; start blank each time.
    LaunchedEffect(Unit) { viewModel.reset() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = floatingNavBarReservedHeight())) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .hazeSource(hazeState)
                .padding(start = 16.dp, end = 16.dp, top = reservedTop),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                stringResource(Res.string.organize_rule_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RuleConditionsEditor(
                conditions = state.conditions,
                matchAll = state.matchAll,
                pickers = pickers,
                baseUrl = baseUrl,
                previewCount = state.previewCount,
                previewSampleIds = state.previewSampleIds,
                isPreviewing = state.isPreviewing,
                onSetMatchAll = viewModel::setMatchAll,
                onUpsertCondition = viewModel::upsertCondition,
                onRemoveCondition = viewModel::removeCondition,
                onPeopleQuery = viewModel::setPeopleQuery,
                onSceneQuery = viewModel::setSceneQuery,
                onObjectQuery = viewModel::setObjectQuery,
                onEnsureFolders = viewModel::ensureFolders,
            )

            DestinationRow(
                path = state.targetFolderPath,
                onClick = { showFolderPicker = true },
            )

            OrganizeByYearRow(
                checked = state.organizeByYear,
                onToggle = viewModel::setOrganizeByYear,
            )

            if (state.organizeByYear && state.yearBreakdown.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.organize_rule_year_split_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    YearBreakdownChips(state.yearBreakdown)
                }
            }

            state.error?.let { err ->
                Text(
                    err.userMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        MoveBar(
            count = state.previewCount,
            path = state.targetFolderPath,
            enabled = state.canMove,
            isMoving = state.isMoving || state.isLoadingReview,
            onMove = { viewModel.openReview() },
        )
        }

        state.reviewGroups?.let { groups ->
            MoveReviewScreen(
                movedTotal = state.previewCount ?: groups.sumOf { it.count },
                groups = groups,
                baseUrl = baseUrl,
                isMoving = state.isMoving,
                organizeByYear = state.organizeByYear,
                onBack = viewModel::closeReview,
                onConfirm = {
                    viewModel.move { outcome ->
                        // With a year split, confirm the distribution first; the
                        // navigation back happens when the summary is dismissed.
                        if (outcome.yearBreakdown.isNotEmpty()) summary = outcome
                        else onMoved(outcome.moved)
                    }
                },
            )
        }

        // The review overlay is a full-screen opaque Surface with its own top
        // bar, so the floating chrome only rides over the rule-builder body.
        if (state.reviewGroups == null) {
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

    if (showFolderPicker) {
        FolderPickerDialog(
            title = stringResource(Res.string.organize_move_destination_label),
            folders = destinations,
            isSubmitting = false,
            includeRoot = false,
            initialSelectionId = state.targetFolderId,
            onDismiss = { showFolderPicker = false },
            onConfirm = { targetFolderId, _ ->
                showFolderPicker = false
                targetFolderId?.let { id ->
                    destinations.firstOrNull { it.id == id }?.let { folder ->
                        viewModel.setTarget(folder.id, folder.path)
                    }
                }
            },
        )
    }

    summary?.let { s ->
        MoveSummaryDialog(s) {
            summary = null
            onMoved(s.moved)
        }
    }
}

@Composable
private fun DestinationRow(path: String?, onClick: () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.organize_move_destination_label), style = MaterialTheme.typography.titleSmall)
                Text(
                    path?.let { prettyPath(it) } ?: stringResource(Res.string.organize_move_destination_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OrganizeByYearRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Spacer(Modifier.size(4.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.organize_move_by_year_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.organize_move_by_year_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoveBar(
    count: Int?,
    path: String?,
    enabled: Boolean,
    isMoving: Boolean,
    onMove: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onMove,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isMoving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    val n = count ?: 0
                    Text(
                        when {
                            path == null -> stringResource(Res.string.organize_move_pick_destination)
                            n == 0 -> stringResource(Res.string.organize_move_no_matches)
                            else -> stringResource(Res.string.organize_move_action_count, n)
                        }
                    )
                }
            }
        }
    }
}

/** Trims the internal "/assets/users/{username}" prefix so the destination reads
 *  as the user sees their library ("/Familia/2026"), falling back to the raw path. */
private fun prettyPath(path: String?): String {
    if (path == null) return ""
    val marker = "/assets/users/"
    val idx = path.indexOf(marker)
    if (idx < 0) return path
    val afterUser = path.substring(idx + marker.length).substringAfter('/', "")
    return if (afterUser.isBlank()) path else "/$afterUser"
}
