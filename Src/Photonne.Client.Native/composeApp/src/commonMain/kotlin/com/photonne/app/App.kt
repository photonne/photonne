@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.photonne.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import coil3.compose.setSingletonImageLoaderFactory
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_section_appearance
import com.photonne.app.resources.account_section_profile
import com.photonne.app.resources.account_section_security
import com.photonne.app.resources.account_section_storage
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
import com.photonne.app.resources.admin_shared_trash
import com.photonne.app.resources.admin_settings_user_defaults
import com.photonne.app.resources.admin_settings_version
import com.photonne.app.resources.admin_system_backup
import com.photonne.app.resources.admin_system_duplicates
import com.photonne.app.resources.admin_system_embedding
import com.photonne.app.resources.admin_system_face
import com.photonne.app.resources.admin_system_index
import com.photonne.app.resources.admin_system_maintenance
import com.photonne.app.resources.admin_system_metadata
import com.photonne.app.resources.admin_system_object
import com.photonne.app.resources.admin_system_scene
import com.photonne.app.resources.admin_system_text
import com.photonne.app.resources.admin_system_run_tasks
import com.photonne.app.resources.admin_system_thumbnails
import com.photonne.app.resources.administration_title
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_save
import com.photonne.app.resources.album_action_edit
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.archive_title
import com.photonne.app.resources.archive_action_unarchive_all_title
import com.photonne.app.resources.archive_action_unarchive_all_message
import com.photonne.app.resources.archive_action_unarchive_all
import com.photonne.app.resources.folder_action_edit
import com.photonne.app.resources.folder_action_new
import com.photonne.app.resources.folder_move_assets_title
import com.photonne.app.resources.folder_move_title
import com.photonne.app.resources.trash_action_delete_forever
import com.photonne.app.resources.trash_action_empty
import com.photonne.app.resources.trash_action_restore_all
import com.photonne.app.resources.trash_dialog_empty_message
import com.photonne.app.resources.trash_dialog_purge_message
import com.photonne.app.resources.trash_dialog_restore_all_message
import com.photonne.app.resources.favorites_title
import com.photonne.app.resources.people_title
import com.photonne.app.resources.people_unnamed
import com.photonne.app.resources.map_title
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
import com.photonne.app.resources.unsupported_files_title
import com.photonne.app.resources.explore_title
import com.photonne.app.resources.explore_section_memories
import com.photonne.app.resources.explore_section_places
import com.photonne.app.resources.explore_section_scenes
import com.photonne.app.resources.explore_section_objects
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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
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
import com.photonne.app.ui.actions.ShareAssetsDialog
import com.photonne.app.ui.actions.ShareLinkResultDialog
import com.photonne.app.ui.main.AlbumsListTopBar
import com.photonne.app.ui.main.ArchiveMode
import com.photonne.app.ui.main.AssetSelectionBottomBar
import com.photonne.app.ui.main.AssetSelectionTopBar
import com.photonne.app.ui.main.FolderDetailTopBar
import com.photonne.app.ui.main.FoldersListTopBar
import com.photonne.app.ui.main.MainScaffold
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.main.MoreScreen
import com.photonne.app.ui.main.MoreTopBar
import com.photonne.app.ui.main.TimelineTopBar
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
    val onFavoriteChanged: (assetId: String, isFavorite: Boolean) -> Unit
) {
    enum class Source { Timeline, Album }
}

private data class AddToAlbumState(
    val asset: TimelineItem,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

private enum class MoreSubscreen {
    Upload,
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
    UnsupportedFiles,
    ExploreMemories,
    ExploreScenes,
    ExploreObjects,
    AccountSettings,
    AccountProfile,
    AccountSecurity,
    AccountAppearance,
    AccountStorage,
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
    AdminSharedTrash,
    AdminSettingsUserDefaults,
    AdminSettingsVersion,
    AdminSystemHub,
    AdminSystemRunTasks,
    AdminSystemDuplicates,
    AdminSystemMaintenance,
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
    MoreSubscreen.AdminSharedTrash,
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
    MoreSubscreen.AdminSystemMaintenance,
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
    MoreSubscreen.AdminSharedTrash ->
        Res.string.admin_shared_trash to Unit
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
    MoreSubscreen.AdminSystemMaintenance -> Res.string.admin_system_maintenance to Unit
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
    MoreSubscreen.UtilitiesLocations -> MoreSubscreen.Utilities
    MoreSubscreen.ExploreMemories,
    MoreSubscreen.ExploreScenes,
    MoreSubscreen.ExploreObjects -> null
    MoreSubscreen.PeopleSuggestions -> MoreSubscreen.People
    MoreSubscreen.AccountProfile,
    MoreSubscreen.AccountSecurity,
    MoreSubscreen.AccountAppearance,
    MoreSubscreen.AccountStorage -> MoreSubscreen.AccountSettings
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
    MoreSubscreen.AdminSharedTrash,
    MoreSubscreen.AdminSettingsUserDefaults,
    MoreSubscreen.AdminSettingsVersion -> MoreSubscreen.AdminSettingsHub
    MoreSubscreen.AdminSystemDuplicates -> MoreSubscreen.AdminSystemRunTasks
    MoreSubscreen.AdminSystemRunTasks,
    MoreSubscreen.AdminSystemMaintenance,
    MoreSubscreen.AdminSystemBackup -> MoreSubscreen.AdminSystemHub
    MoreSubscreen.Upload,
    MoreSubscreen.DeviceBackup,
    MoreSubscreen.Favorites,
    MoreSubscreen.People,
    MoreSubscreen.Map,
    MoreSubscreen.Archived,
    MoreSubscreen.Trash,
    MoreSubscreen.Utilities,
    MoreSubscreen.UnsupportedFiles,
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
                if (first) first = false
                else recoveryScope.launch { foregroundRecovery.onEnterForeground() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val themeStore: com.photonne.app.data.settings.ThemePreferenceStore = koinInject()
    val themePreference by themeStore.value.collectAsState()

    val sessionBootstrapper: com.photonne.app.data.auth.SessionBootstrapper = koinInject()
    LaunchedEffect(Unit) {
        sessionBootstrapper.restore()
    }

    PhotonneTheme(preference = themePreference) {
        val authState: AuthStateHolder = koinInject()
        val state by authState.state.collectAsState()
        when (val current = state) {
            is AuthState.Authenticated -> AuthenticatedApp(user = current)
            AuthState.Unauthenticated -> LoginScreen()
            // Booting: restoring a persisted session. Show a neutral splash so
            // the login screen never flashes before the timeline appears.
            AuthState.Unknown -> SessionLoadingScreen()
        }
    }
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
    val timelineZoom by timelineZoomStore.value.collectAsState()
    val albumsViewModel: AlbumsViewModel = koinViewModel()
    val albumDetailViewModel: AlbumDetailViewModel = koinViewModel()
    val searchViewModel: com.photonne.app.ui.search.SearchViewModel = koinViewModel()
    val foldersViewModel: FoldersViewModel = koinViewModel()
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
    val uploadViewModel: com.photonne.app.ui.upload.UploadViewModel = koinViewModel()
    val deviceBackupViewModel: com.photonne.app.ui.devicebackup.DeviceBackupViewModel = koinViewModel()
    val deviceBackupState by deviceBackupViewModel.state.collectAsState()
    val enrichmentStatusViewModel: com.photonne.app.ui.devicebackup.EnrichmentStatusViewModel = koinViewModel()
    val utilitiesDuplicatesViewModel:
        com.photonne.app.ui.utilities.UtilitiesDuplicatesViewModel = koinViewModel()
    val utilitiesLargeFilesViewModel:
        com.photonne.app.ui.utilities.UtilitiesLargeFilesViewModel = koinViewModel()
    val utilitiesLocationsViewModel:
        com.photonne.app.ui.utilities.UtilitiesLocationsViewModel = koinViewModel()
    val exploreFacetsViewModel:
        com.photonne.app.ui.explore.ExploreFacetsViewModel = koinViewModel()
    val memoriesViewModel:
        com.photonne.app.ui.timeline.MemoriesViewModel = koinViewModel()
    val memoriesState by memoriesViewModel.state.collectAsState()
    val notificationsViewModel:
        com.photonne.app.ui.notifications.NotificationsViewModel = koinViewModel()
    val notificationsState by notificationsViewModel.state.collectAsState()
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
    val adminMaintenanceViewModel:
        com.photonne.app.ui.admin.AdminMaintenanceViewModel = koinViewModel()
    val adminBackupViewModel:
        com.photonne.app.ui.admin.AdminBackupViewModel = koinViewModel()
    val timelineState by timelineViewModel.state.collectAsState()
    val albumsState by albumsViewModel.state.collectAsState()
    val albumDetailState by albumDetailViewModel.state.collectAsState()
    val searchState by searchViewModel.state.collectAsState()
    val foldersState by foldersViewModel.state.collectAsState()
    val folderDetailState by folderDetailViewModel.state.collectAsState()
    val folderPermissionsState by folderPermissionsViewModel.state.collectAsState()
    val albumSharesState by albumSharesViewModel.state.collectAsState()
    val albumPermissionsState by albumPermissionsViewModel.state.collectAsState()
    val archivedState by archivedViewModel.state.collectAsState()
    val trashState by trashViewModel.state.collectAsState()
    val favoritesState by favoritesViewModel.state.collectAsState()
    val unsupportedFilesState by unsupportedFilesViewModel.state.collectAsState()
    val peopleState by peopleViewModel.state.collectAsState()
    val personDetailState by personDetailViewModel.state.collectAsState()
    val suggestionsState by personSuggestionsViewModel.state.collectAsState()
    val assetFacesState by assetFacesViewModel.state.collectAsState()
    val uploadState by uploadViewModel.state.collectAsState()
    val actionsState by actionsViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(MainTab.Timeline) }
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var selectedFolder by remember {
        mutableStateOf<com.photonne.app.data.models.FolderSummary?>(null)
    }
    val folderBackStack = remember {
        mutableStateListOf<com.photonne.app.data.models.FolderSummary>()
    }
    var assetDetail by remember { mutableStateOf<AssetDetailContext?>(null) }
    // Tracks the asset shown by the viewer's pager — drives the
    // grid → detail shared-element morph. Null when the viewer is closed
    // so all grid thumbnails return to their normal visible state.
    var currentDetailAssetId by remember { mutableStateOf<String?>(null) }
    var showCreateAlbum by remember { mutableStateOf(false) }
    var showEditAlbum by remember { mutableStateOf(false) }
    var showDeleteAlbum by remember { mutableStateOf(false) }
    var showLeaveAlbum by remember { mutableStateOf(false) }
    var showShares by remember { mutableStateOf(false) }
    var showCreateShare by remember { mutableStateOf(false) }
    var editingShareLink by remember { mutableStateOf<AlbumShareLink?>(null) }
    var showMembers by remember { mutableStateOf(false) }
    var showInviteMember by remember { mutableStateOf(false) }
    var addToAlbum by remember { mutableStateOf<AddToAlbumState?>(null) }
    var bulkAddToAlbum by remember { mutableStateOf<Boolean>(false) }
    var bulkAddToAlbumFromSearch by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromMap by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromFavorites by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromPeople by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromFolder by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromArchive by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromAlbum by remember { mutableStateOf(false) }
    var selectedPerson by remember {
        mutableStateOf<com.photonne.app.data.models.Person?>(null)
    }
    var showRenamePerson by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }
    var showAssetFacesSheet by remember { mutableStateOf(false) }
    var showJumpToDate by remember { mutableStateOf(false) }
    var pendingJumpDate by remember { mutableStateOf<kotlinx.datetime.Instant?>(null) }
    var pendingBulkAddOnCreate by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var showEditFolder by remember { mutableStateOf(false) }
    var showDeleteFolder by remember { mutableStateOf(false) }
    var showEditSubfolder by remember { mutableStateOf(false) }
    var showDeleteSubfolder by remember { mutableStateOf(false) }
    var showFolderMembers by remember { mutableStateOf(false) }
    var showInviteFolderMember by remember { mutableStateOf(false) }
    var showMoveFolder by remember { mutableStateOf(false) }
    var showMoveSelectedAssets by remember { mutableStateOf(false) }
    var showSearchFilters by remember { mutableStateOf(false) }
    var showAlbumsFilters by remember { mutableStateOf(false) }
    var showFoldersFilters by remember { mutableStateOf(false) }
    var pendingActionAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var pendingActionFolder by remember {
        mutableStateOf<com.photonne.app.data.models.FolderSummary?>(null)
    }
    var moreSubscreen by remember { mutableStateOf<MoreSubscreen?>(null) }
    var adminUserEditorId by remember { mutableStateOf<String?>(null) }
    var adminLibraryEditorId by remember { mutableStateOf<String?>(null) }
    var showUnarchiveAll by remember { mutableStateOf(false) }
    var showRestoreAllTrash by remember { mutableStateOf(false) }
    var showEmptyTrash by remember { mutableStateOf(false) }
    var showPurgeSelected by remember { mutableStateOf(false) }

    val onLogout: () -> Unit = { authRepository.logout() }
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
        (moreSubscreen == MoreSubscreen.Trash && trashState.isSelectionActive) ||
        (moreSubscreen == MoreSubscreen.People && selectedPerson != null &&
            personDetailState.isSelectionActive)
    )
    val canHandleBack = (
        assetDetail != null ||
        isAnySelectionActive ||
        selectedAlbum != null ||
        selectedFolder != null ||
        selectedPerson != null ||
        moreSubscreen != null ||
        selectedTab != MainTab.Timeline
    )
    PlatformBackHandler(enabled = canHandleBack) {
        when {
            assetDetail != null -> { assetDetail = null }
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
            moreSubscreen == MoreSubscreen.Trash && trashState.isSelectionActive ->
                trashViewModel.clearSelection()
            moreSubscreen == MoreSubscreen.People && selectedPerson != null &&
                personDetailState.isSelectionActive -> personDetailViewModel.clearSelection()
            selectedTab == MainTab.Albums && selectedAlbum != null -> albumBack()
            selectedTab == MainTab.Folders && selectedFolder != null -> {
                selectedFolder = if (folderBackStack.isNotEmpty()) {
                    folderBackStack.removeAt(folderBackStack.lastIndex)
                } else null
            }
            moreSubscreen == MoreSubscreen.People && selectedPerson != null -> {
                selectedPerson = null
            }
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
            moreSubscreen != null -> { moreSubscreen = parentMoreSubscreen(moreSubscreen!!) }
            selectedTab != MainTab.Timeline -> { selectedTab = MainTab.Timeline }
        }
    }

    val topBar: @Composable () -> Unit = {
        when {
            selectedTab == MainTab.Timeline &&
                timelineState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = timelineState.selection.size,
                    totalCount = timelineState.loadedItems.size,
                    isMutating = timelineState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = timelineViewModel::clearSelection,
                    onSelectAll = timelineViewModel::toggleSelectAll
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
                // The hero inside AlbumDetailScreen owns back / share / overflow
                // controls (PWA-style), so no separate top bar here.
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
                        isSearchActive = albumsState.isSearchActive,
                        onToggleSearch = albumsViewModel::toggleSearch
                    )
                }
            }
            selectedTab == MainTab.Albums && moreSubscreen == null -> AlbumsListTopBar(
                onOpenFilters = { showAlbumsFilters = true },
                isSearchActive = albumsState.isSearchActive,
                onToggleSearch = albumsViewModel::toggleSearch
            )
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
            selectedTab == MainTab.Folders && selectedFolder != null -> {
                val folder = selectedFolder!!
                FolderDetailTopBar(
                    title = (folderDetailState.folderName ?: folder.name).ifBlank { folder.path },
                    subtitle = stringResource(
                        Res.string.albums_count_format,
                        folder.assetCount
                    ),
                    canEdit = folder.isOwner,
                    canDelete = folder.isOwner,
                    canManageMembers = folder.isOwner,
                    canMove = folder.isOwner,
                    onBack = {
                        selectedFolder = if (folderBackStack.isNotEmpty()) {
                            folderBackStack.removeAt(folderBackStack.lastIndex)
                        } else null
                    },
                    onEdit = { showEditFolder = true },
                    onMove = { showMoveFolder = true },
                    onDelete = { showDeleteFolder = true },
                    onManageMembers = {
                        folderPermissionsViewModel.open(folder.id)
                        showFolderMembers = true
                    },
                    canToggleTimeline = folder.isShared && folder.externalLibraryId == null,
                    excludedFromTimeline = folder.excludedFromTimeline,
                    onToggleTimeline = {
                        val nextIncluded = folder.excludedFromTimeline
                        foldersViewModel.setTimelineIncluded(folder.id, included = nextIncluded)
                        selectedFolder = folder.copy(excludedFromTimeline = !nextIncluded)
                    }
                )
            }
            selectedTab == MainTab.Folders && foldersState.isSelectionActive -> {
                val target = (foldersState.personalFolders + foldersState.sharedFolders)
                    .firstOrNull { it.id == foldersState.selectedFolderId }
                if (target != null) {
                    com.photonne.app.ui.main.FolderCardSelectionTopBar(
                        folderName = target.name.ifBlank { target.path },
                        isMutating = foldersState.isMutating,
                        onClose = foldersViewModel::clearSelection
                    )
                } else {
                    FoldersListTopBar(
                        onOpenFilters = { showFoldersFilters = true },
                        isSearchActive = foldersState.isSearchActive,
                        onToggleSearch = foldersViewModel::toggleSearch
                    )
                }
            }
            selectedTab == MainTab.Folders ->
                FoldersListTopBar(
                    onOpenFilters = { showFoldersFilters = true },
                    isSearchActive = foldersState.isSearchActive,
                    onToggleSearch = foldersViewModel::toggleSearch
                )
            selectedTab == MainTab.Search && searchState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = searchState.selection.size,
                    totalCount = searchState.results.size,
                    isMutating = searchState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = searchViewModel::clearSelection,
                    onSelectAll = searchViewModel::toggleSelectAll
                )
            selectedTab == MainTab.Search ->
                com.photonne.app.ui.main.SearchTopBar()
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
            moreSubscreen == MoreSubscreen.DeviceBackup ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.device_backup_title),
                    onBack = { moreSubscreen = null }
                )
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
            moreSubscreen == MoreSubscreen.DeviceBackupPending ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.backup_pending_screen_title),
                    onBack = { moreSubscreen = MoreSubscreen.DeviceBackup }
                )
            moreSubscreen == MoreSubscreen.EnrichmentStatus ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.enrichment_screen_title),
                    onBack = { moreSubscreen = MoreSubscreen.DeviceBackup }
                )
            moreSubscreen == MoreSubscreen.Utilities ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.utilities_title),
                    onBack = { moreSubscreen = null }
                )
            moreSubscreen == MoreSubscreen.UnsupportedFiles ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.unsupported_files_title),
                    onBack = { moreSubscreen = null }
                )
            moreSubscreen == MoreSubscreen.UtilitiesDuplicates ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.utilities_section_duplicates),
                    onBack = { moreSubscreen = MoreSubscreen.Utilities }
                )
            moreSubscreen == MoreSubscreen.UtilitiesLargeFiles ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.utilities_section_large_files),
                    onBack = { moreSubscreen = MoreSubscreen.Utilities }
                )
            moreSubscreen == MoreSubscreen.UtilitiesLocations ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.utilities_section_locations),
                    onBack = { moreSubscreen = MoreSubscreen.Utilities }
                )
            moreSubscreen == MoreSubscreen.ExploreMemories ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.explore_section_memories),
                    onBack = { moreSubscreen = null }
                )
            moreSubscreen == MoreSubscreen.ExploreScenes ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.explore_section_scenes),
                    onBack = { moreSubscreen = null }
                )
            moreSubscreen == MoreSubscreen.ExploreObjects ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.explore_section_objects),
                    onBack = { moreSubscreen = null }
                )
            moreSubscreen == MoreSubscreen.Map ->
                com.photonne.app.ui.main.MapTopBar(
                    title = stringResource(Res.string.map_title),
                    onBack = { moreSubscreen = null },
                    onRefresh = mapViewModel::refresh
                )
            moreSubscreen == MoreSubscreen.PeopleSuggestions ->
                com.photonne.app.ui.main.PersonSuggestionsTopBar(
                    title = (suggestionsState.personName ?: selectedPerson?.name)
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.people_unnamed),
                    subtitle = if (suggestionsState.total > 0)
                        stringResource(Res.string.albums_count_format, suggestionsState.total)
                    else null,
                    isBulkMutating = suggestionsState.isBulkMutating,
                    onBack = { moreSubscreen = MoreSubscreen.People },
                    onAcceptAll = {
                        personSuggestionsViewModel.acceptAll {
                            personDetailViewModel.open(
                                selectedPerson?.id ?: return@acceptAll,
                                selectedPerson?.name
                            )
                            peopleViewModel.refresh()
                        }
                    },
                    onDismissAll = { personSuggestionsViewModel.dismissAll() }
                )
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
            moreSubscreen == MoreSubscreen.People &&
                selectedPerson != null -> {
                val person = selectedPerson!!
                val resolvedName = personDetailState.personName ?: person.name
                com.photonne.app.ui.main.PersonDetailTopBar(
                    title = resolvedName?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.people_unnamed),
                    subtitle = if (personDetailState.total > 0)
                        stringResource(Res.string.albums_count_format, personDetailState.total)
                    else null,
                    isHidden = person.isHidden,
                    onBack = { selectedPerson = null },
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
                    }
                )
            }
            moreSubscreen == MoreSubscreen.People ->
                com.photonne.app.ui.main.PeopleTopBar(
                    title = stringResource(Res.string.people_title),
                    onBack = { moreSubscreen = null },
                    onRecluster = { peopleViewModel.recluster() },
                    showHidden = peopleState.showHidden,
                    onToggleHidden = peopleViewModel::toggleShowHidden
                )
            moreSubscreen == MoreSubscreen.Favorites &&
                favoritesState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = favoritesState.selection.size,
                    totalCount = favoritesState.items.size,
                    isMutating = favoritesState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = favoritesViewModel::clearSelection,
                    onSelectAll = favoritesViewModel::toggleSelectAll
                )
            moreSubscreen == MoreSubscreen.Favorites -> {
                val count = favoritesState.items.size
                com.photonne.app.ui.main.FavoritesTopBar(
                    title = stringResource(Res.string.favorites_title),
                    subtitle = if (count > 0)
                        stringResource(Res.string.albums_count_format, count) else null,
                    onBack = { moreSubscreen = null }
                )
            }
            moreSubscreen == MoreSubscreen.Archived &&
                archivedState.isSelectionActive ->
                AssetSelectionTopBar(
                    selectedCount = archivedState.selection.size,
                    totalCount = archivedState.items.size,
                    isMutating = archivedState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onClose = archivedViewModel::clearSelection,
                    onSelectAll = archivedViewModel::toggleSelectAll
                )
            moreSubscreen == MoreSubscreen.Archived -> {
                val count = archivedState.items.size
                com.photonne.app.ui.main.ArchivedTopBar(
                    title = stringResource(Res.string.archive_title),
                    subtitle = if (count > 0)
                        stringResource(Res.string.albums_count_format, count) else null,
                    canUnarchiveAll = count > 0,
                    onBack = { moreSubscreen = null },
                    onUnarchiveAll = { showUnarchiveAll = true }
                )
            }
            moreSubscreen == MoreSubscreen.Trash &&
                trashState.isSelectionActive ->
                com.photonne.app.ui.main.TrashSelectionTopBar(
                    selectedCount = trashState.selection.size,
                    isMutating = trashState.isBulkMutating,
                    onClose = trashViewModel::clearSelection,
                    onRestore = { trashViewModel.bulkRestore() },
                    onPurge = { showPurgeSelected = true }
                )
            moreSubscreen == MoreSubscreen.Trash -> {
                val count = trashState.items.size
                com.photonne.app.ui.main.TrashTopBar(
                    title = stringResource(Res.string.trash_title),
                    subtitle = if (count > 0)
                        stringResource(Res.string.albums_count_format, count) else null,
                    canActOnAll = count > 0,
                    onBack = { moreSubscreen = null },
                    onRestoreAll = { showRestoreAllTrash = true },
                    onEmptyTrash = { showEmptyTrash = true }
                )
            }
            moreSubscreen == MoreSubscreen.Notifications ->
                com.photonne.app.ui.main.NotificationsTopBar(
                    title = stringResource(Res.string.notifications_title),
                    canMarkAllRead = notificationsState.unreadCount > 0 &&
                        !notificationsState.isMarkingAllRead,
                    onBack = { moreSubscreen = null },
                    onMarkAllRead = notificationsViewModel::markAllRead
                )
            moreSubscreen == MoreSubscreen.AccountSettings ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.account_settings_title),
                    onBack = { moreSubscreen = null },
                )
            moreSubscreen == MoreSubscreen.AccountProfile ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.account_section_profile),
                    onBack = { moreSubscreen = MoreSubscreen.AccountSettings },
                )
            moreSubscreen == MoreSubscreen.AccountSecurity ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.account_section_security),
                    onBack = { moreSubscreen = MoreSubscreen.AccountSettings },
                )
            moreSubscreen == MoreSubscreen.AccountAppearance ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.account_section_appearance),
                    onBack = { moreSubscreen = MoreSubscreen.AccountSettings },
                )
            moreSubscreen == MoreSubscreen.AccountStorage ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.account_section_storage),
                    onBack = { moreSubscreen = MoreSubscreen.AccountSettings },
                )
            moreSubscreen == MoreSubscreen.Administration ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.administration_title),
                    onBack = { moreSubscreen = null },
                )
            moreSubscreen == MoreSubscreen.AdminUsers ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.admin_section_users),
                    onBack = { moreSubscreen = MoreSubscreen.Administration },
                )
            moreSubscreen == MoreSubscreen.AdminUserEditor ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(
                        if (adminUserEditorId == null) Res.string.admin_user_action_new
                        else Res.string.admin_user_edit_title
                    ),
                    onBack = {
                        adminUserEditorId = null
                        adminUsersViewModel.clearMessages()
                        moreSubscreen = MoreSubscreen.AdminUsers
                    },
                )
            moreSubscreen == MoreSubscreen.AdminLibraries ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.admin_section_libraries),
                    onBack = { moreSubscreen = MoreSubscreen.Administration },
                )
            moreSubscreen == MoreSubscreen.AdminLibraryEditor ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(
                        if (adminLibraryEditorId == null) Res.string.admin_libraries_action_new
                        else Res.string.admin_libraries_edit_title
                    ),
                    onBack = {
                        adminLibraryEditorId = null
                        adminLibrariesViewModel.clearMessages()
                        moreSubscreen = MoreSubscreen.AdminLibraries
                    },
                )
            moreSubscreen == MoreSubscreen.AdminStats ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.admin_section_stats),
                    onBack = { moreSubscreen = MoreSubscreen.Administration },
                )
            moreSubscreen == MoreSubscreen.AdminSettingsHub ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.admin_section_settings),
                    onBack = { moreSubscreen = MoreSubscreen.Administration },
                )
            moreSubscreen == MoreSubscreen.AdminSystemHub ->
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(Res.string.admin_section_system),
                    onBack = { moreSubscreen = MoreSubscreen.Administration },
                )
            isAdminSettingsSubpage(moreSubscreen) -> {
                val (titleRes, _) = adminSettingsSubpageMeta(moreSubscreen!!)
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(titleRes),
                    onBack = { moreSubscreen = MoreSubscreen.AdminSettingsHub },
                )
            }
            isAdminSystemSubpage(moreSubscreen) -> {
                val (titleRes, _) = adminSystemSubpageMeta(moreSubscreen!!)
                // The 9 task detail screens are reached via "Ejecutar tareas",
                // so back from there should land back on the consolidated list
                // rather than skipping a level to the system hub.
                val parent = if (isAdminRunTasksDetail(moreSubscreen)) {
                    MoreSubscreen.AdminSystemRunTasks
                } else {
                    MoreSubscreen.AdminSystemHub
                }
                com.photonne.app.ui.main.SettingsTopBar(
                    title = stringResource(titleRes),
                    onBack = { moreSubscreen = parent },
                )
            }
            selectedTab == MainTab.More -> MoreTopBar(
                onOpenUpload = { moreSubscreen = MoreSubscreen.Upload }
            )
            else -> TimelineTopBar(
                onJumpToDate = { showJumpToDate = true },
                currentZoom = timelineZoom,
                onZoomSelected = timelineZoomStore::update,
                onOpenSearch = { selectedTab = MainTab.Search },
                deviceLoading = deviceBackupState.isBackupEnabled &&
                    deviceBackupState.isLoading
            )
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
                    isMutating = timelineState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(timelineState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddToAlbum = true },
                    onDownload = {
                        actionsViewModel.download(timelineState.selection.toList())
                    },
                    onArchive = timelineViewModel::bulkArchive,
                    onTrash = timelineViewModel::bulkTrash
                )
            }
        }
        selectedTab == MainTab.Albums && selectedAlbum != null &&
            albumDetailState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    isMutating = albumDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(albumDetailState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddToAlbumFromAlbum = true },
                    onDownload = {
                        actionsViewModel.download(albumDetailState.selection.toList())
                    },
                    onArchive = albumDetailViewModel::bulkArchive,
                    onTrash = albumDetailViewModel::bulkTrash,
                    onRemoveFromAlbum = if (selectedAlbum?.canWrite == true ||
                        selectedAlbum?.isOwner == true
                    ) {
                        {
                            albumDetailViewModel.bulkRemoveFromAlbum { removed ->
                                selectedAlbum?.let {
                                    albumsViewModel.applyAssetsRemoved(it.id, removed)
                                }
                            }
                        }
                    } else null,
                    onSetAsCover = if (albumDetailState.selection.size == 1 &&
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
                    com.photonne.app.ui.main.FolderCardSelectionBottomBar(
                        canManageMembers = false,
                        canRename = subfolder.isOwner,
                        canDelete = subfolder.isOwner,
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
                AssetSelectionBottomBar(
                    isMutating = folderDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(folderDetailState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddToAlbumFromFolder = true },
                    onDownload = {
                        actionsViewModel.download(folderDetailState.selection.toList())
                    },
                    onArchive = folderDetailViewModel::bulkArchive,
                    onTrash = folderDetailViewModel::bulkTrash,
                    onMove = if (selectedFolder?.isOwner == true) {
                        { showMoveSelectedAssets = true }
                    } else null
                )
            }
        }
        selectedTab == MainTab.Search && searchState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    isMutating = searchState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(searchState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddToAlbumFromSearch = true },
                    onDownload = {
                        actionsViewModel.download(searchState.selection.toList())
                    },
                    onArchive = searchViewModel::bulkArchive,
                    onTrash = searchViewModel::bulkTrash
                )
            }
        }
        moreSubscreen == MoreSubscreen.People &&
            selectedPerson != null && personDetailState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    isMutating = personDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(personDetailState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddToAlbumFromPeople = true },
                    onDownload = {
                        actionsViewModel.download(personDetailState.selection.toList())
                    },
                    onArchive = personDetailViewModel::bulkArchive,
                    onTrash = personDetailViewModel::bulkTrash,
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
        moreSubscreen == MoreSubscreen.Favorites &&
            favoritesState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    isMutating = favoritesState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(favoritesState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddToAlbumFromFavorites = true },
                    onDownload = {
                        actionsViewModel.download(favoritesState.selection.toList())
                    },
                    onArchive = favoritesViewModel::bulkArchive,
                    onTrash = favoritesViewModel::bulkTrash
                )
            }
        }
        moreSubscreen == MoreSubscreen.Archived &&
            archivedState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    isMutating = archivedState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    archiveMode = ArchiveMode.Unarchive,
                    onShare = {
                        actionsViewModel.beginShare(archivedState.selection.toList())
                    },
                    onAddToAlbum = { bulkAddToAlbumFromArchive = true },
                    onDownload = {
                        actionsViewModel.download(archivedState.selection.toList())
                    },
                    onArchive = { archivedViewModel.bulkUnarchive() },
                    onTrash = archivedViewModel::bulkTrash
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
                        canDelete = target.isOwner,
                        isMutating = albumsState.isMutating,
                        onManageMembers = {
                            pendingActionAlbum = target
                            albumPermissionsViewModel.open(target.id)
                            showMembers = true
                        },
                        onEdit = {
                            pendingActionAlbum = target
                            showEditAlbum = true
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
            val target = (foldersState.personalFolders + foldersState.sharedFolders)
                .firstOrNull { it.id == foldersState.selectedFolderId }
            if (target != null) {
                {
                    com.photonne.app.ui.main.FolderCardSelectionBottomBar(
                        canManageMembers = target.isOwner && target.isShared,
                        canRename = target.isOwner,
                        canDelete = target.isOwner,
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
                        excludedFromTimeline = target.excludedFromTimeline,
                        onToggleTimeline = {
                            foldersViewModel.setTimelineIncluded(
                                folderId = target.id,
                                included = target.excludedFromTimeline
                            )
                        }
                    )
                }
            } else null
        }
        else -> null
    }

    // Primary upload entry point on Timeline — shown only when the timeline
    // is the active tab and there's no overriding bottom bar (selection mode
    // takes the bottom bar and would crash into the FAB).
    val floatingActionButton: @Composable () -> Unit = {
        when {
            selectedTab == MainTab.Timeline && bottomBar == null ->
                FloatingActionButton(
                    onClick = {
                        selectedTab = MainTab.More
                        moreSubscreen = MoreSubscreen.Upload
                    }
                ) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = stringResource(Res.string.upload_title)
                    )
                }
            // Folder creation as a FAB so it stays reachable while browsing
            // into subfolders (where the list top bar is gone). Creates a
            // subfolder of the currently opened folder, or a root folder at
            // the top level. Hidden during selection (which owns the bottom
            // bar) and for external libraries, which are read-only mirrors —
            // their folder structure is owned by another system.
            selectedTab == MainTab.Folders && bottomBar == null &&
                foldersState.selectedTab != com.photonne.app.ui.folder.FoldersTab.Libraries &&
                selectedFolder?.externalLibraryId == null ->
                FloatingActionButton(onClick = { showCreateFolder = true }) {
                    Icon(
                        Icons.Outlined.CreateNewFolder,
                        contentDescription = stringResource(Res.string.folder_action_new)
                    )
                }
            // Album creation as a FAB, matching the Timeline upload and Folders
            // create patterns. Only on the albums list (not an open album or a
            // More subscreen) and hidden during selection, which owns the
            // bottom bar.
            selectedTab == MainTab.Albums && bottomBar == null &&
                selectedAlbum == null && moreSubscreen == null ->
                FloatingActionButton(onClick = { showCreateAlbum = true }) {
                    Icon(
                        Icons.Outlined.AddBox,
                        contentDescription = stringResource(Res.string.album_action_new)
                    )
                }
        }
    }

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
        }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides this,
        LocalCurrentDetailAssetId provides currentDetailAssetId
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        MainScaffold(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                // Tapping any bottom-nav tab dismisses an open subscreen layer
                // (People / Map / Explore facets, or any More destination) so
                // it never lingers over the newly selected tab.
                moreSubscreen = null
                selectedPerson = null
                if (tab == MainTab.Albums && selectedTab == MainTab.Albums) selectedAlbum = null
                if (tab == MainTab.Folders && selectedTab == MainTab.Folders) {
                    selectedFolder = null
                    folderBackStack.clear()
                }
                if (tab != MainTab.Albums) albumsViewModel.clearSelection()
                if (tab != MainTab.Folders) foldersViewModel.clearSelection()
                selectedTab = tab
            },
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            moreTabUnreadCount = notificationsState.unreadCount
        ) {
            // Subscreens (People/Map/Explore facets, plus all the More-menu
            // destinations) render as a modal layer over whatever tab is
            // active: opening "Personas" from the Albums tab keeps the bottom
            // nav on Álbumes and returns there on back. Each primary tab is
            // therefore guarded with `moreSubscreen == null`, and the `else`
            // renders the active subscreen (or the More grid when null).
            when {
                selectedTab == MainTab.Timeline && moreSubscreen == null -> TimelineScreen(
                        state = timelineState,
                        onOpenAsset = { mergedItems, mergedIndex ->
                            assetDetail = AssetDetailContext(
                                items = mergedItems,
                                // The pager is bounded to the contiguous
                                // loaded bucket run TimelineScreen handed us.
                                startIndex = mergedIndex,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = timelineViewModel::setFavorite
                            )
                        },
                        onBucketsVisible = timelineViewModel::ensureVisible,
                        onEnsureYearSummaries = timelineViewModel::ensureYearSummaries,
                        onRefresh = timelineViewModel::refresh,
                        onToggleSelection = timelineViewModel::toggleSelection,
                        onOpenUpload = {
                            selectedTab = MainTab.More
                            moreSubscreen = MoreSubscreen.Upload
                        },
                        pendingJumpDate = pendingJumpDate,
                        onJumpHandled = { pendingJumpDate = null },
                        memories = memoriesState.items,
                        onOpenMemory = { items, index ->
                            assetDetail = AssetDetailContext(
                                items = items,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = timelineViewModel::setFavorite
                            )
                        },
                        onSeeAllMemories = { moreSubscreen = MoreSubscreen.ExploreMemories }
                    )
                selectedTab == MainTab.Albums && moreSubscreen == null -> {
                    val openedAlbum = selectedAlbum
                    if (openedAlbum == null) {
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
                            onCreateAlbum = { showCreateAlbum = true },
                            // Explorar cards open their screen as a modal layer
                            // over the Albums tab (no tab switch) so back returns
                            // here and the bottom nav stays on Álbumes.
                            onOpenPeople = {
                                selectedPerson = null
                                moreSubscreen = MoreSubscreen.People
                            },
                            onOpenMap = { moreSubscreen = MoreSubscreen.Map },
                            onOpenScenes = { moreSubscreen = MoreSubscreen.ExploreScenes },
                            onOpenObjects = { moreSubscreen = MoreSubscreen.ExploreObjects }
                        )
                    } else {
                        AlbumDetailScreen(
                            album = openedAlbum,
                            onItemClick = { index ->
                                assetDetail = AssetDetailContext(
                                    items = albumDetailState.items,
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
                            onEdit = { showEditAlbum = true },
                            onDelete = { showDeleteAlbum = true },
                            onManageMembers = {
                                albumPermissionsViewModel.open(openedAlbum.id)
                                showMembers = true
                            },
                            onLeave = { showLeaveAlbum = true },
                            viewModel = albumDetailViewModel
                        )
                    }
                }
                selectedTab == MainTab.Folders && moreSubscreen == null -> {
                    val openedFolder = selectedFolder
                    if (openedFolder == null) {
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
                            }
                        )
                    } else {
                        com.photonne.app.ui.folder.FolderDetailScreen(
                            folderId = openedFolder.id,
                            folderName = openedFolder.name.ifBlank { openedFolder.path },
                            parentFolderId = openedFolder.parentFolderId,
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
                            viewModel = folderDetailViewModel
                        )
                    }
                }
                selectedTab == MainTab.Search && moreSubscreen == null ->
                    com.photonne.app.ui.search.SearchScreen(
                    viewModel = searchViewModel,
                    onOpenFilters = { showSearchFilters = true },
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
                                hasMore = false,
                                onLoadMore = {},
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
                    null -> MoreScreen(
                        user = user.user,
                        onLogout = onLogout,
                        onOpenFavorites = { moreSubscreen = MoreSubscreen.Favorites },
                        onOpenArchived = { moreSubscreen = MoreSubscreen.Archived },
                        onOpenTrash = { moreSubscreen = MoreSubscreen.Trash },
                        onOpenUtilities = { moreSubscreen = MoreSubscreen.Utilities },
                        onOpenUnsupportedFiles = { moreSubscreen = MoreSubscreen.UnsupportedFiles },
                        onOpenDeviceBackup = { moreSubscreen = MoreSubscreen.DeviceBackup },
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
                        onDismissPickerError = uploadViewModel::clearPickerError
                    )
                    MoreSubscreen.DeviceBackup ->
                        com.photonne.app.ui.devicebackup.BackupScreen(
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
                            viewModel = enrichmentStatusViewModel
                        )
                    MoreSubscreen.UnsupportedFiles ->
                        com.photonne.app.ui.library.UnsupportedFilesScreen(
                            state = unsupportedFilesState,
                            onLoad = unsupportedFilesViewModel::ensureLoaded,
                            onRefresh = unsupportedFilesViewModel::refresh,
                            onLoadMore = unsupportedFilesViewModel::loadMore,
                            onDownload = unsupportedFilesViewModel::download
                        )
                    MoreSubscreen.Utilities ->
                        com.photonne.app.ui.utilities.UtilitiesHubScreen(
                            onOpen = { entry ->
                                moreSubscreen = when (entry) {
                                    com.photonne.app.ui.utilities.UtilitiesEntry.Duplicates ->
                                        MoreSubscreen.UtilitiesDuplicates
                                    com.photonne.app.ui.utilities.UtilitiesEntry.LargeFiles ->
                                        MoreSubscreen.UtilitiesLargeFiles
                                    com.photonne.app.ui.utilities.UtilitiesEntry.Locations ->
                                        MoreSubscreen.UtilitiesLocations
                                }
                            }
                        )
                    MoreSubscreen.UtilitiesDuplicates ->
                        com.photonne.app.ui.utilities.UtilitiesDuplicatesScreen(
                            viewModel = utilitiesDuplicatesViewModel,
                            baseUrl = apiBaseUrl,
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
                            viewModel = utilitiesLocationsViewModel
                        )
                    MoreSubscreen.ExploreMemories ->
                        com.photonne.app.ui.explore.ExploreMemoriesScreen(
                            viewModel = memoriesViewModel,
                            baseUrl = apiBaseUrl,
                            onGroupClick = { groupItems ->
                                assetDetail = AssetDetailContext(
                                    items = groupItems,
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
                    MoreSubscreen.ExploreScenes ->
                        com.photonne.app.ui.explore.ExploreScenesScreen(
                            viewModel = exploreFacetsViewModel,
                            // Tapping a scene jumps to the Search tab pre-filtered
                            // by that label — same flow as the PWA, where Explorar
                            // is just a deep-linking surface for the search engine.
                            onSceneClick = { label ->
                                searchViewModel.showResultsForSceneLabel(label)
                                moreSubscreen = null
                                selectedTab = MainTab.Search
                            }
                        )
                    MoreSubscreen.ExploreObjects ->
                        com.photonne.app.ui.explore.ExploreObjectsScreen(
                            viewModel = exploreFacetsViewModel,
                            onObjectClick = { label ->
                                searchViewModel.showResultsForObjectLabel(label)
                                moreSubscreen = null
                                selectedTab = MainTab.Search
                            }
                        )
                    MoreSubscreen.Map -> com.photonne.app.ui.map.MapScreen(
                        viewModel = mapViewModel,
                        onPointOpen = { point ->
                            // Single-marker tap → open the asset viewer
                            // seeded with that one item. The viewer
                            // re-fetches asset detail on display, so a
                            // synthetic TimelineItem is enough.
                            assetDetail = AssetDetailContext(
                                items = listOf(point.toSyntheticTimelineItem()),
                                startIndex = 0,
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
                        onBulkAddToAlbum = { bulkAddToAlbumFromMap = true }
                    )
                    MoreSubscreen.PeopleSuggestions ->
                        com.photonne.app.ui.people.PersonSuggestionsScreen(
                            state = suggestionsState,
                            onAccept = personSuggestionsViewModel::acceptFace,
                            onDismissFace = personSuggestionsViewModel::dismissFace,
                            onLoadMore = personSuggestionsViewModel::loadMore,
                            onOpen = {
                                selectedPerson?.let {
                                    personSuggestionsViewModel.open(it.id, it.name)
                                }
                            }
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
                                onRefresh = peopleViewModel::refresh
                            )
                        } else {
                            com.photonne.app.ui.people.PersonDetailScreen(
                                state = personDetailState,
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
                                onLoadMore = personDetailViewModel::loadMore
                            )
                        }
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
                        onRefresh = favoritesViewModel::refresh
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
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = archivedState.hasMore,
                                    onLoadMore = archivedViewModel::loadMore,
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
                        onLoad = archivedViewModel::ensureLoaded,
                        onRefresh = archivedViewModel::refresh
                    )
                    MoreSubscreen.Trash -> com.photonne.app.ui.library.TrashScreen(
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
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = trashState.hasMore,
                                    onLoadMore = trashViewModel::loadMore,
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
                        onRefresh = trashViewModel::refresh
                    )
                    MoreSubscreen.Notifications ->
                        com.photonne.app.ui.notifications.NotificationsScreen(
                            viewModel = notificationsViewModel
                        )
                    MoreSubscreen.AccountSettings ->
                        com.photonne.app.ui.settings.AccountSettingsScreen(
                            onOpen = { section ->
                                moreSubscreen = when (section) {
                                    com.photonne.app.ui.settings.AccountSettingsSection.Profile ->
                                        MoreSubscreen.AccountProfile
                                    com.photonne.app.ui.settings.AccountSettingsSection.Security ->
                                        MoreSubscreen.AccountSecurity
                                    com.photonne.app.ui.settings.AccountSettingsSection.Appearance ->
                                        MoreSubscreen.AccountAppearance
                                    com.photonne.app.ui.settings.AccountSettingsSection.Storage ->
                                        MoreSubscreen.AccountStorage
                                }
                            }
                        )
                    MoreSubscreen.AccountProfile ->
                        com.photonne.app.ui.settings.AccountProfileScreen(
                            viewModel = accountProfileViewModel
                        )
                    MoreSubscreen.AccountSecurity ->
                        com.photonne.app.ui.settings.AccountSecurityScreen(
                            viewModel = accountSecurityViewModel
                        )
                    MoreSubscreen.AccountAppearance ->
                        com.photonne.app.ui.settings.AccountAppearanceScreen(
                            viewModel = appearanceViewModel
                        )
                    MoreSubscreen.AccountStorage ->
                        com.photonne.app.ui.settings.AccountStorageScreen(
                            viewModel = accountStorageViewModel
                        )
                    MoreSubscreen.Administration ->
                        com.photonne.app.ui.admin.AdministrationScreen(
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
                            viewModel = adminUsersViewModel,
                            onCreate = {
                                adminUserEditorId = null
                                adminUsersViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminUserEditor
                            },
                            onEdit = { user ->
                                adminUserEditorId = user.id
                                adminUsersViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminUserEditor
                            }
                        )
                    MoreSubscreen.AdminUserEditor ->
                        com.photonne.app.ui.admin.AdminUserEditorScreen(
                            viewModel = adminUsersViewModel,
                            userId = adminUserEditorId,
                            onDone = {
                                adminUserEditorId = null
                                moreSubscreen = MoreSubscreen.AdminUsers
                            }
                        )
                    MoreSubscreen.AdminLibraries -> {
                        val usersState by adminUsersViewModel.state.collectAsState()
                        LaunchedEffect(Unit) { adminUsersViewModel.ensureLoaded() }
                        com.photonne.app.ui.admin.AdminLibrariesScreen(
                            viewModel = adminLibrariesViewModel,
                            knownUsers = usersState.users,
                            onCreate = {
                                adminLibraryEditorId = null
                                adminLibrariesViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminLibraryEditor
                            },
                            onEdit = { library ->
                                adminLibraryEditorId = library.id
                                adminLibrariesViewModel.clearMessages()
                                moreSubscreen = MoreSubscreen.AdminLibraryEditor
                            }
                        )
                    }
                    MoreSubscreen.AdminLibraryEditor ->
                        com.photonne.app.ui.admin.AdminLibraryEditorScreen(
                            viewModel = adminLibrariesViewModel,
                            libraryId = adminLibraryEditorId,
                            onDone = {
                                adminLibraryEditorId = null
                                moreSubscreen = MoreSubscreen.AdminLibraries
                            }
                        )
                    MoreSubscreen.AdminStats ->
                        com.photonne.app.ui.admin.AdminStatsScreen(
                            viewModel = adminStatsViewModel
                        )
                    MoreSubscreen.AdminSettingsHub ->
                        com.photonne.app.ui.admin.AdminSettingsHubScreen(
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
                                    com.photonne.app.ui.admin.AdminSettingsEntry.SharedTrash ->
                                        MoreSubscreen.AdminSharedTrash
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
                            viewModel = vm,
                            onOpenNightly = {
                                moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsImage ->
                        com.photonne.app.ui.admin.AdminImageSettingsScreen(
                            viewModel = adminImageSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsMetadata ->
                        com.photonne.app.ui.admin.AdminMetadataSettingsScreen(
                            viewModel = adminMetadataSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsNightly ->
                        com.photonne.app.ui.admin.AdminNightlySettingsScreen(
                            viewModel = adminNightlySettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsNotifications ->
                        com.photonne.app.ui.admin.AdminNotificationSettingsScreen(
                            viewModel = adminNotificationSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsServer ->
                        com.photonne.app.ui.admin.AdminServerSettingsScreen(
                            viewModel = adminServerSettingsViewModel,
                            deviceConnectionViewModel = deviceConnectionViewModel
                        )
                    MoreSubscreen.AdminSettingsTrash ->
                        com.photonne.app.ui.admin.AdminTrashSettingsScreen(
                            viewModel = adminTrashSettingsViewModel
                        )
                    MoreSubscreen.AdminSharedTrash ->
                        com.photonne.app.ui.admin.AdminSharedTrashScreen(
                            viewModel = adminSharedTrashViewModel
                        )
                    MoreSubscreen.AdminSettingsUserDefaults ->
                        com.photonne.app.ui.admin.AdminUserDefaultsScreen(
                            viewModel = adminUserDefaultsViewModel
                        )
                    MoreSubscreen.AdminSettingsVersion ->
                        com.photonne.app.ui.admin.AdminServerScreen(
                            viewModel = adminVersionViewModel
                        )
                    MoreSubscreen.AdminSystemHub ->
                        com.photonne.app.ui.admin.AdminSystemHubScreen(
                            onOpen = { entry ->
                                moreSubscreen = when (entry) {
                                    com.photonne.app.ui.admin.AdminSystemEntry.RunTasks ->
                                        MoreSubscreen.AdminSystemRunTasks
                                    com.photonne.app.ui.admin.AdminSystemEntry.Maintenance ->
                                        MoreSubscreen.AdminSystemMaintenance
                                    com.photonne.app.ui.admin.AdminSystemEntry.Backup ->
                                        MoreSubscreen.AdminSystemBackup
                                }
                            }
                        )
                    MoreSubscreen.AdminSystemRunTasks -> {
                        val vm: com.photonne.app.ui.admin.AdminRunTasksViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminRunTasksScreen(
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
                        )
                    }
                    MoreSubscreen.AdminSystemDuplicates ->
                        com.photonne.app.ui.admin.AdminDuplicatesScreen(
                            viewModel = adminDuplicatesViewModel
                        )
                    MoreSubscreen.AdminSystemMaintenance ->
                        com.photonne.app.ui.admin.AdminMaintenanceScreen(
                            viewModel = adminMaintenanceViewModel
                        )
                    MoreSubscreen.AdminSystemBackup ->
                        com.photonne.app.ui.admin.AdminBackupScreen(
                            viewModel = adminBackupViewModel
                        )
                }
            }
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
        AnimatedVisibility(
            visible = isDetailVisible,
            // Match the shared-element morph duration so AnimatedVisibility
            // keeps the detail composed until the morph finishes — otherwise
            // the photo snaps the last few pixels when the content unmounts
            // mid-spring.
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 320)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 320)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (displayCtx != null && displayCtx.startIndex in displayCtx.items.indices) {
                // Force a fresh AssetDetailScreen — and therefore a fresh
                // pagerState seeded at the new startIndex — whenever the
                // user opens a different asset.
                key(displayCtx) {
                    AssetDetailScreen(
                        items = displayCtx.items,
                        startIndex = displayCtx.startIndex,
                        hasMore = displayCtx.hasMore,
                        onLoadMore = displayCtx.onLoadMore,
                        onBack = { assetDetail = null },
                        onPageChanged = { id -> currentDetailAssetId = id },
                        animatedVisibilityScope = this@AnimatedVisibility,
                        onFavoriteChanged = displayCtx.onFavoriteChanged,
                        onAddToAlbum = { item -> addToAlbum = AddToAlbumState(asset = item) },
                        onAssetTrashed = { id ->
                            timelineViewModel.removeItemLocal(id)
                            if (displayCtx.source == AssetDetailContext.Source.Album) {
                                albumDetailViewModel.applyAssetRemovedLocal(id)
                            }
                            searchViewModel.removeItem(id)
                            archivedViewModel.applyAssetRemovedLocal(id)
                            personDetailViewModel.applyAssetRemovedLocal(id)
                            assetDetail = null
                        },
                        onAssetArchived = { id ->
                            timelineViewModel.removeItemLocal(id)
                            if (displayCtx.source == AssetDetailContext.Source.Album) {
                                albumDetailViewModel.applyAssetRemovedLocal(id)
                            }
                            searchViewModel.removeItem(id)
                            trashViewModel.applyAssetRemovedLocal(id)
                            personDetailViewModel.applyAssetRemovedLocal(id)
                            assetDetail = null
                        },
                        onOpenFaces = { assetId ->
                            assetFacesViewModel.open(assetId)
                            showAssetFacesSheet = true
                        },
                        onShare = { item -> actionsViewModel.shareDirectly(listOf(item.id)) },
                        onOpenAsset = { item ->
                            // Open a related asset as its own single-item viewer;
                            // key(displayCtx) forces a fresh screen + detail load.
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

    if (showCreateAlbum) {
        AlbumFormDialog(
            title = stringResource(Res.string.album_action_new),
            confirmLabel = stringResource(Res.string.action_create),
            isSubmitting = albumsState.isMutating || timelineState.isBulkMutating,
            errorMessage = albumsState.error?.userMessage,
            onDismiss = {
                showCreateAlbum = false
                pendingBulkAddOnCreate = false
                albumsViewModel.clearError()
            },
            onConfirm = { name, description ->
                albumsViewModel.create(name, description) { newAlbum ->
                    if (pendingBulkAddOnCreate) {
                        pendingBulkAddOnCreate = false
                        timelineViewModel.bulkAddToAlbum(newAlbum.id) { added ->
                            albumsViewModel.applyAssetsAdded(newAlbum.id, added.size)
                            showCreateAlbum = false
                            selectedTab = MainTab.Albums
                            selectedAlbum = newAlbum
                        }
                    } else {
                        showCreateAlbum = false
                        selectedTab = MainTab.Albums
                        selectedAlbum = newAlbum
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
            onRevoke = { token -> albumSharesViewModel.revoke(token) }
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
            onRevoke = { member ->
                albumPermissionsViewModel.revoke(member) { newCount ->
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
                albumPermissionsViewModel.grant(selectedUser, role) { newCount ->
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
                }
                showInviteMember = false
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
            onConfirm = { expiresAt, password, allowDownload, maxViews ->
                albumSharesViewModel.createLink(
                    expiresAt = expiresAt,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews
                )
                showCreateShare = false
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
            onConfirm = { expiresAt, password, allowDownload, maxViews ->
                albumSharesViewModel.editLink(
                    token = link.token,
                    expiresAt = expiresAt,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews
                )
                editingShareLink = null
            }
        )
    }

    if (bulkAddToAlbum) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = timelineState.isBulkMutating,
            errorMessage = timelineState.error?.userMessage,
            onCreateNew = {
                bulkAddToAlbum = false
                pendingBulkAddOnCreate = true
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                timelineViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbum = false
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
            errorMessage = folderDetailState.error?.userMessage,
            onDismiss = {
                showDeleteFolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = {
                folderDetailViewModel.delete { folderId ->
                    showDeleteFolder = false
                    foldersViewModel.applyDelete(folderId)
                    selectedFolder = if (folderBackStack.isNotEmpty()) {
                        folderBackStack.removeAt(folderBackStack.lastIndex)
                    } else null
                }
            }
        )
    } else if (showDeleteFolder && pendingActionFolder != null) {
        val target = pendingActionFolder!!
        DeleteFolderDialog(
            folderName = target.name.ifBlank { target.path },
            isSubmitting = foldersState.isMutating,
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
            onRevoke = { member ->
                folderPermissionsViewModel.revoke(member) { newCount ->
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
                folderPermissionsViewModel.grant(selectedUser, role) { newCount ->
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
                }
                showInviteFolderMember = false
            }
        )
    }

    if (showMoveFolder && openedFolder != null) {
        com.photonne.app.ui.folder.FolderPickerDialog(
            title = stringResource(Res.string.folder_move_title),
            folders = foldersState.personalFolders,
            isSubmitting = folderDetailState.isMutating,
            errorMessage = folderDetailState.error?.userMessage,
            excludeFolderId = openedFolder.id,
            includeRoot = true,
            initialSelectionId = openedFolder.parentFolderId,
            onDismiss = {
                showMoveFolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { targetParentId ->
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
            onClearAll = searchViewModel::clearAll
        )
    }

    if (bulkAddToAlbumFromSearch) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = searchState.isBulkMutating,
            errorMessage = searchState.error?.userMessage,
            onCreateNew = {
                bulkAddToAlbumFromSearch = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                searchViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbumFromSearch = false
            },
            onDismiss = { bulkAddToAlbumFromSearch = false }
        )
    }

    if (showMoveSelectedAssets && openedFolder != null) {
        com.photonne.app.ui.folder.FolderPickerDialog(
            title = stringResource(Res.string.folder_move_assets_title),
            folders = foldersState.personalFolders,
            isSubmitting = folderDetailState.isBulkMutating,
            errorMessage = folderDetailState.error?.userMessage,
            excludeFolderId = openedFolder.id,
            includeRoot = false,
            onDismiss = {
                showMoveSelectedAssets = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { targetFolderId ->
                if (targetFolderId != null) {
                    folderDetailViewModel.moveSelectedAssets(targetFolderId) { movedIds ->
                        showMoveSelectedAssets = false
                        val moved = movedIds.size
                        if (moved > 0) {
                            selectedFolder = openedFolder.copy(
                                assetCount = (openedFolder.assetCount - moved).coerceAtLeast(0)
                            )
                        }
                    }
                }
            }
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

    if (showUnarchiveAll) {
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.archive_action_unarchive_all_title),
            message = stringResource(Res.string.archive_action_unarchive_all_message),
            confirmLabel = stringResource(Res.string.archive_action_unarchive_all),
            isDestructive = false,
            isSubmitting = archivedState.isBulkMutating,
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
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_restore_all),
            message = stringResource(Res.string.trash_dialog_restore_all_message),
            confirmLabel = stringResource(Res.string.trash_action_restore_all),
            isDestructive = false,
            isSubmitting = trashState.isBulkMutating,
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
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_empty),
            message = stringResource(Res.string.trash_dialog_empty_message),
            confirmLabel = stringResource(Res.string.trash_action_empty),
            isDestructive = true,
            isSubmitting = trashState.isBulkMutating,
            onDismiss = { showEmptyTrash = false },
            onConfirm = {
                trashViewModel.emptyTrash { showEmptyTrash = false }
            }
        )
    }

    if (showPurgeSelected) {
        val count = trashState.selection.size
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_delete_forever),
            message = stringResource(Res.string.trash_dialog_purge_message, count),
            confirmLabel = stringResource(Res.string.trash_action_delete_forever),
            isDestructive = true,
            isSubmitting = trashState.isBulkMutating,
            onDismiss = { showPurgeSelected = false },
            onConfirm = {
                trashViewModel.bulkPurge { showPurgeSelected = false }
            }
        )
    }

    if (bulkAddToAlbumFromMap) {
        val mapState = mapViewModel.state.collectAsState().value
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = mapState.isBulkMutating,
            errorMessage = mapState.error?.userMessage,
            onCreateNew = {
                bulkAddToAlbumFromMap = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                mapViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbumFromMap = false
            },
            onDismiss = { bulkAddToAlbumFromMap = false }
        )
    }

    if (bulkAddToAlbumFromPeople) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = personDetailState.isBulkMutating,
            errorMessage = personDetailState.error?.userMessage,
            onCreateNew = {
                bulkAddToAlbumFromPeople = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                personDetailViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbumFromPeople = false
            },
            onDismiss = { bulkAddToAlbumFromPeople = false }
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
        com.photonne.app.ui.people.PersonPickerDialog(
            people = peopleState.people,
            baseUrl = apiBaseUrl,
            excludeId = activePerson.id,
            onDismiss = { showMergePicker = false },
            onSelect = { other ->
                showMergePicker = false
                coroutineScope.launch {
                    // The current person absorbs the picked one's faces.
                    // Mirror the PWA: target is the receiving person,
                    // `other` is the one that gets dropped.
                    runCatching {
                        peopleRepository.merge(
                            targetPersonId = activePerson.id,
                            sourcePersonId = other.id
                        )
                    }.onSuccess {
                        peopleViewModel.refresh()
                        // The current detail might now contain more faces.
                        personDetailViewModel.open(activePerson.id, activePerson.name)
                    }
                }
            }
        )
    }

    if (bulkAddToAlbumFromFolder) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = folderDetailState.isBulkMutating,
            errorMessage = folderDetailState.error?.userMessage,
            onCreateNew = {
                bulkAddToAlbumFromFolder = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                folderDetailViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbumFromFolder = false
            },
            onDismiss = { bulkAddToAlbumFromFolder = false }
        )
    }

    if (bulkAddToAlbumFromAlbum) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = albumDetailState.isBulkMutating,
            errorMessage = albumDetailState.error?.userMessage,
            onCreateNew = {
                bulkAddToAlbumFromAlbum = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                albumDetailViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                }
                bulkAddToAlbumFromAlbum = false
            },
            onDismiss = { bulkAddToAlbumFromAlbum = false }
        )
    }

    if (bulkAddToAlbumFromArchive) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = archivedState.isBulkMutating,
            errorMessage = archivedState.error?.userMessage,
            onCreateNew = {
                bulkAddToAlbumFromArchive = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                archivedViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbumFromArchive = false
            },
            onDismiss = { bulkAddToAlbumFromArchive = false }
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

    val createdLink = actionsState.createdLink
    if (createdLink != null) {
        ShareLinkResultDialog(
            url = createdLink,
            onCopy = { actionsViewModel.dismissLink() },
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
            onCancelAssign = assetFacesViewModel::cancelAssigning
        )
    }
}
