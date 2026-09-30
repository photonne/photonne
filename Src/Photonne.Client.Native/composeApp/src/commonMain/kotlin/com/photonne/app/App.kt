@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.photonne.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.setSingletonImageLoaderFactory
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.resources.notifications_no_screen
import com.photonne.app.resources.organize_skipped_done
import com.photonne.app.resources.organize_excluded_included_done
import com.photonne.app.resources.organize_excluded_action_include
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import com.photonne.app.resources.action_logout
import com.photonne.app.resources.action_undo
import com.photonne.app.resources.album_trash_warning
import com.photonne.app.resources.album_trash_warning_shared
import com.photonne.app.resources.selection_trash_blocked_foreign
import com.photonne.app.resources.selection_move_blocked_read_only
import com.photonne.app.resources.selection_trash_blocked_folder
import com.photonne.app.resources.selection_restore_done
import com.photonne.app.resources.logout_confirm_message
import com.photonne.app.resources.member_remove_confirm_action
import com.photonne.app.resources.member_remove_confirm_message
import com.photonne.app.resources.member_remove_confirm_title
import com.photonne.app.resources.share_action_revoke
import com.photonne.app.resources.share_revoke_confirm_message
import com.photonne.app.resources.share_revoke_confirm_title
import com.photonne.app.resources.logout_confirm_pending_backup
import com.photonne.app.resources.logout_confirm_title
import com.photonne.app.resources.people_action_suggestions_accept_all
import com.photonne.app.resources.people_action_suggestions_dismiss_all
import com.photonne.app.resources.people_merge_done
import com.photonne.app.resources.people_recluster_done
import com.photonne.app.resources.people_recluster_done_none
import com.photonne.app.resources.people_suggestions_accept_all_message
import com.photonne.app.resources.people_suggestions_accept_all_title
import com.photonne.app.resources.people_suggestions_accepted_done
import com.photonne.app.resources.people_suggestions_dismiss_all_message
import com.photonne.app.resources.people_suggestions_dismiss_all_title
import com.photonne.app.resources.people_suggestions_dismissed_done
import com.photonne.app.resources.selection_added_to_album_done
import com.photonne.app.resources.selection_archive_done
import com.photonne.app.resources.selection_unarchive_done
import com.photonne.app.resources.selection_moved_to_folder_done
import com.photonne.app.resources.selection_removed_from_album_done
import com.photonne.app.resources.selection_trash_done
import com.photonne.app.resources.selection_favorite_added_done
import com.photonne.app.resources.selection_favorite_removed_done
import com.photonne.app.resources.selection_favorite_failed
import com.photonne.app.resources.selection_deleted_permanently_done
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_system_enrichment_failures
import com.photonne.app.resources.account_section_appearance
import com.photonne.app.resources.account_section_profile
import com.photonne.app.resources.account_section_security
import com.photonne.app.resources.account_section_storage
import com.photonne.app.resources.account_section_connection
import com.photonne.app.resources.account_settings_title
import com.photonne.app.resources.admin_section_libraries
import com.photonne.app.resources.admin_section_settings
import com.photonne.app.resources.admin_section_stats
import com.photonne.app.resources.admin_section_system
import com.photonne.app.resources.admin_section_users
import com.photonne.app.resources.admin_libraries_action_new
import com.photonne.app.resources.admin_libraries_edit_title
import com.photonne.app.resources.admin_user_action_new
import com.photonne.app.resources.admin_user_edit_title
import com.photonne.app.resources.admin_settings_face_recognition
import com.photonne.app.resources.admin_settings_image
import com.photonne.app.resources.admin_settings_image_embedding
import com.photonne.app.resources.admin_settings_metadata
import com.photonne.app.resources.admin_settings_nightly
import com.photonne.app.resources.admin_settings_notifications
import com.photonne.app.resources.admin_settings_object_detection
import com.photonne.app.resources.admin_settings_scene_classification
import com.photonne.app.resources.admin_settings_server
import com.photonne.app.resources.admin_settings_text_recognition
import com.photonne.app.resources.admin_settings_trash
import com.photonne.app.resources.admin_settings_user_defaults
import com.photonne.app.resources.admin_settings_version
import com.photonne.app.resources.admin_system_backup
import com.photonne.app.resources.admin_system_duplicates
import com.photonne.app.resources.admin_system_run_tasks
import com.photonne.app.resources.administration_title
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_retry
import com.photonne.app.resources.action_save
import com.photonne.app.resources.album_action_edit
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.archive_action_unarchive_all_title
import com.photonne.app.resources.archive_action_unarchive_all_message
import com.photonne.app.resources.archive_action_unarchive_all
import com.photonne.app.resources.folder_action_edit
import com.photonne.app.resources.folder_action_new
import com.photonne.app.resources.folder_move_assets_title
import com.photonne.app.resources.organize_rule_title
import com.photonne.app.resources.folder_move_title
import com.photonne.app.resources.trash_action_delete_forever
import com.photonne.app.resources.trash_action_empty
import com.photonne.app.resources.trash_action_restore_all
import com.photonne.app.resources.action_more
import com.photonne.app.resources.trash_dialog_empty_message
import com.photonne.app.resources.trash_dialog_purge_message
import com.photonne.app.resources.trash_dialog_restore_all_message
import com.photonne.app.resources.notifications_title
import com.photonne.app.resources.backup_pending_screen_title
import com.photonne.app.resources.device_backup_action_select_all
import com.photonne.app.resources.enrichment_screen_title
import com.photonne.app.resources.device_backup_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.upload_subtitle_pending
import com.photonne.app.resources.upload_title
import com.photonne.app.resources.utilities_section_duplicates
import com.photonne.app.resources.utilities_section_large_files
import com.photonne.app.resources.utilities_section_locations
import com.photonne.app.resources.utilities_title
import com.photonne.app.resources.my_links_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.navigation.PlatformBackHandler
import com.photonne.app.ui.album.AddToAlbumDialog
import com.photonne.app.ui.album.AlbumDetailScreen
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumFormDialog
import com.photonne.app.ui.album.AlbumPermissionsViewModel
import com.photonne.app.ui.album.AlbumSharesViewModel
import com.photonne.app.ui.album.AlbumsListScreen
import com.photonne.app.ui.album.AlbumsViewModel
import com.photonne.app.data.models.AlbumShareLink
import com.photonne.app.ui.album.CreateShareDialog
import com.photonne.app.ui.album.EditShareDialog
import com.photonne.app.ui.album.DeleteAlbumDialog
import com.photonne.app.ui.album.InviteMemberDialog
import com.photonne.app.ui.album.LeaveAlbumDialog
import com.photonne.app.ui.album.ManagePermissionsDialog
import com.photonne.app.ui.album.ManageSharesDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.photonne.app.ui.asset.AssetDetailScreen
import com.photonne.app.ui.theme.LocalCurrentDetailAssetId
import com.photonne.app.ui.theme.LocalSharedTransitionScope
import com.photonne.app.ui.folder.DeleteFolderDialog
import com.photonne.app.ui.folder.FolderFormDialog
import com.photonne.app.ui.folder.FolderPermissionsViewModel
import com.photonne.app.ui.folder.FoldersViewModel
import com.photonne.app.ui.folder.InviteFolderMemberDialog
import com.photonne.app.ui.folder.ManageFolderPermissionsDialog
import com.photonne.app.ui.image.buildPhotonneImageLoader
import com.photonne.app.ui.login.LoginScreen
import com.photonne.app.ui.actions.AssetActionWorking
import com.photonne.app.ui.actions.DownloadFormatSheet
import com.photonne.app.ui.actions.ShareAssetsDialog
import com.photonne.app.ui.actions.ShareLinkResultDialog
import com.photonne.app.ui.main.AlbumsListTopBar
import com.photonne.app.ui.main.ArchiveMode
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.photonne.app.ui.main.rememberSnackbarController
import com.photonne.app.ui.main.AssetSelectionBottomBar
import com.photonne.app.ui.main.AssetSelectionTopBar
import com.photonne.app.ui.main.FolderDetailChromeActions
import com.photonne.app.ui.main.FoldersListTopBar
import com.photonne.app.ui.main.MainScaffold
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.main.MoreScreen
import com.photonne.app.ui.theme.PhotonneTheme
import com.photonne.app.ui.timeline.TimelineScreen
import com.photonne.app.ui.timeline.TimelineViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private data class AssetDetailContext(
    val items: List<TimelineItem>,
    val startIndex: Int,
    val source: Source,
    val hasMore: Boolean,
    val onLoadMore: () -> Unit,
    val onFavoriteChanged: (assetId: String, isFavorite: Boolean) -> Unit,
    /** Lista viva del origen: con ella el visor ve las páginas (o meses) que
     *  llegan después de abrirlo; sin ella [items] es una foto fija y
     *  [hasMore]/[onLoadMore] no llegan a ninguna parte. */
    val feed: com.photonne.app.ui.asset.AssetViewerFeed? = null
) {
    /** Archive/Trash change the viewer's actions (Unarchive; Restore + Delete
     *  permanently) — see [com.photonne.app.ui.asset.AssetViewerMode]. */
    enum class Source { Timeline, Album, Archive, Trash }
}

/** Visor del que se salió desde su panel de info (una cara → la persona, una
 *  escena u objeto → la búsqueda): Atrás desde allí vuelve a esa foto, y a la
 *  pantalla que había debajo. */
private data class ViewerReturn(
    val tab: MainTab,
    val subscreen: MoreSubscreen?,
    val person: com.photonne.app.data.models.Person?,
    val viewer: AssetDetailContext,
    val viewerStack: List<AssetDetailContext>
)

private data class AddToAlbumState(
    val asset: TimelineItem,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/**
 * "Mover a carpeta" sobre una selección cualquiera (Lote N6): los ids y qué
 * hacer al terminar. El diálogo ya no está atado a la selección del timeline.
 */
private class MoveSelectionRequest(
    val assetIds: List<String>,
    val onMoved: () -> Unit
)

private enum class MoreSubscreen {
    Upload,
    SmartAlbumEditor,
    DeviceBackup,
    DeviceBackupPending,
    EnrichmentStatus,
    Favorites,
    People,
    PeopleSuggestions,
    Map,
    Archived,
    Trash,
    Utilities,
    UtilitiesDuplicates,
    UtilitiesLargeFiles,
    UtilitiesLocations,
    MyLinks,
    UnsupportedFiles,
    OrganizeInbox,
    OrganizeRule,
    OrganizeExcluded,
    DeviceFolders,
    DeviceFolderDetail,
    Memories,
    ExploreScenes,
    ExploreObjects,
    AccountSettings,
    AccountProfile,
    AccountSecurity,
    AccountAppearance,
    AccountStorage,
    AccountConnection,
    Notifications,
    Administration,
    AdminUsers,
    AdminUserEditor,
    AdminLibraries,
    AdminLibraryEditor,
    AdminStats,
    AdminSettingsHub,
    AdminSettingsFaceRecognition,
    AdminSettingsObjectDetection,
    AdminSettingsSceneClassification,
    AdminSettingsTextRecognition,
    AdminSettingsImageEmbedding,
    AdminSettingsImage,
    AdminSettingsMetadata,
    AdminSettingsNightly,
    AdminSettingsNotifications,
    AdminSettingsServer,
    AdminSettingsTrash,
    AdminSettingsUserDefaults,
    AdminSettingsVersion,
    AdminSystemHub,
    AdminSystemRunTasks,
    AdminSystemDuplicates,
    AdminSystemEnrichmentFailures,
    AdminSystemBackup
}

/** True when the given subscreen is one of the 14 Ajustes leaves so the
 *  top bar / back button can be configured generically. */
private fun isAdminSettingsSubpage(subscreen: MoreSubscreen?): Boolean = when (subscreen) {
    MoreSubscreen.AdminSettingsFaceRecognition,
    MoreSubscreen.AdminSettingsObjectDetection,
    MoreSubscreen.AdminSettingsSceneClassification,
    MoreSubscreen.AdminSettingsTextRecognition,
    MoreSubscreen.AdminSettingsImageEmbedding,
    MoreSubscreen.AdminSettingsImage,
    MoreSubscreen.AdminSettingsMetadata,
    MoreSubscreen.AdminSettingsNightly,
    MoreSubscreen.AdminSettingsNotifications,
    MoreSubscreen.AdminSettingsServer,
    MoreSubscreen.AdminSettingsTrash,
    MoreSubscreen.AdminSettingsUserDefaults,
    MoreSubscreen.AdminSettingsVersion -> true
    else -> false
}

private fun isAdminRunTasksDetail(subscreen: MoreSubscreen?): Boolean = when (subscreen) {
    // Duplicates is the only Run Tasks entry that still has its own
    // dedicated detail screen (cleanup / physical toggles + per-group
    // review UI). The pipeline and AI tasks are now handled inline on
    // the hub itself.
    MoreSubscreen.AdminSystemDuplicates -> true
    else -> false
}

private fun isAdminSystemSubpage(subscreen: MoreSubscreen?): Boolean = when (subscreen) {
    MoreSubscreen.AdminSystemRunTasks,
    MoreSubscreen.AdminSystemDuplicates,
    MoreSubscreen.AdminSystemEnrichmentFailures,
    MoreSubscreen.AdminSystemBackup -> true
    else -> false
}

private fun adminSettingsSubpageMeta(
    subscreen: MoreSubscreen
): Pair<org.jetbrains.compose.resources.StringResource, Unit> = when (subscreen) {
    MoreSubscreen.AdminSettingsFaceRecognition ->
        Res.string.admin_settings_face_recognition to Unit
    MoreSubscreen.AdminSettingsObjectDetection ->
        Res.string.admin_settings_object_detection to Unit
    MoreSubscreen.AdminSettingsSceneClassification ->
        Res.string.admin_settings_scene_classification to Unit
    MoreSubscreen.AdminSettingsTextRecognition ->
        Res.string.admin_settings_text_recognition to Unit
    MoreSubscreen.AdminSettingsImageEmbedding ->
        Res.string.admin_settings_image_embedding to Unit
    MoreSubscreen.AdminSettingsImage ->
        Res.string.admin_settings_image to Unit
    MoreSubscreen.AdminSettingsMetadata ->
        Res.string.admin_settings_metadata to Unit
    MoreSubscreen.AdminSettingsNightly ->
        Res.string.admin_settings_nightly to Unit
    MoreSubscreen.AdminSettingsNotifications ->
        Res.string.admin_settings_notifications to Unit
    MoreSubscreen.AdminSettingsServer ->
        Res.string.admin_settings_server to Unit
    MoreSubscreen.AdminSettingsTrash ->
        Res.string.admin_settings_trash to Unit
    MoreSubscreen.AdminSettingsUserDefaults ->
        Res.string.admin_settings_user_defaults to Unit
    MoreSubscreen.AdminSettingsVersion ->
        Res.string.admin_settings_version to Unit
    else -> Res.string.admin_section_settings to Unit
}

private fun adminSystemSubpageMeta(
    subscreen: MoreSubscreen
): Pair<org.jetbrains.compose.resources.StringResource, Unit> = when (subscreen) {
    MoreSubscreen.AdminSystemRunTasks -> Res.string.admin_system_run_tasks to Unit
    MoreSubscreen.AdminSystemDuplicates -> Res.string.admin_system_duplicates to Unit
    MoreSubscreen.AdminSystemEnrichmentFailures -> Res.string.admin_system_enrichment_failures to Unit
    MoreSubscreen.AdminSystemBackup -> Res.string.admin_system_backup to Unit
    else -> Res.string.admin_section_system to Unit
}

/** Where the in-app "back" arrow of [subscreen]'s top bar lands. Null
 *  means the subscreen sits at the More-tab root and back should close
 *  the More tab itself. Mirrors the onBack mapping in [AuthenticatedApp]'s
 *  top bar so the hardware back button can reuse the same precedence. */
private fun parentMoreSubscreen(subscreen: MoreSubscreen): MoreSubscreen? = when (subscreen) {
    MoreSubscreen.DeviceBackupPending -> MoreSubscreen.DeviceBackup
    MoreSubscreen.EnrichmentStatus -> MoreSubscreen.DeviceBackup
    MoreSubscreen.UtilitiesDuplicates,
    MoreSubscreen.UtilitiesLargeFiles,
    MoreSubscreen.UtilitiesLocations,
    MoreSubscreen.UnsupportedFiles -> MoreSubscreen.Utilities
    MoreSubscreen.Memories,
    MoreSubscreen.ExploreScenes,
    MoreSubscreen.ExploreObjects -> null
    MoreSubscreen.PeopleSuggestions -> MoreSubscreen.People
    MoreSubscreen.AccountProfile,
    MoreSubscreen.AccountSecurity,
    MoreSubscreen.AccountAppearance,
    MoreSubscreen.AccountStorage,
    MoreSubscreen.AccountConnection -> MoreSubscreen.AccountSettings
    MoreSubscreen.AdminUsers,
    MoreSubscreen.AdminLibraries,
    MoreSubscreen.AdminStats,
    MoreSubscreen.AdminSettingsHub,
    MoreSubscreen.AdminSystemHub -> MoreSubscreen.Administration
    MoreSubscreen.AdminUserEditor -> MoreSubscreen.AdminUsers
    MoreSubscreen.AdminLibraryEditor -> MoreSubscreen.AdminLibraries
    MoreSubscreen.AdminSettingsFaceRecognition,
    MoreSubscreen.AdminSettingsObjectDetection,
    MoreSubscreen.AdminSettingsSceneClassification,
    MoreSubscreen.AdminSettingsTextRecognition,
    MoreSubscreen.AdminSettingsImageEmbedding,
    MoreSubscreen.AdminSettingsImage,
    MoreSubscreen.AdminSettingsMetadata,
    MoreSubscreen.AdminSettingsNightly,
    MoreSubscreen.AdminSettingsNotifications,
    MoreSubscreen.AdminSettingsServer,
    MoreSubscreen.AdminSettingsTrash,
    MoreSubscreen.AdminSettingsUserDefaults,
    MoreSubscreen.AdminSettingsVersion -> MoreSubscreen.AdminSettingsHub
    MoreSubscreen.AdminSystemDuplicates -> MoreSubscreen.AdminSystemRunTasks
    MoreSubscreen.AdminSystemRunTasks,
    MoreSubscreen.AdminSystemEnrichmentFailures,
    MoreSubscreen.AdminSystemBackup -> MoreSubscreen.AdminSystemHub
    MoreSubscreen.OrganizeRule,
    MoreSubscreen.OrganizeExcluded -> MoreSubscreen.OrganizeInbox
    MoreSubscreen.DeviceFolderDetail -> MoreSubscreen.DeviceFolders
    MoreSubscreen.Upload,
    MoreSubscreen.DeviceFolders,
    MoreSubscreen.SmartAlbumEditor,
    MoreSubscreen.DeviceBackup,
    MoreSubscreen.Favorites,
    MoreSubscreen.People,
    MoreSubscreen.Map,
    MoreSubscreen.Archived,
    MoreSubscreen.Trash,
    MoreSubscreen.Utilities,
    MoreSubscreen.MyLinks,
    MoreSubscreen.OrganizeInbox,
    MoreSubscreen.AccountSettings,
    MoreSubscreen.Notifications,
    MoreSubscreen.Administration -> null
}

/** Build a thin TimelineItem out of a map point so the asset viewer
 * can be seeded without an extra fetch — it re-queries AssetDetail
 * on display, so most fields can stay blank. */
private fun com.photonne.app.data.models.MapPoint.toSyntheticTimelineItem():
    com.photonne.app.data.models.TimelineItem =
    com.photonne.app.data.models.TimelineItem(
        id = id,
        fileName = "",
        fullPath = "",
        fileSize = 0L,
        fileCreatedAt = date,
        fileModifiedAt = date,
        extension = "",
        scannedAt = date,
        type = "Image",
        hasThumbnails = hasThumbnail
    )

/** Same trick for a failures-registry row: the viewer re-queries the
 * asset detail by id, so the registry's id + capture date are enough. */
private fun com.photonne.app.data.api.AdminEnrichmentFailureDto.toSyntheticTimelineItem():
    com.photonne.app.data.models.TimelineItem {
    val date = fileCreatedAt ?: kotlin.time.Instant.DISTANT_PAST
    return com.photonne.app.data.models.TimelineItem(
        id = assetId,
        fileName = fileName,
        fullPath = "",
        fileSize = 0L,
        fileCreatedAt = date,
        fileModifiedAt = date,
        extension = "",
        scannedAt = date,
        type = "Image",
        hasThumbnails = false
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App() {
    val httpClient: HttpClient = koinInject()
    setSingletonImageLoaderFactory { context ->
        buildPhotonneImageLoader(context, httpClient)
    }

    val reachabilityProbe: com.photonne.app.data.api.LocalReachabilityProbe = koinInject()
    val probeScope = rememberCoroutineScope()
    LaunchedEffect(reachabilityProbe) {
        reachabilityProbe.start(probeScope)
    }
    // Estado global "Sin conexión" (píldora del cromo superior).
    val connectivityMonitor: com.photonne.app.data.api.ConnectivityMonitor = koinInject()
    LaunchedEffect(connectivityMonitor) {
        connectivityMonitor.start(probeScope)
    }

    // Recuperación proactiva al volver a primer plano: purga los sockets
    // medio-abiertos y re-sondea la reachability ANTES de que el primer request
    // del usuario reutilice un socket muerto y saque el banner "Reintentar".
    val foregroundRecovery: com.photonne.app.data.api.ForegroundRecovery = koinInject()
    val recoveryScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Salta el ON_START del arranque en frío: el probe ya sondea al iniciar
        // (tick inicial del NetworkMonitor) y el pool está vacío.
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                connectivityMonitor.setForeground(true)
                if (first) first = false
                else recoveryScope.launch { foregroundRecovery.onEnterForeground() }
            } else if (event == Lifecycle.Event.ON_STOP) {
                connectivityMonitor.setForeground(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val themeStore: com.photonne.app.data.settings.ThemePreferenceStore = koinInject()
    val themePreference by themeStore.value.collectAsStateWithLifecycle()

    val sessionBootstrapper: com.photonne.app.data.auth.SessionBootstrapper = koinInject()
    LaunchedEffect(Unit) {
        sessionBootstrapper.restore()
    }

    PhotonneTheme(preference = themePreference) {
        // Los iconos de las barras del sistema siguen al tema efectivo de la
        // app (no solo al del SO). AuthenticatedApp vuelve a llamar con el
        // visor abierto para forzar iconos claros sobre el scrim de fotos.
        com.photonne.app.ui.platform.SyncSystemBarIcons(
            darkBackground = com.photonne.app.ui.theme.LocalIsDarkTheme.current
        )
        val authState: AuthStateHolder = koinInject()
        val state by authState.state.collectAsStateWithLifecycle()
        val sessionStores = viewModel { SessionViewModelStores() }
        when (val current = state) {
            is AuthState.Authenticated -> SessionViewModelScope(sessionStores, current.user.id) {
                AuthenticatedApp(user = current)
            }
            AuthState.Unauthenticated, is AuthState.SessionExpired -> {
                // Logout (or a rejected refresh): drop every view model of the
                // finished session so the next login starts clean.
                LaunchedEffect(Unit) { sessionStores.clear() }
                // Solo la caducidad explica por qué se vuelve al login; un
                // logout voluntario llega sin aviso.
                LoginScreen(expiredSession = current as? AuthState.SessionExpired)
            }
            // Booting: restoring a persisted session. Show a neutral splash so
            // the login screen never flashes before the timeline appears.
            AuthState.Unknown -> SessionLoadingScreen()
        }
    }
}

/**
 * Holds the [ViewModelStore] of the signed-in session. It lives in the
 * platform's own store (so it survives configuration changes like the view
 * models did before) but is cleared on logout or when a different user signs
 * in, so no timeline, selection or admin state of one account can resurface in
 * the next one.
 */
private class SessionViewModelStores : ViewModel() {
    private var sessionKey: String? = null
    private var store: ViewModelStore? = null

    fun storeFor(key: String): ViewModelStore {
        val current = store
        if (current != null && sessionKey == key) return current
        current?.clear()
        return ViewModelStore().also {
            store = it
            sessionKey = key
        }
    }

    fun clear() {
        store?.clear()
        store = null
        sessionKey = null
    }

    override fun onCleared() = clear()
}

/** Scopes every `koinViewModel()` in [content] to the session of [sessionKey]. */
@Composable
private fun SessionViewModelScope(
    stores: SessionViewModelStores,
    sessionKey: String,
    content: @Composable () -> Unit,
) {
    val owner = remember(stores, sessionKey) {
        val store = stores.storeFor(sessionKey)
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** Whose selection the shared add-to-album dialog adds. */
private enum class BulkAddSource { Search, Map, Favorites, People, Folder, Archive, Album, Inbox, Upload, Memory }

/**
 * Qué hay que añadir al álbum que se está creando cuando se llega a "Nuevo
 * álbum" desde un "Añadir a álbum": la selección de una pantalla (null =
 * timeline) o el asset abierto en el visor. Sin esto el álbum nacía vacío en
 * todos los orígenes salvo el timeline.
 */
private sealed interface PendingAddTarget {
    data class Selection(val source: BulkAddSource?) : PendingAddTarget
    data class Asset(val item: TimelineItem) : PendingAddTarget
}

@Composable
private fun SessionLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthenticatedApp(user: AuthState.Authenticated) {
    val authRepository: AuthRepository = koinInject()
    val albumsRepository: AlbumsRepository = koinInject()
    val peopleRepository: com.photonne.app.data.people.PeopleRepository = koinInject()
    val foldersRepository: com.photonne.app.data.folder.FoldersRepository = koinInject()
    val errorFactory: com.photonne.app.data.error.UiErrorFactory = koinInject()
    val apiBaseUrl = com.photonne.app.data.api.rememberApiBaseUrl()
    val appVersionStore: com.photonne.app.data.version.AppVersionStore = koinInject()
    // Refresca la versión del servidor cada vez que cambia el baseUrl
    // efectivo (login, switch LAN↔público). Si falla, el reporte de error
    // sale sin la versión del server — no es bloqueante.
    LaunchedEffect(apiBaseUrl) {
        if (!apiBaseUrl.isNullOrBlank()) appVersionStore.refresh()
    }
    val timelineViewModel: TimelineViewModel = koinViewModel()
    val timelineZoomStore: com.photonne.app.data.settings.TimelineZoomStore = koinInject()
    // Immersive tabs: the active list reports when its chrome hides on scroll
    // so the shared bottom navigation can slide away in the same rhythm. Fotos,
    // Álbumes and Carpetas each drive their own flag.
    var timelineChromeVisible by remember { mutableStateOf(true) }
    var albumsChromeVisible by remember { mutableStateOf(true) }
    var foldersChromeVisible by remember { mutableStateOf(true) }
    var moreChromeVisible by remember { mutableStateOf(true) }
    var searchChromeVisible by remember { mutableStateOf(true) }
    // Same, but for the photo grids inside an open album / folder.
    var albumDetailChromeVisible by remember { mutableStateOf(true) }
    // Compartido por las subpantallas con cromo flotante propio (Personas, Mapa,
    // Escenas, Objetos, Para organizar, Recuerdos): sólo hay una visible a la vez,
    // y ImmersiveChromeEffect lo restaura a `true` al salir de composición.
    var subscreenChromeVisible by remember { mutableStateOf(true) }
    var folderDetailChromeVisible by remember { mutableStateOf(true) }
    val albumsViewModel: AlbumsViewModel = koinViewModel()
    val albumDetailViewModel: AlbumDetailViewModel = koinViewModel()
    val searchViewModel: com.photonne.app.ui.search.SearchViewModel = koinViewModel()
    val foldersViewModel: FoldersViewModel = koinViewModel()
    // Últimos destinos de un movimiento, para ofrecerlos como atajo en el
    // picker: organizar es repetitivo y el árbol es largo.
    val recentDestinationsStore: com.photonne.app.data.settings.RecentDestinationsStore =
        koinInject()
    val recentDestinations by recentDestinationsStore.value.collectAsStateWithLifecycle()
    val memoriesViewModel:
        com.photonne.app.ui.timeline.MemoriesViewModel = koinViewModel()
    // The timeline strip's live "on this day" list (above) and the Recuerdos
    // section's generated feed are different requests with different lifetimes,
    // so they get one ViewModel each.
    val memoryFeedViewModel:
        com.photonne.app.ui.memories.MemoryFeedViewModel = koinViewModel()

    // Third-party data notices for the Más footer. Fetched rather than hardcoded
    // because only the server knows what its image actually bundles: a build that
    // couldn't download GeoNames must not credit data it doesn't have. Failure is
    // silent — an unreachable server has bigger problems than a missing credit,
    // and the README carries the attribution regardless.
    val photonneApi: com.photonne.app.data.api.PhotonneApi = koinInject()
    var attributions by remember {
        mutableStateOf<List<com.photonne.app.data.models.Attribution>>(emptyList())
    }
    LaunchedEffect(apiBaseUrl) {
        runCatching { photonneApi.getAttributions() }
            .onSuccess { attributions = it }
    }
    // La primera carga de las pantallas principales corre en el init de su
    // ViewModel contra la URL pública, antes de que el probe de reachability
    // decida LAN↔público. Desde dentro de la LAN de casa esa petición hace
    // connect-timeout (la pública no es alcanzable sin NAT hairpin). Cuando el
    // probe voltea la URL efectiva, recargamos: cancela la petición condenada y
    // reintenta contra la LAN, así el usuario no se queda atrapado en un error
    // con botón "reintentar". Se ignora el valor inicial (ya lo cargó el init).
    var lastEffectiveUrl by remember { mutableStateOf(apiBaseUrl) }
    LaunchedEffect(apiBaseUrl) {
        if (apiBaseUrl != lastEffectiveUrl) {
            lastEffectiveUrl = apiBaseUrl
            if (apiBaseUrl.isNotBlank()) {
                timelineViewModel.refresh()
                albumsViewModel.refresh()
                foldersViewModel.refresh()
                memoriesViewModel.refresh()
            }
        }
    }
    val folderDetailViewModel: com.photonne.app.ui.folder.FolderDetailViewModel = koinViewModel()
    val folderPermissionsViewModel: FolderPermissionsViewModel = koinViewModel()
    val albumSharesViewModel: AlbumSharesViewModel = koinViewModel()
    val albumPermissionsViewModel: AlbumPermissionsViewModel = koinViewModel()
    val archivedViewModel: com.photonne.app.ui.library.ArchivedViewModel = koinViewModel()
    val trashViewModel: com.photonne.app.ui.library.TrashViewModel = koinViewModel()
    val favoritesViewModel: com.photonne.app.ui.library.FavoritesViewModel = koinViewModel()
    val unsupportedFilesViewModel: com.photonne.app.ui.library.UnsupportedFilesViewModel = koinViewModel()
    val organizeInboxViewModel: com.photonne.app.ui.organize.OrganizeInboxViewModel = koinViewModel()
    val organizeExcludedViewModel: com.photonne.app.ui.organize.OrganizeExcludedViewModel = koinViewModel()
    // Lo resuelve aquí (y no dentro de la pantalla) porque la rejilla de revisión
    // se hospeda FUERA del MainScaffold y necesita el mismo estado.
    val organizeRuleViewModel: com.photonne.app.ui.organize.OrganizeRuleViewModel = koinViewModel()
    val uploadViewModel: com.photonne.app.ui.upload.UploadViewModel = koinViewModel()
    val deviceBackupViewModel: com.photonne.app.ui.devicebackup.DeviceBackupViewModel = koinViewModel()
    val deviceBackupState by deviceBackupViewModel.state.collectAsStateWithLifecycle()
    val enrichmentStatusViewModel: com.photonne.app.ui.devicebackup.EnrichmentStatusViewModel = koinViewModel()
    val utilitiesDuplicatesViewModel:
        com.photonne.app.ui.utilities.UtilitiesDuplicatesViewModel = koinViewModel()
    val utilitiesLargeFilesViewModel:
        com.photonne.app.ui.utilities.UtilitiesLargeFilesViewModel = koinViewModel()
    val utilitiesLocationsViewModel:
        com.photonne.app.ui.utilities.UtilitiesLocationsViewModel = koinViewModel()
    val exploreFacetsViewModel:
        com.photonne.app.ui.explore.ExploreFacetsViewModel = koinViewModel()
    val memoriesState by memoriesViewModel.state.collectAsStateWithLifecycle()
    // Lote L9: "en este día" caduca a medianoche; al volver a primer plano en
    // otro día la tira se recarga.
    val memoriesLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(memoriesLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) memoriesViewModel.refreshIfDayChanged()
        }
        memoriesLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { memoriesLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val activityNotifications: com.photonne.app.data.notifications.ActivityNotifications =
        koinInject()
    val activityNotificationsEnabled by activityNotifications.enabled.collectAsStateWithLifecycle()
    val notificationsViewModel:
        com.photonne.app.ui.notifications.NotificationsViewModel = koinViewModel()
    val notificationsState by notificationsViewModel.state.collectAsStateWithLifecycle()
    val deviceGallery: com.photonne.app.data.devicebackup.DeviceGallery =
        org.koin.compose.koinInject()
    val actionsViewModel: com.photonne.app.ui.actions.AssetSelectionActionsViewModel =
        koinViewModel()
    val mapViewModel: com.photonne.app.ui.map.MapViewModel = koinViewModel()
    val peopleViewModel: com.photonne.app.ui.people.PeopleViewModel = koinViewModel()
    val personDetailViewModel: com.photonne.app.ui.people.PersonDetailViewModel = koinViewModel()
    val personSuggestionsViewModel: com.photonne.app.ui.people.PersonSuggestionsViewModel =
        koinViewModel()
    val assetFacesViewModel: com.photonne.app.ui.people.AssetFacesViewModel = koinViewModel()
    val accountProfileViewModel: com.photonne.app.ui.settings.AccountProfileViewModel =
        koinViewModel()
    val accountSecurityViewModel: com.photonne.app.ui.settings.AccountSecurityViewModel =
        koinViewModel()
    val accountStorageViewModel: com.photonne.app.ui.settings.AccountStorageViewModel =
        koinViewModel()
    val appearanceViewModel: com.photonne.app.ui.settings.AppearanceViewModel = koinViewModel()
    val adminUsersViewModel: com.photonne.app.ui.admin.AdminUsersViewModel = koinViewModel()
    val adminLibrariesViewModel: com.photonne.app.ui.admin.AdminLibrariesViewModel =
        koinViewModel()
    val adminStatsViewModel: com.photonne.app.ui.admin.AdminStatsViewModel = koinViewModel()
    val adminVersionViewModel: com.photonne.app.ui.admin.AdminServerViewModel = koinViewModel()
    val adminImageSettingsViewModel: com.photonne.app.ui.admin.AdminImageSettingsViewModel =
        koinViewModel()
    val adminMetadataSettingsViewModel:
        com.photonne.app.ui.admin.AdminMetadataSettingsViewModel = koinViewModel()
    val adminNightlySettingsViewModel:
        com.photonne.app.ui.admin.AdminNightlySettingsViewModel = koinViewModel()
    val adminNotificationSettingsViewModel:
        com.photonne.app.ui.admin.AdminNotificationSettingsViewModel = koinViewModel()
    val adminServerSettingsViewModel:
        com.photonne.app.ui.admin.AdminServerSettingsViewModel = koinViewModel()
    val deviceConnectionViewModel:
        com.photonne.app.ui.admin.DeviceConnectionViewModel = koinViewModel()
    val adminTrashSettingsViewModel:
        com.photonne.app.ui.admin.AdminTrashSettingsViewModel = koinViewModel()
    val adminSharedTrashViewModel:
        com.photonne.app.ui.admin.AdminSharedTrashViewModel = koinViewModel()
    val adminUserDefaultsViewModel:
        com.photonne.app.ui.admin.AdminUserDefaultsViewModel = koinViewModel()
    val adminDuplicatesViewModel:
        com.photonne.app.ui.admin.AdminDuplicatesViewModel = koinViewModel()
    val adminBackupViewModel:
        com.photonne.app.ui.admin.AdminBackupViewModel = koinViewModel()
    val timelineState by timelineViewModel.state.collectAsStateWithLifecycle()
    val albumsState by albumsViewModel.state.collectAsStateWithLifecycle()
    val albumDetailState by albumDetailViewModel.state.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val foldersState by foldersViewModel.state.collectAsStateWithLifecycle()
    val folderDetailState by folderDetailViewModel.state.collectAsStateWithLifecycle()
    val folderPermissionsState by folderPermissionsViewModel.state.collectAsStateWithLifecycle()
    val albumSharesState by albumSharesViewModel.state.collectAsStateWithLifecycle()
    val albumPermissionsState by albumPermissionsViewModel.state.collectAsStateWithLifecycle()
    val archivedState by archivedViewModel.state.collectAsStateWithLifecycle()
    val trashState by trashViewModel.state.collectAsStateWithLifecycle()
    val favoritesState by favoritesViewModel.state.collectAsStateWithLifecycle()
    val unsupportedFilesState by unsupportedFilesViewModel.state.collectAsStateWithLifecycle()
    val organizeInboxState by organizeInboxViewModel.state.collectAsStateWithLifecycle()
    val organizeExcludedState by organizeExcludedViewModel.state.collectAsStateWithLifecycle()
    val organizeRuleState by organizeRuleViewModel.state.collectAsStateWithLifecycle()
    val peopleState by peopleViewModel.state.collectAsStateWithLifecycle()
    val personDetailState by personDetailViewModel.state.collectAsStateWithLifecycle()
    val suggestionsState by personSuggestionsViewModel.state.collectAsStateWithLifecycle()
    val assetFacesState by assetFacesViewModel.state.collectAsStateWithLifecycle()
    val uploadState by uploadViewModel.state.collectAsStateWithLifecycle()
    val actionsState by actionsViewModel.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // rememberSaveable: la pestaña y la subpantalla de Más sobreviven a la
    // muerte de proceso y a la recreación de la Activity (punto 49; los
    // álbumes/carpetas abiertos guardan objetos completos y quedan pendientes).
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(MainTab.Timeline)
    }
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var selectedFolder by remember {
        mutableStateOf<com.photonne.app.data.models.FolderSummary?>(null)
    }
    val folderBackStack = remember {
        mutableStateListOf<com.photonne.app.data.models.FolderSummary>()
    }
    var assetDetail by remember { mutableStateOf<AssetDetailContext?>(null) }
    // Pila de contextos del visor: abrir una foto relacionada apila el contexto
    // actual para que atrás vuelva a la foto (y la lista) de la que se venía,
    // en lugar de cerrar el visor y perder el sitio.
    var assetDetailStack by remember {
        mutableStateOf<List<AssetDetailContext>>(emptyList())
    }
    fun closeAssetDetail() {
        val previous = assetDetailStack.lastOrNull()
        if (previous != null) {
            assetDetailStack = assetDetailStack.dropLast(1)
            assetDetail = previous
        } else {
            assetDetail = null
        }
    }
    // Red de seguridad: cualquier cierre directo (borrar, papelera del
    // dispositivo…) vacía la pila para no resucitar contextos viejos.
    LaunchedEffect(assetDetail == null) {
        if (assetDetail == null) assetDetailStack = emptyList()
    }
    // Con el visor abierto el fondo bajo las barras es el scrim negro de la
    // foto: iconos claros aunque el tema sea claro. Este sitio recompone al
    // abrir/cerrar el visor, así que también restaura el estado del tema.
    com.photonne.app.ui.platform.SyncSystemBarIcons(
        darkBackground = com.photonne.app.ui.theme.LocalIsDarkTheme.current ||
            assetDetail != null
    )
    // Retocar la pestaña Fotos activa vuelve arriba (consumido por TimelineScreen).
    var timelineScrollToTopTick by remember { mutableStateOf(0) }
    var albumsScrollToTopTick by remember { mutableStateOf(0) }
    var foldersScrollToTopTick by remember { mutableStateOf(0) }
    // The bucket the "Mi dispositivo" detail subscreen shows. Survives going
    // back to the bucket list (harmless), reset on every open.
    var deviceFolderBucket by remember {
        mutableStateOf<com.photonne.app.data.devicelibrary.DeviceBucket?>(null)
    }
    // Type filter the failures registry opens with when reached from a
    // notification actionUrl ("/admin/enrichment-failures?type=Exif").
    var adminEnrichmentInitialType by remember { mutableStateOf<String?>(null) }
    // The failures registry is reachable from three places now — the System
    // hub, a notification's actionUrl, and a task row whose queue is stuck —
    // so "volver" has to remember which one, instead of always landing on the
    // hub the way it did when the hub was the only door.
    var adminEnrichmentReturnTo by remember { mutableStateOf(MoreSubscreen.AdminSystemHub) }
    // An open memory, shown as an album. An overlay rather than a MoreSubscreen:
    // it's reached from the Fotos strip too, not just from Más → Recuerdos, so it
    // can't hang off the Más hierarchy.
    var memoryDetail by remember {
        mutableStateOf<com.photonne.app.ui.memories.MemoryDetailContext?>(null)
    }
    // Lote L9: el recuerdo abierto lleva sus fotos en mano (no tiene
    // ViewModel), así que lo borrado o archivado desde el visor se le quita
    // aquí; si se queda vacío, se cierra.
    val assetMutationBus: com.photonne.app.data.events.AssetMutationBus = koinInject()
    LaunchedEffect(assetMutationBus) {
        assetMutationBus.events.collect { event ->
            val memory = memoryDetail ?: return@collect
            when (event) {
                // Favorito (visor o favorito en bloque): el rótulo de la barra de
                // selección depende de este flag.
                is com.photonne.app.data.events.AssetMutation.FavoriteChanged -> {
                    fun List<TimelineItem>.patched() = map {
                        if (it.id == event.assetId) it.copy(isFavorite = event.isFavorite) else it
                    }
                    memoryDetail = memory.copy(
                        items = memory.items.patched(),
                        openedItems = memory.openedItems.patched()
                    )
                }
                // Deshacer de la papelera/archivar lanzado desde el recuerdo: las
                // fotos vuelven a su sitio en el orden curado original.
                is com.photonne.app.data.events.AssetMutation.Restored -> {
                    val back = event.assetIds.toSet()
                    val current = memory.items.mapTo(HashSet()) { it.id }
                    if (memory.openedItems.none { it.id in back && it.id !in current }) {
                        return@collect
                    }
                    memoryDetail = memory.copy(
                        items = memory.openedItems.filter { it.id in current || it.id in back }
                    )
                }
                is com.photonne.app.data.events.AssetMutation.Removed,
                is com.photonne.app.data.events.AssetMutation.Purged -> {
                    val removed = when (event) {
                        is com.photonne.app.data.events.AssetMutation.Removed -> event.assetIds
                        is com.photonne.app.data.events.AssetMutation.Purged -> event.assetIds
                        else -> emptyList()
                    }.toSet()
                    val remaining = memory.items.filterNot { it.id in removed }
                    if (remaining.size != memory.items.size) {
                        memoryDetail = if (remaining.isEmpty()) null else memory.copy(items = remaining)
                    }
                }
                else -> Unit
            }
        }
    }
    // Selección múltiple del recuerdo abierto (tanda 2 de funciones nuevas).
    val memorySelectionViewModel: com.photonne.app.ui.memories.MemorySelectionViewModel =
        koinViewModel()
    val memorySelectionState by memorySelectionViewModel.state.collectAsStateWithLifecycle()
    // Cerrar o cambiar de recuerdo no arrastra la selección al siguiente.
    val openMemoryKey = memoryDetail?.let { it.title to it.coverAssetId }
    LaunchedEffect(openMemoryKey) { memorySelectionViewModel.clearSelection() }
    // Tracks the asset shown by the viewer's pager — drives the
    // grid → detail shared-element morph. Null when the viewer is closed
    // so all grid thumbnails return to their normal visible state.
    var currentDetailAssetId by remember { mutableStateOf<String?>(null) }
    // Qué rejilla abrió el visor: esa sigue a la foto vista para que al cerrar
    // la miniatura esté en pantalla (tanda 2 de funciones nuevas).
    val viewerReturnState = remember { com.photonne.app.ui.grid.ViewerReturnState() }
    var showCreateAlbum by remember { mutableStateOf(false) }
    var showAlbumTypeChooser by remember { mutableStateOf(false) }
    var showEditAlbum by remember { mutableStateOf(false) }
    // Smart album opened in the rule editor (SmartAlbumEditor subscreen); null = new album.
    var editingSmartAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var showDeleteAlbum by remember { mutableStateOf(false) }
    var showLeaveAlbum by remember { mutableStateOf(false) }
    var showShares by remember { mutableStateOf(false) }
    var showCreateShare by remember { mutableStateOf(false) }
    var editingShareLink by remember { mutableStateOf<AlbumShareLink?>(null) }
    var showMembers by remember { mutableStateOf(false) }
    var showInviteMember by remember { mutableStateOf(false) }
    var addToAlbum by remember { mutableStateOf<AddToAlbumState?>(null) }
    var bulkAddToAlbum by remember { mutableStateOf<Boolean>(false) }
    var bulkAddSource by remember { mutableStateOf<BulkAddSource?>(null) }
    var selectedPerson by remember {
        mutableStateOf<com.photonne.app.data.models.Person?>(null)
    }
    // Punto 49: el álbum, la carpeta y la persona abiertos son objetos
    // completos (no Saveable). Se guarda solo su id y, tras una recreación
    // (rotación en tablet, muerte de proceso), se vuelven a pedir al
    // servidor antes de que la pantalla los necesite. La pila de carpetas
    // no se rehidrata: atrás desde la carpeta restaurada vuelve a la raíz.
    var savedAlbumId by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var savedFolderId by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var savedPersonId by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var openIdsRehydrated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        savedAlbumId?.takeIf { selectedAlbum == null }?.let { id ->
            runCatching { albumsRepository.get(id) }.onSuccess { selectedAlbum = it }
        }
        savedFolderId?.takeIf { selectedFolder == null }?.let { id ->
            runCatching { foldersRepository.get(id) }.onSuccess { selectedFolder = it }
        }
        savedPersonId?.takeIf { selectedPerson == null }?.let { id ->
            runCatching { peopleRepository.get(id) }.onSuccess { selectedPerson = it }
        }
        openIdsRehydrated = true
    }
    // Solo después de rehidratar: si no, la primera composición (todo a null)
    // pisaría los ids guardados antes de poder leerlos.
    LaunchedEffect(openIdsRehydrated, selectedAlbum?.id, selectedFolder?.id, selectedPerson?.id) {
        if (openIdsRehydrated) {
            savedAlbumId = selectedAlbum?.id
            savedFolderId = selectedFolder?.id
            savedPersonId = selectedPerson?.id
        }
    }
    var showRenamePerson by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }
    // Confirmación de fusión: la persona elegida (que desaparecerá) y el
    // estado de la petición.
    var mergeSource by remember {
        mutableStateOf<com.photonne.app.data.models.Person?>(null)
    }
    var isMerging by remember { mutableStateOf(false) }
    var mergeError by remember { mutableStateOf<String?>(null) }
    var showAcceptAllSuggestions by remember { mutableStateOf(false) }
    var showDismissAllSuggestions by remember { mutableStateOf(false) }
    // Confirmaciones de revocar enlace / quitar miembro (antes, un toque).
    var revokingShareToken by remember { mutableStateOf<String?>(null) }
    var revokingAlbumMember by remember {
        mutableStateOf<com.photonne.app.data.models.AlbumPermission?>(null)
    }
    var revokingFolderMember by remember {
        mutableStateOf<com.photonne.app.data.models.AlbumPermission?>(null)
    }
    var showAssetFacesSheet by remember { mutableStateOf(false) }
    var assetFacesRevision by remember { mutableStateOf(0) }
    var showJumpToDate by remember { mutableStateOf(false) }
    var pendingJumpDate by remember { mutableStateOf<kotlin.time.Instant?>(null) }
    var pendingAddTarget by remember { mutableStateOf<PendingAddTarget?>(null) }
    // Álbum ya creado cuyo alta falló: reintentar solo repite el alta, nunca
    // crea un segundo álbum.
    var pendingAddAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var pendingAssetAddSubmitting by remember { mutableStateOf(false) }
    var pendingAssetAddError by remember { mutableStateOf<String?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var showEditFolder by remember { mutableStateOf(false) }
    var showDeleteFolder by remember { mutableStateOf(false) }
    var showEditSubfolder by remember { mutableStateOf(false) }
    var showDeleteSubfolder by remember { mutableStateOf(false) }
    var showFolderMembers by remember { mutableStateOf(false) }
    var showInviteFolderMember by remember { mutableStateOf(false) }
    var showMoveFolder by remember { mutableStateOf(false) }
    var showMoveSelectedAssets by remember { mutableStateOf(false) }
    var showMoveSelectedAssetsTimeline by remember { mutableStateOf(false) }
    var showMoveSelectedAssetsInbox by remember { mutableStateOf(false) }
    var moveSelectionRequest by remember { mutableStateOf<MoveSelectionRequest?>(null) }
    var moveSelectionSubmitting by remember { mutableStateOf(false) }
    var moveSelectionError by remember { mutableStateOf<String?>(null) }
    // Non-null while the inbox move "Revisar" grid is open: the chosen destination.
    var inboxReviewTarget by remember { mutableStateOf<String?>(null) }
    // Resumen del reparto por año tras mover por condiciones: se confirma antes
    // de volver a la bandeja.
    var organizeRuleSummary by remember {
        mutableStateOf<com.photonne.app.data.models.MoveOutcome?>(null)
    }
    var showSearchFilters by remember { mutableStateOf(false) }
    var showAlbumsFilters by remember { mutableStateOf(false) }
    var showFoldersFilters by remember { mutableStateOf(false) }
    var pendingActionAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var pendingActionFolder by remember {
        mutableStateOf<com.photonne.app.data.models.FolderSummary?>(null)
    }
    var moreSubscreen by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<MoreSubscreen?>(null)
    }
    // Perfil abierto desde la cabecera de Más: Atrás vuelve a Más, no a Ajustes.
    var profileOpenedFromMore by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    // Buscar abierto desde una etiqueta de Explorar: Atrás vuelve a esa
    // subpantalla (y a la pestaña que había debajo), no a Fotos.
    var searchReturnTo by remember { mutableStateOf<Pair<MainTab, MoreSubscreen>?>(null) }
    // Búsqueda abierta desde una escena u objeto del visor: Atrás vuelve a la foto.
    var searchViewerReturn by remember { mutableStateOf<ViewerReturn?>(null) }
    LaunchedEffect(selectedTab) {
        if (selectedTab != MainTab.Search) {
            searchReturnTo = null
            searchViewerReturn = null
        }
    }
    fun restoreViewer(returnTo: ViewerReturn) {
        selectedTab = returnTo.tab
        moreSubscreen = returnTo.subscreen
        selectedPerson = returnTo.person
        // Si debajo había otra persona abierta, su detalle vuelve a cargarse:
        // el view model es uno solo y ahora tiene la que se abrió desde la cara.
        returnTo.person?.let { personDetailViewModel.open(it.id, it.name) }
        assetDetailStack = returnTo.viewerStack
        assetDetail = returnTo.viewer
    }
    /** Fotografía del visor abierto (en la foto que se está viendo) para volver
     *  a él; null si no hay visor. */
    fun viewerReturnPoint(): ViewerReturn? {
        val ctx = assetDetail ?: return null
        val atIndex = ctx.items.indexOfFirst { it.id == currentDetailAssetId }
            .takeIf { it >= 0 } ?: ctx.startIndex
        return ViewerReturn(
            tab = selectedTab,
            subscreen = moreSubscreen,
            person = selectedPerson,
            viewer = ctx.copy(startIndex = atIndex),
            viewerStack = assetDetailStack
        )
    }
    /** Escena u objeto tocado en el panel de info: búsqueda filtrada por él. */
    fun openSearchFromViewer(applyFilter: () -> Unit) {
        val returnPoint = viewerReturnPoint() ?: return
        applyFilter()
        assetDetailStack = emptyList()
        assetDetail = null
        moreSubscreen = null
        selectedTab = MainTab.Search
        searchReturnTo = null
        searchViewerReturn = returnPoint
    }
    fun searchBack() {
        searchViewerReturn?.let { returnTo ->
            searchViewerReturn = null
            restoreViewer(returnTo)
            return
        }
        val returnTo = searchReturnTo
        searchReturnTo = null
        if (returnTo != null) {
            selectedTab = returnTo.first
            moreSubscreen = returnTo.second
        } else {
            selectedTab = MainTab.Timeline
        }
    }
    // Carpeta abierta desde Ubicaciones (Lote N8): al salir de su raíz, Atrás
    // vuelve a esa subpantalla (y a la pestaña de debajo), no a la lista de
    // Carpetas. Cualquier toque en la barra de navegación lo olvida.
    var folderReturnTo by remember { mutableStateOf<Pair<MainTab, MoreSubscreen>?>(null) }
    fun folderBack() {
        if (folderBackStack.isNotEmpty()) {
            selectedFolder = folderBackStack.removeAt(folderBackStack.lastIndex)
            return
        }
        selectedFolder = null
        val returnTo = folderReturnTo
        folderReturnTo = null
        if (returnTo != null) {
            selectedTab = returnTo.first
            moreSubscreen = returnTo.second
        }
    }
    // Persona abierta desde una cara del visor: Atrás vuelve a la foto.
    var personReturnTo by remember { mutableStateOf<ViewerReturn?>(null) }
    fun personBack() {
        selectedPerson = null
        val returnTo = personReturnTo
        personReturnTo = null
        if (returnTo != null) restoreViewer(returnTo)
    }
    /**
     * Toque en una cara con persona: cierra el visor y abre esa persona. Solo
     * llega el id (la cara no trae el nombre), así que se abre ya con él y el
     * nombre y el resto se completan en cuanto responde el servidor.
     */
    fun openPersonFromViewer(personId: String) {
        personReturnTo = viewerReturnPoint() ?: return
        assetDetailStack = emptyList()
        assetDetail = null
        selectedTab = MainTab.More
        moreSubscreen = MoreSubscreen.People
        selectedPerson = com.photonne.app.data.models.Person(id = personId)
        personDetailViewModel.open(personId, null)
        coroutineScope.launch {
            runCatching { peopleRepository.get(personId) }.onSuccess { person ->
                if (selectedPerson?.id == personId) {
                    selectedPerson = person
                    personDetailViewModel.open(person.id, person.name)
                }
            }
        }
    }
    // Enlace compartido abierto desde fuera (photonne://share/{token}): tapa la
    // pantalla como un recuerdo; Atrás lo cierra.
    val sharedLinkViewModel: com.photonne.app.ui.share.SharedLinkViewModel = koinViewModel()
    val sharedLinkState by sharedLinkViewModel.state.collectAsStateWithLifecycle()
    val sharedLinkOpen = sharedLinkState.target != null
    // Lote M4: una notificación del backup abre su pantalla (progreso) o sus
    // Pendientes (fallos). Se consume una vez, también con la app ya abierta.
    // Funciones nuevas 3/3: también lo compartido desde otra app (Subida con
    // la cola ya cargada), los enlaces photonne:// y el aviso de actividad.
    val externalDestination by com.photonne.app.ui.main.ExternalNavigation.pending
        .collectAsStateWithLifecycle()
    LaunchedEffect(externalDestination) {
        when (com.photonne.app.ui.main.ExternalNavigation.consume()) {
            com.photonne.app.ui.main.ExternalDestination.Upload -> {
                val files = com.photonne.app.ui.main.ExternalNavigation.consumeSharedFiles()
                assetDetail = null
                sharedLinkViewModel.close()
                selectedTab = MainTab.More
                moreSubscreen = MoreSubscreen.Upload
                uploadViewModel.enqueue(files) { timelineViewModel.refresh() }
            }
            com.photonne.app.ui.main.ExternalDestination.SharedLink -> {
                com.photonne.app.ui.main.ExternalNavigation.consumeSharedLink()?.let { target ->
                    assetDetail = null
                    sharedLinkViewModel.open(target)
                }
            }
            com.photonne.app.ui.main.ExternalDestination.Notifications -> {
                assetDetail = null
                sharedLinkViewModel.close()
                selectedTab = MainTab.More
                moreSubscreen = MoreSubscreen.Notifications
                notificationsViewModel.refresh()
            }
            com.photonne.app.ui.main.ExternalDestination.Backup -> {
                assetDetail = null
                selectedTab = MainTab.More
                moreSubscreen = MoreSubscreen.DeviceBackup
            }
            com.photonne.app.ui.main.ExternalDestination.BackupPending -> {
                assetDetail = null
                selectedTab = MainTab.More
                moreSubscreen = MoreSubscreen.DeviceBackupPending
            }
            null -> Unit
        }
    }
    // Vuelta a la bandeja tras un movimiento por condiciones, con el contador y
    // la rejilla al día.
    val organizeRuleMoved = {
        moreSubscreen = MoreSubscreen.OrganizeInbox
        organizeInboxViewModel.refresh()
        foldersViewModel.refreshOrganizeCount()
    }
    var adminUserEditorId by remember { mutableStateOf<String?>(null) }
    var adminLibraryEditorId by remember { mutableStateOf<String?>(null) }
    var showUnarchiveAll by remember { mutableStateOf(false) }
    var showRestoreAllTrash by remember { mutableStateOf(false) }
    var showEmptyTrash by remember { mutableStateOf(false) }
    var showPurgeSelected by remember { mutableStateOf(false) }
    // Active tab of the unified Trash screen (Personal / Compartida).
    var trashTab by remember { mutableStateOf(com.photonne.app.ui.library.TrashTab.Personal) }

    // Cerrar sesión era un solo toque directo a authRepository.logout();
    // ahora confirma, y la confirmación avisa si quedan copias pendientes.
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val onLogout: () -> Unit = { showLogoutConfirm = true }
    val albumBack: () -> Unit = { selectedAlbum = null }

    // Mirror the share link count for the currently opened album back into
    // the albums list so the public-link badge stays in sync after
    // create/revoke without a full refresh.
    val openedAlbumId = selectedAlbum?.id
    val sharedAlbumId = albumSharesState.albumId
    val activeLinks = albumSharesState.links.isNotEmpty()
    LaunchedEffect(openedAlbumId, sharedAlbumId, activeLinks) {
        if (openedAlbumId != null && openedAlbumId == sharedAlbumId) {
            albumsViewModel.applyShareLinkChanged(openedAlbumId, activeLinks)
            selectedAlbum = selectedAlbum?.copy(hasActiveShareLink = activeLinks)
        }
    }

    // Hardware/gesture back: mirrors the same precedence as the in-app
    // back arrows so the system back button feels native. Disabled at the
    // root (Hub on Timeline tab, no overlays) so the system can finish
    // the activity. Modal dialogs/sheets aren't enumerated here because
    // Compose Material3 forwards back-press to their onDismissRequest.
    val isAnySelectionActive = (
        (selectedTab == MainTab.Timeline && timelineState.isSelectionActive) ||
        (selectedTab == MainTab.Albums && selectedAlbum != null &&
            albumDetailState.isSelectionActive) ||
        (selectedTab == MainTab.Albums && albumsState.isSelectionActive) ||
        (selectedTab == MainTab.Folders && selectedFolder != null &&
            (folderDetailState.isSelectionActive ||
                folderDetailState.isSubfolderSelectionActive)) ||
        (selectedTab == MainTab.Folders && foldersState.isSelectionActive) ||
        (selectedTab == MainTab.Search && searchState.isSelectionActive) ||
        (moreSubscreen == MoreSubscreen.Favorites && favoritesState.isSelectionActive) ||
        (moreSubscreen == MoreSubscreen.Archived && archivedState.isSelectionActive) ||
        (moreSubscreen == MoreSubscreen.Trash && trashTab == com.photonne.app.ui.library.TrashTab.Personal && trashState.isSelectionActive) ||
        (moreSubscreen == MoreSubscreen.People && selectedPerson != null &&
            personDetailState.isSelectionActive) ||
        (moreSubscreen == MoreSubscreen.OrganizeInbox && organizeInboxState.isSelectionActive) ||
        (moreSubscreen == MoreSubscreen.OrganizeExcluded && organizeExcludedState.isSelectionActive)
    )
    var overlayForward by remember { mutableStateOf(true) }
    val canHandleBack = (
        assetDetail != null ||
        sharedLinkOpen ||
        memoryDetail != null ||
        isAnySelectionActive ||
        // Un álbum o una carpeta abiertos cuentan solo en SU pestaña: si se
        // quedaron abiertos por debajo y estamos en Fotos, ninguna rama de abajo
        // los cierra y el Atrás se tragaba sin hacer nada (ni salir de la app).
        (selectedTab == MainTab.Albums && selectedAlbum != null) ||
        (selectedTab == MainTab.Folders && selectedFolder != null) ||
        selectedPerson != null ||
        moreSubscreen != null ||
        selectedTab != MainTab.Timeline
    )
    // Dirección de la última navegación del overlay. Se marca aquí y la consume
    // el bloque de transición al montar el destino nuevo: "atrás" entra desde el
    // lado contrario, como en cualquier pila de navegación.
    PlatformBackHandler(enabled = canHandleBack) {
        overlayForward = false
        when {
            assetDetail != null -> { closeAssetDetail() }
            sharedLinkOpen -> sharedLinkViewModel.close()
            // Before every selection case: an open memory covers the screen, so
            // back closes what you're actually looking at, not what's underneath.
            memoryDetail != null -> {
                if (memorySelectionState.isSelectionActive) {
                    memorySelectionViewModel.clearSelection()
                } else {
                    memoryDetail = null
                }
            }
            // Igual con la revisión previa a mover: tapa la pantalla entera (y no
            // se cierra a media confirmación, que el movimiento es irreversible).
            inboxReviewTarget != null ->
                if (!organizeInboxState.isBulkMutating) inboxReviewTarget = null
            organizeRuleState.reviewGroups != null ->
                if (!organizeRuleState.isMoving) organizeRuleViewModel.closeReview()
            selectedTab == MainTab.Timeline &&
                timelineState.isSelectionActive -> timelineViewModel.clearSelection()
            selectedTab == MainTab.Albums && selectedAlbum != null &&
                albumDetailState.isSelectionActive -> albumDetailViewModel.clearSelection()
            selectedTab == MainTab.Albums && albumsState.isSelectionActive ->
                albumsViewModel.clearSelection()
            selectedTab == MainTab.Folders && selectedFolder != null &&
                folderDetailState.isSelectionActive -> folderDetailViewModel.clearSelection()
            selectedTab == MainTab.Folders && selectedFolder != null &&
                folderDetailState.isSubfolderSelectionActive ->
                folderDetailViewModel.clearSubfolderSelection()
            selectedTab == MainTab.Folders && foldersState.isSelectionActive ->
                foldersViewModel.clearSelection()
            selectedTab == MainTab.Search && searchState.isSelectionActive ->
                searchViewModel.clearSelection()
            moreSubscreen == MoreSubscreen.Favorites && favoritesState.isSelectionActive ->
                favoritesViewModel.clearSelection()
            moreSubscreen == MoreSubscreen.Archived && archivedState.isSelectionActive ->
                archivedViewModel.clearSelection()
            moreSubscreen == MoreSubscreen.Trash && trashTab == com.photonne.app.ui.library.TrashTab.Personal && trashState.isSelectionActive ->
                trashViewModel.clearSelection()
            moreSubscreen == MoreSubscreen.People && selectedPerson != null &&
                personDetailState.isSelectionActive -> personDetailViewModel.clearSelection()
            moreSubscreen == MoreSubscreen.OrganizeInbox && organizeInboxState.isSelectionActive ->
                organizeInboxViewModel.clearSelection()
            moreSubscreen == MoreSubscreen.OrganizeExcluded && organizeExcludedState.isSelectionActive ->
                organizeExcludedViewModel.clearSelection()
            selectedTab == MainTab.Albums && selectedAlbum != null -> albumBack()
            selectedTab == MainTab.Folders && selectedFolder != null -> folderBack()
            moreSubscreen == MoreSubscreen.People && selectedPerson != null -> personBack()
            moreSubscreen == MoreSubscreen.AdminUserEditor -> {
                adminUserEditorId = null
                adminUsersViewModel.clearMessages()
                moreSubscreen = MoreSubscreen.AdminUsers
            }
            moreSubscreen == MoreSubscreen.AdminLibraryEditor -> {
                adminLibraryEditorId = null
                adminLibrariesViewModel.clearMessages()
                moreSubscreen = MoreSubscreen.AdminLibraries
            }
            moreSubscreen == MoreSubscreen.AccountProfile && profileOpenedFromMore -> {
                moreSubscreen = null
            }
            moreSubscreen != null -> { moreSubscreen = parentMoreSubscreen(moreSubscreen!!) }
            selectedTab == MainTab.Search -> searchBack()
            selectedTab != MainTab.Timeline -> { selectedTab = MainTab.Timeline }
        }
    }

    // Escritorio: Ctrl/Cmd+A selecciona lo mismo que el "Seleccionar todo" de
    // la barra de cada pantalla (el timeline no lo ofrece: se pagina sobre toda
    // la biblioteca). Solo selecciona: si ya está todo, no deselecciona.
    fun selectAllOf(selected: Int, total: Int, toggle: () -> Unit): (() -> Unit)? =
        if (total <= 0) null else ({ if (selected < total) toggle() })
    val selectAllShortcut: (() -> Unit)? = when {
        assetDetail != null -> null
        memoryDetail != null -> memoryDetail?.let { memory ->
            selectAllOf(memorySelectionState.selection.size, memory.items.size) {
                memorySelectionViewModel.toggleSelectAll(memory.items.map { it.id })
            }
        }
        selectedTab == MainTab.Albums && selectedAlbum != null ->
            selectAllOf(albumDetailState.selection.size, albumDetailState.items.size,
                albumDetailViewModel::toggleSelectAll)
        selectedTab == MainTab.Folders && selectedFolder != null &&
            !folderDetailState.isSubfolderSelectionActive ->
            selectAllOf(folderDetailState.selection.size, folderDetailState.items.size,
                folderDetailViewModel::toggleSelectAll)
        selectedTab == MainTab.Search ->
            selectAllOf(searchState.selection.size, searchState.results.size,
                searchViewModel::toggleSelectAll)
        moreSubscreen == MoreSubscreen.Favorites ->
            selectAllOf(favoritesState.selection.size, favoritesState.items.size,
                favoritesViewModel::toggleSelectAll)
        moreSubscreen == MoreSubscreen.Archived ->
            selectAllOf(archivedState.selection.size, archivedState.items.size,
                archivedViewModel::toggleSelectAll)
        moreSubscreen == MoreSubscreen.People && selectedPerson != null ->
            selectAllOf(personDetailState.selection.size, personDetailState.items.size,
                personDetailViewModel::toggleSelectAll)
        moreSubscreen == MoreSubscreen.OrganizeInbox ->
            selectAllOf(organizeInboxState.selection.size, organizeInboxState.items.size,
                organizeInboxViewModel::toggleSelectAll)
        else -> null
    }
    com.photonne.app.ui.selection.SelectionShortcutsHandler(onSelectAll = selectAllShortcut)

    // ---- Horizontal swipe between the four primary tabs ----
    // The bottom-nav tabs, in bar order, become pages of a HorizontalPager so a
    // left/right drag glides between Fotos · Álbumes · Carpetas · Más. Buscar is
    // not a nav tab (it has no page); it opens as an overlay like the detail
    // screens and subscreens do.
    val navTabs = remember {
        listOf(MainTab.Timeline, MainTab.Albums, MainTab.Folders, MainTab.More)
    }
    val mainPagerState = rememberPagerState(
        initialPage = navTabs.indexOf(selectedTab).coerceAtLeast(0),
        pageCount = { navTabs.size }
    )
    // Whether an opaque overlay (drill-down / Buscar / More subscreen) is covering
    // the pager base layer. When true the overlay must paint its own solid
    // background, otherwise the tab body underneath shows through — the pager
    // keeps all top-level bodies composed behind it.
    val overlayVisible = moreSubscreen != null ||
        (selectedTab == MainTab.Albums && selectedAlbum != null) ||
        (selectedTab == MainTab.Folders && selectedFolder != null) ||
        selectedTab == MainTab.Search
    // Only allow the horizontal tab-swipe on a bare top-level tab: never while a
    // detail, Buscar, a subscreen or a multi-select session owns the screen —
    // those render as an opaque overlay and take the horizontal gesture (paging
    // through photos, panning a map, selecting items) for themselves.
    //
    // Se mira el overlay A LA VISTA, no `selectedAlbum`/`selectedFolder` a secas:
    // un álbum o una carpeta siguen abiertos en su pestaña al saltar a otra (al
    // volver se retoman), y contarlos aquí dejaba el gesto muerto en las cuatro
    // pestañas hasta volver a cerrarlos.
    val canSwipeTabs = !overlayVisible &&
        !timelineState.isSelectionActive &&
        !albumsState.isSelectionActive &&
        !foldersState.isSelectionActive
    // The tab whose chrome (top bar + immersive edge-to-edge padding) should show
    // *right now*. While a swipe is in flight we follow the pager's most-visible
    // page (which flips at the half-way point) rather than `selectedTab` (which
    // only updates once the swipe settles): otherwise the incoming page snaps its
    // top padding on landing and the grid jumps up by the app-bar height. Any
    // overlay (detail / Buscar / subscreen / selection) pins it back to
    // selectedTab so those never inherit a neighbour's chrome mid-drag.
    val chromeTab = if (canSwipeTabs) {
        navTabs.getOrNull(mainPagerState.currentPage) ?: selectedTab
    } else {
        selectedTab
    }
    // Identidad del destino que ocupa el overlay. Cambiarla es lo que dispara la
    // transición de entrada; navegar dentro del MISMO destino (abrir el visor,
    // seleccionar fotos) la deja quieta.
    val overlayKey: Any = when {
        moreSubscreen != null ->
            "more:${moreSubscreen!!.name}:${selectedPerson?.id ?: ""}"
        selectedTab == MainTab.Albums && selectedAlbum != null ->
            "album:${selectedAlbum!!.id}"
        selectedTab == MainTab.Folders && selectedFolder != null ->
            "folder:${selectedFolder!!.id}"
        selectedTab == MainTab.Search -> "search"
        else -> "none"
    }
    // The pager runs edge-to-edge at the top whenever a bare top-level tab is
    // showing: each page then paints its own top bar inside itself (so it slides
    // with the content, no shared Scaffold bar snapping the grid down). It drops
    // back to a padded Scaffold bar only while a selection is active on the
    // visible tab (or an overlay is up) — those need the docked Scaffold bar.
    val pagerBareTop = !overlayVisible &&
        !(chromeTab == MainTab.Timeline && timelineState.isSelectionActive) &&
        !(chromeTab == MainTab.Albums && albumsState.isSelectionActive) &&
        !(chromeTab == MainTab.Folders && foldersState.isSelectionActive)
    // Subpantallas que pintan su PROPIO cromo flotante sobre el contenido
    // (cápsulas de cristal que se acoplan arriba y se esconden al bajar, como
    // Fotos) en vez de la barra acoplada de este Scaffold. Con una selección
    // activa vuelven a la barra sólida: una acción no puede escaparse scroll
    // abajo. Sólo hay una a la vez, así que comparten el estado de visibilidad.
    val floatingChromeSubscreen = when (moreSubscreen) {
        MoreSubscreen.People ->
            selectedPerson == null || !personDetailState.isSelectionActive
        MoreSubscreen.ExploreScenes,
        MoreSubscreen.ExploreObjects,
        MoreSubscreen.Memories,
        MoreSubscreen.DeviceFolders,
        MoreSubscreen.DeviceFolderDetail,
        MoreSubscreen.Map -> true
        MoreSubscreen.OrganizeInbox -> !organizeInboxState.isSelectionActive
        MoreSubscreen.OrganizeExcluded -> !organizeExcludedState.isSelectionActive
        MoreSubscreen.Favorites -> !favoritesState.isSelectionActive
        MoreSubscreen.Archived -> !archivedState.isSelectionActive
        MoreSubscreen.UnsupportedFiles -> true
        MoreSubscreen.PeopleSuggestions -> true
        // Formularios: cromo flotante estático dibujado dentro de cada pantalla.
        // Los que tienen selección (DeviceBackupPending / Trash) vuelven a la
        // barra acoplada de selección mientras haya algo seleccionado.
        MoreSubscreen.DeviceBackupPending -> deviceBackupState.selectedCount == 0
        MoreSubscreen.Trash -> !trashState.isSelectionActive
        MoreSubscreen.DeviceBackup,
        MoreSubscreen.EnrichmentStatus,
        MoreSubscreen.Utilities,
        MoreSubscreen.UtilitiesDuplicates,
        MoreSubscreen.UtilitiesLargeFiles,
        MoreSubscreen.UtilitiesLocations,
        MoreSubscreen.MyLinks,
        MoreSubscreen.OrganizeRule,
        MoreSubscreen.Notifications,
        MoreSubscreen.AccountSettings,
        MoreSubscreen.AccountProfile,
        MoreSubscreen.AccountSecurity,
        MoreSubscreen.AccountAppearance,
        MoreSubscreen.AccountStorage,
        MoreSubscreen.AccountConnection,
        MoreSubscreen.Administration,
        MoreSubscreen.AdminUsers,
        MoreSubscreen.AdminUserEditor,
        MoreSubscreen.AdminLibraries,
        MoreSubscreen.AdminLibraryEditor,
        MoreSubscreen.AdminStats,
        MoreSubscreen.AdminSettingsHub,
        MoreSubscreen.AdminSystemHub,
        MoreSubscreen.AdminSettingsFaceRecognition,
        MoreSubscreen.AdminSettingsObjectDetection,
        MoreSubscreen.AdminSettingsSceneClassification,
        MoreSubscreen.AdminSettingsTextRecognition,
        MoreSubscreen.AdminSettingsImageEmbedding,
        MoreSubscreen.AdminSettingsImage,
        MoreSubscreen.AdminSettingsMetadata,
        MoreSubscreen.AdminSettingsNightly,
        MoreSubscreen.AdminSettingsNotifications,
        MoreSubscreen.AdminSettingsServer,
        MoreSubscreen.AdminSettingsTrash,
        MoreSubscreen.AdminSettingsUserDefaults,
        MoreSubscreen.AdminSettingsVersion,
        MoreSubscreen.AdminSystemRunTasks,
        MoreSubscreen.AdminSystemDuplicates,
        MoreSubscreen.AdminSystemEnrichmentFailures,
        MoreSubscreen.AdminSystemBackup -> true
        else -> false
    }
    // La cápsula estática de los formularios nunca reporta visibilidad, así que
    // al entrar a cualquier subpantalla se reinicia la nav a visible; las que sí
    // scrollean la vuelven a reportar en cuanto se mueven.
    LaunchedEffect(moreSubscreen) { subscreenChromeVisible = true }

    /**
     * Favorito en bloque desde la barra de selección. Si TODAS las
     * seleccionadas ya son favoritas la acción es quitar; si no, añadir. Solo
     * se llama al servidor por las que tienen que cambiar, y el snackbar dice
     * cuántas cambiaron y cuántas fallaron. Lo local del dispositivo no tiene
     * favorito en el servidor y se ignora.
     */
    fun runBulkFavorite(
        items: List<TimelineItem>,
        selection: Set<String>,
        snackbar: com.photonne.app.ui.main.SnackbarController?,
        clearSelection: () -> Unit,
    ) {
        val selected = items.filter { it.id in selection && !it.isLocalOnly }
        if (selected.isEmpty()) return
        val favorite = !selected.all { it.isFavorite }
        val pending = selected.filter { it.isFavorite != favorite }.map { it.id }
        clearSelection()
        actionsViewModel.setFavorites(pending, favorite) { changed, failed ->
            coroutineScope.launch {
                val done = org.jetbrains.compose.resources.getPluralString(
                    if (favorite) Res.plurals.selection_favorite_added_done
                    else Res.plurals.selection_favorite_removed_done,
                    // Las que ya estaban como se pedía cuentan como hechas.
                    selected.size - failed,
                    selected.size - failed
                )
                val message = if (failed == 0) done else {
                    val failedText = org.jetbrains.compose.resources.getPluralString(
                        Res.plurals.selection_favorite_failed, failed, failed
                    )
                    if (changed == 0 && selected.size == failed) failedText
                    else "$done · $failedText"
                }
                snackbar?.show(message)
            }
        }
    }

    /** ¿Todas las seleccionadas (del servidor) son ya favoritas? Decide el rótulo. */
    fun selectionAllFavorite(items: List<TimelineItem>, selection: Set<String>): Boolean {
        val selected = items.filter { it.id in selection && !it.isLocalOnly }
        return selected.isNotEmpty() && selected.all { it.isFavorite }
    }

    val topBar: @Composable () -> Unit = {
        when {
            selectedTab == MainTab.Timeline &&
                timelineState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = timelineState.selection.size,
                    isMutating = timelineState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = timelineViewModel::clearSelection
                    // Sin "Seleccionar todo": el timeline se pagina sobre toda
                    // la biblioteca y el botón solo cogía lo cargado, que no es
                    // lo que promete. La casilla de mes cubre el caso real.
                )
            selectedTab == MainTab.Albums && selectedAlbum != null &&
                albumDetailState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = albumDetailState.selection.size,
                    totalCount = albumDetailState.items.size,
                    isMutating = albumDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = albumDetailViewModel::clearSelection,
                    onSelectAll = albumDetailViewModel::toggleSelectAll
                )
            selectedTab == MainTab.Albums && selectedAlbum != null -> {
                // AlbumDetailScreen paints its own floating top chrome over the
                // grid (docked on the hero's cover, frosted capsules once
                // scrolled), like Fotos, so no separate top bar here.
            }
            selectedTab == MainTab.Albums && albumsState.isSelectionActive -> {
                val target = albumsState.albums.firstOrNull {
                    it.id == albumsState.selectedAlbumId
                }
                if (target != null) {
                    com.photonne.app.ui.main.AlbumCardSelectionTopBar(
                        albumName = target.name,
                        isMutating = albumsState.isMutating,
                        onClose = albumsViewModel::clearSelection
                    )
                } else {
                    AlbumsListTopBar(
                        onOpenFilters = { showAlbumsFilters = true },
                        isFilterActive = albumsState.isFilterActive,
                        isSearchActive = albumsState.isSearchActive,
                        onToggleSearch = albumsViewModel::toggleSearch,
                        onCreateAlbum = { showAlbumTypeChooser = true }
                    )
                }
            }
            // (Bare Álbumes list bar now lives inside the pager page so it slides
            // with the content — see the pager's MainTab.Albums branch.)
            selectedTab == MainTab.Folders && selectedFolder != null &&
                folderDetailState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = folderDetailState.selection.size,
                    totalCount = folderDetailState.items.size,
                    isMutating = folderDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = folderDetailViewModel::clearSelection,
                    onSelectAll = folderDetailViewModel::toggleSelectAll
                )
            selectedTab == MainTab.Folders && selectedFolder != null &&
                folderDetailState.isSubfolderSelectionActive -> {
                val subfolder = folderDetailState.selectedSubfolder
                com.photonne.app.ui.main.FolderCardSelectionTopBar(
                    folderName = (subfolder?.name ?: "").ifBlank { subfolder?.path ?: "" },
                    isMutating = folderDetailState.isMutating,
                    onClose = folderDetailViewModel::clearSubfolderSelection
                )
            }
            // El detalle de carpeta pinta su propio cromo flotante dentro de la
            // pantalla (título de la carpeta + acciones en la cápsula); aquí no va
            // barra acoplada.
            selectedTab == MainTab.Folders && selectedFolder != null -> {
            }
            selectedTab == MainTab.Folders && foldersState.isSelectionActive -> {
                val target = foldersState.findFolder(foldersState.selectedFolderId)
                if (target != null) {
                    com.photonne.app.ui.main.FolderCardSelectionTopBar(
                        folderName = target.name.ifBlank { target.path },
                        isMutating = foldersState.isMutating,
                        onClose = foldersViewModel::clearSelection
                    )
                } else {
                    FoldersListTopBar(
                        onOpenFilters = { showFoldersFilters = true },
                        isFilterActive = foldersState.isFilterActive,
                        isSearchActive = foldersState.isSearchActive,
                        onToggleSearch = foldersViewModel::toggleSearch,
                        onCreateFolder = if (
                            foldersState.scope !=
                                com.photonne.app.ui.folder.FoldersScope.External
                        ) {
                            { showCreateFolder = true }
                        } else null
                    )
                }
            }
            // (Bare Carpetas list bar now lives inside the pager page.)
            selectedTab == MainTab.Search && searchState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = searchState.selection.size,
                    totalCount = searchState.results.size,
                    isMutating = searchState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = searchViewModel::clearSelection,
                    onSelectAll = searchViewModel::toggleSelectAll
                )
            // Buscar pinta su propio cromo flotante (campo + modo + filtros); aquí
            // no va barra acoplada salvo la de selección (rama de arriba).
            selectedTab == MainTab.Search -> {
            }
            moreSubscreen == MoreSubscreen.Upload ->
                com.photonne.app.ui.main.UploadTopBar(
                    title = stringResource(Res.string.upload_title),
                    subtitle = if (uploadState.pendingCount > 0)
                        stringResource(
                            Res.string.upload_subtitle_pending,
                            uploadState.pendingCount
                        )
                    else null,
                    onBack = { moreSubscreen = null }
                )
            // Cromo flotante dibujado dentro de la pantalla.
            moreSubscreen == MoreSubscreen.DeviceBackup -> { }
            moreSubscreen == MoreSubscreen.DeviceBackupPending &&
                deviceBackupState.selectedCount > 0 ->
                // Same contextual selection bar as Timeline/Albums, with a
                // select-all action for queueing every pending file at once.
                AssetSelectionTopBar(
                    selectedCount = deviceBackupState.selectedCount,
                    isMutating = deviceBackupState.isSyncing,
                    onClose = deviceBackupViewModel::clearSelection,
                    actions = {
                        androidx.compose.material3.IconButton(
                            onClick = deviceBackupViewModel::selectAllNotSynced,
                            enabled = !deviceBackupState.isSyncing
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = stringResource(
                                    Res.string.device_backup_action_select_all
                                )
                            )
                        }
                    }
                )
            // Cromo flotante dibujado dentro de la pantalla (con selección vuelve
            // a la barra acoplada, rama de arriba).
            moreSubscreen == MoreSubscreen.DeviceBackupPending -> { }
            moreSubscreen == MoreSubscreen.EnrichmentStatus -> { }
            moreSubscreen == MoreSubscreen.Utilities -> { }
            moreSubscreen == MoreSubscreen.MyLinks -> { }
            // "Archivos no compatibles" pinta su propio cromo flotante dentro de la pantalla.
            moreSubscreen == MoreSubscreen.UnsupportedFiles -> {
            }
            moreSubscreen == MoreSubscreen.OrganizeInbox &&
                organizeInboxState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = organizeInboxState.selection.size,
                    totalCount = organizeInboxState.items.size,
                    isMutating = organizeInboxState.isBulkMutating,
                    onClose = organizeInboxViewModel::clearSelection,
                    onSelectAll = organizeInboxViewModel::toggleSelectAll,
                    // El servidor no da los ids de toda la lista: solo se
                    // puede seleccionar lo cargado, y así se rotula.
                    selectAllLoadedOnly = organizeInboxState.hasMore
                )
            // Para organizar pinta su propio cromo flotante (con "Mover por
            // condiciones" en su cápsula de acciones); con una selección activa
            // manda la rama de arriba.
            moreSubscreen == MoreSubscreen.OrganizeInbox -> {
            }
            moreSubscreen == MoreSubscreen.OrganizeRule -> { }
            // Apartadas: con selección, la barra acoplada lleva "Devolver a la
            // bandeja" (una sola acción, como Restaurar en la papelera).
            moreSubscreen == MoreSubscreen.OrganizeExcluded &&
                organizeExcludedState.isSelectionActive -> {
                val snackbar = LocalSnackbarController.current
                val includedCount = organizeExcludedState.selection.size
                val includedMessage = pluralStringResource(
                    Res.plurals.organize_excluded_included_done, includedCount, includedCount
                )
                AssetSelectionTopBar(
                    selectedCount = organizeExcludedState.selection.size,
                    totalCount = organizeExcludedState.items.size,
                    isMutating = organizeExcludedState.isBulkMutating,
                    onClose = organizeExcludedViewModel::clearSelection,
                    onSelectAll = organizeExcludedViewModel::toggleSelectAll,
                    selectAllLoadedOnly = organizeExcludedState.hasMore
                ) {
                    TextButton(
                        onClick = {
                            organizeExcludedViewModel.includeSelected {
                                snackbar?.show(includedMessage)
                                organizeInboxViewModel.refresh()
                                foldersViewModel.refreshOrganizeCount()
                            }
                        },
                        enabled = !organizeExcludedState.isBulkMutating
                    ) {
                        Text(stringResource(Res.string.organize_excluded_action_include))
                    }
                }
            }
            moreSubscreen == MoreSubscreen.OrganizeExcluded -> { }
            moreSubscreen == MoreSubscreen.UtilitiesDuplicates -> { }
            moreSubscreen == MoreSubscreen.UtilitiesLargeFiles -> { }
            moreSubscreen == MoreSubscreen.UtilitiesLocations -> { }
            // Recuerdos / Escenas / Objetos / Mapa pintan su propio cromo
            // flotante dentro de la pantalla (ver floatingChromeSubscreen), así
            // que aquí no va ninguna barra.
            moreSubscreen == MoreSubscreen.Memories ||
                moreSubscreen == MoreSubscreen.ExploreScenes ||
                moreSubscreen == MoreSubscreen.ExploreObjects ||
                moreSubscreen == MoreSubscreen.Map -> {
            }
            // Sugerencias de una persona pinta su propio cromo flotante.
            moreSubscreen == MoreSubscreen.PeopleSuggestions -> {
            }
            moreSubscreen == MoreSubscreen.People &&
                selectedPerson != null && personDetailState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = personDetailState.selection.size,
                    totalCount = personDetailState.items.size,
                    isMutating = personDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = personDetailViewModel::clearSelection,
                    onSelectAll = personDetailViewModel::toggleSelectAll
                )
            // La lista de Personas Y el detalle pintan su propio cromo flotante
            // (menú de recluster / ocultas o de renombrar / fusionar en su cápsula
            // de acciones); aquí no va barra acoplada.
            moreSubscreen == MoreSubscreen.People -> {
            }
            moreSubscreen == MoreSubscreen.Favorites &&
                favoritesState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = favoritesState.selection.size,
                    totalCount = favoritesState.items.size,
                    isMutating = favoritesState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = favoritesViewModel::clearSelection,
                    onSelectAll = favoritesViewModel::toggleSelectAll,
                    // El servidor no da los ids de toda la lista: solo se
                    // puede seleccionar lo cargado, y así se rotula.
                    selectAllLoadedOnly = favoritesState.hasMore
                )
            // Favoritos pinta su propio cromo flotante dentro de la pantalla
            // (ver floatingChromeSubscreen); aquí no va barra acoplada.
            moreSubscreen == MoreSubscreen.Favorites -> {
            }
            moreSubscreen == MoreSubscreen.Archived &&
                archivedState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = archivedState.selection.size,
                    totalCount = archivedState.items.size,
                    isMutating = archivedState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = archivedViewModel::clearSelection,
                    onSelectAll = archivedViewModel::toggleSelectAll,
                    // El servidor no da los ids de toda la lista: solo se
                    // puede seleccionar lo cargado, y así se rotula.
                    selectAllLoadedOnly = archivedState.hasMore
                )
            // Archivados pinta su propio cromo flotante dentro de la pantalla.
            moreSubscreen == MoreSubscreen.Archived -> {
            }
            // Personal tab in selection mode: restore/purge selected.
            moreSubscreen == MoreSubscreen.Trash &&
                trashTab == com.photonne.app.ui.library.TrashTab.Personal &&
                trashState.isSelectionActive ->
                com.photonne.app.ui.main.TrashSelectionTopBar(
                    selectedCount = trashState.selection.size,
                    isMutating = trashState.isBulkMutating,
                    onClose = trashViewModel::clearSelection,
                    onRestore = { trashViewModel.bulkRestore() },
                    onPurge = { showPurgeSelected = true }
                )
            // Papelera (sin selección): cromo flotante dibujado en el contenido,
            // con las acciones restaurar-todo / vaciar en su cápsula (solo en la
            // pestaña Personal).
            moreSubscreen == MoreSubscreen.Trash -> { }
            // Todas estas subpantallas pintan su propio cromo flotante estático
            // dentro de la pantalla (título + atrás, y acciones en su cápsula
            // cuando las tienen); aquí no va barra acoplada.
            moreSubscreen == MoreSubscreen.Notifications -> { }
            moreSubscreen == MoreSubscreen.AccountSettings -> { }
            moreSubscreen == MoreSubscreen.AccountProfile -> { }
            moreSubscreen == MoreSubscreen.AccountSecurity -> { }
            moreSubscreen == MoreSubscreen.AccountAppearance -> { }
            moreSubscreen == MoreSubscreen.AccountStorage -> { }
            moreSubscreen == MoreSubscreen.AccountConnection -> { }
            moreSubscreen == MoreSubscreen.Administration -> { }
            moreSubscreen == MoreSubscreen.AdminUsers -> { }
            moreSubscreen == MoreSubscreen.AdminUserEditor -> { }
            moreSubscreen == MoreSubscreen.AdminLibraries -> { }
            moreSubscreen == MoreSubscreen.AdminLibraryEditor -> { }
            moreSubscreen == MoreSubscreen.AdminStats -> { }
            moreSubscreen == MoreSubscreen.AdminSettingsHub -> { }
            moreSubscreen == MoreSubscreen.AdminSystemHub -> { }
            isAdminSettingsSubpage(moreSubscreen) -> { }
            isAdminSystemSubpage(moreSubscreen) -> { }
            else -> {
                // Every bare top-level tab now renders its own top bar *inside*
                // its pager page (Fotos its floating bar; Álbumes/Carpetas/Más a
                // docked bar in a Column) so the bar slides with the content and
                // the Scaffold reserves no shared top space — no top bar here.
            }
        }
    }

    // While any multi-asset selection is active, the bottom navigation is
    // replaced by an action bar so the primary actions sit within thumb
    // reach on mobile. The slim selection top bar above keeps just Close + count.
    val bottomBar: (@Composable () -> Unit)? = when {
        selectedTab == MainTab.Timeline &&
            timelineState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = timelineState.selection.size,
                    isMutating = timelineState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(timelineState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddToAlbum = true },
                    onMove = { showMoveSelectedAssetsTimeline = true },
                    onDownload = {
                        actionsViewModel.download(timelineState.selection.toList())
                    },
                    onArchive = timelineViewModel::bulkArchive,
                    onTrash = timelineViewModel::bulkTrash,
                    selectedIds = { timelineState.selection.toList() },
                    allFavorite = selectionAllFavorite(timelineState.loadedItems, timelineState.selection),
                    onToggleFavorite = run {
                        val favoriteSnackbar = LocalSnackbarController.current
                        {
                            runBulkFavorite(
                                timelineState.loadedItems, timelineState.selection, favoriteSnackbar,
                                timelineViewModel::clearSelection
                            )
                        }
                    },
                    onUndo = { kind, ids ->
                        actionsViewModel.undoBulk(kind, ids)
                    },
                )
            }
        }
        selectedTab == MainTab.Albums && selectedAlbum != null &&
            albumDetailState.isSelectionActive -> {
            {
                // Textos del snackbar de "Quitar del álbum", resueltos en
                // composición (en el callback ya no hay recursos).
                val removeCount = albumDetailState.selection.size
                val removedFromAlbumMessage = pluralStringResource(
                    Res.plurals.selection_removed_from_album_done, removeCount, removeCount
                )
                val removeUndoLabel = stringResource(Res.string.action_undo)
                val removeSnackbar = LocalSnackbarController.current
                // Un álbum compartido mezcla fotos de varios miembros: una ajena
                // hacía fallar la papelera de todo el lote. Se desactiva y se
                // explica. Y como mandar a la papelera saca la foto de TODOS los
                // álbumes (y restaurarla no la devuelve), se avisa antes.
                val untrashable = com.photonne.app.ui.actions.countUntrashable(
                    albumDetailState.items, albumDetailState.selection, user.user.username
                )
                val albumTrashBlocked = if (untrashable > 0) pluralStringResource(
                    Res.plurals.selection_trash_blocked_foreign, untrashable, untrashable
                ) else null
                val albumTrashWarning = pluralStringResource(
                    if (selectedAlbum?.isShared == true) Res.plurals.album_trash_warning_shared
                    else Res.plurals.album_trash_warning,
                    removeCount,
                    removeCount
                )
                AssetSelectionBottomBar(
                    selectedCount = albumDetailState.selection.size,
                    trashDisabledReason = albumTrashBlocked,
                    trashConfirmMessage = albumTrashWarning,
                    isMutating = albumDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(albumDetailState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddSource = BulkAddSource.Album },
                    onDownload = {
                        actionsViewModel.download(albumDetailState.selection.toList())
                    },
                    onArchive = albumDetailViewModel::bulkArchive,
                    onTrash = albumDetailViewModel::bulkTrash,
                    selectedIds = { albumDetailState.selection.toList() },
                    allFavorite = selectionAllFavorite(albumDetailState.items, albumDetailState.selection),
                    onToggleFavorite = run {
                        val favoriteSnackbar = LocalSnackbarController.current
                        {
                            runBulkFavorite(
                                albumDetailState.items, albumDetailState.selection, favoriteSnackbar,
                                albumDetailViewModel::clearSelection
                            )
                        }
                    },
                    onUndo = { kind, ids ->
                        actionsViewModel.undoBulk(kind, ids)
                    },
                    onMove = run {
                        val unmovable = com.photonne.app.ui.actions.countUnmovable(
                            albumDetailState.items, albumDetailState.selection
                        )
                        val moveBlocked = if (unmovable > 0) pluralStringResource(
                            Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                        ) else null
                        val moveSnackbar = LocalSnackbarController.current
                        {
                            if (moveBlocked != null) {
                                moveSnackbar?.show(moveBlocked)
                            } else {
                                moveSelectionError = null
                                moveSelectionRequest = MoveSelectionRequest(
                                    assetIds = albumDetailState.selection.toList(),
                                    onMoved = albumDetailViewModel::clearSelection
                                )
                            }
                        }
                    },
                    // En un álbum inteligente el contenido lo deciden las
                    // reglas: ni quitar fotos ni fijar portada aplican.
                    onRemoveFromAlbum = if (selectedAlbum?.isSmart != true &&
                        (selectedAlbum?.canWrite == true ||
                            selectedAlbum?.isOwner == true)
                    ) {
                        {
                            val albumId = selectedAlbum?.id
                            albumDetailViewModel.bulkRemoveFromAlbum(
                                onSuccess = { removed ->
                                    selectedAlbum?.let {
                                        albumsViewModel.applyAssetsRemoved(it.id, removed)
                                    }
                                },
                                onResult = { removedIds, error ->
                                    if (error != null) {
                                        removeSnackbar?.show(error.userMessage)
                                    } else {
                                        removeSnackbar?.show(
                                            removedFromAlbumMessage,
                                            removeUndoLabel
                                        ) {
                                            if (albumId != null) {
                                                coroutineScope.launch {
                                                    runCatching {
                                                        albumsRepository.addAssetsBatch(
                                                            albumId, removedIds
                                                        )
                                                    }.onSuccess {
                                                        albumDetailViewModel.refresh()
                                                        albumsViewModel.applyAssetsAdded(
                                                            albumId, removedIds.size
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    } else null,
                    onSetAsCover = if (albumDetailState.selection.size == 1 &&
                        selectedAlbum?.isSmart != true &&
                        (selectedAlbum?.canWrite == true || selectedAlbum?.isOwner == true)
                    ) {
                        {
                            val assetId = albumDetailState.selection.first()
                            albumDetailViewModel.setCover(assetId) { updated ->
                                albumsViewModel.applyUpdate(updated)
                                selectedAlbum = selectedAlbum?.copy(
                                    coverThumbnailUrl = updated.coverThumbnailUrl
                                )
                                albumDetailViewModel.clearSelection()
                            }
                        }
                    } else null
                )
            }
        }
        selectedTab == MainTab.Folders && selectedFolder != null &&
            folderDetailState.isSubfolderSelectionActive -> {
            val subfolder = folderDetailState.selectedSubfolder
            if (subfolder != null) {
                {
                    // Members management for a subfolder is reached by opening it
                    // and using the detail top bar; the selection bar stays focused
                    // on the rename/delete the user asked for.
                    // Mismos flags que el servidor (CanWrite/CanDelete) y nada
                    // de mutar una biblioteca externa, como en la lista.
                    val subfolderIsExternal = subfolder.externalLibraryId != null
                    com.photonne.app.ui.main.FolderCardSelectionBottomBar(
                        canManageMembers = false,
                        canRename = subfolder.canWrite && !subfolderIsExternal,
                        canDelete = subfolder.canDelete && !subfolderIsExternal,
                        isMutating = folderDetailState.isMutating,
                        onManageMembers = {},
                        onRename = { showEditSubfolder = true },
                        onDelete = { showDeleteSubfolder = true }
                    )
                }
            } else null
        }
        selectedTab == MainTab.Folders && selectedFolder != null &&
            folderDetailState.isSelectionActive -> {
            {
                // Sin CanDelete en la carpeta (o en una biblioteca externa) el
                // servidor rechaza la papelera: se desactiva con el motivo.
                val folderTrashBlocked = if (selectedFolder?.canDelete == false ||
                    selectedFolder?.externalLibraryId != null
                ) stringResource(Res.string.selection_trash_blocked_folder) else null
                AssetSelectionBottomBar(
                    selectedCount = folderDetailState.selection.size,
                    trashDisabledReason = folderTrashBlocked,
                    isMutating = folderDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(folderDetailState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddSource = BulkAddSource.Folder },
                    onDownload = {
                        actionsViewModel.download(folderDetailState.selection.toList())
                    },
                    onArchive = folderDetailViewModel::bulkArchive,
                    onTrash = folderDetailViewModel::bulkTrash,
                    selectedIds = { folderDetailState.selection.toList() },
                    allFavorite = selectionAllFavorite(folderDetailState.items, folderDetailState.selection),
                    onToggleFavorite = run {
                        val favoriteSnackbar = LocalSnackbarController.current
                        {
                            runBulkFavorite(
                                folderDetailState.items, folderDetailState.selection, favoriteSnackbar,
                                folderDetailViewModel::clearSelection
                            )
                        }
                    },
                    onUndo = { kind, ids ->
                        actionsViewModel.undoBulk(kind, ids)
                    },
                    // El servidor pide escritura en la carpeta de origen, no
                    // ser su dueño.
                    onMove = if (selectedFolder?.canWrite == true &&
                        selectedFolder?.externalLibraryId == null
                    ) {
                        { showMoveSelectedAssets = true }
                    } else null
                )
            }
        }
        selectedTab == MainTab.Search && searchState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = searchState.selection.size,
                    isMutating = searchState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(searchState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddSource = BulkAddSource.Search },
                    onDownload = {
                        actionsViewModel.download(searchState.selection.toList())
                    },
                    onArchive = searchViewModel::bulkArchive,
                    onTrash = searchViewModel::bulkTrash,
                    selectedIds = { searchState.selection.toList() },
                    allFavorite = selectionAllFavorite(searchState.results, searchState.selection),
                    onToggleFavorite = run {
                        val favoriteSnackbar = LocalSnackbarController.current
                        {
                            runBulkFavorite(
                                searchState.results, searchState.selection, favoriteSnackbar,
                                searchViewModel::clearSelection
                            )
                        }
                    },
                    onUndo = { kind, ids ->
                        actionsViewModel.undoBulk(kind, ids)
                    },
                    onMove = run {
                        val unmovable = com.photonne.app.ui.actions.countUnmovable(
                            searchState.results, searchState.selection
                        )
                        val moveBlocked = if (unmovable > 0) pluralStringResource(
                            Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                        ) else null
                        val moveSnackbar = LocalSnackbarController.current
                        {
                            if (moveBlocked != null) {
                                moveSnackbar?.show(moveBlocked)
                            } else {
                                moveSelectionError = null
                                moveSelectionRequest = MoveSelectionRequest(
                                    assetIds = searchState.selection.toList(),
                                    onMoved = searchViewModel::clearSelection
                                )
                            }
                        }
                    },
                )
            }
        }
        moreSubscreen == MoreSubscreen.People &&
            selectedPerson != null && personDetailState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = personDetailState.selection.size,
                    isMutating = personDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(personDetailState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddSource = BulkAddSource.People },
                    onDownload = {
                        actionsViewModel.download(personDetailState.selection.toList())
                    },
                    onArchive = personDetailViewModel::bulkArchive,
                    onTrash = personDetailViewModel::bulkTrash,
                    selectedIds = { personDetailState.selection.toList() },
                    allFavorite = selectionAllFavorite(personDetailState.items, personDetailState.selection),
                    onToggleFavorite = run {
                        val favoriteSnackbar = LocalSnackbarController.current
                        {
                            runBulkFavorite(
                                personDetailState.items, personDetailState.selection, favoriteSnackbar,
                                personDetailViewModel::clearSelection
                            )
                        }
                    },
                    onUndo = { kind, ids ->
                        actionsViewModel.undoBulk(kind, ids)
                    },
                    onUnlink = {
                        personDetailViewModel.bulkUnlinkFromPerson { detached ->
                            // Local fan-out: faces removed from a person also
                            // shrink that person's face count in the list.
                            selectedPerson?.let { p ->
                                val newCount = (p.faceCount - detached).coerceAtLeast(0)
                                selectedPerson = p.copy(faceCount = newCount)
                            }
                        }
                    }
                )
            }
        }
        moreSubscreen == MoreSubscreen.OrganizeInbox &&
            organizeInboxState.isSelectionActive -> {
            {
                // Los textos se resuelven aquí, en composición: la acción de
                // apartar corre en un callback y allí ya no hay recursos.
                val skippedCount = organizeInboxState.selection.size
                val skippedMessage = pluralStringResource(
                    Res.plurals.organize_skipped_done, skippedCount, skippedCount
                )
                val undoLabel = stringResource(Res.string.action_undo)
                val snackbar = LocalSnackbarController.current
                AssetSelectionBottomBar(
                    selectedCount = organizeInboxState.selection.size,
                    isMutating = organizeInboxState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(organizeInboxState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddSource = BulkAddSource.Inbox },
                    onDownload = {
                        actionsViewModel.download(organizeInboxState.selection.toList())
                    },
                    onArchive = organizeInboxViewModel::bulkArchive,
                    onTrash = organizeInboxViewModel::bulkTrash,
                    selectedIds = { organizeInboxState.selection.toList() },
                    allFavorite = selectionAllFavorite(organizeInboxState.items, organizeInboxState.selection),
                    onToggleFavorite = run {
                        val favoriteSnackbar = LocalSnackbarController.current
                        {
                            runBulkFavorite(
                                organizeInboxState.items, organizeInboxState.selection, favoriteSnackbar,
                                organizeInboxViewModel::clearSelection
                            )
                        }
                    },
                    onUndo = { kind, ids ->
                        actionsViewModel.undoBulk(kind, ids) { organizeInboxViewModel.refresh() }
                    },
                    onMove = {
                        showMoveSelectedAssetsInbox = true
                        organizeInboxViewModel.loadMoveYearBreakdown()
                    },
                    onExcludeFromOrganize = {
                        organizeInboxViewModel.excludeSelected { ids ->
                            foldersViewModel.refreshOrganizeCount()
                            snackbar?.show(
                                message = skippedMessage,
                                actionLabel = undoLabel
                            ) {
                                organizeInboxViewModel.includeAgain(ids) {
                                    foldersViewModel.refreshOrganizeCount()
                                }
                            }
                        }
                    }
                )
            }
        }
        moreSubscreen == MoreSubscreen.Favorites &&
            favoritesState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = favoritesState.selection.size,
                    isMutating = favoritesState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(favoritesState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddSource = BulkAddSource.Favorites },
                    onDownload = {
                        actionsViewModel.download(favoritesState.selection.toList())
                    },
                    onArchive = favoritesViewModel::bulkArchive,
                    onTrash = favoritesViewModel::bulkTrash,
                    selectedIds = { favoritesState.selection.toList() },
                    allFavorite = selectionAllFavorite(favoritesState.items, favoritesState.selection),
                    onToggleFavorite = run {
                        val favoriteSnackbar = LocalSnackbarController.current
                        {
                            runBulkFavorite(
                                favoritesState.items, favoritesState.selection, favoriteSnackbar,
                                favoritesViewModel::clearSelection
                            )
                        }
                    },
                    onUndo = { kind, ids ->
                        actionsViewModel.undoBulk(kind, ids)
                    },
                    onMove = run {
                        val unmovable = com.photonne.app.ui.actions.countUnmovable(
                            favoritesState.items, favoritesState.selection
                        )
                        val moveBlocked = if (unmovable > 0) pluralStringResource(
                            Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                        ) else null
                        val moveSnackbar = LocalSnackbarController.current
                        {
                            if (moveBlocked != null) {
                                moveSnackbar?.show(moveBlocked)
                            } else {
                                moveSelectionError = null
                                moveSelectionRequest = MoveSelectionRequest(
                                    assetIds = favoritesState.selection.toList(),
                                    onMoved = favoritesViewModel::clearSelection
                                )
                            }
                        }
                    },
                )
            }
        }
        moreSubscreen == MoreSubscreen.Archived &&
            archivedState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = archivedState.selection.size,
                    isMutating = archivedState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    archiveMode = ArchiveMode.Unarchive,
                    onShare = {
                        actionsViewModel.beginShare(archivedState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddSource = BulkAddSource.Archive },
                    onDownload = {
                        actionsViewModel.download(archivedState.selection.toList())
                    },
                    onArchive = { done -> archivedViewModel.bulkUnarchive(onResult = done) },
                    onTrash = archivedViewModel::bulkTrash,
                    selectedIds = { archivedState.selection.toList() },
                    allFavorite = selectionAllFavorite(archivedState.items, archivedState.selection),
                    onToggleFavorite = run {
                        val favoriteSnackbar = LocalSnackbarController.current
                        {
                            runBulkFavorite(
                                archivedState.items, archivedState.selection, favoriteSnackbar,
                                archivedViewModel::clearSelection
                            )
                        }
                    },
                    onUndo = { kind, ids ->
                        actionsViewModel.undoBulk(kind, ids)
                    },
                    onMove = run {
                        val unmovable = com.photonne.app.ui.actions.countUnmovable(
                            archivedState.items, archivedState.selection
                        )
                        val moveBlocked = if (unmovable > 0) pluralStringResource(
                            Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                        ) else null
                        val moveSnackbar = LocalSnackbarController.current
                        {
                            if (moveBlocked != null) {
                                moveSnackbar?.show(moveBlocked)
                            } else {
                                moveSelectionError = null
                                moveSelectionRequest = MoveSelectionRequest(
                                    assetIds = archivedState.selection.toList(),
                                    onMoved = archivedViewModel::clearSelection
                                )
                            }
                        }
                    },
                )
            }
        }
        selectedTab == MainTab.Albums && albumsState.isSelectionActive -> {
            val target = albumsState.albums.firstOrNull {
                it.id == albumsState.selectedAlbumId
            }
            if (target != null) {
                {
                    com.photonne.app.ui.main.AlbumCardSelectionBottomBar(
                        canManageMembers = target.isOwner || target.canManagePermissions,
                        canEdit = target.canWrite || target.isOwner,
                        canLeave = !target.isOwner,
                        canDelete = target.isOwner || target.canDelete,
                        isMutating = albumsState.isMutating,
                        onManageMembers = {
                            pendingActionAlbum = target
                            albumPermissionsViewModel.open(target.id)
                            showMembers = true
                        },
                        onEdit = {
                            // Un álbum inteligente propio se edita entero (condiciones
                            // incluidas) en su editor; el resto, nombre y descripción.
                            if (target.isSmart && target.isOwner) {
                                albumsViewModel.clearSelection()
                                editingSmartAlbum = target
                                moreSubscreen = MoreSubscreen.SmartAlbumEditor
                            } else {
                                pendingActionAlbum = target
                                showEditAlbum = true
                            }
                        },
                        onLeave = {
                            pendingActionAlbum = target
                            showLeaveAlbum = true
                        },
                        onDelete = {
                            pendingActionAlbum = target
                            showDeleteAlbum = true
                        }
                    )
                }
            } else null
        }
        selectedTab == MainTab.Folders && foldersState.isSelectionActive -> {
            val target = foldersState.findFolder(foldersState.selectedFolderId)
            if (target != null) {
                // An external library is a read-only mirror of a host path, but
                // the server still reports IsOwner for an admin on any shared
                // path — so gate the destructive actions on the library id too,
                // the way canToggleTimeline already does.
                val isExternal = target.externalLibraryId != null
                {
                    com.photonne.app.ui.main.FolderCardSelectionBottomBar(
                        canManageMembers = target.isOwner && target.isShared,
                        canRename = target.canWrite && !isExternal,
                        canDelete = target.canDelete && !isExternal,
                        isMutating = foldersState.isMutating,
                        onManageMembers = {
                            pendingActionFolder = target
                            folderPermissionsViewModel.open(target.id)
                            showFolderMembers = true
                        },
                        onRename = {
                            pendingActionFolder = target
                            showEditFolder = true
                        },
                        onDelete = {
                            pendingActionFolder = target
                            showDeleteFolder = true
                        },
                        canToggleTimeline = target.isShared && target.externalLibraryId == null,
                        excludedFromDiscovery = target.excludedFromDiscovery,
                        onToggleTimeline = {
                            foldersViewModel.setTimelineIncluded(
                                folderId = target.id,
                                included = target.excludedFromDiscovery
                            )
                        }
                    )
                }
            } else null
        }
        else -> null
    }

    // Every "add something here" entry point now lives as the first action of its
    // own top bar (see CreateAction), so there is no FAB anywhere in the shell —
    // one pattern instead of three, and nothing floating over the bottom nav.

    // On open: pre-populate the morph target synchronously so the grid
    // thumbnail has a sharedElement bounds source ready before the
    // viewer's pager LaunchedEffect catches up.
    // On close: keep the morph target alive long enough for the
    // sharedElement exit animation to complete before clearing — otherwise
    // the source thumbnail flips back to visible mid-morph and the photo
    // snaps the last bit.
    LaunchedEffect(assetDetail) {
        val ctx = assetDetail
        if (ctx != null) {
            currentDetailAssetId = ctx.items.getOrNull(ctx.startIndex)?.id
        } else {
            kotlinx.coroutines.delay(360)
            currentDetailAssetId = null
            viewerReturnState.clear()
        }
    }

    // Which primary tab is currently showing its immersive scrollable list (no
    // open detail, no selection, no overriding subscreen). Each drives the
    // shared bottom nav's hide-on-scroll + edge-to-edge behaviour. Keyed off
    // [chromeTab] so the padding tracks the swipe instead of snapping on settle.
    //
    // "Detalle abierto" es el detalle A LA VISTA (selectedTab), no el álbum o la
    // carpeta que se quedaron abiertos en su pestaña: al deslizar hacia ella lo
    // que asoma bajo el dedo es la lista, y debe ir a sangre como siempre; el
    // detalle solo vuelve a tapar al asentar el gesto.
    val timelineImmersive = chromeTab == MainTab.Timeline &&
        moreSubscreen == null &&
        !timelineState.isSelectionActive
    val albumsImmersive = chromeTab == MainTab.Albums &&
        moreSubscreen == null &&
        !(selectedTab == MainTab.Albums && selectedAlbum != null) &&
        !albumsState.isSelectionActive
    val foldersImmersive = chromeTab == MainTab.Folders &&
        moreSubscreen == null &&
        !(selectedTab == MainTab.Folders && selectedFolder != null) &&
        !foldersState.isSelectionActive
    val moreImmersive = chromeTab == MainTab.More && moreSubscreen == null
    // Buscar dibuja su propio cromo flotante (campo + modo + filtros) que se acopla
    // y se oculta al scroll; con una selección activa vuelve la barra acoplada.
    val searchImmersive = selectedTab == MainTab.Search &&
        moreSubscreen == null &&
        !searchState.isSelectionActive
    // Inside an open album / folder: the photo grid gets the same immersive
    // treatment (nav hides on scroll, grid bleeds behind it), unless a
    // selection is active (which shows its own bottom action bar). These are
    // overlays, so they stay keyed off selectedTab.
    val albumDetailImmersive = selectedTab == MainTab.Albums &&
        moreSubscreen == null &&
        selectedAlbum != null &&
        !albumDetailState.isSelectionActive
    val folderDetailImmersive = selectedTab == MainTab.Folders &&
        moreSubscreen == null &&
        selectedFolder != null &&
        !folderDetailState.isSelectionActive &&
        !folderDetailState.isSubfolderSelectionActive

    // Las mismas pantallas, pero con una selección activa. La barra de acciones
    // es también una cápsula flotante, así que la rejilla sigue dibujando a
    // sangre por debajo: lo que la selección apaga es el ocultarse al hacer
    // scroll (una acción no puede escaparse), no el apoyarse sobre las fotos.
    // Son justo las cinco que reservan el hueco de la cápsula al final de su
    // scroll; el resto de pantallas con selección (Buscar, persona, favoritos…)
    // no lo hacen, así que allí el Scaffold sigue apartando el contenido.
    val timelineSelecting = chromeTab == MainTab.Timeline &&
        moreSubscreen == null &&
        timelineState.isSelectionActive
    val albumsSelecting = chromeTab == MainTab.Albums &&
        moreSubscreen == null &&
        selectedAlbum == null &&
        albumsState.isSelectionActive
    val foldersSelecting = chromeTab == MainTab.Folders &&
        moreSubscreen == null &&
        selectedFolder == null &&
        foldersState.isSelectionActive
    val albumDetailSelecting = selectedTab == MainTab.Albums &&
        moreSubscreen == null &&
        selectedAlbum != null &&
        albumDetailState.isSelectionActive
    val folderDetailSelecting = selectedTab == MainTab.Folders &&
        moreSubscreen == null &&
        selectedFolder != null &&
        (folderDetailState.isSelectionActive ||
            folderDetailState.isSubfolderSelectionActive)

    // Shared tab-switch side effects, run by both a nav tap and a settled swipe:
    // drop any open subscreen / person layer, collapse an open detail when the
    // same tab is re-selected, and clear the other tabs' multi-selection.
    val switchTab: (MainTab) -> Unit = { tab ->
        // Retocar Fotos ya activa (y sin subpantalla que cerrar) vuelve arriba,
        // como en cualquier app de galería; antes no hacía nada.
        if (tab == MainTab.Timeline && selectedTab == MainTab.Timeline &&
            moreSubscreen == null && selectedPerson == null
        ) {
            timelineScrollToTopTick++
        }
        // Lo mismo en Álbumes y Carpetas, solo en su raíz: con un álbum o una
        // carpeta abiertos el retoque sigue cerrándolos (abajo).
        if (tab == MainTab.Albums && selectedTab == MainTab.Albums &&
            moreSubscreen == null && selectedPerson == null && selectedAlbum == null
        ) {
            albumsScrollToTopTick++
        }
        if (tab == MainTab.Folders && selectedTab == MainTab.Folders &&
            moreSubscreen == null && selectedPerson == null && selectedFolder == null
        ) {
            foldersScrollToTopTick++
        }
        moreSubscreen = null
        selectedPerson = null
        folderReturnTo = null
        personReturnTo = null
        if (tab == MainTab.Albums && selectedTab == MainTab.Albums) selectedAlbum = null
        if (tab == MainTab.Folders && selectedTab == MainTab.Folders) {
            selectedFolder = null
            folderBackStack.clear()
        }
        if (tab != MainTab.Albums) albumsViewModel.clearSelection()
        if (tab != MainTab.Folders) foldersViewModel.clearSelection()
        selectedTab = tab
    }
    // Tap on a nav tab (or any programmatic tab change) glides the pager over.
    LaunchedEffect(selectedTab) {
        val idx = navTabs.indexOf(selectedTab)
        if (idx >= 0 && mainPagerState.currentPage != idx) {
            mainPagerState.animateScrollToPage(idx)
        }
    }
    // A settled swipe adopts that page as the active tab, running the same side
    // effects a tap would. Guarded so a programmatic settle (or being parked on
    // Buscar) never fights the effect above.
    LaunchedEffect(mainPagerState.settledPage) {
        val tab = navTabs.getOrNull(mainPagerState.settledPage)
        if (tab != null && tab != selectedTab && selectedTab in navTabs) {
            switchTab(tab)
        }
    }

    val snackbarController = rememberSnackbarController()
    // Papelera del servidor (J5): el visor lo consulta al borrar para no
    // ofrecer Deshacer sobre un borrado definitivo.
    val serverTrashPolicy: com.photonne.app.data.actions.ServerTrashPolicy = koinInject()
    // Canal único de feedback: el toast de "descarga guardada" que el VM de acciones
    // ya componía en statusMessage pero que no se pintaba en ninguna parte.
    LaunchedEffect(actionsState.statusMessage) {
        actionsState.statusMessage?.let { message -> 
            snackbarController.show(message)
            actionsViewModel.dismissMessage()
        }
    }
    // El fallo iba por el mismo canal: sin esto una descarga o un compartir que
    // revientan no dicen nada (el visor no pinta el estado del VM de acciones).
    LaunchedEffect(actionsState.error) {
        actionsState.error?.let { error ->
            snackbarController.show(error.userMessage)
            actionsViewModel.dismissMessage()
        }
    }

    // Confirmaciones de éxito de añadir-a-álbum y mover: los textos son plurales
    // con argumentos que solo se conocen al pulsar (recuento y destino), así que
    // se resuelven con getPluralString en un scope y no en composición.
    fun showAddedToAlbumSnackbar(count: Int, albumName: String) {
        if (count <= 0) return
        coroutineScope.launch {
            snackbarController.show(
                org.jetbrains.compose.resources.getPluralString(
                    Res.plurals.selection_added_to_album_done, count, count, albumName
                )
            )
        }
    }
    fun showMovedToFolderSnackbar(count: Int, folderName: String?) {
        if (count <= 0) return
        coroutineScope.launch {
            snackbarController.show(
                org.jetbrains.compose.resources.getPluralString(
                    Res.plurals.selection_moved_to_folder_done, count, count, folderName ?: ""
                ).trimEnd()
            )
        }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides this,
        LocalCurrentDetailAssetId provides currentDetailAssetId,
        com.photonne.app.ui.grid.LocalViewerReturn provides viewerReturnState,
        LocalSnackbarController provides snackbarController
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        MainScaffold(
            selectedTab = selectedTab,
            // Tapping any bottom-nav tab also dismisses an open subscreen layer
            // (People / Map / Explore facets, or any More destination) so it
            // never lingers over the newly selected tab — see [switchTab].
            onTabSelected = { tab -> switchTab(tab) },
            topBar = topBar,
            bottomBar = bottomBar,
            moreTabUnreadCount = notificationsState.unreadCount,
            // Every bare pager tab draws to the top of the screen and paints its
            // own top bar inside its page (Fotos' floating bar; Álbumes/Carpetas/
            // Más a docked bar) so the bar travels with the swipe. The Scaffold
            // only reserves top space again for a selection/overlay bar.
            //
            // Lo mismo para todo lo que pinte su PROPIO cromo flotante: si no,
            // el inset de la status bar se cuenta DOS veces. Este Scaffold, con
            // un slot `topBar` que no emite nada (justo lo que hacen esas ramas),
            // no deja el hueco a cero: lo rellena con el inset del sistema. Y el
            // cromo de cada pantalla vuelve a aplicárselo por su cuenta.
            edgeToEdgeTop = pagerBareTop || floatingChromeSubscreen ||
                albumDetailImmersive || folderDetailImmersive || searchImmersive,
            // On the immersive tabs the bottom nav hides while scrolling down
            // (driven by each screen's chrome), and always shows elsewhere.
            bottomBarVisible = when {
                timelineImmersive -> timelineChromeVisible
                albumsImmersive -> albumsChromeVisible
                foldersImmersive -> foldersChromeVisible
                moreImmersive -> moreChromeVisible
                searchImmersive -> searchChromeVisible
                albumDetailImmersive -> albumDetailChromeVisible
                folderDetailImmersive -> folderDetailChromeVisible
                floatingChromeSubscreen -> subscreenChromeVisible
                else -> true
            },
            // The grid draws behind the bottom nav so content is revealed when
            // it slides away (always full-screen, the bar just covers it). Lo
            // mismo con la cápsula de selección, que ocupa ese hueco: sin esto
            // flotaría sobre el fondo del Scaffold en vez de sobre las fotos.
            edgeToEdgeBottom = timelineImmersive || albumsImmersive || foldersImmersive ||
                albumDetailImmersive || folderDetailImmersive ||
                timelineSelecting || albumsSelecting || foldersSelecting ||
                albumDetailSelecting || folderDetailSelecting ||
                // Toda subpantalla de Más (y Buscar), y el propio menú de Más,
                // dibujan a sangre por debajo de la nav flotante como las pestañas
                // principales; el hueco de la cápsula lo reserva cada pantalla en su
                // propio scroll. Excepción: el editor de álbum inteligente monta su
                // propio Scaffold (con su barra), así que sigue con el hueco que le
                // reserva este Scaffold para no solaparse con la nav.
                (moreSubscreen != null &&
                    moreSubscreen != MoreSubscreen.SmartAlbumEditor) ||
                selectedTab == MainTab.Search ||
                (selectedTab == MainTab.More && moreSubscreen == null)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            // Base layer: the four primary tabs live in a HorizontalPager so a
            // left/right swipe glides between Fotos · Álbumes · Carpetas · Más
            // (continuous drag; the neighbour page peeks in under the finger).
            HorizontalPager(
                state = mainPagerState,
                userScrollEnabled = canSwipeTabs,
                // Keep the immediate-neighbour pages composed so a swipe (or a
                // return to a tab) doesn't dispose + rebuild the heavy Timeline
                // grid — that rebuild is what reset the chrome and re-fetched
                // buckets, reading as a jump + flash.
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (navTabs.getOrNull(page)) {
                    MainTab.Timeline -> TimelineScreen(
                        state = timelineState,
                        scrollToTopTick = timelineScrollToTopTick,
                        // El pager principal compone esta página también como
                        // vecina: la tira de Recuerdos solo anima cuando Fotos
                        // es de verdad la pestaña visible.
                        memoriesAutoPlay = selectedTab == MainTab.Timeline &&
                            assetDetail == null,
                        onOpenAsset = { mergedItems, mergedIndex, feed ->
                            assetDetail = AssetDetailContext(
                                items = mergedItems,
                                // The pager starts on the contiguous loaded
                                // bucket run TimelineScreen handed us; the feed
                                // appends older months as the viewer reaches
                                // the end of it.
                                startIndex = mergedIndex,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = timelineViewModel::setFavorite,
                                feed = feed
                            )
                        },
                        onBucketsVisible = timelineViewModel::ensureVisible,
                        onEnsureYearSummaries = timelineViewModel::ensureYearSummaries,
                        // El pull-to-refresh de Fotos también trae la tira
                        // de Recuerdos (lote L9).
                        onRefresh = {
                            timelineViewModel.refresh()
                            memoriesViewModel.refresh()
                        },
                        onDismissError = timelineViewModel::clearError,
                        onToggleSelection = timelineViewModel::toggleSelection,
                        onSetSelected = timelineViewModel::setSelected,
                        onApplySelection = timelineViewModel::applySelection,
                        onOpenUpload = {
                            selectedTab = MainTab.More
                            moreSubscreen = MoreSubscreen.Upload
                        },
                        backupPendingCount = if (deviceBackupState.isBackupEnabled) {
                            deviceBackupState.pendingEntries.size
                        } else 0,
                        onOpenBackup = {
                            selectedTab = MainTab.More
                            moreSubscreen = MoreSubscreen.DeviceBackup
                        },
                        onJumpToDate = { showJumpToDate = true },
                        onOpenSearch = { selectedTab = MainTab.Search },
                        onChromeVisibleChange = { timelineChromeVisible = it },
                        pendingJumpDate = pendingJumpDate,
                        onJumpHandled = { pendingJumpDate = null },
                        memories = memoriesState.items,
                        onOpenMemory = { memory -> memoryDetail = memory },
                        onSeeAllMemories = { moreSubscreen = MoreSubscreen.Memories }
                    )
                    MainTab.Albums -> Column(modifier = Modifier.fillMaxSize()) {
                        // La búsqueda va DENTRO de la cápsula flotante que dibuja
                        // AlbumsListScreen (campo en titleContent), como el buscador;
                        // ya no hay barra acoplada aquí.
                        Box(modifier = Modifier.weight(1f)) {
                            AlbumsListScreen(
                                onAlbumClick = { album ->
                                    if (albumsState.isSelectionActive) {
                                        if (albumsState.selectedAlbumId == album.id) {
                                            albumsViewModel.clearSelection()
                                        } else {
                                            albumsViewModel.selectAlbum(album.id)
                                        }
                                    } else {
                                        selectedAlbum = album
                                    }
                                },
                                onAlbumLongPress = { album ->
                                    albumsViewModel.selectAlbum(album.id)
                                },
                                onCreateAlbum = { showAlbumTypeChooser = true },
                                // Explorar cards open their screen as a modal layer
                                // over the Albums tab (no tab switch) so back
                                // returns here and the bottom nav stays on Álbumes.
                                onOpenPeople = {
                                    selectedPerson = null
                                    moreSubscreen = MoreSubscreen.People
                                },
                                onOpenMap = { moreSubscreen = MoreSubscreen.Map },
                                onOpenScenes = { moreSubscreen = MoreSubscreen.ExploreScenes },
                                onOpenObjects = { moreSubscreen = MoreSubscreen.ExploreObjects },
                                onOpenFilters = { showAlbumsFilters = true },
                                immersive = albumsImmersive,
                                onChromeVisibleChange = { albumsChromeVisible = it },
                                scrollToTopTick = albumsScrollToTopTick
                            )
                        }
                    }
                    MainTab.Folders -> Column(modifier = Modifier.fillMaxSize()) {
                        // La búsqueda va DENTRO de la cápsula flotante que dibuja
                        // FoldersListScreen (campo en titleContent), como el buscador;
                        // ya no hay barra acoplada aquí.
                        val foldersCreate = if (
                            foldersState.scope !=
                                com.photonne.app.ui.folder.FoldersScope.External
                        ) {
                            { showCreateFolder = true }
                        } else null
                        Box(modifier = Modifier.weight(1f)) {
                            com.photonne.app.ui.folder.FoldersListScreen(
                                onFolderClick = { folder ->
                                    if (foldersState.isSelectionActive) {
                                        if (foldersState.selectedFolderId == folder.id) {
                                            foldersViewModel.clearSelection()
                                        } else {
                                            foldersViewModel.selectFolder(folder.id)
                                        }
                                    } else {
                                        selectedFolder = folder
                                    }
                                },
                                onFolderLongPress = { folder ->
                                    foldersViewModel.selectFolder(folder.id)
                                },
                                onOpenOrganize = { moreSubscreen = MoreSubscreen.OrganizeInbox },
                                // Como People/Map desde Álbumes: capa modal sobre la
                                // pestaña, sin cambiar de tab. La tarjeta solo se
                                // muestra donde hay buckets, así que el callback
                                // puede ser incondicional.
                                onOpenDeviceFolders = {
                                    moreSubscreen = MoreSubscreen.DeviceFolders
                                },
                                onOpenFilters = { showFoldersFilters = true },
                                onCreateFolder = foldersCreate,
                                immersive = foldersImmersive,
                                onChromeVisibleChange = { foldersChromeVisible = it },
                                scrollToTopTick = foldersScrollToTopTick
                            )
                        }
                    }
                    // Más pinta su propio cromo flotante dentro de la pantalla
                    // (título + acción Subir), como Fotos: nada de barra acoplada.
                    else -> MoreScreen(
                        user = user.user,
                        onLogout = onLogout,
                        onOpenFavorites = { moreSubscreen = MoreSubscreen.Favorites },
                        onOpenArchived = { moreSubscreen = MoreSubscreen.Archived },
                        onOpenTrash = {
                            trashTab = com.photonne.app.ui.library.TrashTab.Personal
                            moreSubscreen = MoreSubscreen.Trash
                        },
                        onOpenUtilities = { moreSubscreen = MoreSubscreen.Utilities },
                        onOpenMyLinks = { moreSubscreen = MoreSubscreen.MyLinks },
                        onOpenProfile = {
                            profileOpenedFromMore = true
                            moreSubscreen = MoreSubscreen.AccountProfile
                        },
                        onOpenDeviceBackup = { moreSubscreen = MoreSubscreen.DeviceBackup },
                        backupPendingCount = if (deviceBackupState.isBackupEnabled) {
                            deviceBackupState.pendingEntries.size
                        } else 0,
                        onOpenNotifications = {
                            moreSubscreen = MoreSubscreen.Notifications
                        },
                        notificationsUnreadCount = notificationsState.unreadCount,
                        onOpenAccountSettings = {
                            moreSubscreen = MoreSubscreen.AccountSettings
                        },
                        onOpenAdministration = if (
                            user.user.role.equals("Admin", ignoreCase = true)
                        ) {
                            { moreSubscreen = MoreSubscreen.Administration }
                        } else {
                            null
                        },
                        onOpenUpload = { moreSubscreen = MoreSubscreen.Upload },
                        onChromeVisibleChange = { moreChromeVisible = it },
                        attributions = attributions
                    )
                }
            }
            // Overlay layer, drawn over the pager: drill-downs (album/folder
            // detail), Buscar and every More subscreen. Rendered inside an opaque
            // full-bleed Box only while one is active, so it fully hides the
            // pager base (which keeps every top-level body composed behind it) —
            // without it the tab underneath shows through and the two mix.
            if (overlayVisible) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
            // Las subpantallas se conmutaban en seco. Ahora el destino nuevo
            // entra deslizando y fundiéndose (eje compartido de Material): un
            // desplazamiento corto, lo justo para que se lea de dónde viene.
            //
            // No se anima la SALIDA: el `when` de abajo se resuelve contra el
            // estado actual, así que el contenido saliente ya se habría
            // convertido en el entrante y el gesto contaría una mentira. Con
            // `key` el subárbol se remonta y la entrada arranca sola.
            key(overlayKey) {
                val enteringForward = remember { overlayForward }
                var entered by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    entered = true
                    // Consumida: la siguiente navegación vuelve a ser hacia
                    // dentro salvo que el back handler diga lo contrario.
                    overlayForward = true
                }
                val enterProgress by animateFloatAsState(
                    targetValue = if (entered) 1f else 0f,
                    animationSpec = tween(durationMillis = com.photonne.app.ui.theme.MotionDurations.EMPHASIS_MS),
                    label = "overlayEnter"
                )
                val slidePx = with(LocalDensity.current) { 24.dp.toPx() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (1f - enterProgress) *
                                if (enteringForward) slidePx else -slidePx
                            alpha = enterProgress
                        }
                ) {
            when {
                selectedTab == MainTab.Timeline && moreSubscreen == null -> {
                    // shown by the pager base layer
                }
                selectedTab == MainTab.Albums && moreSubscreen == null -> {
                    val openedAlbum = selectedAlbum
                    if (openedAlbum != null) {
                        AlbumDetailScreen(
                            album = openedAlbum,
                            onItemClick = { index ->
                                assetDetail = AssetDetailContext(
                                    // Grid renders the re-sorted displayItems, so
                                    // the tapped index is into that list — not the
                                    // raw server-order items.
                                    items = albumDetailState.displayItems,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Album,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { id, isFav ->
                                        albumDetailViewModel.setFavorite(id, isFav)
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            },
                            onBack = albumBack,
                            onShare = {
                                albumSharesViewModel.open(openedAlbum.id)
                                showShares = true
                            },
                            onEdit = {
                                if (openedAlbum.isSmart && openedAlbum.isOwner) {
                                    editingSmartAlbum = openedAlbum.copy(
                                        name = albumDetailState.albumName ?: openedAlbum.name,
                                        description = albumDetailState.albumDescription ?: openedAlbum.description,
                                    )
                                    moreSubscreen = MoreSubscreen.SmartAlbumEditor
                                } else {
                                    showEditAlbum = true
                                }
                            },
                            onDelete = { showDeleteAlbum = true },
                            onManageMembers = {
                                albumPermissionsViewModel.open(openedAlbum.id)
                                showMembers = true
                            },
                            onLeave = { showLeaveAlbum = true },
                            viewModel = albumDetailViewModel,
                            immersive = albumDetailImmersive,
                            onChromeVisibleChange = { albumDetailChromeVisible = it }
                        )
                    }
                }
                selectedTab == MainTab.Folders && moreSubscreen == null -> {
                    val openedFolder = selectedFolder
                    if (openedFolder != null) {
                        com.photonne.app.ui.folder.FolderDetailScreen(
                            folderId = openedFolder.id,
                            folderName = openedFolder.name.ifBlank { openedFolder.path },
                            parentFolderId = openedFolder.parentFolderId,
                            title = (folderDetailState.folderName ?: openedFolder.name)
                                .ifBlank { openedFolder.path },
                            onBack = { folderBack() },
                            onItemClick = { index ->
                                if (folderDetailState.isSelectionActive) {
                                    folderDetailState.items.getOrNull(index)?.let {
                                        folderDetailViewModel.toggleSelection(it.id)
                                    }
                                } else {
                                    val folderState = folderDetailViewModel.state.value
                                    assetDetail = AssetDetailContext(
                                        items = folderState.items,
                                        startIndex = index,
                                        source = AssetDetailContext.Source.Timeline,
                                        hasMore = false,
                                        onLoadMore = {},
                                        onFavoriteChanged = { id, isFav ->
                                            folderDetailViewModel.setFavorite(id, isFav)
                                            timelineViewModel.setFavorite(id, isFav)
                                        }
                                    )
                                }
                            },
                            onItemLongClick = { index ->
                                folderDetailState.items.getOrNull(index)?.let {
                                    folderDetailViewModel.toggleSelection(it.id)
                                }
                            },
                            onSubfolderClick = { subfolder ->
                                if (folderDetailState.isSubfolderSelectionActive) {
                                    folderDetailViewModel.toggleSubfolderSelection(subfolder.id)
                                } else {
                                    folderBackStack.add(openedFolder)
                                    selectedFolder = subfolder
                                }
                            },
                            onSubfolderLongPress = { subfolder ->
                                folderDetailViewModel.selectSubfolder(subfolder.id)
                            },
                            viewModel = folderDetailViewModel,
                            actions = {
                                // Cada acción con el flag que comprueba el
                                // servidor (antes todo colgaba de isOwner) y sin
                                // tocar bibliotecas externas, como en la lista.
                                val openedIsExternal = openedFolder.externalLibraryId != null
                                FolderDetailChromeActions(
                                    canEdit = openedFolder.canWrite && !openedIsExternal,
                                    canDelete = openedFolder.canDelete && !openedIsExternal,
                                    canManageMembers = openedFolder.isOwner && !openedIsExternal,
                                    canMove = openedFolder.canWrite && !openedIsExternal,
                                    onEdit = { showEditFolder = true },
                                    onMove = { showMoveFolder = true },
                                    onDelete = { showDeleteFolder = true },
                                    onManageMembers = {
                                        folderPermissionsViewModel.open(openedFolder.id)
                                        showFolderMembers = true
                                    },
                                    canToggleTimeline = openedFolder.isShared &&
                                        openedFolder.externalLibraryId == null,
                                    excludedFromDiscovery = openedFolder.excludedFromDiscovery,
                                    onToggleTimeline = {
                                        val nextIncluded = openedFolder.excludedFromDiscovery
                                        foldersViewModel.setTimelineIncluded(
                                            openedFolder.id, included = nextIncluded
                                        )
                                        selectedFolder = openedFolder.copy(
                                            excludedFromDiscovery = !nextIncluded
                                        )
                                    },
                                    onCreateSubfolder = if (!openedIsExternal && openedFolder.canWrite) {
                                        { showCreateFolder = true }
                                    } else null
                                )
                            },
                            immersive = folderDetailImmersive,
                            onChromeVisibleChange = { folderDetailChromeVisible = it }
                        )
                    }
                }
                selectedTab == MainTab.Search && moreSubscreen == null ->
                    com.photonne.app.ui.search.SearchScreen(
                    viewModel = searchViewModel,
                    onOpenFilters = { showSearchFilters = true },
                    onBack = { searchBack() },
                    onChromeVisibleChange = { searchChromeVisible = it },
                    onItemClick = { index ->
                        if (searchState.isSelectionActive) {
                            searchState.results.getOrNull(index)?.let {
                                searchViewModel.toggleSelection(it.id)
                            }
                        } else {
                            assetDetail = AssetDetailContext(
                                items = searchState.results,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = searchState.hasMore,
                                onLoadMore = searchViewModel::loadMore,
                                // La búsqueda pagina: el visor sigue a la lista
                                // viva en vez de pararse en la primera página.
                                feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                    items = { searchState.results },
                                    hasMore = { searchState.hasMore },
                                    loadMore = searchViewModel::loadMore
                                ),
                                onFavoriteChanged = { id, isFav ->
                                    searchViewModel.setFavorite(id, isFav)
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                        }
                    },
                    onItemLongClick = { index ->
                        searchState.results.getOrNull(index)?.let {
                            searchViewModel.toggleSelection(it.id)
                        }
                    }
                )
                else -> when (moreSubscreen) {
                    null -> {
                        // The More grid is shown by the pager base layer; a
                        // non-null subscreen renders its screen on top.
                    }
                    MoreSubscreen.SmartAlbumEditor -> com.photonne.app.ui.album.smart.SmartAlbumEditorScreen(
                        editAlbum = editingSmartAlbum,
                        onBack = {
                            moreSubscreen = null
                            editingSmartAlbum = null
                        },
                        onChromeVisibleChange = { subscreenChromeVisible = it },
                        onSaved = { saved ->
                            val wasEditing = editingSmartAlbum != null
                            moreSubscreen = null
                            editingSmartAlbum = null
                            if (wasEditing) {
                                // La respuesta del PUT no trae portada ni miniaturas:
                                // se recarga la lista en vez de pisar la tarjeta.
                                albumsViewModel.refresh()
                                // Nuevas condiciones = otro contenido: si el álbum está
                                // abierto, se recarga al volver a él.
                                selectedAlbum?.takeIf { it.id == saved.id }?.let { opened ->
                                    selectedAlbum = opened.copy(
                                        name = saved.name,
                                        description = saved.description,
                                        assetCount = saved.assetCount,
                                    )
                                    albumDetailViewModel.refresh()
                                }
                            } else {
                                albumsViewModel.refresh()
                                selectedTab = MainTab.Albums
                                selectedAlbum = saved
                            }
                        }
                    )
                    MoreSubscreen.Upload -> com.photonne.app.ui.upload.UploadScreen(
                        state = uploadState,
                        onPicked = { files ->
                            uploadViewModel.enqueue(files) { timelineViewModel.refresh() }
                        },
                        onPickerError = uploadViewModel::pickerErrorRaised,
                        onRetry = { id ->
                            uploadViewModel.retry(id) { timelineViewModel.refresh() }
                        },
                        onRemove = uploadViewModel::remove,
                        onCancelAll = uploadViewModel::cancelAll,
                        onClearFinished = uploadViewModel::clearFinished,
                        onDismissPickerError = uploadViewModel::clearPickerError,
                        onViewBatch = {
                            uploadState.lastBatch?.takeIf { it.isNotEmpty() }?.let { batch ->
                                assetDetail = AssetDetailContext(
                                    items = batch,
                                    startIndex = 0,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { id, isFav ->
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        },
                        onAddBatchToAlbum = { bulkAddSource = BulkAddSource.Upload },
                        onDismissBatch = uploadViewModel::dismissBatchSummary
                    )
                    MoreSubscreen.DeviceBackup ->
                        com.photonne.app.ui.devicebackup.BackupScreen(
                            title = stringResource(Res.string.device_backup_title),
                            onBack = { moreSubscreen = null },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = deviceBackupViewModel,
                            enrichmentViewModel = enrichmentStatusViewModel,
                            gallery = deviceGallery,
                            onOpenPending = {
                                moreSubscreen = MoreSubscreen.DeviceBackupPending
                            },
                            onOpenEnrichment = {
                                moreSubscreen = MoreSubscreen.EnrichmentStatus
                            }
                        )
                    MoreSubscreen.DeviceBackupPending ->
                        com.photonne.app.ui.devicebackup.BackupPendingScreen(
                            title = stringResource(Res.string.backup_pending_screen_title),
                            onBack = { moreSubscreen = MoreSubscreen.DeviceBackup },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = deviceBackupViewModel,
                            gallery = deviceGallery,
                            onOpenAsset = { item ->
                                assetDetail = AssetDetailContext(
                                    items = listOf(item),
                                    startIndex = 0,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { id, isFav ->
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        )
                    MoreSubscreen.EnrichmentStatus ->
                        com.photonne.app.ui.devicebackup.EnrichmentStatusScreen(
                            title = stringResource(Res.string.enrichment_screen_title),
                            onBack = { moreSubscreen = MoreSubscreen.DeviceBackup },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = enrichmentStatusViewModel
                        )
                    MoreSubscreen.MyLinks ->
                        com.photonne.app.ui.album.MyLinksScreen(
                            title = stringResource(Res.string.my_links_title),
                            onBack = { moreSubscreen = null },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.UnsupportedFiles ->
                        com.photonne.app.ui.library.UnsupportedFilesScreen(
                            state = unsupportedFilesState,
                            onLoad = unsupportedFilesViewModel::ensureLoaded,
                            onRefresh = unsupportedFilesViewModel::refresh,
                            onLoadMore = unsupportedFilesViewModel::loadMore,
                            onDownload = unsupportedFilesViewModel::download,
                            onDelete = unsupportedFilesViewModel::delete,
                            onClearDeleteError = unsupportedFilesViewModel::clearDeleteError,
                            onBack = { moreSubscreen = MoreSubscreen.Utilities },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.OrganizeInbox ->
                        com.photonne.app.ui.organize.OrganizeInboxScreen(
                            state = organizeInboxState,
                            onLoad = organizeInboxViewModel::ensureLoaded,
                            onRefresh = organizeInboxViewModel::refresh,
                            onLoadMore = organizeInboxViewModel::loadMore,
                            onItemClick = { index ->
                                if (organizeInboxState.isSelectionActive) {
                                    organizeInboxState.items.getOrNull(index)?.let {
                                        organizeInboxViewModel.toggleSelection(it.id)
                                    }
                                } else {
                                    assetDetail = AssetDetailContext(
                                        items = organizeInboxViewModel.state.value.items,
                                        startIndex = index,
                                        source = AssetDetailContext.Source.Timeline,
                                        hasMore = organizeInboxState.hasMore,
                                        onLoadMore = organizeInboxViewModel::loadMore,
                                        feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                            items = { organizeInboxState.items },
                                            hasMore = { organizeInboxState.hasMore },
                                            loadMore = organizeInboxViewModel::loadMore
                                        ),
                                        onFavoriteChanged = { id, isFav ->
                                            timelineViewModel.setFavorite(id, isFav)
                                        }
                                    )
                                }
                            },
                            onItemLongClick = { index ->
                                organizeInboxState.items.getOrNull(index)?.let {
                                    organizeInboxViewModel.toggleSelection(it.id)
                                }
                            },
                            onBack = {
                                moreSubscreen = null
                                foldersViewModel.refreshOrganizeCount()
                            },
                            onOpenRules = { moreSubscreen = MoreSubscreen.OrganizeRule },
                            onPickSuggestion = organizeInboxViewModel::selectSuggestion,
                            onSeeAllItems = organizeInboxViewModel::showAllItems,
                            onBackToSuggestions = organizeInboxViewModel::showSuggestions,
                            onApplySelection = organizeInboxViewModel::applySelection,
                            onOpenExcluded = {
                                organizeExcludedViewModel.refresh()
                                moreSubscreen = MoreSubscreen.OrganizeExcluded
                            },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.OrganizeExcluded ->
                        com.photonne.app.ui.organize.OrganizeExcludedScreen(
                            state = organizeExcludedState,
                            onLoad = organizeExcludedViewModel::ensureLoaded,
                            onRefresh = organizeExcludedViewModel::refresh,
                            onLoadMore = organizeExcludedViewModel::loadMore,
                            onItemClick = { index ->
                                if (organizeExcludedState.isSelectionActive) {
                                    organizeExcludedState.items.getOrNull(index)?.let {
                                        organizeExcludedViewModel.toggleSelection(it.id)
                                    }
                                } else {
                                    assetDetail = AssetDetailContext(
                                        items = organizeExcludedViewModel.state.value.items,
                                        startIndex = index,
                                        source = AssetDetailContext.Source.Timeline,
                                        hasMore = organizeExcludedState.hasMore,
                                        onLoadMore = organizeExcludedViewModel::loadMore,
                                        feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                            items = { organizeExcludedState.items },
                                            hasMore = { organizeExcludedState.hasMore },
                                            loadMore = organizeExcludedViewModel::loadMore
                                        ),
                                        onFavoriteChanged = { id, isFav ->
                                            timelineViewModel.setFavorite(id, isFav)
                                        }
                                    )
                                }
                            },
                            onItemLongClick = { index ->
                                organizeExcludedState.items.getOrNull(index)?.let {
                                    organizeExcludedViewModel.toggleSelection(it.id)
                                }
                            },
                            onBack = { moreSubscreen = MoreSubscreen.OrganizeInbox },
                            onApplySelection = organizeExcludedViewModel::applySelection,
                            onErrorShown = organizeExcludedViewModel::clearError,
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.OrganizeRule ->
                        com.photonne.app.ui.organize.OrganizeRuleScreen(
                            title = stringResource(Res.string.organize_rule_title),
                            onBack = { moreSubscreen = MoreSubscreen.OrganizeInbox },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            destinations = foldersState.moveDestinations,
                            viewModel = organizeRuleViewModel,
                            reviewOpen = organizeRuleState.reviewGroups != null
                        )
                    MoreSubscreen.Utilities ->
                        com.photonne.app.ui.utilities.UtilitiesHubScreen(
                            title = stringResource(Res.string.utilities_title),
                            onBack = { moreSubscreen = null },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            onOpen = { entry ->
                                moreSubscreen = when (entry) {
                                    com.photonne.app.ui.utilities.UtilitiesEntry.Duplicates ->
                                        MoreSubscreen.UtilitiesDuplicates
                                    com.photonne.app.ui.utilities.UtilitiesEntry.LargeFiles ->
                                        MoreSubscreen.UtilitiesLargeFiles
                                    com.photonne.app.ui.utilities.UtilitiesEntry.Locations ->
                                        MoreSubscreen.UtilitiesLocations
                                    com.photonne.app.ui.utilities.UtilitiesEntry.UnsupportedFiles ->
                                        MoreSubscreen.UnsupportedFiles
                                }
                            }
                        )
                    MoreSubscreen.UtilitiesDuplicates ->
                        com.photonne.app.ui.utilities.UtilitiesDuplicatesScreen(
                            title = stringResource(Res.string.utilities_section_duplicates),
                            onBack = { moreSubscreen = MoreSubscreen.Utilities },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = utilitiesDuplicatesViewModel,
                            baseUrl = apiBaseUrl,
                            onUndoTrash = { ids ->
                                actionsViewModel.undoBulk(
                                    com.photonne.app.ui.actions.BulkUndoKind.Trash,
                                    ids
                                ) {
                                    utilitiesDuplicatesViewModel.refresh()
                                    timelineViewModel.refresh()
                                }
                            },
                            onOpenAsset = { index, items ->
                                assetDetail = AssetDetailContext(
                                    items = items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { id, isFav ->
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        )
                    MoreSubscreen.UtilitiesLargeFiles ->
                        com.photonne.app.ui.utilities.UtilitiesLargeFilesScreen(
                            title = stringResource(Res.string.utilities_section_large_files),
                            onBack = { moreSubscreen = MoreSubscreen.Utilities },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = utilitiesLargeFilesViewModel,
                            baseUrl = apiBaseUrl,
                            onAssetClick = { index, items ->
                                assetDetail = AssetDetailContext(
                                    items = items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { id, isFav ->
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        )
                    MoreSubscreen.UtilitiesLocations ->
                        com.photonne.app.ui.utilities.UtilitiesLocationsScreen(
                            title = stringResource(Res.string.utilities_section_locations),
                            onBack = { moreSubscreen = MoreSubscreen.Utilities },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = utilitiesLocationsViewModel,
                            onFolderClick = { node ->
                                coroutineScope.launch {
                                    // El resumen real trae los permisos (escribir,
                                    // borrar); sin red se abre con lo que da el
                                    // árbol, en solo lectura, y el detalle pinta
                                    // su propio error con Reintentar.
                                    val folder = runCatching { foldersRepository.get(node.id) }
                                        .getOrElse {
                                            com.photonne.app.data.models.FolderSummary(
                                                id = node.id,
                                                path = node.path,
                                                name = node.name,
                                                parentFolderId = node.parentFolderId,
                                                createdAt = kotlin.time.Clock.System.now(),
                                                assetCount = node.assetCount,
                                                isShared = node.isShared,
                                                isOwner = node.isOwner,
                                                canWrite = false,
                                                canDelete = false,
                                                externalLibraryId = node.externalLibraryId
                                            )
                                        }
                                    folderReturnTo = selectedTab to MoreSubscreen.UtilitiesLocations
                                    folderBackStack.clear()
                                    selectedFolder = folder
                                    moreSubscreen = null
                                    selectedTab = MainTab.Folders
                                }
                            }
                        )
                    MoreSubscreen.Memories ->
                        com.photonne.app.ui.memories.MemoriesScreen(
                            viewModel = memoryFeedViewModel,
                            baseUrl = apiBaseUrl,
                            onOpenMemory = { detail ->
                                memoryDetail = com.photonne.app.ui.memories.MemoryDetailContext(
                                    title = detail.title,
                                    subtitle = detail.subtitle,
                                    coverAssetId = detail.coverAssetId,
                                    items = detail.assets
                                )
                            },
                            onBack = { moreSubscreen = null },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.ExploreScenes ->
                        com.photonne.app.ui.explore.ExploreScenesScreen(
                            viewModel = exploreFacetsViewModel,
                            // Tapping a scene jumps to the Search tab pre-filtered
                            // by that label — same flow as the PWA, where Explorar
                            // is just a deep-linking surface for the search engine.
                            onSceneClick = { label ->
                                searchViewModel.showResultsForSceneLabel(label)
                                searchReturnTo = selectedTab to MoreSubscreen.ExploreScenes
                                moreSubscreen = null
                                selectedTab = MainTab.Search
                            },
                            onBack = { moreSubscreen = null },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.ExploreObjects ->
                        com.photonne.app.ui.explore.ExploreObjectsScreen(
                            viewModel = exploreFacetsViewModel,
                            onObjectClick = { label ->
                                searchViewModel.showResultsForObjectLabel(label)
                                searchReturnTo = selectedTab to MoreSubscreen.ExploreObjects
                                moreSubscreen = null
                                selectedTab = MainTab.Search
                            },
                            onBack = { moreSubscreen = null },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.Map -> com.photonne.app.ui.map.MapScreen(
                        viewModel = mapViewModel,
                        onPointOpen = { visiblePoints, index ->
                            // Single-marker tap → open the asset viewer
                            // seeded with every point visible in the
                            // viewport (by date), starting at the tapped
                            // one, so it swipes like a cluster does. The
                            // viewer re-fetches asset detail on display, so
                            // synthetic TimelineItems are enough.
                            assetDetail = AssetDetailContext(
                                items = visiblePoints.map { it.toSyntheticTimelineItem() },
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = { id, isFav ->
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                        },
                        onClusterPhotoOpen = { sheetPoints, index ->
                            // Bottom-sheet thumbnail tap → open the
                            // viewer seeded with the whole cluster so
                            // the user can swipe through it.
                            val items = sheetPoints.map { it.toSyntheticTimelineItem() }
                            assetDetail = AssetDetailContext(
                                items = items,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = { id, isFav ->
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                            mapViewModel.closeClusterSheet()
                        },
                        onBulkAddToAlbum = { bulkAddSource = BulkAddSource.Map },
                        onBack = { moreSubscreen = null }
                    )
                    MoreSubscreen.PeopleSuggestions ->
                        com.photonne.app.ui.people.PersonSuggestionsScreen(
                            state = suggestionsState,
                            title = (suggestionsState.personName ?: selectedPerson?.name).orEmpty(),
                            isBulkMutating = suggestionsState.isBulkMutating,
                            onAccept = personSuggestionsViewModel::acceptFace,
                            onDismissFace = personSuggestionsViewModel::dismissFace,
                            onLoadMore = personSuggestionsViewModel::loadMore,
                            onOpen = {
                                selectedPerson?.let {
                                    personSuggestionsViewModel.open(it.id, it.name)
                                }
                            },
                            onBack = { moreSubscreen = MoreSubscreen.People },
                            // Actúan también sobre las páginas no cargadas, así
                            // que primero confirman con el recuento del servidor.
                            onAcceptAll = { showAcceptAllSuggestions = true },
                            onDismissAll = { showDismissAllSuggestions = true },
                            onRefresh = personSuggestionsViewModel::refresh,
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.People -> {
                        val person = selectedPerson
                        if (person == null) {
                            com.photonne.app.ui.people.PeopleScreen(
                                state = peopleState,
                                onPersonClick = { picked ->
                                    selectedPerson = picked
                                    personDetailViewModel.open(picked.id, picked.name)
                                },
                                onLoadMore = peopleViewModel::loadMore,
                                onLoad = peopleViewModel::ensureLoaded,
                                onRefresh = peopleViewModel::refresh,
                                onBack = { moreSubscreen = null },
                                onToggleSearch = peopleViewModel::toggleSearch,
                                onSearchChange = peopleViewModel::setSearch,
                                onSortChange = peopleViewModel::setSort,
                                onRecluster = {
                                    // El servidor devuelve cuántas personas nuevas
                                    // salieron del reagrupado; antes se descartaba.
                                    peopleViewModel.recluster { created ->
                                        coroutineScope.launch {
                                            snackbarController.show(
                                                if (created > 0) {
                                                    org.jetbrains.compose.resources.getPluralString(
                                                        Res.plurals.people_recluster_done,
                                                        created, created
                                                    )
                                                } else {
                                                    org.jetbrains.compose.resources.getString(
                                                        Res.string.people_recluster_done_none
                                                    )
                                                }
                                            )
                                        }
                                    }
                                },
                                onToggleHidden = peopleViewModel::toggleShowHidden,
                                onChromeVisibleChange = { subscreenChromeVisible = it }
                            )
                        } else {
                            com.photonne.app.ui.people.PersonDetailScreen(
                                state = personDetailState,
                                title = personDetailState.personName ?: person.name.orEmpty(),
                                isHidden = person.isHidden,
                                onRetry = { personDetailViewModel.open(person.id, person.name) },
                                onRefresh = personDetailViewModel::refresh,
                                onItemClick = { index ->
                                    if (personDetailState.isSelectionActive) {
                                        personDetailState.items.getOrNull(index)?.let {
                                            personDetailViewModel.toggleSelection(it.id)
                                        }
                                    } else {
                                        assetDetail = AssetDetailContext(
                                            items = personDetailState.items,
                                            startIndex = index,
                                            source = AssetDetailContext.Source.Timeline,
                                            hasMore = personDetailState.hasMore,
                                            onLoadMore = personDetailViewModel::loadMore,
                                            feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                                items = { personDetailState.items },
                                                hasMore = { personDetailState.hasMore },
                                                loadMore = personDetailViewModel::loadMore
                                            ),
                                            onFavoriteChanged = { id, isFav ->
                                                personDetailViewModel.setFavorite(id, isFav)
                                                timelineViewModel.setFavorite(id, isFav)
                                            }
                                        )
                                    }
                                },
                                onItemLongClick = { index ->
                                    personDetailState.items.getOrNull(index)?.let {
                                        personDetailViewModel.toggleSelection(it.id)
                                    }
                                },
                                onLoadMore = personDetailViewModel::loadMore,
                                onApplySelection = personDetailViewModel::applySelection,
                                onBack = ::personBack,
                                onRename = { showRenamePerson = true },
                                onSuggestions = {
                                    personSuggestionsViewModel.open(person.id, person.name)
                                    moreSubscreen = MoreSubscreen.PeopleSuggestions
                                },
                                onMerge = { showMergePicker = true },
                                onToggleHidden = {
                                    if (person.isHidden) {
                                        peopleViewModel.unhide(person.id) {
                                            selectedPerson = person.copy(isHidden = false)
                                        }
                                    } else {
                                        peopleViewModel.hide(person.id) {
                                            selectedPerson = null
                                        }
                                    }
                                },
                                onChromeVisibleChange = { subscreenChromeVisible = it }
                            )
                        }
                    }
                    MoreSubscreen.DeviceFolders -> com.photonne.app.ui.folder.DeviceFoldersScreen(
                        backedUpUris = remember(deviceBackupState.folders) {
                            deviceBackupState.folders.map { it.uri }.toSet()
                        },
                        onAddToBackup = deviceBackupViewModel::onFolderPicked,
                        onOpenBucket = { bucket ->
                            deviceFolderBucket = bucket
                            moreSubscreen = MoreSubscreen.DeviceFolderDetail
                        },
                        onBack = { moreSubscreen = null },
                        onChromeVisibleChange = { subscreenChromeVisible = it }
                    )
                    MoreSubscreen.DeviceFolderDetail -> deviceFolderBucket?.let { bucket ->
                        com.photonne.app.ui.folder.DeviceFolderDetailScreen(
                            bucket = bucket,
                            onOpenAsset = { items, index ->
                                assetDetail = AssetDetailContext(
                                    items = items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { _, _ -> }
                                )
                            },
                            onBack = { moreSubscreen = MoreSubscreen.DeviceFolders },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    }
                    MoreSubscreen.Favorites -> com.photonne.app.ui.library.FavoritesScreen(
                        state = favoritesState,
                        onItemClick = { index ->
                            if (favoritesState.isSelectionActive) {
                                favoritesState.items.getOrNull(index)?.let {
                                    favoritesViewModel.toggleSelection(it.id)
                                }
                            } else {
                                assetDetail = AssetDetailContext(
                                    items = favoritesState.items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = favoritesState.hasMore,
                                    onLoadMore = favoritesViewModel::loadMore,
                                    feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                        items = { favoritesState.items },
                                        hasMore = { favoritesState.hasMore },
                                        loadMore = favoritesViewModel::loadMore
                                    ),
                                    onFavoriteChanged = { id, isFav ->
                                        favoritesViewModel.setFavorite(id, isFav)
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        },
                        onItemLongClick = { index ->
                            favoritesState.items.getOrNull(index)?.let {
                                favoritesViewModel.toggleSelection(it.id)
                            }
                        },
                        onLoadMore = favoritesViewModel::loadMore,
                        onLoad = favoritesViewModel::ensureLoaded,
                        onRefresh = favoritesViewModel::refresh,
                        onApplySelection = favoritesViewModel::applySelection,
                        onBack = { moreSubscreen = null },
                        onChromeVisibleChange = { subscreenChromeVisible = it }
                    )
                    MoreSubscreen.Archived -> com.photonne.app.ui.library.ArchivedScreen(
                        state = archivedState,
                        onItemClick = { index ->
                            if (archivedState.isSelectionActive) {
                                archivedState.items.getOrNull(index)?.let {
                                    archivedViewModel.toggleSelection(it.id)
                                }
                            } else {
                                assetDetail = AssetDetailContext(
                                    items = archivedState.items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Archive,
                                    hasMore = archivedState.hasMore,
                                    onLoadMore = archivedViewModel::loadMore,
                                    feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                        items = { archivedState.items },
                                        hasMore = { archivedState.hasMore },
                                        loadMore = archivedViewModel::loadMore
                                    ),
                                    onFavoriteChanged = { id, isFav ->
                                        archivedViewModel.setFavorite(id, isFav)
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        },
                        onItemLongClick = { index ->
                            archivedState.items.getOrNull(index)?.let {
                                archivedViewModel.toggleSelection(it.id)
                            }
                        },
                        onLoadMore = archivedViewModel::loadMore,
                        onApplySelection = archivedViewModel::applySelection,
                        onLoad = archivedViewModel::ensureLoaded,
                        onRefresh = archivedViewModel::refresh,
                        onBack = { moreSubscreen = null },
                        onUnarchiveAll = { showUnarchiveAll = true },
                        onChromeVisibleChange = { subscreenChromeVisible = it }
                    )
                    MoreSubscreen.Trash -> Box(modifier = Modifier.fillMaxSize()) {
                        // Con una selección activa manda la barra acoplada de
                        // selección (rama del topBar); si no, la pantalla reserva
                        // el hueco del cromo flotante y lo dibuja ella misma.
                        val trashSelecting = trashState.isSelectionActive
                        // La rejilla de la papelera personal (hermana Haze +
                        // fuente de scroll) para que el cromo se acople en reposo.
                        val trashHazeState = remember { HazeState() }
                        val trashGridState = rememberLazyGridState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (trashSelecting) 0.dp
                                    else subscreenChromeReservedTop()
                                )
                        ) {
                        com.photonne.app.ui.library.TrashTabBar(
                            selected = trashTab,
                            onSelect = { tab ->
                                if (tab != trashTab) {
                                    // Leaving the personal tab drops its selection
                                    // so the top bar/back don't act on a hidden tab.
                                    trashViewModel.clearSelection()
                                    trashTab = tab
                                }
                            }
                        )
                        when (trashTab) {
                            com.photonne.app.ui.library.TrashTab.Personal ->
                                com.photonne.app.ui.library.TrashScreen(
                                    state = trashState,
                                    onItemClick = { index ->
                                        if (trashState.isSelectionActive) {
                                            trashState.items.getOrNull(index)?.let {
                                                trashViewModel.toggleSelection(it.id)
                                            }
                                        } else {
                                            assetDetail = AssetDetailContext(
                                                items = trashState.items,
                                                startIndex = index,
                                                source = AssetDetailContext.Source.Trash,
                                                hasMore = trashState.hasMore,
                                                onLoadMore = trashViewModel::loadMore,
                                                feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                                    items = { trashState.items },
                                                    hasMore = { trashState.hasMore },
                                                    loadMore = trashViewModel::loadMore
                                                ),
                                                onFavoriteChanged = { id, isFav ->
                                                    // Trashed assets ignore favorite changes
                                                    // server-side, but keep the local copy
                                                    // consistent if the viewer toggles.
                                                    timelineViewModel.setFavorite(id, isFav)
                                                }
                                            )
                                        }
                                    },
                                    onItemLongClick = { index ->
                                        trashState.items.getOrNull(index)?.let {
                                            trashViewModel.toggleSelection(it.id)
                                        }
                                    },
                                    onLoadMore = trashViewModel::loadMore,
                                    onLoad = trashViewModel::ensureLoaded,
                                    onRefresh = trashViewModel::refresh,
                                    onApplySelection = trashViewModel::applySelection,
                                    gridState = trashGridState,
                                    hazeState = trashHazeState
                                )
                            com.photonne.app.ui.library.TrashTab.Shared ->
                                com.photonne.app.ui.admin.AdminSharedTrashScreen(
                                    viewModel = adminSharedTrashViewModel
                                )
                        }
                        }
                        if (!trashSelecting) {
                            val count = trashState.items.size
                            SubscreenFloatingChrome(
                                title = stringResource(Res.string.trash_title),
                                onBack = { moreSubscreen = null },
                                // La rejilla personal manda el acople/ocultar; en
                                // Compartida no está compuesta, así que queda en
                                // reposo (acoplada), que es lo correcto.
                                scroll = SubscreenScroll(
                                    firstVisibleItemIndex = { trashGridState.firstVisibleItemIndex },
                                    firstVisibleItemScrollOffset = { trashGridState.firstVisibleItemScrollOffset },
                                    isScrollInProgress = { trashGridState.isScrollInProgress },
                                    scrollToTopMinIndex = 4,
                                    onScrollToTop = { trashGridState.animateScrollToItem(0) }
                                ),
                                hazeState = trashHazeState,
                                onChromeVisibleChange = { subscreenChromeVisible = it },
                                // Restaurar todo / vaciar solo aplican a la papelera
                                // personal; en la compartida la propia pantalla pinta
                                // sus acciones, así que la cápsula va sin ellas.
                                actions = if (
                                    trashTab == com.photonne.app.ui.library.TrashTab.Personal &&
                                    count > 0
                                ) {
                                    {
                                        var trashMenuOpen by remember { mutableStateOf(false) }
                                        androidx.compose.material3.IconButton(
                                            onClick = { trashMenuOpen = true }
                                        ) {
                                            Icon(
                                                Icons.Filled.MoreVert,
                                                contentDescription = stringResource(Res.string.action_more)
                                            )
                                        }
                                        androidx.compose.material3.DropdownMenu(
                                            expanded = trashMenuOpen,
                                            onDismissRequest = { trashMenuOpen = false }
                                        ) {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = {
                                                    androidx.compose.material3.Text(
                                                        stringResource(Res.string.trash_action_restore_all)
                                                    )
                                                },
                                                onClick = {
                                                    trashMenuOpen = false
                                                    showRestoreAllTrash = true
                                                }
                                            )
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = {
                                                    androidx.compose.material3.Text(
                                                        stringResource(Res.string.trash_action_empty),
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                onClick = {
                                                    trashMenuOpen = false
                                                    showEmptyTrash = true
                                                }
                                            )
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                    MoreSubscreen.Notifications -> {
                        val noScreenMessage =
                            stringResource(Res.string.notifications_no_screen)
                        com.photonne.app.ui.notifications.NotificationsScreen(
                            title = stringResource(Res.string.notifications_title),
                            onBack = { moreSubscreen = null },
                            canMarkAllRead = notificationsState.unreadCount > 0 &&
                                !notificationsState.isMarkingAllRead,
                            onMarkAllRead = notificationsViewModel::markAllRead,
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = notificationsViewModel,
                            onNavigate = { url ->
                                // Map known server actionUrls to in-app
                                // subscreens; unknown routes are a no-op.
                                val path = url.substringBefore('?').trimEnd('/')
                                when {
                                    path == "/shared-trash" ||
                                        path.endsWith("/shared-trash") -> {
                                        trashTab = com.photonne.app.ui.library.TrashTab.Shared
                                        moreSubscreen = MoreSubscreen.Trash
                                    }
                                    path == "/admin/enrichment-failures" ||
                                        path.endsWith("/admin/enrichment-failures") -> {
                                        adminEnrichmentInitialType = url
                                            .substringAfter('?', "")
                                            .split('&')
                                            .firstOrNull { it.startsWith("type=") }
                                            ?.substringAfter('=')
                                            ?.takeIf { it.isNotBlank() }
                                        adminEnrichmentReturnTo = MoreSubscreen.AdminSystemHub
                                        moreSubscreen = MoreSubscreen.AdminSystemEnrichmentFailures
                                    }
                                    path == "/admin/stats" ||
                                        path.endsWith("/admin/stats") -> {
                                        moreSubscreen = MoreSubscreen.AdminStats
                                    }
                                    path == "/people" || path.endsWith("/people") -> {
                                        moreSubscreen = MoreSubscreen.People
                                    }
                                    // Ruta sin pantalla nativa: decirlo vale
                                    // más que un toque que no hace nada.
                                    else -> snackbarController.show(noScreenMessage)
                                }
                            }
                        )
                    }
                    MoreSubscreen.AccountSettings ->
                        com.photonne.app.ui.settings.AccountSettingsScreen(
                            title = stringResource(Res.string.account_settings_title),
                            onBack = { moreSubscreen = null },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            activityNotificationsEnabled = activityNotificationsEnabled
                                .takeIf { activityNotifications.isSupported },
                            onActivityNotificationsChange = activityNotifications::setEnabled,
                            onOpen = { section ->
                                profileOpenedFromMore = false
                                moreSubscreen = when (section) {
                                    com.photonne.app.ui.settings.AccountSettingsSection.Profile ->
                                        MoreSubscreen.AccountProfile
                                    com.photonne.app.ui.settings.AccountSettingsSection.Security ->
                                        MoreSubscreen.AccountSecurity
                                    com.photonne.app.ui.settings.AccountSettingsSection.Appearance ->
                                        MoreSubscreen.AccountAppearance
                                    com.photonne.app.ui.settings.AccountSettingsSection.Storage ->
                                        MoreSubscreen.AccountStorage
                                    com.photonne.app.ui.settings.AccountSettingsSection.Connection ->
                                        MoreSubscreen.AccountConnection
                                }
                            }
                        )
                    MoreSubscreen.AccountProfile ->
                        com.photonne.app.ui.settings.AccountProfileScreen(
                            title = stringResource(Res.string.account_section_profile),
                            onBack = {
                                moreSubscreen = if (profileOpenedFromMore) null
                                else MoreSubscreen.AccountSettings
                            },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = accountProfileViewModel
                        )
                    MoreSubscreen.AccountSecurity ->
                        com.photonne.app.ui.settings.AccountSecurityScreen(
                            title = stringResource(Res.string.account_section_security),
                            onBack = { moreSubscreen = MoreSubscreen.AccountSettings },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = accountSecurityViewModel
                        )
                    MoreSubscreen.AccountAppearance ->
                        com.photonne.app.ui.settings.AccountAppearanceScreen(
                            title = stringResource(Res.string.account_section_appearance),
                            onBack = { moreSubscreen = MoreSubscreen.AccountSettings },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = appearanceViewModel
                        )
                    MoreSubscreen.AccountStorage ->
                        com.photonne.app.ui.settings.AccountStorageScreen(
                            title = stringResource(Res.string.account_section_storage),
                            onBack = { moreSubscreen = MoreSubscreen.AccountSettings },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = accountStorageViewModel
                        )
                    MoreSubscreen.AccountConnection ->
                        com.photonne.app.ui.settings.AccountConnectionScreen(
                            title = stringResource(Res.string.account_section_connection),
                            onBack = { moreSubscreen = MoreSubscreen.AccountSettings },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = deviceConnectionViewModel
                        )
                    MoreSubscreen.Administration ->
                        com.photonne.app.ui.admin.AdministrationScreen(
                            title = stringResource(Res.string.administration_title),
                            onBack = { moreSubscreen = null },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            onOpen = { section ->
                                moreSubscreen = when (section) {
                                    com.photonne.app.ui.admin.AdministrationSection.Users ->
                                        MoreSubscreen.AdminUsers
                                    com.photonne.app.ui.admin.AdministrationSection.Libraries ->
                                        MoreSubscreen.AdminLibraries
                                    com.photonne.app.ui.admin.AdministrationSection.Stats ->
                                        MoreSubscreen.AdminStats
                                    com.photonne.app.ui.admin.AdministrationSection.Settings ->
                                        MoreSubscreen.AdminSettingsHub
                                    com.photonne.app.ui.admin.AdministrationSection.System ->
                                        MoreSubscreen.AdminSystemHub
                                }
                            }
                        )
                    MoreSubscreen.AdminUsers ->
                        com.photonne.app.ui.admin.AdminUsersScreen(
                            title = stringResource(Res.string.admin_section_users),
                            onBack = { moreSubscreen = MoreSubscreen.Administration },
                            onCreateNew = {
                                adminUserEditorId = null
                                adminUsersViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminUserEditor
                            },
                            viewModel = adminUsersViewModel,
                            onEdit = { user ->
                                adminUserEditorId = user.id
                                adminUsersViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminUserEditor
                            },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.AdminUserEditor ->
                        com.photonne.app.ui.admin.AdminUserEditorScreen(
                            title = stringResource(
                                if (adminUserEditorId == null) Res.string.admin_user_action_new
                                else Res.string.admin_user_edit_title
                            ),
                            onBack = {
                                adminUserEditorId = null
                                adminUsersViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminUsers
                            },
                            viewModel = adminUsersViewModel,
                            userId = adminUserEditorId,
                            onDone = {
                                adminUserEditorId = null
                                moreSubscreen = MoreSubscreen.AdminUsers
                            },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.AdminLibraries -> {
                        val usersState by adminUsersViewModel.state.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { adminUsersViewModel.ensureLoaded() }
                        com.photonne.app.ui.admin.AdminLibrariesScreen(
                            title = stringResource(Res.string.admin_section_libraries),
                            onBack = { moreSubscreen = MoreSubscreen.Administration },
                            onCreateNew = {
                                adminLibraryEditorId = null
                                adminLibrariesViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminLibraryEditor
                            },
                            viewModel = adminLibrariesViewModel,
                            knownUsers = usersState.users,
                            onEdit = { library ->
                                adminLibraryEditorId = library.id
                                adminLibrariesViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminLibraryEditor
                            },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    }
                    MoreSubscreen.AdminLibraryEditor ->
                        com.photonne.app.ui.admin.AdminLibraryEditorScreen(
                            title = stringResource(
                                if (adminLibraryEditorId == null) Res.string.admin_libraries_action_new
                                else Res.string.admin_libraries_edit_title
                            ),
                            onBack = {
                                adminLibraryEditorId = null
                                adminLibrariesViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminLibraries
                            },
                            viewModel = adminLibrariesViewModel,
                            libraryId = adminLibraryEditorId,
                            onDone = {
                                adminLibraryEditorId = null
                                moreSubscreen = MoreSubscreen.AdminLibraries
                            },
                            onChromeVisibleChange = { subscreenChromeVisible = it }
                        )
                    MoreSubscreen.AdminStats ->
                        com.photonne.app.ui.admin.AdminStatsScreen(
                            title = stringResource(Res.string.admin_section_stats),
                            onBack = { moreSubscreen = MoreSubscreen.Administration },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminStatsViewModel
                        )
                    MoreSubscreen.AdminSettingsHub ->
                        com.photonne.app.ui.admin.AdminSettingsHubScreen(
                            title = stringResource(Res.string.admin_section_settings),
                            onBack = { moreSubscreen = MoreSubscreen.Administration },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            onOpen = { entry ->
                                moreSubscreen = when (entry) {
                                    com.photonne.app.ui.admin.AdminSettingsEntry.FaceRecognition ->
                                        MoreSubscreen.AdminSettingsFaceRecognition
                                    com.photonne.app.ui.admin.AdminSettingsEntry.ObjectDetection ->
                                        MoreSubscreen.AdminSettingsObjectDetection
                                    com.photonne.app.ui.admin.AdminSettingsEntry.SceneClassification ->
                                        MoreSubscreen.AdminSettingsSceneClassification
                                    com.photonne.app.ui.admin.AdminSettingsEntry.TextRecognition ->
                                        MoreSubscreen.AdminSettingsTextRecognition
                                    com.photonne.app.ui.admin.AdminSettingsEntry.ImageEmbedding ->
                                        MoreSubscreen.AdminSettingsImageEmbedding
                                    com.photonne.app.ui.admin.AdminSettingsEntry.ImageSettings ->
                                        MoreSubscreen.AdminSettingsImage
                                    com.photonne.app.ui.admin.AdminSettingsEntry.Metadata ->
                                        MoreSubscreen.AdminSettingsMetadata
                                    com.photonne.app.ui.admin.AdminSettingsEntry.NightlyTasks ->
                                        MoreSubscreen.AdminSettingsNightly
                                    com.photonne.app.ui.admin.AdminSettingsEntry.Notifications ->
                                        MoreSubscreen.AdminSettingsNotifications
                                    com.photonne.app.ui.admin.AdminSettingsEntry.Server ->
                                        MoreSubscreen.AdminSettingsServer
                                    com.photonne.app.ui.admin.AdminSettingsEntry.Trash ->
                                        MoreSubscreen.AdminSettingsTrash
                                    com.photonne.app.ui.admin.AdminSettingsEntry.UserDefaults ->
                                        MoreSubscreen.AdminSettingsUserDefaults
                                    com.photonne.app.ui.admin.AdminSettingsEntry.VersionCheck ->
                                        MoreSubscreen.AdminSettingsVersion
                                }
                            }
                        )
                    MoreSubscreen.AdminSettingsFaceRecognition -> {
                        val vm: com.photonne.app.ui.admin.AdminFaceRecognitionSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminFaceRecognitionSettingsScreen(
                            title = stringResource(Res.string.admin_settings_face_recognition),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsObjectDetection -> {
                        val vm: com.photonne.app.ui.admin.AdminObjectDetectionSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminObjectDetectionSettingsScreen(
                            title = stringResource(Res.string.admin_settings_object_detection),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsSceneClassification -> {
                        val vm: com.photonne.app.ui.admin.AdminSceneClassificationSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminSceneClassificationSettingsScreen(
                            title = stringResource(Res.string.admin_settings_scene_classification),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsTextRecognition -> {
                        val vm: com.photonne.app.ui.admin.AdminTextRecognitionSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminTextRecognitionSettingsScreen(
                            title = stringResource(Res.string.admin_settings_text_recognition),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsImageEmbedding -> {
                        val vm: com.photonne.app.ui.admin.AdminImageEmbeddingSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminImageEmbeddingSettingsScreen(
                            title = stringResource(Res.string.admin_settings_image_embedding),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsImage ->
                        com.photonne.app.ui.admin.AdminImageSettingsScreen(
                            title = stringResource(Res.string.admin_settings_image),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminImageSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsMetadata ->
                        com.photonne.app.ui.admin.AdminMetadataSettingsScreen(
                            title = stringResource(Res.string.admin_settings_metadata),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminMetadataSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsNightly ->
                        com.photonne.app.ui.admin.AdminNightlySettingsScreen(
                            title = stringResource(Res.string.admin_settings_nightly),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminNightlySettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsNotifications ->
                        com.photonne.app.ui.admin.AdminNotificationSettingsScreen(
                            title = stringResource(Res.string.admin_settings_notifications),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminNotificationSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsServer ->
                        com.photonne.app.ui.admin.AdminServerSettingsScreen(
                            title = stringResource(Res.string.admin_settings_server),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminServerSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsTrash ->
                        com.photonne.app.ui.admin.AdminTrashSettingsScreen(
                            title = stringResource(Res.string.admin_settings_trash),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminTrashSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsUserDefaults ->
                        com.photonne.app.ui.admin.AdminUserDefaultsScreen(
                            title = stringResource(Res.string.admin_settings_user_defaults),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminUserDefaultsViewModel
                        )
                    MoreSubscreen.AdminSettingsVersion ->
                        com.photonne.app.ui.admin.AdminServerScreen(
                            title = stringResource(Res.string.admin_settings_version),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminVersionViewModel
                        )
                    MoreSubscreen.AdminSystemHub ->
                        com.photonne.app.ui.admin.AdminSystemHubScreen(
                            title = stringResource(Res.string.admin_section_system),
                            onBack = { moreSubscreen = MoreSubscreen.Administration },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            onOpen = { entry ->
                                moreSubscreen = when (entry) {
                                    com.photonne.app.ui.admin.AdminSystemEntry.RunTasks ->
                                        MoreSubscreen.AdminSystemRunTasks
                                    com.photonne.app.ui.admin.AdminSystemEntry.EnrichmentFailures -> {
                                        adminEnrichmentInitialType = null
                                        adminEnrichmentReturnTo = MoreSubscreen.AdminSystemHub
                                        MoreSubscreen.AdminSystemEnrichmentFailures
                                    }
                                    com.photonne.app.ui.admin.AdminSystemEntry.Backup ->
                                        MoreSubscreen.AdminSystemBackup
                                }
                            }
                        )
                    MoreSubscreen.AdminSystemRunTasks -> {
                        val vm: com.photonne.app.ui.admin.AdminRunTasksViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminRunTasksScreen(
                            title = stringResource(Res.string.admin_system_run_tasks),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSystemHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = vm,
                            // Only Duplicates still drills into its own
                            // screen; pipeline + AI rows handle their
                            // entire UX inline on the hub. Other taps are
                            // silently ignored because the hub doesn't
                            // currently expose any `onOpen` for them.
                            onOpenTask = { task ->
                                if (task == com.photonne.app.ui.admin.AdminRunTask.DetectDuplicates) {
                                    moreSubscreen = MoreSubscreen.AdminSystemDuplicates
                                }
                            },
                            // A backfill skips assets that used up their
                            // retries, so a row whose queue is full of them has
                            // no button left to press. The registry is the only
                            // place they can be retried or suppressed.
                            onOpenFailures = { type ->
                                adminEnrichmentInitialType = type
                                adminEnrichmentReturnTo = MoreSubscreen.AdminSystemRunTasks
                                moreSubscreen = MoreSubscreen.AdminSystemEnrichmentFailures
                            },
                        )
                    }
                    MoreSubscreen.AdminSystemDuplicates ->
                        com.photonne.app.ui.admin.AdminDuplicatesScreen(
                            title = stringResource(Res.string.admin_system_duplicates),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSystemRunTasks },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminDuplicatesViewModel
                        )
                    MoreSubscreen.AdminSystemEnrichmentFailures -> {
                        val vm: com.photonne.app.ui.admin.AdminEnrichmentFailuresViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminEnrichmentFailuresScreen(
                            title = stringResource(Res.string.admin_system_enrichment_failures),
                            initialType = adminEnrichmentInitialType,
                            onBack = { moreSubscreen = adminEnrichmentReturnTo },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenAsset = { failure ->
                                assetDetail = AssetDetailContext(
                                    items = listOf(failure.toSyntheticTimelineItem()),
                                    startIndex = 0,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { id, isFav ->
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        )
                    }
                    MoreSubscreen.AdminSystemBackup ->
                        com.photonne.app.ui.admin.AdminBackupScreen(
                            title = stringResource(Res.string.admin_system_backup),
                            onBack = { moreSubscreen = MoreSubscreen.AdminSystemHub },
                            onChromeVisibleChange = { subscreenChromeVisible = it },
                            viewModel = adminBackupViewModel
                        )
                }
            }
            }
                }
            }
            }
        }

        // Above the tabs but below the viewer, so tapping a photo covers the
        // memory rather than replacing it — back then lands on the grid again.
        memoryDetail?.let { memory ->
            val memorySelection = memorySelectionState.selection
            com.photonne.app.ui.memories.MemoryDetailScreen(
                memory = memory,
                baseUrl = apiBaseUrl,
                onItemClick = { index ->
                    if (memorySelectionState.isSelectionActive) {
                        memory.items.getOrNull(index)?.let {
                            memorySelectionViewModel.toggleSelection(it.id)
                        }
                    } else {
                        assetDetail = AssetDetailContext(
                            items = memory.items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = timelineViewModel::setFavorite
                        )
                    }
                },
                onBack = { memoryDetail = null },
                selection = memorySelection,
                onItemLongClick = { index ->
                    memory.items.getOrNull(index)?.let {
                        memorySelectionViewModel.toggleSelection(it.id)
                    }
                },
                onApplySelection = memorySelectionViewModel::applySelection,
                selectionTopBar = {
                    AssetSelectionTopBar(
                        selectedCount = memorySelection.size,
                        totalCount = memory.items.size,
                        isMutating = memorySelectionState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onClose = memorySelectionViewModel::clearSelection,
                        onSelectAll = {
                            memorySelectionViewModel.toggleSelectAll(memory.items.map { it.id })
                        }
                    )
                },
                selectionBottomBar = {
                    // Un recuerdo mezcla fotos de cualquier sitio (también de
                    // álbumes compartidos): lo ajeno no se puede mandar a la
                    // papelera ni mover, igual que en un álbum.
                    val untrashable = com.photonne.app.ui.actions.countUntrashable(
                        memory.items, memorySelection, user.user.username
                    )
                    val memoryTrashBlocked = if (untrashable > 0) pluralStringResource(
                        Res.plurals.selection_trash_blocked_foreign, untrashable, untrashable
                    ) else null
                    AssetSelectionBottomBar(
                        selectedCount = memorySelection.size,
                        trashDisabledReason = memoryTrashBlocked,
                        isMutating = memorySelectionState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onShare = { actionsViewModel.beginShare(memorySelection.toList()) },
                        onAddToAlbum = { bulkAddSource = BulkAddSource.Memory },
                        onDownload = { actionsViewModel.download(memorySelection.toList()) },
                        onArchive = memorySelectionViewModel::bulkArchive,
                        onTrash = memorySelectionViewModel::bulkTrash,
                        selectedIds = { memorySelection.toList() },
                        onUndo = { kind, ids -> actionsViewModel.undoBulk(kind, ids) },
                        allFavorite = selectionAllFavorite(memory.items, memorySelection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    memory.items, memorySelection, favoriteSnackbar,
                                    memorySelectionViewModel::clearSelection
                                )
                            }
                        },
                        onMove = run {
                            val unmovable = com.photonne.app.ui.actions.countUnmovable(
                                memory.items, memorySelection
                            )
                            val moveBlocked = if (unmovable > 0) pluralStringResource(
                                Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                            ) else null
                            val moveSnackbar = LocalSnackbarController.current
                            {
                                if (moveBlocked != null) {
                                    moveSnackbar?.show(moveBlocked)
                                } else {
                                    moveSelectionError = null
                                    moveSelectionRequest = MoveSelectionRequest(
                                        assetIds = memorySelection.toList(),
                                        onMoved = memorySelectionViewModel::clearSelection
                                    )
                                }
                            }
                        },
                    )
                }
            )
        }

        val ctx = assetDetail
        val isDetailVisible = ctx != null && ctx.startIndex in ctx.items.indices
        // Keep the last visible context alive during AnimatedVisibility's
        // exit animation so the shared-element morph has data to render
        // after `assetDetail` has been cleared by the back tap. Critically
        // we read the LIVE `ctx` first and only fall back to the
        // remembered value when `ctx` is null — otherwise on re-open the
        // AnimatedVisibility content composes with last cycle's data
        // (the LaunchedEffect updates `rememberedCtx` one frame later)
        // and the inner pagerState locks onto the old startIndex.
        val rememberedCtx = remember { mutableStateOf<AssetDetailContext?>(null) }
        LaunchedEffect(ctx) {
            if (ctx != null) rememberedCtx.value = ctx
        }
        val displayCtx = ctx ?: rememberedCtx.value
        // Device-trash for LOCAL entries: the platform shows its own consent
        // dialog; on success the library refreshes immediately (ahead of the
        // change observer) and the viewer closes, mirroring the server-trash
        // flow above.
        val deviceLibraryStore: com.photonne.app.data.devicelibrary.DeviceLibraryStore =
            koinInject()
        val deviceMediaTrasher =
            com.photonne.app.data.devicelibrary.rememberDeviceMediaTrasher { trashed ->
                if (trashed) {
                    deviceLibraryStore.requestRefresh()
                    assetDetail = null
                }
            }
        AnimatedVisibility(
            visible = isDetailVisible,
            // Match the shared-element morph duration so AnimatedVisibility
            // keeps the detail composed until the morph finishes — otherwise
            // the photo snaps the last few pixels when the content unmounts
            // mid-spring.
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = com.photonne.app.ui.theme.MotionDurations.OVERLAY_MS)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = com.photonne.app.ui.theme.MotionDurations.OVERLAY_MS)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (displayCtx != null && displayCtx.startIndex in displayCtx.items.indices) {
                // Force a fresh AssetDetailScreen — and therefore a fresh
                // pagerState seeded at the new startIndex — whenever the
                // user opens a different asset.
                key(displayCtx) {
                    // Con fuente viva, lo nuevo se AÑADE al final de la lista
                    // con la que se abrió: nada se reordena ni desaparece bajo
                    // el dedo, y el índice actual no se mueve.
                    val feed = displayCtx.feed
                    val liveItems = feed?.items?.invoke()
                    val viewerItems = remember(displayCtx.items, liveItems) {
                        if (liveItems == null) {
                            displayCtx.items
                        } else {
                            val known = displayCtx.items.mapTo(HashSet()) { it.id }
                            val extra = liveItems.filterNot { it.id in known }
                            if (extra.isEmpty()) displayCtx.items else displayCtx.items + extra
                        }
                    }
                    AssetDetailScreen(
                        items = viewerItems,
                        startIndex = displayCtx.startIndex,
                        hasMore = feed?.hasMore?.invoke() ?: displayCtx.hasMore,
                        onLoadMore = feed?.loadMore ?: displayCtx.onLoadMore,
                        onBack = { closeAssetDetail() },
                        onPageChanged = { id -> currentDetailAssetId = id },
                        animatedVisibilityScope = this@AnimatedVisibility,
                        onFavoriteChanged = displayCtx.onFavoriteChanged,
                        onAddToAlbum = { item -> addToAlbum = AddToAlbumState(asset = item) },
                        mode = when (displayCtx.source) {
                            AssetDetailContext.Source.Archive ->
                                com.photonne.app.ui.asset.AssetViewerMode.Archive
                            AssetDetailContext.Source.Trash ->
                                com.photonne.app.ui.asset.AssetViewerMode.Trash
                            else -> com.photonne.app.ui.asset.AssetViewerMode.Default
                        },
                        // Desarchivar / restaurar / borrar para siempre: la foto
                        // sale de Archivo o Papelera por el AssetMutationBus; aquí
                        // se cierra el visor y se confirma, como con archivar.
                        onAssetUnarchived = { id ->
                            assetDetailStack = emptyList()
                            assetDetail = null
                            coroutineScope.launch {
                                snackbarController.show(
                                    message = org.jetbrains.compose.resources.getPluralString(
                                        Res.plurals.selection_unarchive_done, 1, 1
                                    ),
                                    actionLabel = org.jetbrains.compose.resources.getString(
                                        Res.string.action_undo
                                    )
                                ) {
                                    actionsViewModel.undoBulk(
                                        com.photonne.app.ui.actions.BulkUndoKind.Unarchive,
                                        listOf(id)
                                    )
                                }
                            }
                        },
                        onAssetRestored = { _ ->
                            assetDetailStack = emptyList()
                            assetDetail = null
                            coroutineScope.launch {
                                snackbarController.show(
                                    org.jetbrains.compose.resources.getPluralString(
                                        Res.plurals.selection_restore_done, 1, 1
                                    )
                                )
                            }
                        },
                        onAssetPurged = { _ ->
                            assetDetailStack = emptyList()
                            assetDetail = null
                            coroutineScope.launch {
                                snackbarController.show(
                                    org.jetbrains.compose.resources.getPluralString(
                                        Res.plurals.selection_deleted_permanently_done, 1, 1
                                    )
                                )
                            }
                        },
                        onAssetTrashed = { id ->
                            // Las listas se enteran por AssetMutationBus (punto 52):
                            // aquí solo queda cerrar el visor y ofrecer Deshacer.
                            // Cierre TOTAL (sin volver a un contexto apilado que
                            // podría contener la foto recién borrada).
                            assetDetailStack = emptyList()
                            assetDetail = null
                            // El visor se cerraba en silencio: confirmación con
                            // Deshacer, como las acciones en bloque. Con la
                            // papelera del servidor apagada el borrado es
                            // definitivo: mensaje sin Deshacer.
                            if (!serverTrashPolicy.enabled.value) {
                                coroutineScope.launch {
                                    snackbarController.show(
                                        org.jetbrains.compose.resources.getPluralString(
                                            Res.plurals.selection_deleted_permanently_done, 1, 1
                                        )
                                    )
                                }
                                return@AssetDetailScreen
                            }
                            coroutineScope.launch {
                                snackbarController.show(
                                    message = org.jetbrains.compose.resources.getPluralString(
                                        Res.plurals.selection_trash_done, 1, 1
                                    ),
                                    actionLabel = org.jetbrains.compose.resources.getString(
                                        Res.string.action_undo
                                    )
                                ) {
                                    actionsViewModel.undoBulk(
                                        com.photonne.app.ui.actions.BulkUndoKind.Trash,
                                        listOf(id)
                                    )
                                }
                            }
                        },
                        onAssetArchived = { id ->
                            assetDetailStack = emptyList()
                            assetDetail = null
                            coroutineScope.launch {
                                snackbarController.show(
                                    message = org.jetbrains.compose.resources.getPluralString(
                                        Res.plurals.selection_archive_done, 1, 1
                                    ),
                                    actionLabel = org.jetbrains.compose.resources.getString(
                                        Res.string.action_undo
                                    )
                                ) {
                                    actionsViewModel.undoBulk(
                                        com.photonne.app.ui.actions.BulkUndoKind.Archive,
                                        listOf(id)
                                    )
                                }
                            }
                        },
                        onOpenFaces = { assetId ->
                            assetFacesViewModel.open(assetId)
                            showAssetFacesSheet = true
                        },
                        onOpenPerson = { personId -> openPersonFromViewer(personId) },
                        onSearchScene = { label ->
                            openSearchFromViewer { searchViewModel.showResultsForSceneLabel(label) }
                        },
                        onSearchObject = { label ->
                            openSearchFromViewer { searchViewModel.showResultsForObjectLabel(label) }
                        },
                        facesRevision = assetFacesRevision,
                        onShare = { item -> actionsViewModel.shareDirectly(listOf(item.id)) },
                        onDownload = { item -> actionsViewModel.download(listOf(item.id)) },
                        onDeleteFromDevice = { item ->
                            item.localUri?.let { deviceMediaTrasher(listOf(it)) }
                        },
                        onOpenAsset = { item ->
                            // Open a related asset as its own single-item viewer;
                            // key(displayCtx) forces a fresh screen + detail load.
                            // El contexto actual se apila: atrás vuelve a la foto
                            // y a la lista de las que se venía.
                            assetDetailStack = assetDetailStack + displayCtx
                            assetDetail = AssetDetailContext(
                                items = listOf(item),
                                startIndex = 0,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = { id, isFav ->
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
    }
    }

    if (showJumpToDate) {
        com.photonne.app.ui.timeline.JumpToDateDialog(
            onDismiss = { showJumpToDate = false },
            onConfirm = { date ->
                showJumpToDate = false
                pendingJumpDate = date
            }
        )
    }

    if (showAlbumTypeChooser) {
        com.photonne.app.ui.album.smart.AlbumTypeChooserSheet(
            onDismiss = { showAlbumTypeChooser = false },
            onManual = {
                showAlbumTypeChooser = false
                showCreateAlbum = true
            },
            onSmart = {
                showAlbumTypeChooser = false
                moreSubscreen = MoreSubscreen.SmartAlbumEditor
            }
        )
    }

    if (showCreateAlbum) {
        val addTarget = pendingAddTarget
        val mapBulkState = mapViewModel.state.collectAsStateWithLifecycle().value
        // Estado del alta pendiente según el origen: el error se enseña en la
        // propia hoja del álbum, no en el banner de la pantalla de origen.
        val (addSubmitting, addError) = when (addTarget) {
            null -> false to null
            is PendingAddTarget.Asset -> pendingAssetAddSubmitting to pendingAssetAddError
            is PendingAddTarget.Selection -> when (addTarget.source) {
                null -> timelineState.isBulkMutating to timelineState.error?.userMessage
                BulkAddSource.Search -> searchState.isBulkMutating to searchState.error?.userMessage
                BulkAddSource.Map -> mapBulkState.isBulkMutating to mapBulkState.error?.userMessage
                BulkAddSource.Favorites -> favoritesState.isBulkMutating to favoritesState.error?.userMessage
                BulkAddSource.People -> personDetailState.isBulkMutating to personDetailState.error?.userMessage
                BulkAddSource.Folder -> folderDetailState.isBulkMutating to folderDetailState.error?.userMessage
                BulkAddSource.Archive -> archivedState.isBulkMutating to archivedState.error?.userMessage
                BulkAddSource.Album -> albumDetailState.isBulkMutating to albumDetailState.error?.userMessage
                BulkAddSource.Inbox -> organizeInboxState.isBulkMutating to organizeInboxState.error?.userMessage
                BulkAddSource.Upload -> uploadState.isBulkMutating to uploadState.error?.userMessage
                BulkAddSource.Memory -> memorySelectionState.isBulkMutating to memorySelectionState.error?.userMessage
            }
        }
        fun clearPendingAddError(target: PendingAddTarget?) {
            when (target) {
                null -> Unit
                is PendingAddTarget.Asset -> pendingAssetAddError = null
                is PendingAddTarget.Selection -> when (target.source) {
                    null -> timelineViewModel.clearError()
                    BulkAddSource.Search -> searchViewModel.clearError()
                    BulkAddSource.Map -> mapViewModel.clearError()
                    BulkAddSource.Favorites -> favoritesViewModel.clearError()
                    BulkAddSource.People -> personDetailViewModel.clearError()
                    BulkAddSource.Folder -> folderDetailViewModel.clearError()
                    BulkAddSource.Archive -> archivedViewModel.clearError()
                    BulkAddSource.Album -> albumDetailViewModel.clearError()
                    BulkAddSource.Inbox -> organizeInboxViewModel.clearError()
                    BulkAddSource.Upload -> uploadViewModel.clearError()
                    BulkAddSource.Memory -> memorySelectionViewModel.clearError()
                }
            }
        }
        fun finishCreateFlow(album: AlbumSummary, addedCount: Int?) {
            val target = pendingAddTarget
            showCreateAlbum = false
            pendingAddTarget = null
            pendingAddAlbum = null
            if (target == null || target == PendingAddTarget.Selection(null)) {
                // Timeline (y creación directa): se abre el álbum recién creado.
                selectedTab = MainTab.Albums
                selectedAlbum = album
            } else if (addedCount != null) {
                // Desde otras pantallas o el visor se queda donde estaba.
                showAddedToAlbumSnackbar(addedCount, album.name)
            }
        }
        fun addPendingTo(album: AlbumSummary) {
            val onAdded: (List<TimelineItem>) -> Unit = { added ->
                albumsViewModel.applyAssetsAdded(album.id, added.size)
                finishCreateFlow(album, added.size)
            }
            when (val target = pendingAddTarget) {
                null -> finishCreateFlow(album, null)
                is PendingAddTarget.Asset -> {
                    pendingAssetAddSubmitting = true
                    pendingAssetAddError = null
                    coroutineScope.launch {
                        runCatching { albumsRepository.addAsset(album.id, target.item.id) }
                            .onSuccess {
                                pendingAssetAddSubmitting = false
                                albumsViewModel.applyAssetAdded(album.id)
                                finishCreateFlow(album, 1)
                            }
                            .onFailure { error ->
                                pendingAssetAddSubmitting = false
                                pendingAssetAddError = error.message ?: "No se pudo añadir al álbum"
                            }
                    }
                }
                is PendingAddTarget.Selection -> when (target.source) {
                    null -> timelineViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Search -> searchViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Map -> mapViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Favorites -> favoritesViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.People -> personDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Folder -> folderDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Archive -> archivedViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Album -> albumDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Inbox -> organizeInboxViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Upload -> uploadViewModel.addBatchToAlbum(album.id, onAdded)
                    BulkAddSource.Memory -> memorySelectionViewModel.bulkAddToAlbum(
                        album.id, memoryDetail?.items.orEmpty(), onAdded
                    )
                }
            }
        }
        LaunchedEffect(Unit) { clearPendingAddError(pendingAddTarget) }
        val createdAlbum = pendingAddAlbum
        AlbumFormDialog(
            title = stringResource(Res.string.album_action_new),
            // Álbum ya creado y alta fallida: el botón solo reintenta el alta.
            confirmLabel = stringResource(
                if (createdAlbum != null) Res.string.action_retry else Res.string.action_create
            ),
            initialName = createdAlbum?.name.orEmpty(),
            initialDescription = createdAlbum?.description,
            isSubmitting = albumsState.isMutating || addSubmitting,
            errorMessage = albumsState.error?.userMessage ?: addError,
            onDismiss = {
                clearPendingAddError(pendingAddTarget)
                showCreateAlbum = false
                pendingAddTarget = null
                pendingAddAlbum = null
                albumsViewModel.clearError()
            },
            onConfirm = { name, description ->
                if (createdAlbum != null) {
                    addPendingTo(createdAlbum)
                } else {
                    albumsViewModel.create(name, description) { newAlbum ->
                        if (pendingAddTarget != null) pendingAddAlbum = newAlbum
                        addPendingTo(newAlbum)
                    }
                }
            }
        )
    }

    val openedAlbum = selectedAlbum
    if (showEditAlbum && openedAlbum != null) {
        AlbumFormDialog(
            title = stringResource(Res.string.album_action_edit),
            confirmLabel = stringResource(Res.string.action_save),
            initialName = albumDetailState.albumName ?: openedAlbum.name,
            initialDescription = albumDetailState.albumDescription ?: openedAlbum.description,
            isSubmitting = albumDetailState.isMutating,
            errorMessage = albumDetailState.error?.userMessage,
            onDismiss = {
                showEditAlbum = false
                albumDetailViewModel.clearError()
            },
            onConfirm = { name, description ->
                albumDetailViewModel.rename(name, description) { updated ->
                    showEditAlbum = false
                    selectedAlbum = openedAlbum.copy(name = updated.name, description = updated.description)
                    albumsViewModel.applyUpdate(updated)
                }
            }
        )
    } else if (showEditAlbum && pendingActionAlbum != null) {
        val target = pendingActionAlbum!!
        AlbumFormDialog(
            title = stringResource(Res.string.album_action_edit),
            confirmLabel = stringResource(Res.string.action_save),
            initialName = target.name,
            initialDescription = target.description,
            isSubmitting = albumsState.isMutating,
            errorMessage = albumsState.error?.userMessage,
            onDismiss = {
                showEditAlbum = false
                pendingActionAlbum = null
                albumsViewModel.clearError()
            },
            onConfirm = { name, description ->
                albumsViewModel.renameAlbum(target.id, name, description) {
                    showEditAlbum = false
                    pendingActionAlbum = null
                }
            }
        )
    }

    if (showDeleteAlbum && openedAlbum != null) {
        DeleteAlbumDialog(
            albumName = albumDetailState.albumName ?: openedAlbum.name,
            isSubmitting = albumDetailState.isMutating,
            errorMessage = albumDetailState.error?.userMessage,
            onDismiss = {
                showDeleteAlbum = false
                albumDetailViewModel.clearError()
            },
            onConfirm = {
                albumDetailViewModel.delete { albumId ->
                    showDeleteAlbum = false
                    albumsViewModel.applyDelete(albumId)
                    selectedAlbum = null
                }
            }
        )
    } else if (showDeleteAlbum && pendingActionAlbum != null) {
        val target = pendingActionAlbum!!
        DeleteAlbumDialog(
            albumName = target.name,
            isSubmitting = albumsState.isMutating,
            errorMessage = albumsState.error?.userMessage,
            onDismiss = {
                showDeleteAlbum = false
                pendingActionAlbum = null
                albumsViewModel.clearError()
            },
            onConfirm = {
                albumsViewModel.deleteAlbum(target.id) {
                    showDeleteAlbum = false
                    pendingActionAlbum = null
                }
            }
        )
    }

    if (showLeaveAlbum && openedAlbum != null) {
        LeaveAlbumDialog(
            albumName = albumDetailState.albumName ?: openedAlbum.name,
            isSubmitting = albumDetailState.isMutating,
            errorMessage = albumDetailState.error?.userMessage,
            onDismiss = {
                showLeaveAlbum = false
                albumDetailViewModel.clearError()
            },
            onConfirm = {
                albumDetailViewModel.leave { albumId ->
                    showLeaveAlbum = false
                    albumsViewModel.applyDelete(albumId)
                    selectedAlbum = null
                }
            }
        )
    } else if (showLeaveAlbum && pendingActionAlbum != null) {
        val target = pendingActionAlbum!!
        LeaveAlbumDialog(
            albumName = target.name,
            isSubmitting = albumsState.isMutating,
            errorMessage = albumsState.error?.userMessage,
            onDismiss = {
                showLeaveAlbum = false
                pendingActionAlbum = null
                albumsViewModel.clearError()
            },
            onConfirm = {
                albumsViewModel.leaveAlbum(target.id) {
                    showLeaveAlbum = false
                    pendingActionAlbum = null
                }
            }
        )
    }

    if (showShares && openedAlbum != null) {
        ManageSharesDialog(
            state = albumSharesState,
            onDismiss = {
                showShares = false
                albumSharesViewModel.clearError()
            },
            onCreate = { showCreateShare = true },
            onEdit = { link -> editingShareLink = link },
            // Revocar mata el enlace para todo el mundo: confirma, como en
            // "Mis enlaces".
            onRevoke = { token -> revokingShareToken = token }
        )
    }

    revokingShareToken?.let { token ->
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.share_revoke_confirm_title),
            message = stringResource(Res.string.share_revoke_confirm_message),
            confirmLabel = stringResource(Res.string.share_action_revoke),
            isDestructive = true,
            isSubmitting = albumSharesState.isMutating,
            errorMessage = albumSharesState.error?.userMessage,
            onDismiss = { revokingShareToken = null },
            onConfirm = {
                albumSharesViewModel.revoke(token) { revokingShareToken = null }
            }
        )
    }

    revokingAlbumMember?.let { member ->
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.member_remove_confirm_title),
            message = stringResource(
                Res.string.member_remove_confirm_message, member.username
            ),
            confirmLabel = stringResource(Res.string.member_remove_confirm_action),
            isDestructive = true,
            isSubmitting = albumPermissionsState.isMutating,
            errorMessage = albumPermissionsState.error?.userMessage,
            onDismiss = { revokingAlbumMember = null },
            onConfirm = {
                albumPermissionsViewModel.revoke(member) { newCount ->
                    revokingAlbumMember = null
                    selectedAlbum?.let { album ->
                        val updated = album.copy(
                            isShared = newCount > 0,
                            sharedWithCount = newCount
                        )
                        selectedAlbum = updated
                        albumsViewModel.applyUpdate(updated)
                    }
                    pendingActionAlbum?.let { album ->
                        val updated = album.copy(
                            isShared = newCount > 0,
                            sharedWithCount = newCount
                        )
                        pendingActionAlbum = updated
                        albumsViewModel.applyUpdate(updated)
                    }
                }
            }
        )
    }

    revokingFolderMember?.let { member ->
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.member_remove_confirm_title),
            message = stringResource(
                Res.string.member_remove_confirm_message, member.username
            ),
            confirmLabel = stringResource(Res.string.member_remove_confirm_action),
            isDestructive = true,
            isSubmitting = folderPermissionsState.isMutating,
            errorMessage = folderPermissionsState.error?.userMessage,
            onDismiss = { revokingFolderMember = null },
            onConfirm = {
                folderPermissionsViewModel.revoke(member) { newCount ->
                    revokingFolderMember = null
                    selectedFolder?.let { folder ->
                        val updated = folder.copy(
                            isShared = newCount > 0,
                            sharedWithCount = newCount
                        )
                        selectedFolder = updated
                        foldersViewModel.applyUpdate(updated)
                    }
                    pendingActionFolder?.let { folder ->
                        val updated = folder.copy(
                            isShared = newCount > 0,
                            sharedWithCount = newCount
                        )
                        pendingActionFolder = updated
                        foldersViewModel.applyUpdate(updated)
                    }
                }
            }
        )
    }

    if (showMembers && (openedAlbum != null || pendingActionAlbum != null)) {
        ManagePermissionsDialog(
            state = albumPermissionsState,
            onDismiss = {
                showMembers = false
                if (openedAlbum == null) pendingActionAlbum = null
                albumPermissionsViewModel.clearError()
            },
            onInvite = { showInviteMember = true },
            onChangeRole = { member, role -> albumPermissionsViewModel.changeRole(member, role) },
            onRevoke = { member -> revokingAlbumMember = member }
        )
    }

    if (showInviteMember && (openedAlbum != null || pendingActionAlbum != null)) {
        InviteMemberDialog(
            candidates = albumPermissionsState.invitableUsers,
            isSubmitting = albumPermissionsState.isMutating,
            errorMessage = albumPermissionsState.error?.userMessage,
            onDismiss = {
                showInviteMember = false
                albumPermissionsViewModel.clearError()
            },
            onInvite = { selectedUser, role ->
                // Abierto hasta el resultado: si la invitación falla, el error
                // se ve aquí mismo; antes el diálogo ya se había cerrado.
                albumPermissionsViewModel.grant(
                    user = selectedUser,
                    role = role,
                    onMembershipChanged = { newCount ->
                        selectedAlbum?.let { album ->
                            val updated = album.copy(
                                isShared = true,
                                sharedWithCount = newCount
                            )
                            selectedAlbum = updated
                            albumsViewModel.applyUpdate(updated)
                        }
                        pendingActionAlbum?.let { album ->
                            val updated = album.copy(
                                isShared = true,
                                sharedWithCount = newCount
                            )
                            pendingActionAlbum = updated
                            albumsViewModel.applyUpdate(updated)
                        }
                    },
                    onSuccess = { showInviteMember = false }
                )
            }
        )
    }

    if (showCreateShare && openedAlbum != null) {
        CreateShareDialog(
            isSubmitting = albumSharesState.isMutating,
            errorMessage = albumSharesState.error?.userMessage,
            onDismiss = {
                showCreateShare = false
                albumSharesViewModel.clearError()
            },
            onConfirm = { expiresAt, password, allowDownload, maxViews, allowUpload ->
                albumSharesViewModel.createLink(
                    expiresAt = expiresAt,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews,
                    allowUpload = allowUpload
                ) {
                    showCreateShare = false
                }
            }
        )
    }

    editingShareLink?.let { link ->
        EditShareDialog(
            link = link,
            isSubmitting = albumSharesState.isMutating,
            errorMessage = albumSharesState.error?.userMessage,
            onDismiss = {
                editingShareLink = null
                albumSharesViewModel.clearError()
            },
            onConfirm = { expiresAt, password, allowDownload, maxViews, allowUpload ->
                albumSharesViewModel.editLink(
                    token = link.token,
                    expiresAt = expiresAt,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews,
                    allowUpload = allowUpload
                ) {
                    editingShareLink = null
                }
            }
        )
    }

    if (bulkAddToAlbum) {
        // Un error viejo de otra acción no debe estrenar el diálogo.
        LaunchedEffect(Unit) { timelineViewModel.clearError() }
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = timelineState.isBulkMutating,
            errorMessage = timelineState.error?.userMessage,
            onCreateNew = {
                bulkAddToAlbum = false
                pendingAddTarget = PendingAddTarget.Selection(null)
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                // El diálogo se queda abierto hasta el resultado: si falla,
                // enseña el error y permite reintentar; antes se cerraba al
                // instante y el fallo no se veía en ninguna parte.
                timelineViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                    bulkAddToAlbum = false
                    showAddedToAlbumSnackbar(added.size, album.name)
                }
            },
            onDismiss = { bulkAddToAlbum = false }
        )
    }

    if (showCreateFolder) {
        // When a folder is open, create inside it (a subfolder); shared-space
        // is a root-level concept only, so the option is hidden there.
        val createParent = selectedFolder
        FolderFormDialog(
            title = stringResource(Res.string.folder_action_new),
            confirmLabel = stringResource(Res.string.action_create),
            isSubmitting = foldersState.isMutating,
            errorMessage = foldersState.error?.userMessage,
            showSharedSpaceOption = createParent == null,
            onDismiss = {
                showCreateFolder = false
                foldersViewModel.clearError()
            },
            onConfirm = { name, isSharedSpace ->
                foldersViewModel.create(
                    name = name,
                    parentFolderId = createParent?.id,
                    isSharedSpace = isSharedSpace
                ) {
                    showCreateFolder = false
                    if (createParent != null) folderDetailViewModel.refresh()
                }
            }
        )
    }

    val openedFolder = selectedFolder
    if (showEditFolder && openedFolder != null) {
        FolderFormDialog(
            title = stringResource(Res.string.folder_action_edit),
            confirmLabel = stringResource(Res.string.action_save),
            initialName = folderDetailState.folderName ?: openedFolder.name,
            isSubmitting = folderDetailState.isMutating,
            errorMessage = folderDetailState.error?.userMessage,
            onDismiss = {
                showEditFolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { name, _ ->
                folderDetailViewModel.rename(name) { updated ->
                    showEditFolder = false
                    selectedFolder = openedFolder.copy(name = updated.name, path = updated.path)
                    foldersViewModel.applyUpdate(updated)
                }
            }
        )
    } else if (showEditFolder && pendingActionFolder != null) {
        val target = pendingActionFolder!!
        FolderFormDialog(
            title = stringResource(Res.string.folder_action_edit),
            confirmLabel = stringResource(Res.string.action_save),
            initialName = target.name,
            isSubmitting = foldersState.isMutating,
            errorMessage = foldersState.error?.userMessage,
            onDismiss = {
                showEditFolder = false
                pendingActionFolder = null
                foldersViewModel.clearError()
            },
            onConfirm = { name, _ ->
                foldersViewModel.renameFolder(target.id, name) {
                    showEditFolder = false
                    pendingActionFolder = null
                }
            }
        )
    }

    if (showDeleteFolder && openedFolder != null) {
        DeleteFolderDialog(
            folderName = folderDetailState.folderName ?: openedFolder.name.ifBlank { openedFolder.path },
            isSubmitting = folderDetailState.isMutating,
            itemCount = openedFolder.assetCount,
            errorMessage = folderDetailState.error?.userMessage,
            onDismiss = {
                showDeleteFolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = {
                folderDetailViewModel.delete { folderId ->
                    showDeleteFolder = false
                    foldersViewModel.applyDelete(folderId)
                    folderBack()
                }
            }
        )
    } else if (showDeleteFolder && pendingActionFolder != null) {
        val target = pendingActionFolder!!
        DeleteFolderDialog(
            folderName = target.name.ifBlank { target.path },
            isSubmitting = foldersState.isMutating,
            itemCount = target.assetCount,
            errorMessage = foldersState.error?.userMessage,
            onDismiss = {
                showDeleteFolder = false
                pendingActionFolder = null
                foldersViewModel.clearError()
            },
            onConfirm = {
                foldersViewModel.deleteFolder(target.id) {
                    showDeleteFolder = false
                    pendingActionFolder = null
                }
            }
        )
    }

    // Rename/delete for a selected subfolder inside the open folder. These mirror
    // the top-level folder selection actions but target a child of the open
    // folder via FolderDetailViewModel, which patches its subfolder list in place.
    val selectedSubfolder = folderDetailState.selectedSubfolder
    if (showEditSubfolder && selectedSubfolder != null) {
        FolderFormDialog(
            title = stringResource(Res.string.folder_action_edit),
            confirmLabel = stringResource(Res.string.action_save),
            initialName = selectedSubfolder.name.ifBlank { selectedSubfolder.path },
            isSubmitting = folderDetailState.isMutating,
            errorMessage = folderDetailState.error?.userMessage,
            onDismiss = {
                showEditSubfolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { name, _ ->
                folderDetailViewModel.renameSubfolder(selectedSubfolder.id, name) {
                    showEditSubfolder = false
                    foldersViewModel.refresh()
                }
            }
        )
    }

    if (showDeleteSubfolder && selectedSubfolder != null) {
        DeleteFolderDialog(
            folderName = selectedSubfolder.name.ifBlank { selectedSubfolder.path },
            isSubmitting = folderDetailState.isMutating,
            itemCount = selectedSubfolder.assetCount,
            errorMessage = folderDetailState.error?.userMessage,
            onDismiss = {
                showDeleteSubfolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = {
                folderDetailViewModel.deleteSubfolder(selectedSubfolder.id) {
                    showDeleteSubfolder = false
                    foldersViewModel.refresh()
                }
            }
        )
    }

    if (showFolderMembers && (openedFolder != null || pendingActionFolder != null)) {
        ManageFolderPermissionsDialog(
            state = folderPermissionsState,
            onDismiss = {
                showFolderMembers = false
                if (openedFolder == null) pendingActionFolder = null
                folderPermissionsViewModel.clearError()
            },
            onInvite = { showInviteFolderMember = true },
            onChangeRole = { member, role -> folderPermissionsViewModel.changeRole(member, role) },
            onRevoke = { member -> revokingFolderMember = member }
        )
    }

    if (showInviteFolderMember && (openedFolder != null || pendingActionFolder != null)) {
        InviteFolderMemberDialog(
            candidates = folderPermissionsState.invitableUsers,
            isSubmitting = folderPermissionsState.isMutating,
            errorMessage = folderPermissionsState.error?.userMessage,
            onDismiss = {
                showInviteFolderMember = false
                folderPermissionsViewModel.clearError()
            },
            onInvite = { selectedUser, role ->
                // Abierto hasta el resultado, como el invite de álbum.
                folderPermissionsViewModel.grant(
                    user = selectedUser,
                    role = role,
                    onMembershipChanged = { newCount ->
                        selectedFolder?.let { folder ->
                            val updated = folder.copy(
                                isShared = true,
                                sharedWithCount = newCount
                            )
                            selectedFolder = updated
                            foldersViewModel.applyUpdate(updated)
                        }
                        pendingActionFolder?.let { folder ->
                            val updated = folder.copy(
                                isShared = true,
                                sharedWithCount = newCount
                            )
                            pendingActionFolder = updated
                            foldersViewModel.applyUpdate(updated)
                        }
                    },
                    onSuccess = { showInviteFolderMember = false }
                )
            }
        )
    }

    if (showMoveFolder && openedFolder != null) {
        com.photonne.app.ui.folder.FolderPickerDialog(
            title = stringResource(Res.string.folder_move_title),
            // Mismos destinos que los otros selectores (toda la profundidad,
            // personales y compartidas con escritura). Antes solo ofrecía las
            // personales de primer nivel. La propia carpeta y su subárbol los
            // poda excludeFolderId.
            folders = foldersState.moveDestinations,
            isSubmitting = folderDetailState.isMutating,
            errorMessage = folderDetailState.error?.userMessage,
            excludeFolderId = openedFolder.id,
            includeRoot = true,
            // Padre actual preseleccionado; si no es un destino listado (la raíz
            // personal), queda marcada la raíz.
            initialSelectionId = openedFolder.parentFolderId?.takeIf { parentId ->
                foldersState.moveDestinations.any { it.id == parentId }
            },
            onDismiss = {
                showMoveFolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { targetParentId, _ ->
                folderDetailViewModel.move(targetParentId) { updated ->
                    showMoveFolder = false
                    selectedFolder = openedFolder.copy(
                        path = updated.path,
                        parentFolderId = updated.parentFolderId
                    )
                    foldersViewModel.applyUpdate(updated)
                }
            }
        )
    }

    if (showAlbumsFilters) {
        com.photonne.app.ui.album.AlbumsFiltersSheet(
            state = albumsState,
            onDismiss = { showAlbumsFilters = false },
            onScopeChange = albumsViewModel::setScope,
            onSortChange = albumsViewModel::setSort,
            onDirectionChange = albumsViewModel::setDirection,
            onViewModeChange = albumsViewModel::setViewMode,
            onGroupByYearChange = albumsViewModel::setGroupByYear
        )
    }

    if (showFoldersFilters) {
        com.photonne.app.ui.folder.FoldersFiltersSheet(
            state = foldersState,
            onDismiss = { showFoldersFilters = false },
            onScopeChange = foldersViewModel::setScope,
            onSortChange = foldersViewModel::setSort,
            onDirectionChange = foldersViewModel::setDirection,
            onViewModeChange = foldersViewModel::setViewMode
        )
    }

    if (showSearchFilters) {
        com.photonne.app.ui.search.SearchFiltersSheet(
            state = searchState,
            onDismiss = { showSearchFilters = false },
            onDateRangeChange = searchViewModel::setDateRange,
            onOcrChange = searchViewModel::setOcrText,
            onToggleObject = searchViewModel::toggleObjectLabel,
            onToggleScene = searchViewModel::toggleSceneLabel,
            onTogglePerson = searchViewModel::togglePerson,
            onPeopleQueryChange = searchViewModel::setPeopleQuery,
            onClearAll = searchViewModel::clearAll
        )
    }

    // One add-to-album dialog for every screen's selection bar; [bulkAddSource]
    // says whose selection it adds.
    bulkAddSource?.let { source ->
        val (isSubmitting, errorMessage) = when (source) {
            BulkAddSource.Search -> searchState.isBulkMutating to searchState.error?.userMessage
            BulkAddSource.Map -> mapViewModel.state.collectAsStateWithLifecycle().value
                .let { it.isBulkMutating to it.error?.userMessage }
            BulkAddSource.Favorites -> favoritesState.isBulkMutating to favoritesState.error?.userMessage
            BulkAddSource.People -> personDetailState.isBulkMutating to personDetailState.error?.userMessage
            BulkAddSource.Folder -> folderDetailState.isBulkMutating to folderDetailState.error?.userMessage
            BulkAddSource.Archive -> archivedState.isBulkMutating to archivedState.error?.userMessage
            BulkAddSource.Album -> albumDetailState.isBulkMutating to albumDetailState.error?.userMessage
            BulkAddSource.Inbox -> organizeInboxState.isBulkMutating to organizeInboxState.error?.userMessage
            BulkAddSource.Upload -> uploadState.isBulkMutating to uploadState.error?.userMessage
            BulkAddSource.Memory -> memorySelectionState.isBulkMutating to memorySelectionState.error?.userMessage
        }
        LaunchedEffect(source) {
            when (source) {
                BulkAddSource.Search -> searchViewModel.clearError()
                BulkAddSource.Map -> mapViewModel.clearError()
                BulkAddSource.Favorites -> favoritesViewModel.clearError()
                BulkAddSource.People -> personDetailViewModel.clearError()
                BulkAddSource.Folder -> folderDetailViewModel.clearError()
                BulkAddSource.Archive -> archivedViewModel.clearError()
                BulkAddSource.Album -> albumDetailViewModel.clearError()
                BulkAddSource.Inbox -> organizeInboxViewModel.clearError()
                BulkAddSource.Upload -> uploadViewModel.clearError()
                BulkAddSource.Memory -> memorySelectionViewModel.clearError()
            }
        }
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = isSubmitting,
            errorMessage = errorMessage,
            onCreateNew = {
                bulkAddSource = null
                pendingAddTarget = PendingAddTarget.Selection(source)
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                val onAdded: (List<com.photonne.app.data.models.TimelineItem>) -> Unit = { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    // Adding from inside an album never targets that same album's
                    // open list, so only other sources patch the detail view.
                    if (source != BulkAddSource.Album) {
                        albumDetailViewModel.applyAssetsAdded(album.id, added)
                    }
                    bulkAddSource = null
                    showAddedToAlbumSnackbar(added.size, album.name)
                }
                when (source) {
                    BulkAddSource.Search -> searchViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Map -> mapViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Favorites -> favoritesViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.People -> personDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Folder -> folderDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Archive -> archivedViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Album -> albumDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Inbox -> organizeInboxViewModel.bulkAddToAlbum(album.id, onAdded)
                    BulkAddSource.Upload -> uploadViewModel.addBatchToAlbum(album.id, onAdded)
                    BulkAddSource.Memory -> memorySelectionViewModel.bulkAddToAlbum(
                        album.id, memoryDetail?.items.orEmpty(), onAdded
                    )
                }
            },
            onDismiss = { bulkAddSource = null }
        )
    }

    if (showMoveSelectedAssets && openedFolder != null) {
        com.photonne.app.ui.folder.FolderPickerDialog(
            title = stringResource(Res.string.folder_move_assets_title),
            folders = foldersState.moveDestinations,
            isSubmitting = folderDetailState.isBulkMutating,
            errorMessage = folderDetailState.error?.userMessage,
            excludeFolderId = openedFolder.id,
            includeRoot = false,
            recentDestinationIds = recentDestinations,
            onDismiss = {
                showMoveSelectedAssets = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { targetFolderId, _ ->
                if (targetFolderId != null) {
                    recentDestinationsStore.record(targetFolderId)
                    val targetName = foldersState.moveDestinations
                        .firstOrNull { it.id == targetFolderId }?.name
                    folderDetailViewModel.moveSelectedAssets(targetFolderId) { movedIds ->
                        showMoveSelectedAssets = false
                        val moved = movedIds.size
                        if (moved > 0) {
                            selectedFolder = openedFolder.copy(
                                assetCount = (openedFolder.assetCount - moved).coerceAtLeast(0)
                            )
                            foldersViewModel.refreshOrganizeCount()
                            showMovedToFolderSnackbar(moved, targetName)
                        }
                    }
                }
            }
        )
    }

    // "Mover a carpeta" de una selección: el mismo diálogo para el timeline
    // (que refresca su store en su ViewModel) y para Álbum, Búsqueda,
    // Favoritos y Archivados, que solo necesitan los ids (Lote N6).
    @Composable
    fun MoveAssetsToFolderDialog(
        isSubmitting: Boolean,
        errorMessage: String?,
        onDismiss: () -> Unit,
        onMove: (targetFolderId: String, targetName: String?) -> Unit
    ) {
        com.photonne.app.ui.folder.FolderPickerDialog(
            title = stringResource(Res.string.folder_move_assets_title),
            folders = foldersState.moveDestinations,
            isSubmitting = isSubmitting,
            errorMessage = errorMessage,
            includeRoot = false,
            recentDestinationIds = recentDestinations,
            onDismiss = onDismiss,
            onConfirm = { targetFolderId, _ ->
                if (targetFolderId != null) {
                    recentDestinationsStore.record(targetFolderId)
                    onMove(
                        targetFolderId,
                        foldersState.moveDestinations.firstOrNull { it.id == targetFolderId }?.name
                    )
                }
            }
        )
    }

    if (showMoveSelectedAssetsTimeline) {
        MoveAssetsToFolderDialog(
            isSubmitting = timelineState.isBulkMutating,
            errorMessage = timelineState.error?.userMessage,
            onDismiss = {
                showMoveSelectedAssetsTimeline = false
                timelineViewModel.clearError()
            },
            onMove = { targetFolderId, targetName ->
                timelineViewModel.moveSelectedAssets(targetFolderId) { movedIds ->
                    showMoveSelectedAssetsTimeline = false
                    foldersViewModel.refreshOrganizeCount()
                    showMovedToFolderSnackbar(movedIds.size, targetName)
                }
            }
        )
    }

    moveSelectionRequest?.let { request ->
        MoveAssetsToFolderDialog(
            isSubmitting = moveSelectionSubmitting,
            errorMessage = moveSelectionError,
            onDismiss = {
                if (!moveSelectionSubmitting) {
                    moveSelectionRequest = null
                    moveSelectionError = null
                }
            },
            onMove = { targetFolderId, targetName ->
                if (!moveSelectionSubmitting) {
                    moveSelectionSubmitting = true
                    moveSelectionError = null
                    coroutineScope.launch {
                        runCatching {
                            foldersRepository.moveAssets(
                                sourceFolderId = null,
                                targetFolderId = targetFolderId,
                                assetIds = request.assetIds
                            )
                        }.onSuccess {
                            moveSelectionSubmitting = false
                            moveSelectionRequest = null
                            request.onMoved()
                            foldersViewModel.refreshOrganizeCount()
                            showMovedToFolderSnackbar(request.assetIds.size, targetName)
                        }.onFailure { error ->
                            moveSelectionSubmitting = false
                            moveSelectionError =
                                errorFactory.from(error, "No se pudo mover").userMessage
                        }
                    }
                }
            }
        )
    }

    if (showMoveSelectedAssetsInbox) {
        com.photonne.app.ui.folder.FolderPickerDialog(
            title = stringResource(Res.string.folder_move_assets_title),
            folders = foldersState.moveDestinations,
            isSubmitting = organizeInboxState.isBulkMutating,
            errorMessage = organizeInboxState.error?.userMessage,
            includeRoot = false,
            recentDestinationIds = recentDestinations,
            showOrganizeByDate = true,
            yearBreakdown = organizeInboxState.moveYearGroups.map {
                com.photonne.app.data.models.YearCount(it.year, it.count)
            },
            onDismiss = {
                showMoveSelectedAssetsInbox = false
                organizeInboxViewModel.clearError()
            },
            onConfirm = { targetFolderId, organizeByYear ->
                if (targetFolderId != null) {
                    recentDestinationsStore.record(targetFolderId)
                    showMoveSelectedAssetsInbox = false
                    // With year foldering, review the split before committing;
                    // otherwise (or if the preview failed to load) move straight away.
                    if (organizeByYear && organizeInboxState.moveYearGroups.isNotEmpty()) {
                        inboxReviewTarget = targetFolderId
                    } else {
                        organizeInboxViewModel.moveSelectedAssets(targetFolderId, organizeByYear) {
                            foldersViewModel.refreshOrganizeCount()
                        }
                    }
                }
            }
        )
    }

    inboxReviewTarget?.let { target ->
        com.photonne.app.ui.organize.MoveReviewScreen(
            movedTotal = organizeInboxState.moveYearGroups.sumOf { it.count },
            groups = organizeInboxState.moveYearGroups,
            baseUrl = apiBaseUrl,
            isMoving = organizeInboxState.isBulkMutating,
            organizeByYear = true,
            onBack = { inboxReviewTarget = null },
            onConfirm = { keptIds ->
                organizeInboxViewModel.moveSelectedAssets(
                    targetFolderId = target,
                    organizeByYear = true,
                    onlyIds = keptIds
                ) {
                    inboxReviewTarget = null
                    foldersViewModel.refreshOrganizeCount()
                }
            }
        )
    }

    // Misma rejilla de revisión para el flujo por condiciones, hospedada también
    // aquí FUERA del MainScaffold: dentro del contenido quedaba por debajo de la
    // nav flotante y su botón de confirmar era inalcanzable.
    if (moreSubscreen == MoreSubscreen.OrganizeRule) {
        organizeRuleState.reviewGroups?.let { groups ->
            com.photonne.app.ui.organize.MoveReviewScreen(
                // El total sale de los grupos que se están revisando, NO del
                // previewCount: si divergen, `keptTotal` (total − quitadas) se
                // desmadra y puede deshabilitar el botón con fotos en pantalla.
                movedTotal = groups.sumOf { it.count },
                groups = groups,
                baseUrl = apiBaseUrl,
                isMoving = organizeRuleState.isMoving,
                organizeByYear = organizeRuleState.organizeByYear,
                onBack = organizeRuleViewModel::closeReview,
                onConfirm = { keptIds ->
                    organizeRuleViewModel.move(onlyIds = keptIds) { outcome ->
                        // With a year split, confirm the distribution first; the
                        // navigation back happens when the summary is dismissed.
                        if (outcome.yearBreakdown.isNotEmpty()) organizeRuleSummary = outcome
                        else organizeRuleMoved()
                    }
                }
            )
        }
    }

    organizeRuleSummary?.let { outcome ->
        com.photonne.app.ui.organize.MoveSummaryDialog(
            outcome = outcome,
            onDismiss = {
                organizeRuleSummary = null
                organizeRuleMoved()
            }
        )
    }

    organizeInboxState.lastMoveSummary?.let { outcome ->
        com.photonne.app.ui.organize.MoveSummaryDialog(
            outcome = outcome,
            onDismiss = organizeInboxViewModel::clearMoveSummary
        )
    }

    val addToAlbumState = addToAlbum
    if (addToAlbumState != null) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = addToAlbumState.isSubmitting,
            errorMessage = addToAlbumState.errorMessage,
            onCreateNew = {
                addToAlbum = null
                pendingAddTarget = PendingAddTarget.Asset(addToAlbumState.asset)
                pendingAssetAddError = null
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                addToAlbum = addToAlbumState.copy(isSubmitting = true, errorMessage = null)
                coroutineScope.launch {
                    runCatching { albumsRepository.addAsset(album.id, addToAlbumState.asset.id) }
                        .onSuccess {
                            albumsViewModel.applyAssetAdded(album.id)
                            albumDetailViewModel.applyAssetAdded(album.id, addToAlbumState.asset)
                            addToAlbum = null
                            showAddedToAlbumSnackbar(1, album.name)
                        }
                        .onFailure { error ->
                            addToAlbum = addToAlbumState.copy(
                                isSubmitting = false,
                                errorMessage = error.message ?: "Failed to add to album"
                            )
                        }
                }
            },
            onDismiss = { addToAlbum = null }
        )
    }

    if (showLogoutConfirm) {
        val pendingBackup = if (deviceBackupState.isBackupEnabled) {
            deviceBackupState.pendingEntries.size
        } else 0
        val baseMessage = stringResource(Res.string.logout_confirm_message)
        val message = if (pendingBackup > 0) {
            baseMessage + "\n\n" + pluralStringResource(
                Res.plurals.logout_confirm_pending_backup, pendingBackup, pendingBackup
            )
        } else baseMessage
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.logout_confirm_title),
            message = message,
            confirmLabel = stringResource(Res.string.action_logout),
            isDestructive = true,
            isSubmitting = false,
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                authRepository.logout()
            }
        )
    }

    if (showAcceptAllSuggestions) {
        LaunchedEffect(Unit) { personSuggestionsViewModel.clearError() }
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.people_suggestions_accept_all_title),
            message = pluralStringResource(
                Res.plurals.people_suggestions_accept_all_message,
                suggestionsState.total,
                suggestionsState.total
            ),
            confirmLabel = stringResource(Res.string.people_action_suggestions_accept_all),
            isDestructive = false,
            isSubmitting = suggestionsState.isBulkMutating,
            errorMessage = suggestionsState.error?.userMessage,
            onDismiss = { showAcceptAllSuggestions = false },
            onConfirm = {
                personSuggestionsViewModel.acceptAll { affected ->
                    showAcceptAllSuggestions = false
                    selectedPerson?.let { personDetailViewModel.open(it.id, it.name) }
                    peopleViewModel.refresh()
                    coroutineScope.launch {
                        snackbarController.show(
                            org.jetbrains.compose.resources.getPluralString(
                                Res.plurals.people_suggestions_accepted_done,
                                affected, affected
                            )
                        )
                    }
                }
            }
        )
    }

    if (showDismissAllSuggestions) {
        LaunchedEffect(Unit) { personSuggestionsViewModel.clearError() }
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.people_suggestions_dismiss_all_title),
            message = pluralStringResource(
                Res.plurals.people_suggestions_dismiss_all_message,
                suggestionsState.total,
                suggestionsState.total
            ),
            confirmLabel = stringResource(Res.string.people_action_suggestions_dismiss_all),
            isDestructive = true,
            isSubmitting = suggestionsState.isBulkMutating,
            errorMessage = suggestionsState.error?.userMessage,
            onDismiss = { showDismissAllSuggestions = false },
            onConfirm = {
                personSuggestionsViewModel.dismissAll { affected ->
                    showDismissAllSuggestions = false
                    coroutineScope.launch {
                        snackbarController.show(
                            org.jetbrains.compose.resources.getPluralString(
                                Res.plurals.people_suggestions_dismissed_done,
                                affected, affected
                            )
                        )
                    }
                }
            }
        )
    }

    if (showUnarchiveAll) {
        LaunchedEffect(Unit) { archivedViewModel.clearError() }
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.archive_action_unarchive_all_title),
            message = stringResource(Res.string.archive_action_unarchive_all_message),
            confirmLabel = stringResource(Res.string.archive_action_unarchive_all),
            isDestructive = false,
            isSubmitting = archivedState.isBulkMutating,
            errorMessage = archivedState.error?.userMessage,
            onDismiss = { showUnarchiveAll = false },
            onConfirm = {
                archivedViewModel.unarchiveAll {
                    showUnarchiveAll = false
                    timelineViewModel.refresh()
                }
            }
        )
    }

    if (showRestoreAllTrash) {
        LaunchedEffect(Unit) { trashViewModel.clearError() }
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_restore_all),
            message = stringResource(Res.string.trash_dialog_restore_all_message),
            confirmLabel = stringResource(Res.string.trash_action_restore_all),
            isDestructive = false,
            isSubmitting = trashState.isBulkMutating,
            errorMessage = trashState.error?.userMessage,
            onDismiss = { showRestoreAllTrash = false },
            onConfirm = {
                trashViewModel.restoreAll {
                    showRestoreAllTrash = false
                    timelineViewModel.refresh()
                }
            }
        )
    }

    if (showEmptyTrash) {
        LaunchedEffect(Unit) { trashViewModel.clearError() }
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_empty),
            message = stringResource(Res.string.trash_dialog_empty_message),
            confirmLabel = stringResource(Res.string.trash_action_empty),
            isDestructive = true,
            isSubmitting = trashState.isBulkMutating,
            errorMessage = trashState.error?.userMessage,
            onDismiss = { showEmptyTrash = false },
            onConfirm = {
                trashViewModel.emptyTrash { showEmptyTrash = false }
            }
        )
    }

    if (showPurgeSelected) {
        val count = trashState.selection.size
        LaunchedEffect(Unit) { trashViewModel.clearError() }
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_delete_forever),
            message = pluralStringResource(Res.plurals.trash_dialog_purge_message, count, count),
            confirmLabel = stringResource(Res.string.trash_action_delete_forever),
            isDestructive = true,
            isSubmitting = trashState.isBulkMutating,
            errorMessage = trashState.error?.userMessage,
            onDismiss = { showPurgeSelected = false },
            onConfirm = {
                trashViewModel.bulkPurge { showPurgeSelected = false }
            }
        )
    }

    val activePerson = selectedPerson
    if (showRenamePerson && activePerson != null) {
        com.photonne.app.ui.people.RenamePersonDialog(
            initialName = personDetailState.personName ?: activePerson.name,
            isSubmitting = peopleState.isMutating,
            errorMessage = peopleState.error?.userMessage,
            onDismiss = {
                showRenamePerson = false
                peopleViewModel.clearError()
            },
            onConfirm = { name ->
                peopleViewModel.rename(activePerson.id, name) {
                    personDetailViewModel.applyRename(name)
                    selectedPerson = activePerson.copy(name = name)
                    showRenamePerson = false
                }
            }
        )
    }

    if (showMergePicker && activePerson != null) {
        // El selector debe poder encontrar a CUALQUIERA: se cargan todas las
        // páginas al abrir (con el paginado perezoso las no cargadas eran
        // infusionables).
        LaunchedEffect(Unit) { peopleViewModel.loadAllPages() }
        com.photonne.app.ui.people.PersonPickerDialog(
            people = peopleState.people,
            baseUrl = apiBaseUrl,
            excludeId = activePerson.id,
            isLoading = peopleState.isAppending,
            onDismiss = { showMergePicker = false },
            onSelect = { other ->
                showMergePicker = false
                mergeSource = other
                mergeError = null
            }
        )
    }

    val mergeSourcePerson = mergeSource
    if (mergeSourcePerson != null && activePerson != null) {
        com.photonne.app.ui.people.ConfirmMergeDialog(
            target = activePerson,
            source = mergeSourcePerson,
            baseUrl = apiBaseUrl,
            isSubmitting = isMerging,
            errorMessage = mergeError,
            onDismiss = {
                mergeSource = null
                mergeError = null
            },
            onConfirm = {
                isMerging = true
                mergeError = null
                coroutineScope.launch {
                    // The current person absorbs the picked one's faces.
                    // Mirror the PWA: target is the receiving person,
                    // `other` is the one that gets dropped.
                    runCatching {
                        peopleRepository.merge(
                            targetPersonId = activePerson.id,
                            sourcePersonId = mergeSourcePerson.id
                        )
                    }.onSuccess {
                        isMerging = false
                        mergeSource = null
                        peopleViewModel.refresh()
                        // The current detail might now contain more faces.
                        personDetailViewModel.open(activePerson.id, activePerson.name)
                        snackbarController.show(
                            org.jetbrains.compose.resources.getString(
                                Res.string.people_merge_done
                            )
                        )
                    }.onFailure { error ->
                        // El diálogo sigue abierto con el error: antes el fallo
                        // era silencio absoluto (runCatching sin onFailure).
                        isMerging = false
                        mergeError = errorFactory
                            .from(error, "No se pudo fusionar")
                            .userMessage
                    }
                }
            }
        )
    }

    val shareIds = actionsState.shareChooserIds
    if (shareIds != null) {
        ShareAssetsDialog(
            selectedCount = shareIds.size,
            onDismiss = actionsViewModel::cancelShare,
            onShareDirectly = { actionsViewModel.shareDirectly(shareIds) },
            onCreateLink = { name -> actionsViewModel.createPhotonneLink(shareIds, name) }
        )
    }

    val formatChooser = actionsState.formatChooser
    if (formatChooser != null) {
        DownloadFormatSheet(
            chooser = formatChooser,
            onDismiss = actionsViewModel::cancelFormatChoice,
            onChoose = actionsViewModel::chooseFormat
        )
    }

    val createdLink = actionsState.createdLink
    if (createdLink != null) {
        ShareLinkResultDialog(
            url = createdLink,
            onDismiss = actionsViewModel::dismissLink
        )
    }

    if (showAssetFacesSheet && assetFacesState.assetId != null) {
        com.photonne.app.ui.people.AssetFacesSheet(
            state = assetFacesState,
            baseUrl = apiBaseUrl,
            onDismiss = {
                showAssetFacesSheet = false
                assetFacesViewModel.close()
                assetFacesRevision++
            },
            onAcceptSuggestion = assetFacesViewModel::acceptSuggestion,
            onDismissSuggestion = assetFacesViewModel::dismissSuggestion,
            onAssign = assetFacesViewModel::startAssigning,
            onAssignToPerson = assetFacesViewModel::assignToPerson,
            onAssignToNewPerson = { faceId, name ->
                assetFacesViewModel.assignToNewPerson(faceId, name)
                peopleViewModel.refresh()
            },
            onUnassign = assetFacesViewModel::unassign,
            onReject = assetFacesViewModel::reject,
            onSetCover = { personId, faceId ->
                assetFacesViewModel.setAsCover(personId, faceId) {
                    peopleViewModel.refresh()
                }
            },
            onCancelAssign = assetFacesViewModel::cancelAssigning,
            onPickerQueryChange = assetFacesViewModel::setPickerQuery,
            onOpenPerson = { personId ->
                showAssetFacesSheet = false
                assetFacesViewModel.close()
                assetFacesRevision++
                openPersonFromViewer(personId)
            }
        )
    }

    // Enlace compartido abierto desde fuera: sobre todo lo demás (el visor se
    // cierra al abrirlo), bajo el snackbar.
    if (sharedLinkOpen) {
        com.photonne.app.ui.share.SharedLinkScreen(
            state = sharedLinkState,
            onBack = sharedLinkViewModel::close,
            onSubmitPassword = sharedLinkViewModel::submitPassword,
            onRetry = sharedLinkViewModel::retry
        )
    }

    // Host del snackbar sobre todo lo demás. Entra por arriba, bajo el cromo
    // flotante (barra de estado + cápsula): abajo caía sobre la nav flotante y
    // sobre el botón que lo acababa de provocar.
    // Encima, la píldora "Sin conexión": el snackbar entra justo debajo de ella.
    val connectivityMonitor: com.photonne.app.data.api.ConnectivityMonitor = koinInject()
    val connectivity by connectivityMonitor.status.collectAsStateWithLifecycle()
    // Y la de incompatibilidad app↔servidor, que explica fallos sueltos igual.
    val compatibility by appVersionStore.compatibility.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.padding(top = com.photonne.app.ui.main.subscreenChromeReservedTop()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.photonne.app.ui.main.ConnectivityPill(
                status = connectivity,
                onRetry = connectivityMonitor::retry
            )
            com.photonne.app.ui.main.CompatibilityPill(compatibility = compatibility)
            com.photonne.app.ui.main.TopSnackbarHost(hostState = snackbarController.hostState)
        }
    }

    // Píldora de operación masiva (descarga/ZIP/compartir/enlace) con Cancelar,
    // por encima de la nav flotante. Antes el único indicio era la barra de
    // selección atenuada, sin forma de abortar.
    if (actionsState.working != AssetActionWorking.Idle) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            com.photonne.app.ui.actions.WorkingPill(
                working = actionsState.working,
                onCancel = actionsViewModel::cancelWorking,
                modifier = Modifier.padding(
                    bottom = com.photonne.app.ui.main.floatingNavBarReservedHeight() + 16.dp
                )
            )
        }
    }
}
