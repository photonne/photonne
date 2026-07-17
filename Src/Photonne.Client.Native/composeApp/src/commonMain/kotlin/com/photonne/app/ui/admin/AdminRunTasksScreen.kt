package com.photonne.app.ui.admin

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Flight
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
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.BackgroundTaskDto
import com.photonne.app.data.models.PendingCountResponse
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_run_tasks_action_cancel
import com.photonne.app.resources.admin_run_tasks_action_start
import com.photonne.app.resources.admin_run_tasks_ai_enqueuing_format
import com.photonne.app.resources.admin_run_tasks_ai_progress_format
import com.photonne.app.resources.admin_run_tasks_last_run_format
import com.photonne.app.resources.admin_run_tasks_last_run_never
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
import com.photonne.app.resources.admin_maintenance_action_missing
import com.photonne.app.resources.admin_maintenance_action_orphans
import com.photonne.app.resources.admin_maintenance_action_purge_missing
import com.photonne.app.resources.admin_maintenance_action_recalculate
import com.photonne.app.resources.admin_maintenance_desc_empty_trash
import com.photonne.app.resources.admin_maintenance_desc_missing
import com.photonne.app.resources.admin_maintenance_desc_orphans
import com.photonne.app.resources.admin_maintenance_desc_purge_missing
import com.photonne.app.resources.admin_maintenance_desc_recalculate
import com.photonne.app.resources.admin_run_tasks_section_memories_subtitle
import com.photonne.app.resources.admin_run_tasks_status_in_progress
import com.photonne.app.resources.admin_run_tasks_time_d_format
import com.photonne.app.resources.admin_run_tasks_time_h_format
import com.photonne.app.resources.admin_run_tasks_time_m_format
import com.photonne.app.resources.admin_run_tasks_time_now
import com.photonne.app.resources.admin_backfill_action_clustering
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
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.StringResource
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
        icon = Icons.Outlined.Sync,
        backfillKind = null,
        section = AdminRunTaskSection.NewPhotos,
        backgroundType = "IndexAssets",
    ),
    ExtractMetadata(
        titleRes = Res.string.admin_system_metadata,
        subtitleRes = Res.string.admin_system_metadata_subtitle,
        icon = Icons.Outlined.Info,
        backfillKind = null,
        section = AdminRunTaskSection.NewPhotos,
        backgroundType = "Metadata",
    ),
    GenerateThumbnails(
        titleRes = Res.string.admin_system_thumbnails,
        subtitleRes = Res.string.admin_system_thumbnails_subtitle,
        icon = Icons.Outlined.Image,
        backfillKind = null,
        section = AdminRunTaskSection.NewPhotos,
        backgroundType = "Thumbnails",
    ),
    RestoreDates(
        titleRes = Res.string.admin_system_restore_dates,
        subtitleRes = Res.string.admin_system_restore_dates_subtitle,
        icon = Icons.Outlined.DateRange,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = "DateRestore",
    ),
    FaceRecognition(
        titleRes = Res.string.admin_system_face,
        subtitleRes = Res.string.admin_system_face_subtitle,
        icon = Icons.Outlined.Face,
        backfillKind = AdminBackfillKind.FaceRecognition,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    ObjectDetection(
        titleRes = Res.string.admin_system_object,
        subtitleRes = Res.string.admin_system_object_subtitle,
        icon = Icons.Outlined.Category,
        backfillKind = AdminBackfillKind.ObjectDetection,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    SceneClassification(
        titleRes = Res.string.admin_system_scene,
        subtitleRes = Res.string.admin_system_scene_subtitle,
        icon = Icons.Outlined.Landscape,
        backfillKind = AdminBackfillKind.SceneClassification,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    TextRecognition(
        titleRes = Res.string.admin_system_text,
        subtitleRes = Res.string.admin_system_text_subtitle,
        icon = Icons.Outlined.TextFields,
        backfillKind = AdminBackfillKind.TextRecognition,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    ImageEmbedding(
        titleRes = Res.string.admin_system_embedding,
        subtitleRes = Res.string.admin_system_embedding_subtitle,
        icon = Icons.Outlined.ImageSearch,
        backfillKind = AdminBackfillKind.ImageEmbedding,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    InterpolateLocations(
        titleRes = Res.string.admin_system_interpolate,
        subtitleRes = Res.string.admin_system_interpolate_subtitle,
        icon = Icons.Outlined.MyLocation,
        backfillKind = null,
        section = AdminRunTaskSection.Memories,
        backgroundType = "Maintenance",
        maintenanceKind = "interpolate-locations",
    ),
    ReverseGeocode(
        titleRes = Res.string.admin_system_geocode,
        subtitleRes = Res.string.admin_system_geocode_subtitle,
        icon = Icons.Outlined.Place,
        backfillKind = null,
        section = AdminRunTaskSection.Memories,
        backgroundType = "Maintenance",
        maintenanceKind = "reverse-geocode",
    ),
    DetectTrips(
        titleRes = Res.string.admin_system_trips,
        subtitleRes = Res.string.admin_system_trips_subtitle,
        icon = Icons.Outlined.Flight,
        backfillKind = null,
        section = AdminRunTaskSection.Memories,
        backgroundType = "Maintenance",
        maintenanceKind = "detect-trips",
    ),
    GenerateMemories(
        titleRes = Res.string.admin_system_memories,
        subtitleRes = Res.string.admin_system_memories_subtitle,
        icon = Icons.Outlined.AutoAwesome,
        backfillKind = null,
        section = AdminRunTaskSection.Memories,
        backgroundType = "Maintenance",
        maintenanceKind = "generate-memories",
    ),
    MediaRecognition(
        titleRes = Res.string.admin_system_media_recognition,
        subtitleRes = Res.string.admin_system_media_recognition_subtitle,
        icon = Icons.Outlined.MotionPhotosOn,
        backfillKind = AdminBackfillKind.MediaRecognition,
        section = AdminRunTaskSection.Repair,
        backgroundType = null,
    ),
    DetectDuplicates(
        titleRes = Res.string.admin_system_duplicates,
        subtitleRes = Res.string.admin_system_duplicates_subtitle,
        icon = Icons.Outlined.ContentCopy,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = null,
    ),
    MarkMissingFiles(
        titleRes = Res.string.admin_maintenance_action_missing,
        subtitleRes = Res.string.admin_maintenance_desc_missing,
        icon = Icons.Outlined.SearchOff,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = MaintenanceTaskType,
        maintenanceKind = "missing-files",
    ),
    RecalculateSizes(
        titleRes = Res.string.admin_maintenance_action_recalculate,
        subtitleRes = Res.string.admin_maintenance_desc_recalculate,
        icon = Icons.Outlined.Straighten,
        backfillKind = null,
        section = AdminRunTaskSection.Repair,
        backgroundType = MaintenanceTaskType,
        maintenanceKind = "recalculate-sizes",
    ),
    OrphanThumbnails(
        titleRes = Res.string.admin_maintenance_action_orphans,
        subtitleRes = Res.string.admin_maintenance_desc_orphans,
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
}

private const val PollIntervalMs = 10_000L

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
     *  request failed silently). */
    val triggering: Set<AdminRunTask> = emptySet(),
    /** ML tasks whose hub-level enqueueing loop is currently firing
     *  `/backfill` batches against the server. Separates the "Encolando
     *  234 / 1000" phase from the later "workers draining the queue"
     *  phase so the progress bar doesn't have to mix two metrics on the
     *  same row (which made the % visibly go backwards while we kept
     *  enqueueing). */
    val enqueuing: Map<AdminRunTask, EnqueuingProgress> = emptyMap(),
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
    val errorMessage: String? = null,
)

/** Session-scoped snapshot of an in-flight ML enqueue loop driven by
 *  [AdminRunTasksViewModel.triggerMlBackfill]. */
data class EnqueuingProgress(
    val initial: Int,
    val enqueuedSoFar: Int,
)

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

    // Active enqueuing loops keyed by ML task. The hub-level "Iniciar"
    // mirrors the detail screen's iterative POST behaviour — we keep
    // calling `/backfill` until the server reports no unprocessed work
    // left. This map lets the cancel button reach in and stop the loop
    // server-side cancellation alone can't, because the loop would
    // otherwise immediately refill the queue we just cleared.
    private val mlBackfillJobs = mutableMapOf<AdminRunTask, Job>()

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
                delay(PollIntervalMs)
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
        _state.update { it.copy(triggering = it.triggering + task) }
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
            }
            refresh(showLoading = false)
            _state.update { it.copy(triggering = it.triggering - task) }
        }
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
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(TriggerTimeoutMs) {
                    repository.faceClusteringStream().take(1).collect {}
                }
            }
            refresh(showLoading = false)
        }
    }

    /**
     * Enqueues every remaining unprocessed asset for an ML task. The
     * server caps each `/backfill` POST at `TaskSettings.BackfillBatchSize`
     * (default 500, max 5000), so "encolar todo" needs an iterative loop
     * here — exactly what `AdminBackfillViewModel.start()` does on the
     * detail screen. The hub favours fire-and-forget: once the first
     * batch lands, the row flips to its running visual (`inQueue > 0`)
     * and the loop keeps adding batches in the background. The admin
     * goes into the detail screen for batch-size tweaks or `overwrite`.
     *
     * The loop bails on the first response with `enqueued == 0` to avoid
     * spinning forever if the server can't drain the unprocessed pool
     * (e.g. every remaining asset is already Pending/Processing).
     */
    fun triggerMlBackfill(task: AdminRunTask) {
        val kind = task.backfillKind?.apiPath ?: return
        if (task in _state.value.triggering) return
        if (mlBackfillJobs[task]?.isActive == true) return
        // Capture the `completed` count BEFORE the first batch goes out so
        // the session-scoped progress (`(now - baseline) / (delta + queue)`)
        // starts at 0 % and reaches 100 % when everything we enqueue here
        // finishes — independently of how many assets were already done
        // before the user tapped Iniciar.
        val baseline = _state.value.pending[task]?.completed ?: 0
        _state.update {
            it.copy(
                triggering = it.triggering + task,
                aiSessionBaseline = it.aiSessionBaseline + (task to baseline)
            )
        }

        mlBackfillJobs[task] = viewModelScope.launch {
            var totalEnqueued = 0
            var initial = 0
            var firstBatchSettled = false
            try {
                while (isActive) {
                    val resp = runCatching {
                        repository.backfill(kind = kind, batchSize = null, onlyMissing = true)
                    }.getOrNull() ?: break

                    totalEnqueued += resp.enqueued

                    if (!firstBatchSettled) {
                        // The server's `total` on this first response is
                        // the unprocessed snapshot *before* this batch
                        // enqueued anything — that's exactly the figure
                        // we want to show as "of N".
                        initial = resp.total
                        _state.update {
                            it.copy(
                                triggering = it.triggering - task,
                                enqueuing = it.enqueuing + (task to EnqueuingProgress(
                                    initial = initial,
                                    enqueuedSoFar = totalEnqueued
                                ))
                            )
                        }
                        refresh(showLoading = false)
                        firstBatchSettled = true
                    } else {
                        _state.update {
                            val current = it.enqueuing[task] ?: return@update it
                            it.copy(
                                enqueuing = it.enqueuing + (task to current.copy(
                                    enqueuedSoFar = totalEnqueued
                                ))
                            )
                        }
                    }

                    if (resp.enqueued == 0) break
                    val remaining = (resp.total - resp.enqueued).coerceAtLeast(0)
                    if (remaining == 0) break
                }
            } finally {
                _state.update {
                    it.copy(
                        triggering = it.triggering - task,
                        enqueuing = it.enqueuing - task,
                    )
                }
                mlBackfillJobs.remove(task)
                refresh(showLoading = false)
            }
        }
    }

    /** Cancels the matching server-side task. Pipeline / Other rows are
     *  the only ones with a cancel affordance (ML backfills enqueue then
     *  drop the HTTP handle — they finish on their own). */
    fun cancelTask(taskId: String) {
        viewModelScope.launch {
            runCatching { repository.cancelBackgroundTask(taskId) }
            refresh(showLoading = false)
        }
    }

    /** Clears the Pending queue for an ML task type AND stops the local
     *  enqueuing loop (if any), so cancelling actually halts the work
     *  instead of letting the loop refill what we just drained. Jobs
     *  already Processing are owned by workers and run to completion —
     *  there's no safe way to abort an in-flight inference from here. */
    fun cancelMlQueue(task: AdminRunTask) {
        val kind = task.backfillKind?.apiPath ?: return
        mlBackfillJobs[task]?.cancel()
        mlBackfillJobs.remove(task)
        // Drop the session baseline immediately — a follow-up Iniciar
        // should snapshot a fresh `completed` value, not build on top of
        // the cancelled session.
        _state.update { it.copy(aiSessionBaseline = it.aiSessionBaseline - task) }
        viewModelScope.launch {
            runCatching { repository.cancelMlQueue(kind) }
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
        if (showLoading) _state.update { it.copy(isLoading = true, errorMessage = null) }
        val pendingDeferred: kotlinx.coroutines.Deferred<Map<AdminRunTask, PendingCountResponse>>
        val tasksDeferred: kotlinx.coroutines.Deferred<List<BackgroundTaskDto>>
        coroutineScope {
            pendingDeferred = async {
                AdminRunTask.values()
                    .filter { it.backfillKind != null }
                    .map { task ->
                        async {
                            runCatching {
                                repository.pendingCount(task.backfillKind!!.apiPath)
                            }.getOrNull()?.let { task to it }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .toMap()
            }
            tasksDeferred = async {
                runCatching { repository.listBackgroundTasks() }
                    .getOrNull()
                    ?: _state.value.backgroundTasks
            }
        }
        val pending = pendingDeferred.await()
        val tasks = tasksDeferred.await()
        _state.update { current ->
            // Drop any session baseline whose queue is now empty AND
            // isn't actively enqueueing — the session is done, so further
            // taps should start a fresh baseline rather than building on
            // a stale one.
            val pruned = current.aiSessionBaseline.filter { (task, _) ->
                val stillEnqueueing = task in current.enqueuing
                val stillDraining = (pending[task]?.inQueue ?: 0) > 0
                stillEnqueueing || stillDraining
            }
            current.copy(
                pending = pending,
                backgroundTasks = tasks,
                aiSessionBaseline = pruned,
                isLoading = false
            )
        }
        // Keep a live follower attached to every running pipeline task so the
        // row updates per-asset and resumes after navigating back.
        attachLiveFollowers(tasks)
    }
}

@Composable
fun AdminRunTasksScreen(
    viewModel: AdminRunTasksViewModel,
    onOpenTask: (AdminRunTask) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }
    LaunchedEffect(Unit) { viewModel.load() }

    // Snapshot Clock.now whenever the polled task list changes so every
    // "hace X" label re-evaluates against the same instant. Coarser
    // granularity (minutes/hours/days) means we don't need a per-second
    // ticker here.
    val nowMs = remember(state.backgroundTasks) {
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + floatingNavBarReservedHeight()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.errorMessage?.let { msg ->
            item {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        for (section in AdminRunTaskSection.entries) {
            val tasks = tasksOf(section)
            val expanded = section in state.expandedSections

            item(key = "header-${section.name}") {
                val anyRunning = tasks.any { task ->
                    task in state.triggering ||
                        task.progressKey?.let { runningByKey[it] } != null ||
                        (state.pending[task]?.inQueue ?: 0) > 0 ||
                        task in state.enqueuing
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
                val pending = state.pending[task]
                val running = task.progressKey?.let { runningByKey[it] }
                val isMl = task.backfillKind != null && task.maintenanceKind == null
                val aiInProgress = isMl && (pending?.inQueue ?: 0) > 0
                val isActive = running != null || aiInProgress || task in state.triggering

                TaskRow(
                    task = task,
                    running = running,
                    aiInProgress = aiInProgress,
                    // Live Photos re-pairing reuses the /backfill enqueue loop
                    // but has no honest "N pending → 0" counter (no per-asset
                    // completion marker), so it shows as a fire-and-forget run
                    // rather than a draining queue with a percentage.
                    pending = if (task == AdminRunTask.MediaRecognition) null else pending,
                    enqueuingProgress = state.enqueuing[task],
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
                    // Face recognition gets a second action: re-cluster the
                    // existing detections without re-running the model.
                    onSecondary = if (task == AdminRunTask.FaceRecognition) {
                        SecondaryAction(
                            icon = Icons.Outlined.GroupWork,
                            contentDescription = stringResource(Res.string.admin_backfill_action_clustering),
                            onClick = viewModel::runFaceClustering
                        )
                    } else null,
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
            .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
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

/** Optional extra action surfaced on the right side of the row,
 *  alongside the main Start/Cancel button. Used today only for the
 *  face-recognition clustering pass. */
data class SecondaryAction(
    val icon: ImageVector,
    val contentDescription: String?,
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
            .padding(horizontal = 4.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

/** How many tasks live in a group, as a shape rather than a loose number —
 *  a bare "5" next to a title reads as part of the title. */
@Composable
private fun SectionCountPill(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Full-width row for a single task. Three visual states keyed off the
 * card's container colour so the section list scans at a glance:
 *
 * - Idle (`surfaceVariant`): icon · title · contextual subtitle ·
 *   ▶ Start button.
 * - Triggering (`secondaryContainer`): same layout, ▶ replaced by a
 *   small spinner while the trigger request flies out. Lasts at most
 *   [TriggerTimeoutMs] before the next refresh promotes it to Running
 *   or rolls back to Idle.
 * - Running (`primaryContainer`): ● LiveDot · "45 % — Procesando 234/520"
 *   subtitle · linear progress bar · ⏹ Cancel button.
 *
 * Tapping the card body opens the dedicated detail screen. Tapping the
 * trailing icon button executes the inline action without leaving the
 * hub.
 */
@Composable
private fun TaskRow(
    task: AdminRunTask,
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    enqueuingProgress: EnqueuingProgress?,
    sessionBaseline: Int?,
    lastFinished: BackgroundTaskDto?,
    nowMs: Long,
    pending: PendingCountResponse?,
    isTriggering: Boolean,
    onOpen: (() -> Unit)?,
    onStart: () -> Unit,
    onSecondary: SecondaryAction?,
    onCancelAi: (() -> Unit)?,
    onCancel: ((BackgroundTaskDto) -> Unit)?,
) {
    val isActive = running != null || aiInProgress || enqueuingProgress != null
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
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        enqueuingProgress = enqueuingProgress,
                        sessionBaseline = sessionBaseline,
                        lastFinished = lastFinished,
                        nowMs = nowMs,
                        pending = pending,
                        contentColor = contentColor
                    )
                }
                // Secondary action (face clustering today) sits between
                // the title block and the primary action so it never
                // collides with the Start/Cancel slot.
                if (onSecondary != null) {
                    IconButton(onClick = onSecondary.onClick) {
                        Icon(
                            imageVector = onSecondary.icon,
                            contentDescription = onSecondary.contentDescription,
                            tint = contentColor
                        )
                    }
                }
                TaskRowAction(
                    running = running,
                    aiInProgress = aiInProgress,
                    enqueuing = enqueuingProgress != null,
                    pending = pending,
                    isTriggering = isTriggering,
                    onStart = onStart,
                    onCancel = onCancel,
                    onCancelAi = onCancelAi
                )
            }
            // Progress bar: pipeline DTO, enqueueing counter, or queue-
            // draining ratio — chosen by exclusive `when` so we never
            // overlay two metrics on the same bar (which made the % go
            // backwards while we were still adding work).
            when {
                running != null -> {
                    Spacer(Modifier.size(8.dp))
                    val pct = (running.percentage / 100.0).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
                enqueuingProgress != null -> {
                    Spacer(Modifier.size(8.dp))
                    val pct = if (enqueuingProgress.initial > 0)
                        enqueuingProgress.enqueuedSoFar.toFloat() / enqueuingProgress.initial.toFloat()
                    else 0f
                    LinearProgressIndicator(
                        progress = { pct.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
                aiInProgress && pending != null && sessionBaseline != null -> {
                    Spacer(Modifier.size(8.dp))
                    // Session-scoped progress: how much of *this* run's
                    // work the workers have completed. Stays at 0 % when
                    // the queue is full, climbs to 100 % as it drains —
                    // independent of how many assets were already done
                    // before the user pressed Iniciar.
                    val sessionDone = (pending.completed - sessionBaseline).coerceAtLeast(0)
                    val sessionTotal = sessionDone + pending.inQueue
                    val pct = if (sessionTotal > 0)
                        sessionDone.toFloat() / sessionTotal.toFloat()
                    else 0f
                    LinearProgressIndicator(
                        progress = { pct.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
                aiInProgress -> {
                    // Backfill running but no session baseline — usually
                    // because the queue was started elsewhere (PWA, prev
                    // app session). Show an indeterminate bar so the user
                    // still sees activity without a misleading %.
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
            }

            // What the last run actually said. Without this the row reports
            // "Última ejecución hace 2 min" whether the task did the work or
            // died on the first line, which makes a failing task and a dead
            // button look exactly the same.
            if (!isActive && lastFinished != null && lastFinished.lastMessage.isNotBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(
                    lastFinished.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lastFinished.status == "Failed") MaterialTheme.colorScheme.error
                            else contentColor.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun TaskRowSubtitle(
    task: AdminRunTask,
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    enqueuingProgress: EnqueuingProgress?,
    sessionBaseline: Int?,
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
        if (running != null || aiInProgress || enqueuingProgress != null) {
            LiveDot(active = true)
        }
        val text: String = when {
            running != null -> {
                val pct = running.percentage.toInt().coerceIn(0, 100)
                // The percent sign is concatenated in Kotlin (rather than
                // baked into the format string as `%%`) because compose-
                // resources renders the literal `%%` as-is on some targets
                // — escaping is unreliable across JVM / iOS / wasm.
                if (running.lastMessage.isNotBlank()) "$pct% — ${running.lastMessage}"
                else stringResource(Res.string.admin_run_tasks_status_in_progress, "$pct%")
            }
            // Phase 1: enqueuing loop is firing /backfill batches. Show
            // the running counter rather than a percentage so the user
            // tracks how much of the unprocessed pool is still waiting
            // to even reach the queue.
            enqueuingProgress != null -> stringResource(
                Res.string.admin_run_tasks_ai_enqueuing_format,
                enqueuingProgress.enqueuedSoFar,
                enqueuingProgress.initial
            )
            // Phase 2: queue is full, workers chip away. Use the
            // session-scoped delta (current.completed - baseline) so the
            // % reflects the work *this* run is doing rather than the
            // library's lifetime completion ratio.
            aiInProgress && pending != null && sessionBaseline != null -> {
                val sessionDone = (pending.completed - sessionBaseline).coerceAtLeast(0)
                val sessionTotal = sessionDone + pending.inQueue
                val pct = if (sessionTotal > 0)
                    (sessionDone * 100 / sessionTotal).coerceIn(0, 100)
                else 0
                stringResource(
                    Res.string.admin_run_tasks_ai_progress_format,
                    "$pct%",
                    pending.inQueue
                )
            }
            // Phase 2 fallback when we have no baseline (queue started
            // elsewhere): just show the queue size, no %.
            aiInProgress && pending != null -> stringResource(
                Res.string.admin_run_tasks_pending_format,
                pending.unprocessed,
                pending.inQueue
            )
            // ML idle: just the pending counters.
            pending != null -> stringResource(
                Res.string.admin_run_tasks_pending_format,
                pending.unprocessed,
                pending.inQueue
            )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaskRowAction(
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    enqueuing: Boolean,
    pending: PendingCountResponse?,
    isTriggering: Boolean,
    onStart: () -> Unit,
    onCancel: ((BackgroundTaskDto) -> Unit)?,
    onCancelAi: (() -> Unit)?,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
        when {
            // Pipeline/Other running with cancel affordance.
            running != null && onCancel != null -> {
                IconButton(onClick = { onCancel(running) }) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = stringResource(Res.string.admin_run_tasks_action_cancel),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            // ML enqueueing loop in flight OR queue draining: same
            // cancel handler — it stops the loop AND clears Pending
            // server-side, so the row drops out of every "active" state
            // in one tap.
            (enqueuing || aiInProgress) && onCancelAi != null -> {
                IconButton(onClick = onCancelAi) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = stringResource(Res.string.admin_run_tasks_action_cancel),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            isTriggering -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            // Idle ML row with nothing left to enqueue: hide the Play
            // button so the user isn't tempted to fire an empty backfill.
            pending != null && pending.unprocessed == 0 -> Unit
            running == null -> {
                IconButton(onClick = onStart) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(Res.string.admin_run_tasks_action_start),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
