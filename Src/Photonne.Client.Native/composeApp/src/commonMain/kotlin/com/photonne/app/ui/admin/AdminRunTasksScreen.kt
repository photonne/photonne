package com.photonne.app.ui.admin

import com.photonne.app.ui.theme.Spacing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.models.BackgroundTaskDto
import com.photonne.app.data.models.EnrichmentQueueCounts
import com.photonne.app.data.models.PendingCountResponse
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_run_tasks_action_recluster
import com.photonne.app.resources.admin_run_tasks_action_start
import com.photonne.app.resources.admin_run_tasks_action_stop
import com.photonne.app.resources.admin_run_tasks_ai_failed_format
import com.photonne.app.resources.admin_run_tasks_ai_enqueued_format
import com.photonne.app.resources.admin_run_tasks_ai_nothing_to_enqueue
import com.photonne.app.resources.admin_run_tasks_ai_retrying_format
import com.photonne.app.resources.admin_run_tasks_last_run_format
import com.photonne.app.resources.admin_run_tasks_last_run_never
import com.photonne.app.resources.admin_run_tasks_status_queueing
import com.photonne.app.resources.admin_run_tasks_status_waiting
import com.photonne.app.resources.admin_run_tasks_retry_queue_format
import com.photonne.app.resources.admin_run_tasks_status_starting
import com.photonne.app.resources.admin_run_tasks_detail_progress
import com.photonne.app.resources.admin_run_tasks_detail_progress_value
import com.photonne.app.resources.admin_run_tasks_detail_queue
import com.photonne.app.resources.admin_run_tasks_detail_queue_value
import com.photonne.app.resources.admin_run_tasks_detail_rate
import com.photonne.app.resources.admin_run_tasks_detail_rate_value
import com.photonne.app.resources.admin_run_tasks_detail_remaining
import com.photonne.app.resources.admin_run_tasks_detail_processing
import com.photonne.app.resources.admin_run_tasks_detail_stalled
import com.photonne.app.resources.admin_run_tasks_detail_stalled_value
import com.photonne.app.resources.admin_run_tasks_detail_problems
import com.photonne.app.resources.admin_run_tasks_detail_status
import com.photonne.app.resources.admin_run_tasks_detail_started
import com.photonne.app.resources.admin_run_tasks_detail_started_value
import com.photonne.app.resources.admin_maintenance_active_coverage
import com.photonne.app.resources.admin_maintenance_active_empty_trash
import com.photonne.app.resources.admin_maintenance_active_missing
import com.photonne.app.resources.admin_maintenance_active_orphans
import com.photonne.app.resources.admin_maintenance_active_purge_missing
import com.photonne.app.resources.admin_maintenance_active_recalculate
import com.photonne.app.resources.admin_system_duplicates_active
import com.photonne.app.resources.admin_system_embedding_active
import com.photonne.app.resources.admin_system_face_active
import com.photonne.app.resources.admin_system_geocode_active
import com.photonne.app.resources.admin_system_index_active
import com.photonne.app.resources.admin_system_interpolate_active
import com.photonne.app.resources.admin_system_media_recognition_active
import com.photonne.app.resources.admin_system_memories_active
import com.photonne.app.resources.admin_system_metadata_active
import com.photonne.app.resources.admin_system_object_active
import com.photonne.app.resources.admin_system_restore_dates_active
import com.photonne.app.resources.admin_system_scene_active
import com.photonne.app.resources.admin_system_text_active
import com.photonne.app.resources.admin_system_thumbnails_active
import com.photonne.app.resources.admin_system_trips_active
import com.photonne.app.resources.admin_run_tasks_pending_format
import com.photonne.app.resources.admin_run_tasks_section_ai
import com.photonne.app.resources.admin_run_tasks_section_memories
import com.photonne.app.resources.admin_run_tasks_confirm_empty_trash_message
import com.photonne.app.resources.admin_run_tasks_confirm_empty_trash_title
import com.photonne.app.resources.admin_run_tasks_confirm_purge_message
import com.photonne.app.resources.admin_run_tasks_confirm_purge_title
import com.photonne.app.resources.admin_run_tasks_confirm_run
import com.photonne.app.resources.admin_run_tasks_purge_dry_run
import com.photonne.app.resources.admin_run_tasks_section_cleanup
import com.photonne.app.resources.admin_run_tasks_section_cleanup_subtitle
import com.photonne.app.resources.admin_run_tasks_section_collapse
import com.photonne.app.resources.admin_run_tasks_section_expand
import com.photonne.app.resources.admin_run_tasks_section_new_photos
import com.photonne.app.resources.admin_run_tasks_section_new_photos_subtitle
import com.photonne.app.resources.admin_run_tasks_section_repair
import com.photonne.app.resources.admin_run_tasks_section_repair_subtitle
import com.photonne.app.resources.admin_maintenance_action_empty_trash
import com.photonne.app.resources.admin_maintenance_action_coverage
import com.photonne.app.resources.admin_maintenance_action_missing
import com.photonne.app.resources.admin_maintenance_desc_coverage
import com.photonne.app.resources.admin_maintenance_action_orphans
import com.photonne.app.resources.admin_maintenance_action_purge_missing
import com.photonne.app.resources.admin_maintenance_action_recalculate
import com.photonne.app.resources.admin_maintenance_desc_empty_trash
import com.photonne.app.resources.admin_maintenance_desc_missing
import com.photonne.app.resources.admin_maintenance_desc_orphans
import com.photonne.app.resources.admin_maintenance_desc_purge_missing
import com.photonne.app.resources.admin_maintenance_desc_recalculate
import com.photonne.app.resources.admin_run_tasks_section_memories_subtitle
import com.photonne.app.resources.admin_run_tasks_time_d_format
import com.photonne.app.resources.admin_run_tasks_time_h_format
import com.photonne.app.resources.admin_run_tasks_time_m_format
import com.photonne.app.resources.admin_run_tasks_time_now
import com.photonne.app.resources.admin_metadata_overwrite
import com.photonne.app.resources.admin_restore_dates_dry_run
import com.photonne.app.resources.admin_restore_dates_from_file
import com.photonne.app.resources.admin_restore_dates_infer
import com.photonne.app.resources.admin_restore_dates_use_file_date
import com.photonne.app.resources.admin_restore_dates_write_file
import com.photonne.app.resources.admin_system_restore_dates
import com.photonne.app.resources.admin_system_restore_dates_subtitle
import com.photonne.app.resources.admin_system_duplicates
import com.photonne.app.resources.admin_thumbnails_regenerate
import com.photonne.app.resources.admin_system_duplicates_subtitle
import com.photonne.app.resources.admin_system_embedding
import com.photonne.app.resources.admin_system_embedding_subtitle
import com.photonne.app.resources.admin_system_face
import com.photonne.app.resources.admin_system_face_subtitle
import com.photonne.app.resources.admin_system_index
import com.photonne.app.resources.admin_system_index_subtitle
import com.photonne.app.resources.admin_system_geocode
import com.photonne.app.resources.admin_system_geocode_subtitle
import com.photonne.app.resources.admin_system_interpolate
import com.photonne.app.resources.admin_system_interpolate_subtitle
import com.photonne.app.resources.admin_system_media_recognition
import com.photonne.app.resources.admin_system_media_recognition_subtitle
import com.photonne.app.resources.admin_system_memories
import com.photonne.app.resources.admin_system_memories_subtitle
import com.photonne.app.resources.admin_system_trips
import com.photonne.app.resources.admin_system_trips_subtitle
import com.photonne.app.resources.admin_system_metadata
import com.photonne.app.resources.admin_system_metadata_subtitle
import com.photonne.app.resources.admin_system_object
import com.photonne.app.resources.admin_system_object_subtitle
import com.photonne.app.resources.admin_system_scene
import com.photonne.app.resources.admin_system_scene_subtitle
import com.photonne.app.resources.admin_system_text
import com.photonne.app.resources.admin_system_text_subtitle
import com.photonne.app.resources.admin_system_thumbnails
import com.photonne.app.resources.admin_system_thumbnails_subtitle
import com.photonne.app.ui.charts.LiveDot
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.russhwolf.settings.Settings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Logical grouping for the Run Tasks hub. Order also drives the visual
 * order on the screen.
 *
 * Grouped by WHY you came, not by what the task is made of. Nobody opens
 * this screen wanting "a pipeline task" — they just copied photos in, or a
 * date is wrong, or the disk is full. The previous grouping (pipeline / AI /
 * memories / other) described our architecture, which is of no use to the
 * person looking for a button.
 *
 * - [NewPhotos]: you added files. Index → Metadata → Thumbnails, in the
 *   order they feed each other; ML can only run on assets that finished it.
 * - [Ai]: the five ML enrichments. Each consumes already-indexed assets.
 * - [Memories]: the nightly Recuerdos chain, on demand. Listed in
 *   dependency order — coordinates → place names → trips → memories —
 *   because running one out of order just means it reads yesterday's
 *   input and lands a night late.
 * - [Repair]: something is already wrong and you're fixing it.
 * - [Cleanup]: reclaiming space. The only group that destroys anything, and
 *   grouped so you can see that at a glance rather than reading each row.
 */
enum class AdminRunTaskSection { NewPhotos, Ai, Memories, Repair, Cleanup }

/** Identifies one of the five ML backfill endpoints
 *  (`/api/admin/maintenance/{kind}/backfill`). Used as the API path
 *  segment and as the storage key for per-kind progress in the hub. */
enum class AdminBackfillKind(val apiPath: String) {
    FaceRecognition("face-recognition"),
    ObjectDetection("object-detection"),
    SceneClassification("scene-classification"),
    TextRecognition("text-recognition"),
    ImageEmbedding("image-embedding"),
    // Not an ML model: re-pairs Live Photo stills with their motion clips by
    // re-running MediaRecognition (filesystem sibling check). Lives under
    // Repair as a re-runnable fix, not an IA row.
    MediaRecognition("media-recognition"),
}

/**
 * Catalogue of every runnable processing task in this hub. Each task
 * knows its hub icon, labels, [section], and (for ML) the
 * [AdminBackfillKind] whose `/pending-count` endpoint feeds its inline
 * counter. Entry order matches the visual order within each section.
 */
enum class AdminRunTask(
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    // What the row says while the task runs: a verb, not a number. "12 %"
    // tells you nothing about what the 12 % is of; "Analizando caras" does.
    val activeLabelRes: StringResource,
    val icon: ImageVector,
    val backfillKind: AdminBackfillKind?,
    val section: AdminRunTaskSection,
    // Server-side BackgroundTaskType string for pipeline tasks (matches
    // the enum on `BackgroundTaskManager`). Null for ML / Other tasks
    // that don't register with that manager.
    val backgroundType: String?,
    // URL slug of the maintenance kind behind this task, for the rows backed
    // by `/api/admin/maintenance/{kind}/stream`. These all register under the
    // same backgroundType ("Maintenance"), so `kind` — not type — is what
    // tells one running maintenance row from another.
    val maintenanceKind: String? = null,
    // Asks before running. Only for the two tasks that destroy data a user
    // could still want: every row here is one identical ▶ tap, so the button
    // that empties everyone's trash must not be as easy to hit as Indexar.
    val isDestructive: Boolean = false,
) {
    IndexAssets(
        titleRes = Res.string.admin_system_index,
        subtitleRes = Res.string.admin_system_index_subtitle,
        activeLabelRes = Res.string.admin_system_index_active,
        icon = Icons.Outlined.Sync,
        backfillKind = null,
        section = AdminRunTaskSection.NewPhotos,
        backgroundType = "IndexAssets",
    ),
    ExtractMetadata(
        titleRes = Res.string.admin_system_metadata,
        subtitleRes = Res.string.admin_system_metadata_subtitle,
        activeLabelRes = Res.string.admin_system_metadata_active,
        icon = Icons.Outlined.Info,
        backfillKind = null,
        section = AdminRunTaskSection.NewPhotos,
        backgroundType = "Metadata",
    ),
    GenerateThumbnails(
        titleRes = Res.string.admin_system_thumbnails,
        subtitleRes = Res.string.admin_system_thumbnails_subtitle,
        activeLabelRes = Res.string.admin_system_thumbnails_active,
        icon = Icons.Outlined.Image,
        backfillKind = null,
        section = AdminRunTaskSection.NewPhotos,
        backgroundType = "Thumbnails",
    ),
    RestoreDates(
        titleRes = Res.string.admin_system_restore_dates,
        subtitleRes = Res.string.admin_system_restore_dates_subtitle,
        activeLabelRes = Res.string.admin_system_restore_dates_active,
        icon = Icons.Outlined.DateRange,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = "DateRestore",
    ),
    FaceRecognition(
        titleRes = Res.string.admin_system_face,
        subtitleRes = Res.string.admin_system_face_subtitle,
        activeLabelRes = Res.string.admin_system_face_active,
        icon = Icons.Outlined.Face,
        backfillKind = AdminBackfillKind.FaceRecognition,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    ObjectDetection(
        titleRes = Res.string.admin_system_object,
        subtitleRes = Res.string.admin_system_object_subtitle,
        activeLabelRes = Res.string.admin_system_object_active,
        icon = Icons.Outlined.Category,
        backfillKind = AdminBackfillKind.ObjectDetection,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    SceneClassification(
        titleRes = Res.string.admin_system_scene,
        subtitleRes = Res.string.admin_system_scene_subtitle,
        activeLabelRes = Res.string.admin_system_scene_active,
        icon = Icons.Outlined.Landscape,
        backfillKind = AdminBackfillKind.SceneClassification,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    TextRecognition(
        titleRes = Res.string.admin_system_text,
        subtitleRes = Res.string.admin_system_text_subtitle,
        activeLabelRes = Res.string.admin_system_text_active,
        icon = Icons.Outlined.TextFields,
        backfillKind = AdminBackfillKind.TextRecognition,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    ImageEmbedding(
        titleRes = Res.string.admin_system_embedding,
        subtitleRes = Res.string.admin_system_embedding_subtitle,
        activeLabelRes = Res.string.admin_system_embedding_active,
        icon = Icons.Outlined.ImageSearch,
        backfillKind = AdminBackfillKind.ImageEmbedding,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    InterpolateLocations(
        titleRes = Res.string.admin_system_interpolate,
        subtitleRes = Res.string.admin_system_interpolate_subtitle,
        activeLabelRes = Res.string.admin_system_interpolate_active,
        icon = Icons.Outlined.MyLocation,
        backfillKind = null,
        section = AdminRunTaskSection.Memories,
        backgroundType = "Maintenance",
        maintenanceKind = "interpolate-locations",
    ),
    ReverseGeocode(
        titleRes = Res.string.admin_system_geocode,
        subtitleRes = Res.string.admin_system_geocode_subtitle,
        activeLabelRes = Res.string.admin_system_geocode_active,
        icon = Icons.Outlined.Place,
        backfillKind = null,
        section = AdminRunTaskSection.Memories,
        backgroundType = "Maintenance",
        maintenanceKind = "reverse-geocode",
    ),
    DetectTrips(
        titleRes = Res.string.admin_system_trips,
        subtitleRes = Res.string.admin_system_trips_subtitle,
        activeLabelRes = Res.string.admin_system_trips_active,
        icon = Icons.Outlined.Flight,
        backfillKind = null,
        section = AdminRunTaskSection.Memories,
        backgroundType = "Maintenance",
        maintenanceKind = "detect-trips",
    ),
    GenerateMemories(
        titleRes = Res.string.admin_system_memories,
        subtitleRes = Res.string.admin_system_memories_subtitle,
        activeLabelRes = Res.string.admin_system_memories_active,
        icon = Icons.Outlined.AutoAwesome,
        backfillKind = null,
        section = AdminRunTaskSection.Memories,
        backgroundType = "Maintenance",
        maintenanceKind = "generate-memories",
    ),
    MediaRecognition(
        titleRes = Res.string.admin_system_media_recognition,
        subtitleRes = Res.string.admin_system_media_recognition_subtitle,
        activeLabelRes = Res.string.admin_system_media_recognition_active,
        icon = Icons.Outlined.MotionPhotosOn,
        backfillKind = AdminBackfillKind.MediaRecognition,
        section = AdminRunTaskSection.Repair,
        backgroundType = null,
    ),
    DetectDuplicates(
        titleRes = Res.string.admin_system_duplicates,
        subtitleRes = Res.string.admin_system_duplicates_subtitle,
        activeLabelRes = Res.string.admin_system_duplicates_active,
        icon = Icons.Outlined.ContentCopy,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = null,
    ),
    MarkMissingFiles(
        titleRes = Res.string.admin_maintenance_action_missing,
        subtitleRes = Res.string.admin_maintenance_desc_missing,
        activeLabelRes = Res.string.admin_maintenance_active_missing,
        icon = Icons.Outlined.SearchOff,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = MaintenanceTaskType,
        maintenanceKind = "missing-files",
    ),
    IndexingCoverage(
        titleRes = Res.string.admin_maintenance_action_coverage,
        subtitleRes = Res.string.admin_maintenance_desc_coverage,
        activeLabelRes = Res.string.admin_maintenance_active_coverage,
        icon = Icons.Outlined.FactCheck,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = MaintenanceTaskType,
        maintenanceKind = "indexing-coverage",
    ),
    RecalculateSizes(
        titleRes = Res.string.admin_maintenance_action_recalculate,
        subtitleRes = Res.string.admin_maintenance_desc_recalculate,
        activeLabelRes = Res.string.admin_maintenance_active_recalculate,
        icon = Icons.Outlined.Straighten,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = MaintenanceTaskType,
        maintenanceKind = "recalculate-sizes",
    ),
    OrphanThumbnails(
        titleRes = Res.string.admin_maintenance_action_orphans,
        subtitleRes = Res.string.admin_maintenance_desc_orphans,
        activeLabelRes = Res.string.admin_maintenance_active_orphans,
        icon = Icons.Outlined.BrokenImage,
        backfillKind = null,
        section = AdminRunTaskSection.Cleanup,
        backgroundType = MaintenanceTaskType,
        maintenanceKind = "orphan-thumbnails",
        // Not destructive: a thumbnail with no asset behind it is regenerable
        // by definition. Only the two below take something you can't get back.
    ),
    EmptyTrash(
        titleRes = Res.string.admin_maintenance_action_empty_trash,
        subtitleRes = Res.string.admin_maintenance_desc_empty_trash,
        activeLabelRes = Res.string.admin_maintenance_active_empty_trash,
        icon = Icons.Outlined.DeleteForever,
        backfillKind = null,
        section = AdminRunTaskSection.Cleanup,
        backgroundType = MaintenanceTaskType,
        maintenanceKind = "empty-trash",
        isDestructive = true,
    ),
    PurgeMissing(
        titleRes = Res.string.admin_maintenance_action_purge_missing,
        subtitleRes = Res.string.admin_maintenance_desc_purge_missing,
        activeLabelRes = Res.string.admin_maintenance_active_purge_missing,
        icon = Icons.Outlined.DeleteSweep,
        backfillKind = null,
        section = AdminRunTaskSection.Cleanup,
        backgroundType = MaintenanceTaskType,
        maintenanceKind = "purge-missing",
        isDestructive = true,
    ),
    ;

    /** What keys this task's live progress and its running row. Every
     *  maintenance kind registers under the type "Maintenance", so keying on
     *  type alone would make one task paint another's progress bar. */
    val progressKey: String? get() = maintenanceKind ?: backgroundType

    /** The server's `AssetEnrichmentType` behind this row, when it has one:
     *  the key into the queue summary and the filter the failures registry
     *  opens with. The two sweeps have one too — a retry sent from the registry
     *  runs through the per-asset queue, not through the sweep, and used to be
     *  invisible here: no activity, no "N con errores". */
    val enrichmentType: String?
        get() = when (this) {
            ExtractMetadata -> "Exif"
            GenerateThumbnails -> "Thumbnails"
            else -> backfillKind?.name
        }
}

private const val PollIntervalMs = 10_000L

/** Poll cadence while an AI queue is being filled or drained. Ten seconds
 *  is fine for "did the nightly run", not for watching a bar you just
 *  started: the counters behind it are cheap now (one index probe each), so
 *  the row can afford to move every few seconds. */
private const val ActivePollIntervalMs = 3_000L

/** `BackgroundTaskType.Maintenance` on the server — the type every maintenance
 *  kind shares. */
private const val MaintenanceTaskType = "Maintenance"

// Maximum time we wait for the streaming endpoint's first event after a
// Start tap before falling through to refresh. The server registers the
// task entry as soon as the request hits the handler, well before any
// data is emitted, so the next /api/tasks poll will reliably see it.
private const val TriggerTimeoutMs = 3_000L

data class AdminRunTasksUiState(
    val pending: Map<AdminRunTask, PendingCountResponse> = emptyMap(),
    /** Every per-asset queue, by `AssetEnrichmentType` name. Arrives well
     *  before [pending] — it doesn't count the library, only the task table —
     *  so it's what decides whether a row is working. Empty against a server
     *  that predates the endpoint; the rows then fall back to [pending]. */
    val queues: Map<String, EnrichmentQueueCounts> = emptyMap(),
    /** Every task entry the server reports (running + recently finished).
     *  Used to derive both "running now" rows and "Última ejecución hace X"
     *  subtitles. */
    val backgroundTasks: List<BackgroundTaskDto> = emptyList(),
    /** Task groups currently unfolded. Persisted across visits — see
     *  [readExpandedSections]. */
    val expandedSections: Set<AdminRunTaskSection> = DefaultExpandedSections,
    /** The destructive task waiting for a yes. Null when no dialog is up. */
    val confirming: AdminRunTask? = null,
    /** Count what would be purged instead of purging it. Off by default: the
     *  toggle exists to make the real run safer, not to make it a surprise. */
    val purgeDryRun: Boolean = false,
    /** Live progress for running pipeline tasks, keyed by server task-type
     *  ("IndexAssets" / "Metadata" / "Thumbnails"). Fed by a per-task
     *  follower that subscribes to `/api/tasks/{id}/stream`, so the row
     *  updates per-asset instead of waiting on the 10s poll. Overrides the
     *  polled entry for the same type while present; cleared when the
     *  follower's stream ends. */
    val liveTasks: Map<String, BackgroundTaskDto> = emptyMap(),
    /** Snapshot of `pending.completed` for each AI task at the moment
     *  the user kicked off a backfill from the hub. Used to compute a
     *  session-scoped progress bar (`(now.completed - baseline) /
     *  (delta + inQueue)`) instead of the lifetime ratio, which would
     *  start at 90 % on a library that's already mostly processed and
     *  barely move while the new work happens. Cleared in [refresh]
     *  once the queue for the task drains. */
    val aiSessionBaseline: Map<AdminRunTask, Int> = emptyMap(),
    /** Tasks whose inline Start button was just tapped. The row shows an
     *  intermediate "starting" state with a spinner until the next refresh
     *  promotes it to a real running task (or rolls it back to idle if the
     *  request failed). For ML rows this also covers the enqueue request
     *  itself, which queues the whole remaining pool in one call. */
    val triggering: Set<AdminRunTask> = emptySet(),
    /** Toggle persisted across the hub session: when ON, the next
     *  `triggerTask(GenerateThumbnails)` regenerates every thumbnail
     *  instead of only filling in missing ones. */
    val thumbnailsRegenerate: Boolean = false,
    /** Same for metadata extraction: when ON, the next
     *  `triggerTask(ExtractMetadata)` re-reads EXIF for every asset
     *  regardless of existing data. */
    val metadataOverwrite: Boolean = false,
    /** Same for date restoration: when ON, the next
     *  `triggerTask(RestoreDates)` re-reads EXIF from each file on disk
     *  before restoring; otherwise it uses the EXIF stored in the DB. */
    val dateRestoreFromFile: Boolean = false,
    val dateRestoreInferFromPath: Boolean = false,
    val dateRestoreUseFileDate: Boolean = false,
    // Write-back defaults ON: an inferred date stored only in the DB is lost
    // on a rebuild, while EXIF inside the image survives anything.
    val dateRestoreWriteToFile: Boolean = true,
    val dateRestoreDryRun: Boolean = false,
    val isLoading: Boolean = false,
    /** What went wrong with the last thing the admin asked for. Every action
     *  on this screen is a fire-and-forget request whose only visible effect
     *  arrives 10 seconds later on the next poll, so without this a refused
     *  backfill, a 404 and a dead network all look identical to a task that
     *  simply hasn't started yet. */
    val errorMessage: TaskMessage? = null,
    /** What went right, when the answer isn't visible on the rows either —
     *  "cancelled 320, 2 still running" is the difference between a button
     *  that did nothing and one that did what it could. */
    val infoMessage: TaskMessage? = null,
) {
    /** An AI row is being queued or still has jobs queued: the state in which
     *  the admin is watching, and polling should keep up. */
    /** A row's queue numbers: the summary's when the server sent one, else the
     *  ones that came with its pending count. */
    fun queueOf(task: AdminRunTask): EnrichmentQueueCounts {
        task.enrichmentType?.let { queues[it] }?.let { return it }
        val count = pending[task] ?: return EnrichmentQueueCounts()
        return EnrichmentQueueCounts(
            inQueue = count.inQueue,
            processing = count.processing ?: 0,
            retrying = count.retrying,
            failed = count.failed,
        )
    }

    val hasAiWorkInFlight: Boolean
        get() = triggering.any { it.backfillKind != null } ||
            pending.any { (task, count) -> task.backfillKind != null && count.inQueue > 0 } ||
            queues.values.any { it.inQueue > 0 }
}

/** A line of feedback, and which row it came from. The task travels as the
 *  enum rather than as text because the banner is rendered in a composable and
 *  can resolve the localized title there — "ObjectDetection: …" in an otherwise
 *  Spanish screen reads like a leaked stack trace. */
data class TaskMessage(val task: AdminRunTask?, val text: String)

/** Normalized progress event used by the live-follow plumbing — the pipeline
 *  resume streams (index / metadata / thumbnails / dates) and the maintenance
 *  one all map onto this shared shape so a single follower can drive any of
 *  them. */
private data class LiveProgress(
    val percentage: Double,
    val message: String,
    val isCompleted: Boolean,
    val taskId: String?,
)

/**
 * Drives the hub. Polls three pieces of state every [PollIntervalMs]:
 * per-ML pending counters, the global ML "any missing" total, and the
 * background-task registry (used for both running indicators and
 * last-run timestamps). Also exposes fire-and-forget triggers so the
 * inline Start buttons on each row don't need the detail VMs to be in
 * the composition.
 */
class AdminRunTasksViewModel(
    private val repository: AdminRepository,
    private val settings: Settings,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AdminRunTasksUiState(expandedSections = readExpandedSections(settings))
    )
    val state: StateFlow<AdminRunTasksUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Folds or unfolds a group and remembers the choice for next time. */
    fun toggleSection(section: AdminRunTaskSection) {
        _state.update {
            val next = if (section in it.expandedSections) it.expandedSections - section
                       else it.expandedSections + section
            writeExpandedSections(settings, next)
            it.copy(expandedSections = next)
        }
    }

    /** Puts up the "are you sure" for a destructive task. */
    fun requestConfirm(task: AdminRunTask) {
        _state.update { it.copy(confirming = task) }
    }

    fun dismissConfirm() {
        _state.update { it.copy(confirming = null) }
    }

    /** Runs the task the dialog was asking about. The only path that starts a
     *  destructive task — [start] refuses to. */
    fun confirmAndRun() {
        val task = _state.value.confirming ?: return
        _state.update { it.copy(confirming = null) }
        triggerTask(task)
    }

    /**
     * What a row's ▶ does. Destructive tasks divert to a dialog instead of
     * running; everything else goes straight through. Keeping the fork here
     * rather than in the row means a new destructive task can't be added
     * without a confirmation by simply wiring up its button.
     */
    fun start(task: AdminRunTask) {
        if (task.isDestructive) requestConfirm(task)
        else if (task.backfillKind != null && task.maintenanceKind == null) triggerMlBackfill(task)
        else triggerTask(task)
    }

    fun setPurgeDryRun(value: Boolean) {
        _state.update { it.copy(purgeDryRun = value) }
    }

    // Active live-progress followers keyed by progress key. Each subscribes to
    // `/api/tasks/{id}/stream` for one running task and pushes per-item updates
    // into `liveTasks`. Re-attached automatically by [refresh] whenever a
    // running task has no follower yet, so progress resumes after navigating
    // back.
    private val liveJobs = mutableMapOf<String, Job>()

    // progress key → AdminRunTask, for re-attaching followers. Keyed by
    // progressKey rather than by type so the nine maintenance kinds — which all
    // report as type "Maintenance" — don't collapse onto one entry.
    //
    // A non-null progressKey is exactly the set that has a resume stream: the
    // four pipeline tasks and every maintenance kind. ML rows and Duplicates
    // have neither, and drop out here.
    private val followableByKey: Map<String, AdminRunTask> =
        AdminRunTask.entries
            .filter { it.progressKey != null }
            .associateBy { it.progressKey!! }

    fun load() {
        if (_state.value.isLoading) return
        viewModelScope.launch { refresh(showLoading = true) }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            refresh(showLoading = _state.value.pending.isEmpty())
            while (isActive) {
                delay(if (_state.value.hasAiWorkInFlight) ActivePollIntervalMs else PollIntervalMs)
                refresh(showLoading = false)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    /**
     * Starts a pipeline / Other task on the server. Opens the streaming
     * endpoint just long enough for the server to register the task entry
     * in [BackgroundTaskManager], then drops the connection — the worker
     * keeps running independently (on `Task.Run`) and shows up in the
     * next [refresh] via `/api/tasks`.
     *
     * The connection-open uses [withTimeoutOrNull] because some endpoints
     * may delay the first NDJSON event by several seconds (e.g. the
     * metadata extractor scans the assets table before emitting); without
     * a bound, the UI would sit on the optimistic spinner waiting for an
     * event the user doesn't even care about.
     */
    fun triggerTask(task: AdminRunTask) {
        // Re-entrancy guard: if the user double-taps Start before the
        // first invocation finishes, don't fire a second server-side
        // task entry. Pipeline endpoints don't dedupe — without this
        // we'd register N parallel Index/Thumbnails/Metadata runs
        // every time someone smashes the button.
        if (task in _state.value.triggering) return
        val snapshot = _state.value
        _state.update { it.copy(triggering = it.triggering + task, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(TriggerTimeoutMs) {
                    when (task) {
                        AdminRunTask.IndexAssets ->
                            repository.indexStream().take(1).collect {}
                        AdminRunTask.ExtractMetadata ->
                            repository.metadataStream(overwrite = snapshot.metadataOverwrite)
                                .take(1).collect {}
                        AdminRunTask.GenerateThumbnails ->
                            repository.thumbnailsStream(regenerate = snapshot.thumbnailsRegenerate)
                                .take(1).collect {}
                        AdminRunTask.RestoreDates ->
                            repository.dateRestoreStream(
                                fromFile = snapshot.dateRestoreFromFile,
                                inferFromPath = snapshot.dateRestoreInferFromPath,
                                useFileDate = snapshot.dateRestoreUseFileDate,
                                writeToFile = (snapshot.dateRestoreInferFromPath || snapshot.dateRestoreUseFileDate) &&
                                    snapshot.dateRestoreWriteToFile,
                                dryRun = snapshot.dateRestoreDryRun
                            ).take(1).collect {}
                        AdminRunTask.DetectDuplicates ->
                            repository.duplicatesStream(cleanup = false, physical = false).take(1).collect {}
                        else -> task.maintenanceKind?.let { kind ->
                            // dryRun only means anything to purge-missing; the
                            // other kinds ignore the query param server-side.
                            repository.maintenanceStream(
                                kind = kind,
                                dryRun = task == AdminRunTask.PurgeMissing && snapshot.purgeDryRun,
                            ).take(1).collect {}
                        }
                    }
                }
            }.onFailure { error ->
                // A stream that never opens is the one failure mode this screen
                // has always had and never shown: the row went back to idle and
                // the admin was left guessing whether the task had started.
                _state.update { it.copy(errorMessage = describeFailure(task, error)) }
            }
            refresh(showLoading = false)
            _state.update { it.copy(triggering = it.triggering - task) }
        }
    }

    /**
     * Turns a failed request into something an admin can act on. The server
     * answers a refused backfill with `{"error": "…"}` and the API layer
     * surfaces it as the exception message, so its own words — "Detección de
     * objetos está desactivado en Ajustes" — reach the screen unchanged.
     */
    private fun describeFailure(task: AdminRunTask?, error: Throwable): TaskMessage {
        val detail = (error as? PhotonneApiException)?.let { api ->
            // A 404 here means this server doesn't know the endpoint, not that
            // something is missing — worth saying, because the fix is a deploy.
            if (api.status == 404) "el servidor no conoce esta acción (¿versión antigua?)"
            else api.message
        } ?: error.message ?: "error desconocido"
        return TaskMessage(task, detail)
    }

    fun setThumbnailsRegenerate(value: Boolean) {
        _state.update { it.copy(thumbnailsRegenerate = value) }
    }

    fun setMetadataOverwrite(value: Boolean) {
        _state.update { it.copy(metadataOverwrite = value) }
    }

    fun setDateRestoreFromFile(value: Boolean) {
        _state.update { it.copy(dateRestoreFromFile = value) }
    }

    fun setDateRestoreInferFromPath(value: Boolean) {
        _state.update { it.copy(dateRestoreInferFromPath = value) }
    }

    fun setDateRestoreUseFileDate(value: Boolean) {
        _state.update { it.copy(dateRestoreUseFileDate = value) }
    }

    fun setDateRestoreWriteToFile(value: Boolean) {
        _state.update { it.copy(dateRestoreWriteToFile = value) }
    }

    fun setDateRestoreDryRun(value: Boolean) {
        _state.update { it.copy(dateRestoreDryRun = value) }
    }

    /** Triggers the face-recognition clustering pass server-side as a
     *  background task (`GET /api/admin/maintenance/face-clustering/stream`).
     *  Fire-and-forget: opens the stream just long enough to register the
     *  task entry, then drops the connection — the worker keeps running on
     *  `Task.Run` and surfaces in `/api/tasks`. This replaces the old
     *  synchronous POST that iterated every owner inline and tripped the
     *  client's socket timeout on large libraries. Independent from the face
     *  backfill loop — clustering operates on already-detected faces. */
    fun runFaceClustering() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(TriggerTimeoutMs) {
                    repository.faceClusteringStream().take(1).collect {}
                }
            }.onFailure { error ->
                _state.update { it.copy(errorMessage = describeFailure(null, error)) }
            }
            refresh(showLoading = false)
        }
    }

    /**
     * Queues every remaining unprocessed asset for an ML task, in one request.
     *
     * This used to be a loop: ask for a batch, look at what came back, ask for
     * another, stop when the server says there's nothing left. That termination
     * condition assumes the pending pool shrinks on every pass, and it doesn't
     * always. A model switched off in Ajustes completes each job instantly
     * without recording anything, so the pool is exactly as big after a batch as
     * before it — the loop ran forever, the row said "Encolando…" forever, and
     * because this ViewModel outlives the screen it kept doing so after the
     * admin had walked away. `all = true` moves the iteration server-side, where
     * a single query knows how much work there is.
     *
     * The server now refuses a backfill for a disabled model instead of
     * accepting it, which is the other half of the same fix: the request comes
     * back with a reason the row can show.
     */
    fun triggerMlBackfill(task: AdminRunTask) {
        val kind = task.backfillKind?.apiPath ?: return
        if (task in _state.value.triggering) return
        // Capture the `completed` count BEFORE anything is queued so the
        // session-scoped progress (`(now - baseline) / (delta + queue)`) starts
        // at 0 % and reaches 100 % when this run's work finishes — independently
        // of how much was already done before the admin tapped Iniciar.
        val baseline = _state.value.pending[task]?.completed ?: 0
        _state.update {
            it.copy(
                triggering = it.triggering + task,
                aiSessionBaseline = it.aiSessionBaseline + (task to baseline),
                errorMessage = null,
                infoMessage = null,
            )
        }

        viewModelScope.launch {
            val outcome = runCatching {
                repository.backfill(kind = kind, batchSize = null, onlyMissing = true, all = true)
            }
            // Resolved before the state update: the message needs a string
            // resource, and that lookup suspends.
            val enqueuedMessage: String = when (val resp = outcome.getOrNull()) {
                null -> ""
                else -> if (resp.enqueued > 0) getString(
                    Res.string.admin_run_tasks_ai_enqueued_format,
                    formatCount(resp.enqueued),
                    formatCount(resp.total),
                    // How long the server took to queue it: the wait the admin
                    // just sat through, named, so a slow enqueue and slow
                    // workers stop looking like the same thing.
                    formatDurationSeconds((resp.elapsedMs + 999) / 1000),
                ) else getString(Res.string.admin_run_tasks_ai_nothing_to_enqueue)
            }
            _state.update { current ->
                outcome.fold(
                    onSuccess = { resp ->
                        current.copy(
                            triggering = current.triggering - task,
                            // Nothing queued means nothing to track: keeping the
                            // baseline would leave the row showing a 0 % bar for
                            // a run that never started.
                            aiSessionBaseline = if (resp.enqueued > 0) current.aiSessionBaseline
                                                else current.aiSessionBaseline - task,
                            infoMessage = TaskMessage(task, enqueuedMessage),
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            triggering = current.triggering - task,
                            aiSessionBaseline = current.aiSessionBaseline - task,
                            errorMessage = describeFailure(task, error),
                        )
                    }
                )
            }
            refresh(showLoading = false)
        }
    }

    /** Cancels the matching server-side task. Pipeline / Other rows are
     *  the only ones with a cancel affordance (ML backfills enqueue then
     *  drop the HTTP handle — they finish on their own). */
    fun cancelTask(taskId: String) {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            runCatching { repository.cancelBackgroundTask(taskId) }
                .onFailure { error ->
                    _state.update { it.copy(errorMessage = describeFailure(null, error)) }
                }
            refresh(showLoading = false)
        }
    }

    /** Empties the queue for an ML task type: the jobs waiting, plus the ones
     *  sitting out a retry backoff, which the server used to leave behind so
     *  they marched back into the queue minutes later and made this button look
     *  inert. Jobs a worker already claimed run to completion — there's no safe
     *  way to abort an in-flight inference — so the row says how many those
     *  were instead of pretending the queue is empty. */
    fun cancelMlQueue(task: AdminRunTask) {
        val kind = task.backfillKind?.apiPath ?: return
        // Drop the session baseline immediately — a follow-up Iniciar should
        // snapshot a fresh `completed` value, not build on the cancelled run.
        _state.update {
            it.copy(
                aiSessionBaseline = it.aiSessionBaseline - task,
                errorMessage = null,
                infoMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.cancelMlQueue(kind) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            infoMessage = TaskMessage(task, buildString {
                                append("cola vaciada, ${resp.deleted} trabajo(s) eliminados")
                                if (resp.stillProcessing > 0) {
                                    append("; ${resp.stillProcessing} ya en curso terminarán solos")
                                }
                                append('.')
                            })
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(errorMessage = describeFailure(task, error)) }
                }
            refresh(showLoading = false)
        }
    }

    /** Subscribes to a running task's live stream and mirrors it into
     *  [AdminRunTasksUiState.liveTasks] so the row animates per-item. Idempotent
     *  per progress key. The follower lives in [viewModelScope], so it survives
     *  navigation within the session and is torn down on [onCleared]. */
    private fun followLiveTask(task: AdminRunTask, key: String, taskId: String, startPct: Double) {
        if (liveJobs[key]?.isActive == true) return
        liveJobs[key] = viewModelScope.launch {
            var completed = false
            // The resume stream replays buffered events from the start, so clamp
            // the bar to where polling already had it — otherwise (re)attaching
            // would visibly reset the percentage to 0 and re-climb.
            var maxPct = startPct
            runCatching {
                val flow = resumeFlow(task, taskId) ?: return@runCatching
                flow.collect { p ->
                    if (p.isCompleted) {
                        completed = true
                        return@collect
                    }
                    maxPct = maxOf(maxPct, p.percentage)
                    _state.update {
                        it.copy(
                            liveTasks = it.liveTasks + (key to BackgroundTaskDto(
                                id = p.taskId ?: taskId,
                                type = task.backgroundType ?: "",
                                status = "Running",
                                percentage = maxPct,
                                lastMessage = p.message,
                                startedAt = "",
                                finishedAt = null,
                            ))
                        )
                    }
                }
            }
            liveJobs.remove(key)
            _state.update { it.copy(liveTasks = it.liveTasks - key) }
            // Re-poll immediately only on a clean finish; on a dropped stream we
            // let the 10s poll loop re-attach so a broken connection can't
            // hot-loop refresh ↔ re-attach.
            if (completed) refresh(showLoading = false)
        }
    }

    private suspend fun resumeFlow(task: AdminRunTask, id: String): Flow<LiveProgress>? =
        when (task) {
            AdminRunTask.ExtractMetadata ->
                repository.resumeMetadataTaskStream(id)
                    .map { LiveProgress(it.percentage, it.message, it.isCompleted, it.taskId) }
            AdminRunTask.RestoreDates ->
                repository.resumeDateRestoreTaskStream(id)
                    .map { LiveProgress(it.percentage, it.message, it.isCompleted, it.taskId) }
            AdminRunTask.GenerateThumbnails ->
                repository.resumeThumbnailsTaskStream(id)
                    .map { LiveProgress(it.percentage, it.message, it.isCompleted, it.taskId) }
            AdminRunTask.IndexAssets ->
                repository.resumeIndexTaskStream(id)
                    .map { LiveProgress(it.percentage, it.message, it.isCompleted, it.taskId) }
            // Every maintenance kind resumes through the same stream — this is
            // what the Mantenimiento screen used before it was folded in here,
            // and it's why these rows show per-item progress instead of jumping
            // every 10s with the poll.
            else -> task.maintenanceKind?.let {
                repository.resumeMaintenanceTaskStream(id)
                    .map { LiveProgress(it.percentage, it.message, it.isCompleted, it.taskId) }
            }
        }

    /** Attach a live follower to every running task that lacks one. Called after
     *  each poll so freshly-triggered tasks AND tasks already running when the
     *  screen opens (e.g. started on the PWA, or before navigating away) both
     *  get smooth progress. */
    private fun attachLiveFollowers(tasks: List<BackgroundTaskDto>) {
        for (dto in tasks) {
            if (!dto.isRunning) continue
            // A maintenance task is identified by its kind; everything else by
            // its type. Same rule as the rows, so a follower can't end up
            // driving a different row than the one that started it.
            val key = dto.parameters["kind"] ?: dto.type
            val task = followableByKey[key] ?: continue
            followLiveTask(task, key, dto.id, dto.percentage)
        }
    }

    private suspend fun refresh(showLoading: Boolean) {
        if (showLoading) _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        // Each answer lands on the screen as it arrives. They used to be
        // gathered first — the row for a retry just sent from the failures
        // registry waited for six counts over the whole asset table before it
        // could light up, and by then a short queue had already drained.
        val tasks: List<BackgroundTaskDto> = coroutineScope {
            // The cheap one: who is working, right now.
            launch {
                runCatching { repository.enrichmentQueueSummary() }.getOrNull()?.let { summary ->
                    _state.update { it.copy(queues = summary.types) }
                }
            }
            for (task in AdminRunTask.entries) {
                val kind = task.backfillKind ?: continue
                launch {
                    // One of these timing out must not blank its row's counters
                    // and put a Play button back on a task that's mid-run: last
                    // known value beats no value.
                    runCatching { repository.pendingCount(kind.apiPath) }.getOrNull()?.let { count ->
                        _state.update { it.copy(pending = it.pending + (task to count)) }
                    }
                }
            }
            val tasksDeferred = async {
                runCatching { repository.listBackgroundTasks() }
                    .getOrNull()
                    ?: _state.value.backgroundTasks
            }
            tasksDeferred.await().also { fresh ->
                _state.update { it.copy(backgroundTasks = fresh) }
            }
        }
        _state.update { current ->
            // Drop any session baseline whose queue is now empty AND
            // isn't actively enqueueing — the session is done, so further
            // taps should start a fresh baseline rather than building on
            // a stale one.
            val pruned = current.aiSessionBaseline.filter { (task, _) ->
                val stillQueueing = task in current.triggering
                val stillDraining = current.queueOf(task).inQueue > 0
                stillQueueing || stillDraining
            }
            current.copy(aiSessionBaseline = pruned, isLoading = false)
        }
        // Keep a live follower attached to every running pipeline task so the
        // row updates per-asset and resumes after navigating back.
        attachLiveFollowers(tasks)
    }
}

@Composable
fun AdminRunTasksScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminRunTasksViewModel,
    onOpenTask: (AdminRunTask) -> Unit,
    /** Opens the enrichment failures registry filtered to one task type (the
     *  server's `AssetEnrichmentType` name). Reached from the rows whose queue
     *  has assets that gave up — the only place they can be retried. */
    onOpenFailures: (String) -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }
    LaunchedEffect(Unit) { viewModel.load() }

    // Snapshot Clock.now whenever a poll lands so every "hace X" label — and
    // the AI rows' "sin actividad desde hace X" — re-evaluates against the
    // same instant. Coarser granularity (minutes/hours/days) means we don't
    // need a per-second ticker here.
    val nowMs = remember(state.backgroundTasks, state.pending, state.queues) {
        Clock.System.now().toEpochMilliseconds()
    }

    // One lookup rule for every row: a maintenance task is identified by its
    // kind, everything else by its type. Keying maintenance by type would light
    // up all nine kinds the moment any one of them ran. Live followers
    // (per-item stream) override the coarser 10s-poll entry while active.
    val runningByKey = state.backgroundTasks
        .filter { it.isRunning }
        .associateBy { it.parameters["kind"] ?: it.type } + state.liveTasks
    val lastFinishedByKey = state.backgroundTasks
        .filter { !it.isRunning && it.finishedAt != null }
        .groupBy { it.parameters["kind"] ?: it.type }
        .mapValues { (_, list) -> list.maxByOrNull { it.finishedAt!! } }

    Box(modifier = Modifier.fillMaxSize()) {
    state.confirming?.let { task ->
        ConfirmActionDialog(
            title = stringResource(confirmTitleOf(task)),
            message = stringResource(confirmMessageOf(task)),
            confirmLabel = stringResource(Res.string.admin_run_tasks_confirm_run),
            isDestructive = true,
            isSubmitting = false,
            onDismiss = viewModel::dismissConfirm,
            onConfirm = viewModel::confirmAndRun,
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg + reservedTop, bottom = Spacing.lg + floatingNavBarReservedHeight()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Every action here is fire-and-forget against a 10s poll, so the only
        // place an outcome can land is a line at the top of the list.
        state.errorMessage?.let { msg ->
            item(key = "banner-error") {
                TaskBanner(msg, MaterialTheme.colorScheme.error)
            }
        }
        state.infoMessage?.let { msg ->
            item(key = "banner-info") {
                TaskBanner(msg, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }


        for (section in AdminRunTaskSection.entries) {
            val tasks = tasksOf(section)
            val expanded = section in state.expandedSections

            item(key = "header-${section.name}") {
                val anyRunning = tasks.any { task ->
                    task in state.triggering ||
                        task.progressKey?.let { runningByKey[it] } != null ||
                        state.queueOf(task).inQueue > 0
                }
                SectionHeader(
                    title = stringResource(sectionTitleOf(section)),
                    subtitle = sectionSubtitleOf(section)?.let { stringResource(it) },
                    count = tasks.size,
                    expanded = expanded,
                    // Work doesn't stop because you folded the group away, so
                    // the header keeps reporting it. Without this, folding a
                    // running section looks like cancelling it.
                    showLiveDot = anyRunning && !expanded,
                    onToggle = { viewModel.toggleSection(section) },
                )
            }

            // Collapsed sections emit no rows at all — the app's existing
            // pattern (see RuleConditionsEditor); nothing here uses
            // AnimatedVisibility.
            if (!expanded) continue

            items(tasks, key = { it.name }) { task ->
                val queue = state.queueOf(task)
                // The queue summary is fresher than the pending count (and is
                // there long before it), so its numbers win. A queue with work
                // but no pending count yet still gets a row that says so.
                val pending = state.pending[task]?.copy(
                    inQueue = queue.inQueue,
                    processing = queue.processing,
                    retrying = queue.retrying,
                    failed = queue.failed,
                ) ?: PendingCountResponse(
                    inQueue = queue.inQueue,
                    // Null: without the real count there's no "last finished"
                    // to measure a stall against, and the row would cry wolf.
                    processing = null,
                    retrying = queue.retrying,
                    failed = queue.failed,
                ).takeIf { task.backfillKind != null && queue.inQueue > 0 }
                val running = task.progressKey?.let { runningByKey[it] }
                val isMl = task.backfillKind != null && task.maintenanceKind == null
                val aiInProgress = isMl && queue.inQueue > 0
                // A sweep row (metadata, thumbnails) whose per-asset queue has
                // work: retries sent from the failures registry. Not the sweep
                // itself, so it keeps its Start button.
                val retryQueue = queue.takeIf { !isMl && running == null && it.inQueue > 0 }
                // Queued, and no worker has claimed one yet. Said out loud: it's
                // the moment right after a retry or a Start, when the admin is
                // looking for proof that the tap did something. Only with the
                // summary in hand — older servers don't say what's claimed.
                val waitingForWorker = state.queues.isNotEmpty() && running == null &&
                    queue.inQueue > 0 && queue.processing == 0
                // Not the retry queue: the sweep itself is idle then, and keeps
                // its options.
                val isActive = running != null || aiInProgress || task in state.triggering

                TaskRow(
                    task = task,
                    running = running,
                    aiInProgress = aiInProgress,
                    retryQueue = retryQueue,
                    waitingForWorker = waitingForWorker,
                    // Live Photos re-pairing reuses the /backfill enqueue loop
                    // but has no honest "N pending → 0" counter (no per-asset
                    // completion marker), so it shows as a fire-and-forget run
                    // rather than a draining queue with a percentage.
                    pending = if (task == AdminRunTask.MediaRecognition) null else pending,
                    sessionBaseline = state.aiSessionBaseline[task],
                    lastFinished = task.progressKey?.let { lastFinishedByKey[it] },
                    nowMs = nowMs,
                    isTriggering = task in state.triggering,
                    // Duplicates keeps its dedicated screen: it has toggles and
                    // per-group review UI that don't fit on a single row.
                    onOpen = if (task == AdminRunTask.DetectDuplicates) {
                        { onOpenTask(task) }
                    } else null,
                    onStart = { viewModel.start(task) },
                    secondaryActions = listOfNotNull(
                        // Face recognition gets a second action: re-cluster the
                        // existing detections without re-running the model.
                        if (task == AdminRunTask.FaceRecognition) SecondaryAction(
                            icon = Icons.Outlined.GroupWork,
                            label = stringResource(Res.string.admin_run_tasks_action_recluster),
                            onClick = viewModel::runFaceClustering
                        ) else null,
                        // The way out of the dead end: assets that exhausted
                        // their retries are skipped by every future backfill, so
                        // the row has no Start button and nothing to say. The
                        // registry is where they get retried or suppressed.
                        // Whenever the registry has anything of this type, not
                        // only once it's definitive: a sweep's failures are born
                        // with a retry scheduled, so a run that ended on "190
                        // fallidas" showed no way in for the hours the backoff
                        // takes to run out. The label says which of the two it is.
                        task.enrichmentType?.let { type ->
                            val label = listOfNotNull(
                                stringResource(Res.string.admin_run_tasks_ai_failed_format, queue.failed)
                                    .takeIf { queue.failed > 0 },
                                stringResource(Res.string.admin_run_tasks_ai_retrying_format, queue.retrying)
                                    .takeIf { queue.retrying > 0 },
                            ).joinToString(" · ")
                            if (label.isNotEmpty()) SecondaryAction(
                                icon = Icons.Outlined.ErrorOutline,
                                label = label,
                                // The error colour is for what's waiting on the
                                // admin; retries alone are still the queue's job.
                                isWarning = queue.failed > 0,
                                opensRegistry = true,
                                onClick = { onOpenFailures(type) }
                            ) else null
                        },
                    ),
                    onCancelAi = if (isMl || task == AdminRunTask.MediaRecognition) {
                        { viewModel.cancelMlQueue(task) }
                    } else null,
                    onCancel = if (isMl) null else { dto -> viewModel.cancelTask(dto.id) },
                )

                // Per-task options, only while idle so the row doesn't grow
                // during a run.
                if (!isActive) TaskOptions(task, state, viewModel)
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
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
}

/** The switches that belong to one task, rendered under its row. */
@Composable
private fun TaskOptions(
    task: AdminRunTask,
    state: AdminRunTasksUiState,
    viewModel: AdminRunTasksViewModel,
) {
    when (task) {
        AdminRunTask.GenerateThumbnails -> InlineToggle(
            label = stringResource(Res.string.admin_thumbnails_regenerate),
            checked = state.thumbnailsRegenerate,
            onCheckedChange = viewModel::setThumbnailsRegenerate
        )
        AdminRunTask.ExtractMetadata -> InlineToggle(
            label = stringResource(Res.string.admin_metadata_overwrite),
            checked = state.metadataOverwrite,
            onCheckedChange = viewModel::setMetadataOverwrite
        )
        // The server has always accepted ?dryRun=true here; nothing ever asked
        // for it. It's the cheapest way to find out what a purge would take
        // before it takes it.
        AdminRunTask.PurgeMissing -> InlineToggle(
            label = stringResource(Res.string.admin_run_tasks_purge_dry_run),
            checked = state.purgeDryRun,
            onCheckedChange = viewModel::setPurgeDryRun
        )
        AdminRunTask.RestoreDates -> Column {
            InlineToggle(
                label = stringResource(Res.string.admin_restore_dates_from_file),
                checked = state.dateRestoreFromFile,
                onCheckedChange = viewModel::setDateRestoreFromFile
            )
            InlineToggle(
                label = stringResource(Res.string.admin_restore_dates_infer),
                checked = state.dateRestoreInferFromPath,
                onCheckedChange = viewModel::setDateRestoreInferFromPath
            )
            InlineToggle(
                label = stringResource(Res.string.admin_restore_dates_use_file_date),
                checked = state.dateRestoreUseFileDate,
                onCheckedChange = viewModel::setDateRestoreUseFileDate
            )
            // Always on screen, greyed until there's a derived date to write
            // back. It used to appear only once you'd enabled an inference
            // above, which meant the one switch that touches your actual files
            // was invisible to anyone who hadn't already decided to use it.
            InlineToggle(
                label = stringResource(Res.string.admin_restore_dates_write_file),
                checked = state.dateRestoreWriteToFile,
                onCheckedChange = viewModel::setDateRestoreWriteToFile,
                enabled = state.dateRestoreInferFromPath || state.dateRestoreUseFileDate
            )
            InlineToggle(
                label = stringResource(Res.string.admin_restore_dates_dry_run),
                checked = state.dateRestoreDryRun,
                onCheckedChange = viewModel::setDateRestoreDryRun
            )
        }
        else -> Unit
    }
}

private fun sectionTitleOf(section: AdminRunTaskSection): StringResource = when (section) {
    AdminRunTaskSection.NewPhotos -> Res.string.admin_run_tasks_section_new_photos
    AdminRunTaskSection.Ai -> Res.string.admin_run_tasks_section_ai
    AdminRunTaskSection.Memories -> Res.string.admin_run_tasks_section_memories
    AdminRunTaskSection.Repair -> Res.string.admin_run_tasks_section_repair
    AdminRunTaskSection.Cleanup -> Res.string.admin_run_tasks_section_cleanup
}

private fun sectionSubtitleOf(section: AdminRunTaskSection): StringResource? = when (section) {
    AdminRunTaskSection.NewPhotos -> Res.string.admin_run_tasks_section_new_photos_subtitle
    AdminRunTaskSection.Memories -> Res.string.admin_run_tasks_section_memories_subtitle
    AdminRunTaskSection.Repair -> Res.string.admin_run_tasks_section_repair_subtitle
    AdminRunTaskSection.Cleanup -> Res.string.admin_run_tasks_section_cleanup_subtitle
    AdminRunTaskSection.Ai -> null
}

private fun confirmTitleOf(task: AdminRunTask): StringResource = when (task) {
    AdminRunTask.EmptyTrash -> Res.string.admin_run_tasks_confirm_empty_trash_title
    else -> Res.string.admin_run_tasks_confirm_purge_title
}

private fun confirmMessageOf(task: AdminRunTask): StringResource = when (task) {
    AdminRunTask.EmptyTrash -> Res.string.admin_run_tasks_confirm_empty_trash_message
    else -> Res.string.admin_run_tasks_confirm_purge_message
}

/** Inline switch rendered below a task row when there's a per-task option
 *  (e.g. Thumbnails.regenerate, Metadata.overwrite). Shown only while the row
 *  is idle so it doesn't compete with the progress visualisation.
 *
 *  An option that doesn't apply yet is greyed, never hidden: a switch you can't
 *  find is worse than one you can't use — you can't learn a task writes to your
 *  files from a control that only exists once you've already chosen to. */
@Composable
private fun InlineToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.xs, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/** Extra row in the card's footer, above the main Start/Stop one: the
 *  face-recognition clustering pass, and the way into the failures registry
 *  for a queue that has assets stuck. That second one isn't something the
 *  task does but something it reports, so [isWarning] paints it in the error
 *  colour with a chevron: a notice that navigates, not a button. */
data class SecondaryAction(
    val icon: ImageVector,
    val label: String,
    val isWarning: Boolean = false,
    /** Navigates rather than acts: gets the chevron, and goes first. */
    val opensRegistry: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A task group's header: tap anywhere to fold it away. Carries the task count
 * so a folded group still says how much is in there, and a live dot when work
 * is running inside one — folding a group hides its rows, not its work.
 *
 * Deliberately not a card, unlike everything else on this screen: the header
 * has to read as the parent of the task cards under it, and two nested cards
 * read as siblings. It earns its weight from [titleMedium] — matching the task
 * titles rather than sitting below them — plus the space above it, which is
 * what actually separates one group from the previous group's last row.
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String?,
    count: Int,
    expanded: Boolean,
    showLiveDot: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = Spacing.xs)
            .padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SectionCountPill(count)
                if (showLiveDot) LiveDot(active = true)
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = stringResource(
                if (expanded) Res.string.admin_run_tasks_section_collapse
                else Res.string.admin_run_tasks_section_expand
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One line of outcome at the top of the list. This screen's actions are all
 *  fire-and-forget requests whose effect only shows up on the next 10s poll, so
 *  a refusal, a 404 and a dead network otherwise look identical to a task that
 *  simply hasn't started yet. */
@Composable
private fun TaskBanner(message: TaskMessage, color: androidx.compose.ui.graphics.Color) {
    val text = message.task
        ?.let { "${stringResource(it.titleRes)}: ${message.text}" }
        ?: message.text
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs)
    )
}

/** How many tasks live in a group, as a shape rather than a loose number —
 *  a bare "5" next to a title reads as part of the title. */
@Composable
private fun SectionCountPill(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Full-width card for a single task. The header (icon · title · contextual
 * subtitle) owns the card's width; the actions live in a footer of full-
 * width list rows ([TaskRowActions]). Three visual states keyed off the card's
 * container colour so the section list scans at a glance:
 *
 * - Idle (`surfaceVariant`): header, the last run's message, "Iniciar".
 * - Triggering (`secondaryContainer`): same layout, "Iniciar" replaced by a
 *   small spinner while the trigger request flies out. Lasts at most
 *   [TriggerTimeoutMs] before the next refresh promotes it to Running
 *   or rolls back to Idle.
 * - Running (`primaryContainer`): ● LiveDot · "Analizando caras" subtitle ·
 *   linear progress bar · labelled numbers · "Detener".
 *
 * Tapping the card body opens the dedicated detail screen, for the one task
 * that has one.
 */
@Composable
private fun TaskRow(
    task: AdminRunTask,
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    retryQueue: EnrichmentQueueCounts?,
    waitingForWorker: Boolean,
    sessionBaseline: Int?,
    lastFinished: BackgroundTaskDto?,
    nowMs: Long,
    pending: PendingCountResponse?,
    isTriggering: Boolean,
    onOpen: (() -> Unit)?,
    onStart: () -> Unit,
    secondaryActions: List<SecondaryAction>,
    onCancelAi: (() -> Unit)?,
    onCancel: ((BackgroundTaskDto) -> Unit)?,
) {
    val isActive = running != null || aiInProgress || retryQueue != null
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isTriggering -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isTriggering -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (onOpen != null) mod.clickable(onClick = onOpen) else mod },
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        // The footer's rows and dividers run edge to edge, so only the content
        // above them is padded.
        Column {
            Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Icon(
                        imageVector = task.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(task.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor
                        )
                        TaskRowSubtitle(
                            task = task,
                            running = running,
                            aiInProgress = aiInProgress,
                            retryQueue = retryQueue,
                            waitingForWorker = waitingForWorker,
                            isTriggering = isTriggering,
                            lastFinished = lastFinished,
                            nowMs = nowMs,
                            pending = pending,
                            contentColor = contentColor
                        )
                    }
                }
                // Progress bar: pipeline DTO, enqueueing counter, or queue-
                // draining ratio — chosen by exclusive `when` so we never
                // overlay two metrics on the same bar (which made the % go
                // backwards while we were still adding work).
                when {
                    running != null -> {
                        Spacer(Modifier.size(Spacing.sm))
                        val pct = (running.percentage / 100.0).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                    aiInProgress && pending != null -> {
                        Spacer(Modifier.size(Spacing.sm))
                        // Session-scoped progress: how much of *this* run's
                        // work the workers have completed. Stays at 0 % when
                        // the queue is full, climbs to 100 % as it drains —
                        // independent of how many assets were already done
                        // before the user pressed Iniciar. Without a baseline
                        // (queue started from the PWA, or before this screen
                        // opened) the fraction is unknown and the bar is
                        // indeterminate rather than misleading. A stalled queue
                        // paints the bar in error colour: the subtitle says why.
                        val progress = aiQueueProgress(pending, sessionBaseline, nowMs)
                        val barColor = if (progress.isStalled) MaterialTheme.colorScheme.error
                                       else ProgressIndicatorDefaults.linearColor
                        val fraction = progress.fraction
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction.coerceIn(0f, 1f) },
                                color = barColor,
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                color = barColor,
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                        }
                    }
                    aiInProgress -> {
                        Spacer(Modifier.size(Spacing.sm))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }

                // The numbers behind the bar, one per line with a label. They
                // used to share the subtitle's single line with everything else
                // and get cut off after the second one on a phone.
                if (isActive) {
                    TaskRowDetails(
                        running = running,
                        aiInProgress = aiInProgress,
                        sessionBaseline = sessionBaseline,
                        nowMs = nowMs,
                        pending = pending,
                        contentColor = contentColor,
                    )
                }

                // What the last run actually said. Without this the row reports
                // "Última ejecución hace 2 min" whether the task did the work or
                // died on the first line, which makes a failing task and a dead
                // button look exactly the same.
                if (!isActive && lastFinished != null && lastFinished.lastMessage.isNotBlank()) {
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        lastFinished.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lastFinished.status == "Failed") MaterialTheme.colorScheme.error
                                else contentColor.copy(alpha = 0.75f)
                    )
                }
            }

            TaskRowActions(
                running = running,
                aiInProgress = aiInProgress,
                pending = pending,
                isTriggering = isTriggering,
                contentColor = contentColor,
                secondaryActions = secondaryActions,
                onStart = onStart,
                onCancel = onCancel,
                onCancelAi = onCancelAi
            )
        }
    }
}

/**
 * The one line under the title. While the task runs it says what the task
 * is doing, in words ("Analizando caras"); the numbers go to
 * [TaskRowDetails] under the bar, one per line, where they fit. Idle rows
 * keep their counters and "last run" here, allowed a second line instead
 * of an ellipsis.
 */
@Composable
private fun TaskRowSubtitle(
    task: AdminRunTask,
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    retryQueue: EnrichmentQueueCounts?,
    waitingForWorker: Boolean,
    isTriggering: Boolean,
    lastFinished: BackgroundTaskDto?,
    nowMs: Long,
    pending: PendingCountResponse?,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val mutedColor = contentColor.copy(alpha = 0.75f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (running != null || aiInProgress || retryQueue != null) {
            LiveDot(active = true)
        }

        val text: String = when {
            running != null -> stringResource(task.activeLabelRes)
            waitingForWorker -> stringResource(Res.string.admin_run_tasks_status_waiting)
            aiInProgress -> stringResource(task.activeLabelRes)
            // A sweep row working through retries from the failures registry.
            retryQueue != null ->
                stringResource(Res.string.admin_run_tasks_retry_queue_format, retryQueue.inQueue)
            // The request that starts it is still in flight: for an AI row
            // that's the server queueing the whole pool, for the rest the
            // stream opening.
            isTriggering ->
                if (task.backfillKind != null && task.maintenanceKind == null)
                    stringResource(Res.string.admin_run_tasks_status_queueing)
                else stringResource(Res.string.admin_run_tasks_status_starting)
            // ML idle: the pending counters, plus what's wrong with them. A
            // queue that keeps failing looks exactly like a queue that keeps
            // working if all you report is its size, and the assets that gave
            // up for good vanish from "sin procesar" entirely — which is how a
            // row ends up claiming there is nothing left to do on a library
            // with thousands of unanalysed photos.
            pending != null -> {
                val troubles = listOfNotNull(
                    stringResource(Res.string.admin_run_tasks_ai_retrying_format, pending.retrying)
                        .takeIf { pending.retrying > 0 },
                    stringResource(Res.string.admin_run_tasks_ai_failed_format, pending.failed)
                        .takeIf { pending.failed > 0 },
                )
                val counters = stringResource(
                    Res.string.admin_run_tasks_pending_format,
                    pending.unprocessed,
                    pending.inQueue
                )
                (listOf(counters) + troubles).joinToString(" · ")
            }
            lastFinished?.finishedAt != null -> {
                val finishedMs = runCatching { Instant.parse(lastFinished.finishedAt!!).toEpochMilliseconds() }
                    .getOrNull()
                if (finishedMs != null) {
                    stringResource(
                        Res.string.admin_run_tasks_last_run_format,
                        formatRelativeTime((nowMs - finishedMs) / 1000L)
                    )
                } else stringResource(Res.string.admin_run_tasks_last_run_never)
            }
            // Anything that reports a task entry can honestly say it never ran.
            // ML rows can't: their work is a queue of per-asset jobs with no
            // single "run", so they show their pending counters instead.
            task.progressKey != null -> stringResource(Res.string.admin_run_tasks_last_run_never)
            else -> ""
        }
        if (text.isNotEmpty()) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One labelled value of a running task: "Progreso   1.234 de 20.000 (12 %)". */
private data class TaskDetailLine(
    val label: String,
    val value: String,
    val highlight: Boolean = false,
)

/**
 * The numbers of a running task, one per line, under its progress bar.
 *
 * For a pipeline or maintenance run: the percentage, the server's own status
 * message in full (it names the file or the phase), and when it started.
 * For an AI queue: this run's progress, the measured rate and the time left
 * at it, what's claimed right now, and — in the error colour — a queue that
 * has gone quiet or assets that are failing. Nothing here is abbreviated
 * or cut: the rows only exist while the task runs, so the height is spent
 * exactly when the admin is looking.
 */
@Composable
private fun TaskRowDetails(
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    sessionBaseline: Int?,
    nowMs: Long,
    pending: PendingCountResponse?,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val lines = mutableListOf<TaskDetailLine>()
    when {
        running != null -> {
            val pct = running.percentage.toInt().coerceIn(0, 100)
            // The percent sign is concatenated in Kotlin (rather than baked
            // into a format string as `%%`) because compose-resources renders
            // the literal `%%` as-is on some targets.
            lines += TaskDetailLine(stringResource(Res.string.admin_run_tasks_detail_progress), "$pct%")
            if (running.lastMessage.isNotBlank()) {
                lines += TaskDetailLine(stringResource(Res.string.admin_run_tasks_detail_status), running.lastMessage)
            }
            // Live followers carry no startedAt; the polled entry does.
            val startedMs = runCatching { Instant.parse(running.startedAt).toEpochMilliseconds() }.getOrNull()
            if (startedMs != null) {
                lines += TaskDetailLine(
                    stringResource(Res.string.admin_run_tasks_detail_started),
                    stringResource(Res.string.admin_run_tasks_detail_started_value, formatRelativeTime((nowMs - startedMs) / 1000L)),
                )
            }
        }
        aiInProgress && pending != null -> {
            val progress = aiQueueProgress(pending, sessionBaseline, nowMs)
            val fraction = progress.fraction
            lines += if (fraction != null) {
                val pct = (fraction * 100).toInt().coerceIn(0, 100)
                TaskDetailLine(
                    stringResource(Res.string.admin_run_tasks_detail_progress),
                    stringResource(
                        Res.string.admin_run_tasks_detail_progress_value,
                        formatCount(progress.done), formatCount(progress.total), "$pct%"
                    ),
                )
            } else {
                // No baseline (queue started elsewhere): the queue size, no %.
                TaskDetailLine(
                    stringResource(Res.string.admin_run_tasks_detail_queue),
                    stringResource(Res.string.admin_run_tasks_detail_queue_value, formatCount(pending.inQueue)),
                )
            }
            val stalledFor = progress.stalledForSeconds
            if (stalledFor != null) {
                lines += TaskDetailLine(
                    stringResource(Res.string.admin_run_tasks_detail_stalled),
                    stringResource(Res.string.admin_run_tasks_detail_stalled_value, formatRelativeTime(stalledFor)),
                    highlight = true,
                )
            } else {
                progress.perMinute?.let {
                    lines += TaskDetailLine(
                        stringResource(Res.string.admin_run_tasks_detail_rate),
                        stringResource(Res.string.admin_run_tasks_detail_rate_value, it),
                    )
                }
                progress.etaSeconds?.let {
                    lines += TaskDetailLine(stringResource(Res.string.admin_run_tasks_detail_remaining), formatEta(it))
                }
                progress.processing?.let {
                    lines += TaskDetailLine(stringResource(Res.string.admin_run_tasks_detail_processing), formatCount(it))
                }
            }
            // Only the retries: the ones that failed for good have their own
            // row in the footer, right under this, and it opens the registry.
            if (pending.retrying > 0) {
                lines += TaskDetailLine(
                    stringResource(Res.string.admin_run_tasks_detail_problems),
                    stringResource(Res.string.admin_run_tasks_ai_retrying_format, pending.retrying),
                )
            }
        }
    }
    if (lines.isEmpty()) return

    val mutedColor = contentColor.copy(alpha = 0.75f)
    val errorColor = MaterialTheme.colorScheme.error
    Spacer(Modifier.size(Spacing.sm))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        for (line in lines) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    line.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (line.highlight) errorColor else mutedColor,
                    modifier = Modifier.width(92.dp)
                )
                Text(
                    line.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.highlight) errorColor else contentColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The card's footer: one full-width row per thing, split by hairlines, like a
 * settings list. They used to be bare icon buttons on the title's own line,
 * which left an AI row's title some 90 dp on a phone — "Reconocimiento
 * facial" broke in two — and said nothing about what each icon did.
 *
 * Top to bottom: the failures notice (error colour, chevron: it navigates),
 * any other secondary action, and last the task's own Start/Stop. While the
 * trigger request is in flight that last row stays put with a spinner, so the
 * card doesn't change height under the finger. Emits nothing at all for a
 * row with nothing to offer (an idle AI row with no work left and no
 * failures).
 */
@Composable
private fun TaskRowActions(
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    pending: PendingCountResponse?,
    isTriggering: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    secondaryActions: List<SecondaryAction>,
    onStart: () -> Unit,
    onCancel: ((BackgroundTaskDto) -> Unit)?,
    onCancelAi: (() -> Unit)?,
) {
    // Pipeline/Other running with cancel affordance, or the ML queue: there
    // the same handler stops the enqueueing loop AND clears Pending server-
    // side, so the row drops out of every "active" state in one tap.
    val onStop: (() -> Unit)? = when {
        running != null && onCancel != null -> ({ onCancel(running) })
        aiInProgress && onCancelAi != null -> onCancelAi
        else -> null
    }
    // Idle ML row with nothing left to enqueue: no Start row, so the user
    // isn't tempted to fire an empty backfill.
    val canStart = onStop == null && !isTriggering && running == null &&
        !(pending != null && pending.unprocessed == 0)

    val errorColor = MaterialTheme.colorScheme.error
    for (action in secondaryActions.sortedByDescending { it.isWarning || it.opensRegistry }) {
        TaskActionRow(
            icon = action.icon,
            label = action.label,
            color = if (action.isWarning) errorColor else contentColor,
            dividerColor = contentColor,
            showChevron = action.isWarning || action.opensRegistry,
            onClick = action.onClick,
        )
    }
    when {
        onStop != null -> TaskActionRow(
            icon = Icons.Outlined.Stop,
            label = stringResource(Res.string.admin_run_tasks_action_stop),
            color = errorColor,
            dividerColor = contentColor,
            onClick = onStop,
        )
        isTriggering -> TaskActionRow(
            icon = null,
            label = stringResource(Res.string.admin_run_tasks_action_start),
            color = contentColor.copy(alpha = 0.6f),
            dividerColor = contentColor,
            onClick = null,
        )
        canStart -> TaskActionRow(
            icon = Icons.Outlined.PlayArrow,
            label = stringResource(Res.string.admin_run_tasks_action_start),
            color = MaterialTheme.colorScheme.primary,
            dividerColor = contentColor,
            onClick = onStart,
        )
    }
}

/**
 * One footer row: hairline, then icon · label (· chevron), lined up with the
 * header's icon and title. A null [icon] is the in-flight state: a spinner in
 * the icon's place. A null [onClick] makes the row inert.
 */
@Composable
private fun TaskActionRow(
    icon: ImageVector?,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    dividerColor: androidx.compose.ui.graphics.Color,
    showChevron: Boolean = false,
    onClick: (() -> Unit)?,
) {
    HorizontalDivider(color = dividerColor.copy(alpha = 0.12f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (onClick != null) mod.clickable(role = Role.Button, onClick = onClick) else mod }
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = color
            )
        }
    }
}

/**
 * "ahora" / "5 min" / "2 h" / "4 d" — coarse-grain humanized duration
 * for the "Última ejecución hace X" subtitle. Coarser than
 * [formatDurationSeconds] (which is for live elapsed timers in
 * sub-minute precision).
 */
@Composable
private fun formatRelativeTime(seconds: Long): String = when {
    seconds < 60 -> stringResource(Res.string.admin_run_tasks_time_now)
    seconds < 3600 -> stringResource(Res.string.admin_run_tasks_time_m_format, seconds / 60)
    seconds < 86400 -> stringResource(Res.string.admin_run_tasks_time_h_format, seconds / 3600)
    else -> stringResource(Res.string.admin_run_tasks_time_d_format, seconds / 86400)
}
