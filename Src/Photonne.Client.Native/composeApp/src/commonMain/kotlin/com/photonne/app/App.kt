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
import com.photonne.app.resources.action_undo
import com.photonne.app.resources.album_trash_warning
import com.photonne.app.resources.album_trash_warning_shared
import com.photonne.app.resources.selection_trash_blocked_foreign
import com.photonne.app.resources.selection_move_blocked_read_only
import com.photonne.app.resources.selection_trash_blocked_folder
import com.photonne.app.resources.selection_restore_done
import com.photonne.app.resources.people_recluster_done
import com.photonne.app.resources.people_recluster_done_none
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
import com.photonne.app.resources.admin_shared_trash
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
import com.photonne.app.resources.organize_rule_title
import com.photonne.app.resources.album_bulk_delete_not_allowed
import com.photonne.app.resources.folder_bulk_delete_not_allowed
import com.photonne.app.resources.notifications_title
import com.photonne.app.resources.backup_pending_screen_title
import com.photonne.app.resources.device_backup_action_select_all
import com.photonne.app.resources.enrichment_screen_title
import com.photonne.app.resources.device_backup_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.upload_subtitle_pending
import com.photonne.app.resources.upload_title
import com.photonne.app.resources.utilities_section_duplicates
import com.photonne.app.resources.utilities_duplicates_action_delete
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
import com.photonne.app.ui.album.AlbumDetailScreen
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumPermissionsViewModel
import com.photonne.app.ui.album.AlbumSharesViewModel
import com.photonne.app.ui.album.AlbumsListScreen
import com.photonne.app.ui.album.AlbumsViewModel
import com.photonne.app.data.models.AlbumShareLink
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
import com.photonne.app.ui.folder.FolderPermissionsViewModel
import com.photonne.app.ui.folder.FoldersViewModel
import com.photonne.app.ui.image.buildPhotonneImageLoader
import com.photonne.app.ui.login.LoginScreen
import com.photonne.app.ui.actions.AssetActionWorking
import com.photonne.app.ui.main.ArchiveMode
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.photonne.app.ui.main.rememberSnackbarController
import com.photonne.app.ui.main.AssetSelectionBottomBar
import com.photonne.app.ui.main.AssetSelectionTopBar
import com.photonne.app.ui.main.FolderDetailChromeActions
import com.photonne.app.ui.library.TrashChromeActions
import com.photonne.app.ui.main.MainScaffold
import com.photonne.app.ui.main.toMoreBackupStatus
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.main.MoreScreen
import com.photonne.app.ui.theme.PhotonneTheme
import com.photonne.app.ui.timeline.TimelineScreen
import com.photonne.app.ui.timeline.TimelineViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal data class AssetDetailContext(
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
internal data class ViewerReturn(
    val tab: MainTab,
    val subscreen: MoreSubscreen?,
    val person: com.photonne.app.data.models.Person?,
    val viewer: AssetDetailContext,
    val viewerStack: List<AssetDetailContext>
)

internal data class AddToAlbumState(
    val asset: TimelineItem,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/**
 * "Mover a carpeta" sobre una selección cualquiera (Lote N6): los ids y qué
 * hacer al terminar. El diálogo ya no está atado a la selección del timeline.
 */
internal class MoveSelectionRequest(
    val assetIds: List<String>,
    val onMoved: () -> Unit
)

internal enum class MoreSubscreen {
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
internal enum class BulkAddSource { Search, Map, Favorites, People, Folder, Archive, Album, Inbox, Upload, Memory }

/**
 * Qué hay que añadir al álbum que se está creando cuando se llega a "Nuevo
 * álbum" desde un "Añadir a álbum": la selección de una pantalla (null =
 * timeline) o el asset abierto en el visor. Sin esto el álbum nacía vacío en
 * todos los orígenes salvo el timeline.
 */
internal sealed interface PendingAddTarget {
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
    val albumsViewModel: AlbumsViewModel = koinViewModel()
    val albumDetailViewModel: AlbumDetailViewModel = koinViewModel()
    val searchViewModel: com.photonne.app.ui.search.SearchViewModel = koinViewModel()
    val foldersViewModel: FoldersViewModel = koinViewModel()
    // Últimos destinos de un movimiento, para ofrecerlos como atajo en el
    // picker: organizar es repetitivo y el árbol es largo.
    val recentDestinationsStore: com.photonne.app.data.settings.RecentDestinationsStore =
        koinInject()
    val recentDestinationsRef = recentDestinationsStore.value.collectAsStateWithLifecycle()
    val recentDestinations by recentDestinationsRef
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
    val deviceBackupStateRef = deviceBackupViewModel.state.collectAsStateWithLifecycle()
    val deviceBackupState by deviceBackupStateRef
    val enrichmentStatusViewModel: com.photonne.app.ui.devicebackup.EnrichmentStatusViewModel = koinViewModel()
    val utilitiesDuplicatesViewModel:
        com.photonne.app.ui.utilities.UtilitiesDuplicatesViewModel = koinViewModel()
    val utilitiesDuplicatesState by utilitiesDuplicatesViewModel.state.collectAsStateWithLifecycle()
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
    val timelineStateRef = timelineViewModel.state.collectAsStateWithLifecycle()
    val timelineState by timelineStateRef
    val albumsStateRef = albumsViewModel.state.collectAsStateWithLifecycle()
    val albumsState by albumsStateRef
    val albumDetailStateRef = albumDetailViewModel.state.collectAsStateWithLifecycle()
    val albumDetailState by albumDetailStateRef
    val searchStateRef = searchViewModel.state.collectAsStateWithLifecycle()
    val searchState by searchStateRef
    val foldersStateRef = foldersViewModel.state.collectAsStateWithLifecycle()
    val foldersState by foldersStateRef
    val folderDetailStateRef = folderDetailViewModel.state.collectAsStateWithLifecycle()
    val folderDetailState by folderDetailStateRef
    val folderPermissionsStateRef = folderPermissionsViewModel.state.collectAsStateWithLifecycle()
    val folderPermissionsState by folderPermissionsStateRef
    val albumSharesStateRef = albumSharesViewModel.state.collectAsStateWithLifecycle()
    val albumSharesState by albumSharesStateRef
    val albumPermissionsStateRef = albumPermissionsViewModel.state.collectAsStateWithLifecycle()
    val albumPermissionsState by albumPermissionsStateRef
    val archivedStateRef = archivedViewModel.state.collectAsStateWithLifecycle()
    val archivedState by archivedStateRef
    val trashStateRef = trashViewModel.state.collectAsStateWithLifecycle()
    val trashState by trashStateRef
    val favoritesStateRef = favoritesViewModel.state.collectAsStateWithLifecycle()
    val favoritesState by favoritesStateRef
    val unsupportedFilesState by unsupportedFilesViewModel.state.collectAsStateWithLifecycle()
    val organizeInboxStateRef = organizeInboxViewModel.state.collectAsStateWithLifecycle()
    val organizeInboxState by organizeInboxStateRef
    val organizeExcludedState by organizeExcludedViewModel.state.collectAsStateWithLifecycle()
    val organizeRuleStateRef = organizeRuleViewModel.state.collectAsStateWithLifecycle()
    val organizeRuleState by organizeRuleStateRef
    val peopleStateRef = peopleViewModel.state.collectAsStateWithLifecycle()
    val peopleState by peopleStateRef
    val personDetailStateRef = personDetailViewModel.state.collectAsStateWithLifecycle()
    val personDetailState by personDetailStateRef
    val suggestionsStateRef = personSuggestionsViewModel.state.collectAsStateWithLifecycle()
    val suggestionsState by suggestionsStateRef
    val assetFacesStateRef = assetFacesViewModel.state.collectAsStateWithLifecycle()
    val assetFacesState by assetFacesStateRef
    val uploadStateRef = uploadViewModel.state.collectAsStateWithLifecycle()
    val uploadState by uploadStateRef
    val actionsStateRef = actionsViewModel.state.collectAsStateWithLifecycle()
    val actionsState by actionsStateRef
    val coroutineScope = rememberCoroutineScope()

    val appState = rememberAuthenticatedAppState(
        peopleRepository = peopleRepository,
        personDetailViewModel = personDetailViewModel,
        albumsViewModel = albumsViewModel,
        foldersViewModel = foldersViewModel,
        organizeInboxViewModel = organizeInboxViewModel,
        coroutineScope = coroutineScope,
    )
    // Red de seguridad: cualquier cierre directo (borrar, papelera del
    // dispositivo…) vacía la pila para no resucitar contextos viejos.
    LaunchedEffect(appState.assetDetail == null) {
        if (appState.assetDetail == null) appState.assetDetailStack = emptyList()
    }
    // Con el visor abierto el fondo bajo las barras es el scrim negro de la
    // foto: iconos claros aunque el tema sea claro. Este sitio recompone al
    // abrir/cerrar el visor, así que también restaura el estado del tema.
    com.photonne.app.ui.platform.SyncSystemBarIcons(
        darkBackground = com.photonne.app.ui.theme.LocalIsDarkTheme.current ||
            appState.assetDetail != null
    )
    // Lote L9: el recuerdo abierto lleva sus fotos en mano (no tiene
    // ViewModel), así que lo borrado o archivado desde el visor se le quita
    // aquí; si se queda vacío, se cierra.
    val assetMutationBus: com.photonne.app.data.events.AssetMutationBus = koinInject()
    LaunchedEffect(assetMutationBus) {
        assetMutationBus.events.collect { event ->
            val memory = appState.memoryDetail ?: return@collect
            when (event) {
                // Favorito (visor o favorito en bloque): el rótulo de la barra de
                // selección depende de este flag.
                is com.photonne.app.data.events.AssetMutation.FavoriteChanged -> {
                    fun List<TimelineItem>.patched() = map {
                        if (it.id == event.assetId) it.copy(isFavorite = event.isFavorite) else it
                    }
                    appState.memoryDetail = memory.copy(
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
                    appState.memoryDetail = memory.copy(
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
                        appState.memoryDetail = if (remaining.isEmpty()) null else memory.copy(items = remaining)
                    }
                }
                else -> Unit
            }
        }
    }
    // Selección múltiple del recuerdo abierto (tanda 2 de funciones nuevas).
    val memorySelectionViewModel: com.photonne.app.ui.memories.MemorySelectionViewModel =
        koinViewModel()
    val memorySelectionStateRef = memorySelectionViewModel.state.collectAsStateWithLifecycle()
    val memorySelectionState by memorySelectionStateRef
    // Cerrar o cambiar de recuerdo no arrastra la selección al siguiente.
    val openMemoryKey = appState.memoryDetail?.let { it.title to it.coverAssetId }
    LaunchedEffect(openMemoryKey) { memorySelectionViewModel.clearSelection() }
    LaunchedEffect(Unit) {
        appState.savedAlbumId?.takeIf { appState.selectedAlbum == null }?.let { id ->
            runCatching { albumsRepository.get(id) }.onSuccess { appState.selectedAlbum = it }
        }
        appState.savedFolderId?.takeIf { appState.selectedFolder == null }?.let { id ->
            runCatching { foldersRepository.get(id) }.onSuccess { appState.selectedFolder = it }
        }
        appState.savedPersonId?.takeIf { appState.selectedPerson == null }?.let { id ->
            runCatching { peopleRepository.get(id) }.onSuccess { appState.selectedPerson = it }
        }
        appState.openIdsRehydrated = true
    }
    // Solo después de rehidratar: si no, la primera composición (todo a null)
    // pisaría los ids guardados antes de poder leerlos.
    LaunchedEffect(appState.openIdsRehydrated, appState.selectedAlbum?.id, appState.selectedFolder?.id, appState.selectedPerson?.id) {
        if (appState.openIdsRehydrated) {
            appState.savedAlbumId = appState.selectedAlbum?.id
            appState.savedFolderId = appState.selectedFolder?.id
            appState.savedPersonId = appState.selectedPerson?.id
        }
    }
    LaunchedEffect(appState.selectedTab) {
        if (appState.selectedTab != MainTab.Search) {
            appState.searchReturnTo = null
            appState.searchViewerReturn = null
        }
    }
    // Enlace compartido abierto desde fuera (photonne://share/{token}): tapa la
    // pantalla como un recuerdo; Atrás lo cierra.
    val sharedLinkViewModel: com.photonne.app.ui.share.SharedLinkViewModel = koinViewModel()
    val sharedLinkStateRef = sharedLinkViewModel.state.collectAsStateWithLifecycle()
    val sharedLinkState by sharedLinkStateRef
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
                appState.assetDetail = null
                sharedLinkViewModel.close()
                appState.selectedTab = MainTab.More
                appState.moreSubscreen = MoreSubscreen.Upload
                uploadViewModel.enqueue(files) { timelineViewModel.refresh() }
            }
            com.photonne.app.ui.main.ExternalDestination.SharedLink -> {
                com.photonne.app.ui.main.ExternalNavigation.consumeSharedLink()?.let { target ->
                    appState.assetDetail = null
                    sharedLinkViewModel.open(target)
                }
            }
            com.photonne.app.ui.main.ExternalDestination.Notifications -> {
                appState.assetDetail = null
                sharedLinkViewModel.close()
                appState.selectedTab = MainTab.More
                appState.moreSubscreen = MoreSubscreen.Notifications
                notificationsViewModel.refresh()
            }
            com.photonne.app.ui.main.ExternalDestination.Backup -> {
                appState.assetDetail = null
                appState.selectedTab = MainTab.More
                appState.moreSubscreen = MoreSubscreen.DeviceBackup
            }
            com.photonne.app.ui.main.ExternalDestination.BackupPending -> {
                appState.assetDetail = null
                appState.selectedTab = MainTab.More
                appState.moreSubscreen = MoreSubscreen.DeviceBackupPending
            }
            null -> Unit
        }
    }

    val onLogout: () -> Unit = { appState.showLogoutConfirm = true }
    val albumBack: () -> Unit = { appState.selectedAlbum = null }

    // Mirror the share link count for the currently opened album back into
    // the albums list so the public-link badge stays in sync after
    // create/revoke without a full refresh.
    val openedAlbumId = appState.selectedAlbum?.id
    val sharedAlbumId = albumSharesState.albumId
    val activeLinks = albumSharesState.links.isNotEmpty()
    LaunchedEffect(openedAlbumId, sharedAlbumId, activeLinks) {
        if (openedAlbumId != null && openedAlbumId == sharedAlbumId) {
            albumsViewModel.applyShareLinkChanged(openedAlbumId, activeLinks)
            appState.selectedAlbum = appState.selectedAlbum?.copy(hasActiveShareLink = activeLinks)
        }
    }

    // Hardware/gesture back: mirrors the same precedence as the in-app
    // back arrows so the system back button feels native. Disabled at the
    // root (Hub on Timeline tab, no overlays) so the system can finish
    // the activity. Modal dialogs/sheets aren't enumerated here because
    // Compose Material3 forwards back-press to their onDismissRequest.
    val isAnySelectionActive = (
        (appState.selectedTab == MainTab.Timeline && timelineState.isSelectionActive) ||
        (appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null &&
            albumDetailState.isSelectionActive) ||
        (appState.selectedTab == MainTab.Albums && albumsState.isSelectionActive) ||
        (appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
            (folderDetailState.isSelectionActive ||
                folderDetailState.isSubfolderSelectionActive)) ||
        (appState.selectedTab == MainTab.Folders && foldersState.isSelectionActive) ||
        (appState.selectedTab == MainTab.Search && searchState.isSelectionActive) ||
        (appState.moreSubscreen == MoreSubscreen.Favorites && favoritesState.isSelectionActive) ||
        (appState.moreSubscreen == MoreSubscreen.Archived && archivedState.isSelectionActive) ||
        (appState.moreSubscreen == MoreSubscreen.Trash && appState.trashTab == com.photonne.app.ui.library.TrashTab.Personal && trashState.isSelectionActive) ||
        (appState.moreSubscreen == MoreSubscreen.People && appState.selectedPerson != null &&
            personDetailState.isSelectionActive) ||
        (appState.moreSubscreen == MoreSubscreen.OrganizeInbox && organizeInboxState.isSelectionActive) ||
        (appState.moreSubscreen == MoreSubscreen.OrganizeExcluded && organizeExcludedState.isSelectionActive)
    )
    val canHandleBack = (
        appState.assetDetail != null ||
        sharedLinkOpen ||
        appState.memoryDetail != null ||
        isAnySelectionActive ||
        // Un álbum o una carpeta abiertos cuentan solo en SU pestaña: si se
        // quedaron abiertos por debajo y estamos en Fotos, ninguna rama de abajo
        // los cierra y el Atrás se tragaba sin hacer nada (ni salir de la app).
        (appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null) ||
        (appState.selectedTab == MainTab.Folders && appState.selectedFolder != null) ||
        appState.selectedPerson != null ||
        appState.moreSubscreen != null ||
        appState.selectedTab != MainTab.Timeline
    )
    // Dirección de la última navegación del overlay. Se marca aquí y la consume
    // el bloque de transición al montar el destino nuevo: "atrás" entra desde el
    // lado contrario, como en cualquier pila de navegación.
    PlatformBackHandler(enabled = canHandleBack) {
        appState.overlayForward = false
        when {
            appState.assetDetail != null -> { appState.closeAssetDetail() }
            sharedLinkOpen -> sharedLinkViewModel.close()
            // Before every selection case: an open memory covers the screen, so
            // back closes what you're actually looking at, not what's underneath.
            appState.memoryDetail != null -> {
                if (memorySelectionState.isSelectionActive) {
                    memorySelectionViewModel.clearSelection()
                } else {
                    appState.memoryDetail = null
                }
            }
            // Igual con la revisión previa a mover: tapa la pantalla entera (y no
            // se cierra a media confirmación, que el movimiento es irreversible).
            appState.inboxReviewTarget != null ->
                if (!organizeInboxState.isBulkMutating) appState.inboxReviewTarget = null
            organizeRuleState.reviewGroups != null ->
                if (!organizeRuleState.isMoving) organizeRuleViewModel.closeReview()
            appState.selectedTab == MainTab.Timeline &&
                timelineState.isSelectionActive -> timelineViewModel.clearSelection()
            appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null &&
                albumDetailState.isSelectionActive -> albumDetailViewModel.clearSelection()
            appState.selectedTab == MainTab.Albums && albumsState.isSelectionActive ->
                albumsViewModel.clearSelection()
            appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
                folderDetailState.isSelectionActive -> folderDetailViewModel.clearSelection()
            appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
                folderDetailState.isSubfolderSelectionActive ->
                folderDetailViewModel.clearSubfolderSelection()
            appState.selectedTab == MainTab.Folders && foldersState.isSelectionActive ->
                foldersViewModel.clearSelection()
            appState.selectedTab == MainTab.Search && searchState.isSelectionActive ->
                searchViewModel.clearSelection()
            appState.moreSubscreen == MoreSubscreen.Favorites && favoritesState.isSelectionActive ->
                favoritesViewModel.clearSelection()
            appState.moreSubscreen == MoreSubscreen.Archived && archivedState.isSelectionActive ->
                archivedViewModel.clearSelection()
            appState.moreSubscreen == MoreSubscreen.Trash && appState.trashTab == com.photonne.app.ui.library.TrashTab.Personal && trashState.isSelectionActive ->
                trashViewModel.clearSelection()
            appState.moreSubscreen == MoreSubscreen.People && appState.selectedPerson != null &&
                personDetailState.isSelectionActive -> personDetailViewModel.clearSelection()
            appState.moreSubscreen == MoreSubscreen.OrganizeInbox && organizeInboxState.isSelectionActive ->
                organizeInboxViewModel.clearSelection()
            appState.moreSubscreen == MoreSubscreen.OrganizeExcluded && organizeExcludedState.isSelectionActive ->
                organizeExcludedViewModel.clearSelection()
            appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null -> albumBack()
            appState.selectedTab == MainTab.Folders && appState.selectedFolder != null -> appState.folderBack()
            appState.moreSubscreen == MoreSubscreen.People && appState.selectedPerson != null -> appState.personBack()
            appState.moreSubscreen == MoreSubscreen.AdminUserEditor -> {
                appState.adminUserEditorId = null
                adminUsersViewModel.clearMessages()
                appState.moreSubscreen = MoreSubscreen.AdminUsers
            }
            appState.moreSubscreen == MoreSubscreen.AdminLibraryEditor -> {
                appState.adminLibraryEditorId = null
                adminLibrariesViewModel.clearMessages()
                appState.moreSubscreen = MoreSubscreen.AdminLibraries
            }
            appState.moreSubscreen == MoreSubscreen.AccountProfile && appState.profileOpenedFromMore -> {
                appState.moreSubscreen = null
            }
            appState.moreSubscreen != null -> { appState.moreSubscreen = parentMoreSubscreen(appState.moreSubscreen!!) }
            appState.selectedTab == MainTab.Search -> appState.searchBack()
            appState.selectedTab != MainTab.Timeline -> { appState.selectedTab = MainTab.Timeline }
        }
    }

    // Escritorio: Ctrl/Cmd+A selecciona lo mismo que el "Seleccionar todo" de
    // la barra de cada pantalla (el timeline no lo ofrece: se pagina sobre toda
    // la biblioteca). Solo selecciona: si ya está todo, no deselecciona.
    fun selectAllOf(selected: Int, total: Int, toggle: () -> Unit): (() -> Unit)? =
        if (total <= 0) null else ({ if (selected < total) toggle() })
    val selectAllShortcut: (() -> Unit)? = when {
        appState.assetDetail != null -> null
        appState.memoryDetail != null -> appState.memoryDetail?.let { memory ->
            selectAllOf(memorySelectionState.selection.size, memory.items.size) {
                memorySelectionViewModel.toggleSelectAll(memory.items.map { it.id })
            }
        }
        appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null ->
            selectAllOf(albumDetailState.selection.size, albumDetailState.items.size,
                albumDetailViewModel::toggleSelectAll)
        appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
            !folderDetailState.isSubfolderSelectionActive ->
            selectAllOf(folderDetailState.selection.size, folderDetailState.items.size,
                folderDetailViewModel::toggleSelectAll)
        appState.selectedTab == MainTab.Search ->
            selectAllOf(searchState.selection.size, searchState.results.size,
                searchViewModel::toggleSelectAll)
        appState.moreSubscreen == MoreSubscreen.Favorites ->
            selectAllOf(favoritesState.selection.size, favoritesState.items.size,
                favoritesViewModel::toggleSelectAll)
        appState.moreSubscreen == MoreSubscreen.Archived ->
            selectAllOf(archivedState.selection.size, archivedState.items.size,
                archivedViewModel::toggleSelectAll)
        appState.moreSubscreen == MoreSubscreen.People && appState.selectedPerson != null ->
            selectAllOf(personDetailState.selection.size, personDetailState.items.size,
                personDetailViewModel::toggleSelectAll)
        appState.moreSubscreen == MoreSubscreen.OrganizeInbox ->
            selectAllOf(organizeInboxState.selection.size, organizeInboxState.items.size,
                organizeInboxViewModel::toggleSelectAll)
        // Listas de álbumes y carpetas: todas las tarjetas visibles.
        appState.moreSubscreen == null && appState.selectedTab == MainTab.Albums && appState.selectedAlbum == null ->
            selectAllOf(albumsState.selectedAlbums.size, albumsState.visibleAlbums.size,
                albumsViewModel::toggleSelectAllVisible)
        appState.moreSubscreen == null && appState.selectedTab == MainTab.Folders && appState.selectedFolder == null ->
            selectAllOf(foldersState.selectedFolders.size, foldersState.visibleFolders.size,
                foldersViewModel::toggleSelectAllVisible)
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
        initialPage = navTabs.indexOf(appState.selectedTab).coerceAtLeast(0),
        pageCount = { navTabs.size }
    )
    // Whether an opaque overlay (drill-down / Buscar / More subscreen) is covering
    // the pager base layer. When true the overlay must paint its own solid
    // background, otherwise the tab body underneath shows through — the pager
    // keeps all top-level bodies composed behind it.
    val overlayVisible = appState.moreSubscreen != null ||
        (appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null) ||
        (appState.selectedTab == MainTab.Folders && appState.selectedFolder != null) ||
        appState.selectedTab == MainTab.Search
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
        navTabs.getOrNull(mainPagerState.currentPage) ?: appState.selectedTab
    } else {
        appState.selectedTab
    }
    // Identidad del destino que ocupa el overlay. Cambiarla es lo que dispara la
    // transición de entrada; navegar dentro del MISMO destino (abrir el visor,
    // seleccionar fotos) la deja quieta.
    val overlayKey: Any = when {
        appState.moreSubscreen != null ->
            "more:${appState.moreSubscreen!!.name}:${appState.selectedPerson?.id ?: ""}"
        appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null ->
            "album:${appState.selectedAlbum!!.id}"
        appState.selectedTab == MainTab.Folders && appState.selectedFolder != null ->
            "folder:${appState.selectedFolder!!.id}"
        appState.selectedTab == MainTab.Search -> "search"
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
    // activa ceden el sitio a la cápsula de selección, que no se esconde: una
    // acción no puede escaparse scroll abajo. Sólo hay una a la vez, así que
    // comparten el estado de visibilidad.
    val floatingChromeSubscreen = when (appState.moreSubscreen) {
        MoreSubscreen.People ->
            appState.selectedPerson == null || !personDetailState.isSelectionActive
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
        // Los que tienen selección (DeviceBackupPending / Trash) ceden el sitio
        // a la cápsula de selección mientras haya algo seleccionado.
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
    LaunchedEffect(appState.moreSubscreen) { appState.subscreenChromeVisible = true }

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

    // Cápsula superior de selección de la pantalla visible; null sin selección.
    // Es una cápsula de cristal flotante, no una barra acoplada: se superpone al
    // contenido en el mismo hueco que el cromo flotante al que sustituye, y el
    // contenido sigue reservándolo, así que entrar en selección no mueve nada.
    // Por eso el Scaffold dibuja a sangre por arriba mientras haya una (ver
    // edgeToEdgeTop). Sin hazeState propio: difumina con la fuente del Scaffold.
    fun selectionChrome(content: @Composable () -> Unit) = content
    val selectionTopChrome: (@Composable () -> Unit)? = when {
        appState.selectedTab == MainTab.Timeline &&
            timelineState.isSelectionActive -> selectionChrome {
            AssetSelectionTopBar(
                selectedCount = timelineState.selection.size,
                isMutating = timelineState.isBulkMutating ||
                    actionsState.working != AssetActionWorking.Idle,
                onClose = timelineViewModel::clearSelection
                // Sin "Seleccionar todo": el timeline se pagina sobre toda
                // la biblioteca y el botón solo cogía lo cargado, que no es
                // lo que promete. La casilla de mes cubre el caso real.
            )
        }
        appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null &&
            albumDetailState.isSelectionActive -> selectionChrome {
            AssetSelectionTopBar(
                selectedCount = albumDetailState.selection.size,
                totalCount = albumDetailState.items.size,
                isMutating = albumDetailState.isBulkMutating ||
                    actionsState.working != AssetActionWorking.Idle,
                onClose = albumDetailViewModel::clearSelection,
                onSelectAll = albumDetailViewModel::toggleSelectAll
            )
        }
        appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null -> null
        appState.selectedTab == MainTab.Albums && albumsState.isSelectionActive -> selectionChrome {
            // Si la tarjeta ya no está en la lista (filtrada, borrada) queda
            // solo el cerrar: antes caía a la barra acoplada de Álbumes.
            val selected = albumsState.selectedAlbums
            com.photonne.app.ui.main.AlbumCardSelectionTopBar(
                albumName = selected.singleOrNull()?.name ?: "",
                isMutating = albumsState.isMutating,
                onClose = albumsViewModel::clearSelection,
                selectedCount = selected.size,
                totalCount = albumsState.visibleAlbums.size,
                onSelectAll = albumsViewModel::toggleSelectAllVisible
            )
        }
        appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
            folderDetailState.isSelectionActive -> selectionChrome {
            AssetSelectionTopBar(
                selectedCount = folderDetailState.selection.size,
                totalCount = folderDetailState.items.size,
                isMutating = folderDetailState.isBulkMutating ||
                    actionsState.working != AssetActionWorking.Idle,
                onClose = folderDetailViewModel::clearSelection,
                onSelectAll = folderDetailViewModel::toggleSelectAll
            )
        }
        appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
            folderDetailState.isSubfolderSelectionActive -> selectionChrome {
            val subfolder = folderDetailState.selectedSubfolder
            com.photonne.app.ui.main.FolderCardSelectionTopBar(
                folderName = (subfolder?.name ?: "").ifBlank { subfolder?.path ?: "" },
                isMutating = folderDetailState.isMutating,
                onClose = folderDetailViewModel::clearSubfolderSelection,
                // La rejilla del detalle va a sangre bajo la status bar.
                statusBarScrim = true
            )
        }
        appState.selectedTab == MainTab.Folders && appState.selectedFolder != null -> null
        appState.selectedTab == MainTab.Folders && foldersState.isSelectionActive -> selectionChrome {
            val selected = foldersState.selectedFolders
            com.photonne.app.ui.main.FolderCardSelectionTopBar(
                folderName = selected.singleOrNull()?.let { it.name.ifBlank { it.path } } ?: "",
                isMutating = foldersState.isMutating,
                onClose = foldersViewModel::clearSelection,
                selectedCount = selected.size,
                totalCount = foldersState.visibleFolders.size,
                onSelectAll = foldersViewModel::toggleSelectAllVisible
            )
        }
        appState.selectedTab == MainTab.Search && searchState.isSelectionActive -> selectionChrome {
            AssetSelectionTopBar(
                selectedCount = searchState.selection.size,
                totalCount = searchState.results.size,
                isMutating = searchState.isBulkMutating ||
                    actionsState.working != AssetActionWorking.Idle,
                onClose = searchViewModel::clearSelection,
                onSelectAll = searchViewModel::toggleSelectAll,
                statusBarScrim = false
            )
        }
        appState.selectedTab == MainTab.Search -> null
        appState.moreSubscreen == MoreSubscreen.DeviceBackupPending &&
            deviceBackupState.selectedCount > 0 -> selectionChrome {
            // Same contextual selection capsule as Timeline/Albums, with a
            // select-all action for queueing every pending file at once.
            AssetSelectionTopBar(
                selectedCount = deviceBackupState.selectedCount,
                isMutating = deviceBackupState.isSyncing,
                onClose = deviceBackupViewModel::clearSelection,
                statusBarScrim = false,
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
        }
        appState.moreSubscreen == MoreSubscreen.OrganizeInbox &&
            organizeInboxState.isSelectionActive -> selectionChrome {
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
        }
        // Apartadas: con selección, la cápsula lleva "Devolver a la bandeja"
        // (una sola acción, como Restaurar en la papelera).
        appState.moreSubscreen == MoreSubscreen.OrganizeExcluded &&
            organizeExcludedState.isSelectionActive -> selectionChrome {
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
                    Text(
                        stringResource(Res.string.organize_excluded_action_include),
                        maxLines = 1
                    )
                }
            }
        }
        appState.moreSubscreen == MoreSubscreen.People &&
            appState.selectedPerson != null && personDetailState.isSelectionActive -> selectionChrome {
            AssetSelectionTopBar(
                selectedCount = personDetailState.selection.size,
                totalCount = personDetailState.items.size,
                isMutating = personDetailState.isBulkMutating ||
                    actionsState.working != AssetActionWorking.Idle,
                onClose = personDetailViewModel::clearSelection,
                onSelectAll = personDetailViewModel::toggleSelectAll
            )
        }
        appState.moreSubscreen == MoreSubscreen.Favorites &&
            favoritesState.isSelectionActive -> selectionChrome {
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
        }
        appState.moreSubscreen == MoreSubscreen.Archived &&
            archivedState.isSelectionActive -> selectionChrome {
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
        }
        // Personal tab in selection mode: restore/purge selected.
        appState.moreSubscreen == MoreSubscreen.Trash &&
            appState.trashTab == com.photonne.app.ui.library.TrashTab.Personal &&
            trashState.isSelectionActive -> selectionChrome {
            com.photonne.app.ui.main.TrashSelectionTopBar(
                selectedCount = trashState.selection.size,
                isMutating = trashState.isBulkMutating,
                onClose = trashViewModel::clearSelection,
                onRestore = { trashViewModel.bulkRestore() },
                onPurge = { appState.showPurgeSelected = true }
            )
        }
        else -> null
    }

    val topBar: @Composable () -> Unit = {
        val selectionBar = selectionTopChrome
        if (selectionBar != null) selectionBar() else when {
            // AlbumDetailScreen paints its own floating top chrome over the
            // grid (docked on the hero's cover, frosted capsules once
            // scrolled), like Fotos, so no separate top bar here.
            appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null -> {
            }
            // El detalle de carpeta pinta su propio cromo flotante dentro de la
            // pantalla (título de la carpeta + acciones en la cápsula).
            appState.selectedTab == MainTab.Folders && appState.selectedFolder != null -> {
            }
            // Buscar pinta su propio cromo flotante (campo + modo + filtros).
            appState.selectedTab == MainTab.Search -> {
            }
            appState.moreSubscreen == MoreSubscreen.Upload ->
                com.photonne.app.ui.main.UploadTopBar(
                    title = stringResource(Res.string.upload_title),
                    subtitle = if (uploadState.pendingCount > 0)
                        stringResource(
                            Res.string.upload_subtitle_pending,
                            uploadState.pendingCount
                        )
                    else null,
                    onBack = { appState.moreSubscreen = null }
                )
            // Cromo flotante dibujado dentro de la pantalla.
            appState.moreSubscreen == MoreSubscreen.DeviceBackup -> { }
            // Cromo flotante dibujado dentro de la pantalla (con selección manda
            // la cápsula de selección, arriba).
            appState.moreSubscreen == MoreSubscreen.DeviceBackupPending -> { }
            appState.moreSubscreen == MoreSubscreen.EnrichmentStatus -> { }
            appState.moreSubscreen == MoreSubscreen.Utilities -> { }
            appState.moreSubscreen == MoreSubscreen.MyLinks -> { }
            // "Archivos no compatibles" pinta su propio cromo flotante dentro de la pantalla.
            appState.moreSubscreen == MoreSubscreen.UnsupportedFiles -> {
            }
            // Para organizar pinta su propio cromo flotante (con "Mover por
            // condiciones" en su cápsula de acciones); con una selección activa
            // manda la cápsula de selección, arriba.
            appState.moreSubscreen == MoreSubscreen.OrganizeInbox -> {
            }
            appState.moreSubscreen == MoreSubscreen.OrganizeRule -> { }
            appState.moreSubscreen == MoreSubscreen.OrganizeExcluded -> { }
            appState.moreSubscreen == MoreSubscreen.UtilitiesDuplicates -> { }
            appState.moreSubscreen == MoreSubscreen.UtilitiesLargeFiles -> { }
            appState.moreSubscreen == MoreSubscreen.UtilitiesLocations -> { }
            // Recuerdos / Escenas / Objetos / Mapa pintan su propio cromo
            // flotante dentro de la pantalla (ver floatingChromeSubscreen), así
            // que aquí no va ninguna barra.
            appState.moreSubscreen == MoreSubscreen.Memories ||
                appState.moreSubscreen == MoreSubscreen.ExploreScenes ||
                appState.moreSubscreen == MoreSubscreen.ExploreObjects ||
                appState.moreSubscreen == MoreSubscreen.Map -> {
            }
            // Sugerencias de una persona pinta su propio cromo flotante.
            appState.moreSubscreen == MoreSubscreen.PeopleSuggestions -> {
            }
            // La lista de Personas Y el detalle pintan su propio cromo flotante
            // (menú de recluster / ocultas o de renombrar / fusionar en su cápsula
            // de acciones); aquí no va barra acoplada.
            appState.moreSubscreen == MoreSubscreen.People -> {
            }
            // Favoritos pinta su propio cromo flotante dentro de la pantalla
            // (ver floatingChromeSubscreen); aquí no va barra acoplada.
            appState.moreSubscreen == MoreSubscreen.Favorites -> {
            }
            // Archivados pinta su propio cromo flotante dentro de la pantalla.
            appState.moreSubscreen == MoreSubscreen.Archived -> {
            }
            // Papelera (sin selección): cromo flotante dibujado en el contenido,
            // con las acciones restaurar-todo / vaciar en su cápsula (solo en la
            // pestaña Personal).
            appState.moreSubscreen == MoreSubscreen.Trash -> { }
            // Todas estas subpantallas pintan su propio cromo flotante estático
            // dentro de la pantalla (título + atrás, y acciones en su cápsula
            // cuando las tienen); aquí no va barra acoplada.
            appState.moreSubscreen == MoreSubscreen.Notifications -> { }
            appState.moreSubscreen == MoreSubscreen.AccountSettings -> { }
            appState.moreSubscreen == MoreSubscreen.AccountProfile -> { }
            appState.moreSubscreen == MoreSubscreen.AccountSecurity -> { }
            appState.moreSubscreen == MoreSubscreen.AccountAppearance -> { }
            appState.moreSubscreen == MoreSubscreen.AccountStorage -> { }
            appState.moreSubscreen == MoreSubscreen.AccountConnection -> { }
            appState.moreSubscreen == MoreSubscreen.Administration -> { }
            appState.moreSubscreen == MoreSubscreen.AdminUsers -> { }
            appState.moreSubscreen == MoreSubscreen.AdminUserEditor -> { }
            appState.moreSubscreen == MoreSubscreen.AdminLibraries -> { }
            appState.moreSubscreen == MoreSubscreen.AdminLibraryEditor -> { }
            appState.moreSubscreen == MoreSubscreen.AdminStats -> { }
            appState.moreSubscreen == MoreSubscreen.AdminSettingsHub -> { }
            appState.moreSubscreen == MoreSubscreen.AdminSystemHub -> { }
            isAdminSettingsSubpage(appState.moreSubscreen) -> { }
            isAdminSystemSubpage(appState.moreSubscreen) -> { }
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
        appState.selectedTab == MainTab.Timeline &&
            timelineState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = timelineState.selection.size,
                    isMutating = timelineState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(timelineState.selection.toList())
                    },
                    onAddToAlbum = { appState.bulkAddToAlbum = true },
                    onMove = { appState.showMoveSelectedAssetsTimeline = true },
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
        appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null &&
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
                    if (appState.selectedAlbum?.isShared == true) Res.plurals.album_trash_warning_shared
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
                    onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Album },
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
                                appState.moveSelectionError = null
                                appState.moveSelectionRequest = MoveSelectionRequest(
                                    assetIds = albumDetailState.selection.toList(),
                                    onMoved = albumDetailViewModel::clearSelection
                                )
                            }
                        }
                    },
                    // En un álbum inteligente el contenido lo deciden las
                    // reglas: ni quitar fotos ni fijar portada aplican.
                    onRemoveFromAlbum = if (appState.selectedAlbum?.isSmart != true &&
                        (appState.selectedAlbum?.canWrite == true ||
                            appState.selectedAlbum?.isOwner == true)
                    ) {
                        {
                            val albumId = appState.selectedAlbum?.id
                            albumDetailViewModel.bulkRemoveFromAlbum(
                                onSuccess = { removed ->
                                    appState.selectedAlbum?.let {
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
                        appState.selectedAlbum?.isSmart != true &&
                        (appState.selectedAlbum?.canWrite == true || appState.selectedAlbum?.isOwner == true)
                    ) {
                        {
                            val assetId = albumDetailState.selection.first()
                            albumDetailViewModel.setCover(assetId) { updated ->
                                albumsViewModel.applyUpdate(updated)
                                appState.selectedAlbum = appState.selectedAlbum?.copy(
                                    coverThumbnailUrl = updated.coverThumbnailUrl
                                )
                                albumDetailViewModel.clearSelection()
                            }
                        }
                    } else null
                )
            }
        }
        appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
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
                        onRename = { appState.showEditSubfolder = true },
                        onDelete = { appState.showDeleteSubfolder = true }
                    )
                }
            } else null
        }
        appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
            folderDetailState.isSelectionActive -> {
            {
                // Sin CanDelete en la carpeta (o en una biblioteca externa) el
                // servidor rechaza la papelera: se desactiva con el motivo.
                val folderTrashBlocked = if (appState.selectedFolder?.canDelete == false ||
                    appState.selectedFolder?.externalLibraryId != null
                ) stringResource(Res.string.selection_trash_blocked_folder) else null
                AssetSelectionBottomBar(
                    selectedCount = folderDetailState.selection.size,
                    trashDisabledReason = folderTrashBlocked,
                    isMutating = folderDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(folderDetailState.selection.toList())
                    },
                    onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Folder },
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
                    onMove = if (appState.selectedFolder?.canWrite == true &&
                        appState.selectedFolder?.externalLibraryId == null
                    ) {
                        { appState.showMoveSelectedAssets = true }
                    } else null
                )
            }
        }
        appState.selectedTab == MainTab.Search && searchState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = searchState.selection.size,
                    isMutating = searchState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(searchState.selection.toList())
                    },
                    onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Search },
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
                                appState.moveSelectionError = null
                                appState.moveSelectionRequest = MoveSelectionRequest(
                                    assetIds = searchState.selection.toList(),
                                    onMoved = searchViewModel::clearSelection
                                )
                            }
                        }
                    },
                )
            }
        }
        appState.moreSubscreen == MoreSubscreen.People &&
            appState.selectedPerson != null && personDetailState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = personDetailState.selection.size,
                    isMutating = personDetailState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(personDetailState.selection.toList())
                    },
                    onAddToAlbum = { appState.bulkAddSource = BulkAddSource.People },
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
                            appState.selectedPerson?.let { p ->
                                val newCount = (p.faceCount - detached).coerceAtLeast(0)
                                appState.selectedPerson = p.copy(faceCount = newCount)
                            }
                        }
                    }
                )
            }
        }
        appState.moreSubscreen == MoreSubscreen.OrganizeInbox &&
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
                    onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Inbox },
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
                        appState.showMoveSelectedAssetsInbox = true
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
        appState.moreSubscreen == MoreSubscreen.Favorites &&
            favoritesState.isSelectionActive -> {
            {
                AssetSelectionBottomBar(
                    selectedCount = favoritesState.selection.size,
                    isMutating = favoritesState.isBulkMutating ||
                        actionsState.working != AssetActionWorking.Idle,
                    onShare = {
                        actionsViewModel.beginShare(favoritesState.selection.toList())
                    },
                    onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Favorites },
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
                                appState.moveSelectionError = null
                                appState.moveSelectionRequest = MoveSelectionRequest(
                                    assetIds = favoritesState.selection.toList(),
                                    onMoved = favoritesViewModel::clearSelection
                                )
                            }
                        }
                    },
                )
            }
        }
        // Duplicados: con algo marcado, la cápsula de confirmar borrar sustituye
        // a la nav, como la de "Mover" en Para organizar (antes era un FAB).
        appState.moreSubscreen == MoreSubscreen.UtilitiesDuplicates &&
            utilitiesDuplicatesState.totalSelectedCount > 0 -> {
            {
                com.photonne.app.ui.main.ConfirmCapsule(
                    label = stringResource(
                        Res.string.utilities_duplicates_action_delete,
                        utilitiesDuplicatesState.totalSelectedCount,
                        com.photonne.app.ui.format.humanBytes(utilitiesDuplicatesState.totalSelectedBytes)
                    ),
                    enabled = !utilitiesDuplicatesState.isDeleting,
                    isWorking = utilitiesDuplicatesState.isDeleting,
                    icon = com.photonne.app.ui.theme.PhotonneIcons.Delete,
                    destructive = true,
                    onClick = { appState.showDuplicatesConfirm = true }
                )
            }
        }
        appState.moreSubscreen == MoreSubscreen.Archived &&
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
                    onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Archive },
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
                                appState.moveSelectionError = null
                                appState.moveSelectionRequest = MoveSelectionRequest(
                                    assetIds = archivedState.selection.toList(),
                                    onMoved = archivedViewModel::clearSelection
                                )
                            }
                        }
                    },
                )
            }
        }
        appState.selectedTab == MainTab.Albums && albumsState.isSelectionActive -> {
            val selected = albumsState.selectedAlbums
            // Una sola tarjeta: sus acciones de siempre. Varias: solo las que
            // valen para todas (albumSelectionActions), en bloque.
            val target = selected.singleOrNull()
            val allowed = com.photonne.app.ui.album.albumSelectionActions(selected)
            if (selected.isNotEmpty()) {
                {
                    val deleteBlocked = stringResource(Res.string.album_bulk_delete_not_allowed)
                    val shortcutSnackbar = LocalSnackbarController.current
                    fun requestDelete() {
                        if (target != null) {
                            appState.pendingActionAlbum = target
                            appState.showDeleteAlbum = true
                        } else {
                            appState.showBulkDeleteAlbums = true
                        }
                    }
                    // Escritorio: Supr con tarjetas seleccionadas = Eliminar.
                    com.photonne.app.ui.selection.SelectionShortcutsHandler(
                        onDelete = {
                            when {
                                albumsState.isMutating -> Unit
                                !allowed.canDelete -> shortcutSnackbar?.show(deleteBlocked)
                                else -> requestDelete()
                            }
                        }
                    )
                    val togglePin = com.photonne.app.ui.album.rememberAlbumPinToggle(albumsViewModel)
                    com.photonne.app.ui.main.AlbumCardSelectionBottomBar(
                        canPin = allowed.canPin,
                        isPinned = target?.isPinned == true,
                        onTogglePin = {
                            target?.let { togglePin(it.id); albumsViewModel.clearSelection() }
                        },
                        canManageMembers = allowed.canManageMembers,
                        canEdit = allowed.canEdit,
                        canLeave = allowed.canLeave,
                        canDelete = allowed.canDelete,
                        isMutating = albumsState.isMutating,
                        onManageMembers = {
                            if (target != null) {
                                appState.pendingActionAlbum = target
                                albumPermissionsViewModel.open(target.id)
                                appState.showMembers = true
                            }
                        },
                        onEdit = {
                            // Un álbum inteligente propio se edita entero (condiciones
                            // incluidas) en su editor; el resto, nombre y descripción.
                            if (target == null) {
                                Unit
                            } else if (target.isSmart && target.isOwner) {
                                albumsViewModel.clearSelection()
                                appState.editingSmartAlbum = target
                                appState.moreSubscreen = MoreSubscreen.SmartAlbumEditor
                            } else {
                                appState.pendingActionAlbum = target
                                appState.showEditAlbum = true
                            }
                        },
                        onLeave = {
                            if (target != null) {
                                appState.pendingActionAlbum = target
                                appState.showLeaveAlbum = true
                            } else {
                                appState.showBulkLeaveAlbums = true
                            }
                        },
                        onDelete = ::requestDelete
                    )
                }
            } else null
        }
        appState.selectedTab == MainTab.Folders && foldersState.isSelectionActive -> {
            val selected = foldersState.selectedFolders
            val target = selected.singleOrNull()
            // Permisos por tarjeta y sin tocar bibliotecas externas: ver
            // folderSelectionActions. Con varias, solo lo que vale para todas.
            val allowed = com.photonne.app.ui.folder.folderSelectionActions(selected)
            if (selected.isNotEmpty()) {
                {
                    val deleteBlocked = stringResource(Res.string.folder_bulk_delete_not_allowed)
                    val shortcutSnackbar = LocalSnackbarController.current
                    fun requestDelete() {
                        if (target != null) {
                            appState.pendingActionFolder = target
                            appState.showDeleteFolder = true
                        } else {
                            appState.showBulkDeleteFolders = true
                        }
                    }
                    com.photonne.app.ui.selection.SelectionShortcutsHandler(
                        onDelete = {
                            when {
                                foldersState.isMutating -> Unit
                                !allowed.canDelete -> shortcutSnackbar?.show(deleteBlocked)
                                else -> requestDelete()
                            }
                        }
                    )
                    com.photonne.app.ui.main.FolderCardSelectionBottomBar(
                        canManageMembers = allowed.canManageMembers,
                        canRename = allowed.canRename,
                        canDelete = allowed.canDelete,
                        isMutating = foldersState.isMutating,
                        onManageMembers = {
                            if (target != null) {
                                appState.pendingActionFolder = target
                                folderPermissionsViewModel.open(target.id)
                                appState.showFolderMembers = true
                            }
                        },
                        onRename = {
                            if (target != null) {
                                appState.pendingActionFolder = target
                                appState.showEditFolder = true
                            }
                        },
                        onDelete = ::requestDelete,
                        canToggleTimeline = allowed.canToggleTimeline,
                        excludedFromDiscovery = target?.excludedFromDiscovery ?: false,
                        onToggleTimeline = {
                            if (target != null) {
                                foldersViewModel.setTimelineIncluded(
                                    folderId = target.id,
                                    included = target.excludedFromDiscovery
                                )
                            }
                        },
                        canMove = allowed.canMove,
                        onMove = { appState.showBulkMoveFolders = true }
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
    LaunchedEffect(appState.assetDetail) {
        val ctx = appState.assetDetail
        if (ctx != null) {
            appState.currentDetailAssetId = ctx.items.getOrNull(ctx.startIndex)?.id
        } else {
            kotlinx.coroutines.delay(360)
            appState.currentDetailAssetId = null
            appState.viewerReturnState.clear()
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
        appState.moreSubscreen == null &&
        !timelineState.isSelectionActive
    val albumsImmersive = chromeTab == MainTab.Albums &&
        appState.moreSubscreen == null &&
        !(appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null) &&
        !albumsState.isSelectionActive
    val foldersImmersive = chromeTab == MainTab.Folders &&
        appState.moreSubscreen == null &&
        !(appState.selectedTab == MainTab.Folders && appState.selectedFolder != null) &&
        !foldersState.isSelectionActive
    val moreImmersive = chromeTab == MainTab.More && appState.moreSubscreen == null
    // Buscar dibuja su propio cromo flotante (campo + modo + filtros) que se acopla
    // y se oculta al scroll; con una selección activa vuelve la barra acoplada.
    val searchImmersive = appState.selectedTab == MainTab.Search &&
        appState.moreSubscreen == null &&
        !searchState.isSelectionActive
    // Inside an open album / folder: the photo grid gets the same immersive
    // treatment (nav hides on scroll, grid bleeds behind it), unless a
    // selection is active (which shows its own bottom action bar). These are
    // overlays, so they stay keyed off selectedTab.
    val albumDetailImmersive = appState.selectedTab == MainTab.Albums &&
        appState.moreSubscreen == null &&
        appState.selectedAlbum != null &&
        !albumDetailState.isSelectionActive
    val folderDetailImmersive = appState.selectedTab == MainTab.Folders &&
        appState.moreSubscreen == null &&
        appState.selectedFolder != null &&
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
        appState.moreSubscreen == null &&
        timelineState.isSelectionActive
    val albumsSelecting = chromeTab == MainTab.Albums &&
        appState.moreSubscreen == null &&
        appState.selectedAlbum == null &&
        albumsState.isSelectionActive
    val foldersSelecting = chromeTab == MainTab.Folders &&
        appState.moreSubscreen == null &&
        appState.selectedFolder == null &&
        foldersState.isSelectionActive
    val albumDetailSelecting = appState.selectedTab == MainTab.Albums &&
        appState.moreSubscreen == null &&
        appState.selectedAlbum != null &&
        albumDetailState.isSelectionActive
    val folderDetailSelecting = appState.selectedTab == MainTab.Folders &&
        appState.moreSubscreen == null &&
        appState.selectedFolder != null &&
        (folderDetailState.isSelectionActive ||
            folderDetailState.isSubfolderSelectionActive)

    // Tap on a nav tab (or any programmatic tab change) glides the pager over.
    LaunchedEffect(appState.selectedTab) {
        val idx = navTabs.indexOf(appState.selectedTab)
        if (idx >= 0 && mainPagerState.currentPage != idx) {
            mainPagerState.animateScrollToPage(idx)
        }
    }
    // A settled swipe adopts that page as the active tab, running the same side
    // effects a tap would. Guarded so a programmatic settle (or being parked on
    // Buscar) never fights the effect above.
    LaunchedEffect(mainPagerState.settledPage) {
        val tab = navTabs.getOrNull(mainPagerState.settledPage)
        if (tab != null && tab != appState.selectedTab && appState.selectedTab in navTabs) {
            appState.switchTab(tab)
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
        LocalCurrentDetailAssetId provides appState.currentDetailAssetId,
        com.photonne.app.ui.grid.LocalViewerReturn provides appState.viewerReturnState,
        LocalSnackbarController provides snackbarController
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        MainScaffold(
            selectedTab = appState.selectedTab,
            // Tapping any bottom-nav tab also dismisses an open subscreen layer
            // (People / Map / Explore facets, or any More destination) so it
            // never lingers over the newly selected tab — see [switchTab].
            onTabSelected = { tab -> appState.switchTab(tab) },
            topBar = topBar,
            bottomBar = bottomBar,
            moreTabUnreadCount = notificationsState.unreadCount,
            // Every bare pager tab draws to the top of the screen and paints its
            // own top bar inside its page (Fotos' floating bar; Álbumes/Carpetas/
            // Más a docked bar) so the bar travels with the swipe. The Scaffold
            // only reserves top space again for a docked overlay bar (Subir).
            //
            // Lo mismo para todo lo que pinte su PROPIO cromo flotante: si no,
            // el inset de la status bar se cuenta DOS veces. Este Scaffold, con
            // un slot `topBar` que no emite nada (justo lo que hacen esas ramas),
            // no deja el hueco a cero: lo rellena con el inset del sistema. Y el
            // cromo de cada pantalla vuelve a aplicárselo por su cuenta.
            //
            // Y con una selección: su cápsula flota sobre el contenido en el hueco
            // que este ya reserva para su cromo, no aparta nada.
            edgeToEdgeTop = pagerBareTop || floatingChromeSubscreen ||
                albumDetailImmersive || folderDetailImmersive || searchImmersive ||
                selectionTopChrome != null,
            // On the immersive tabs the bottom nav hides while scrolling down
            // (driven by each screen's chrome), and always shows elsewhere.
            bottomBarVisible = when {
                timelineImmersive -> appState.timelineChromeVisible
                albumsImmersive -> appState.albumsChromeVisible
                foldersImmersive -> appState.foldersChromeVisible
                moreImmersive -> appState.moreChromeVisible
                searchImmersive -> appState.searchChromeVisible
                albumDetailImmersive -> appState.albumDetailChromeVisible
                folderDetailImmersive -> appState.folderDetailChromeVisible
                floatingChromeSubscreen -> appState.subscreenChromeVisible
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
                (appState.moreSubscreen != null &&
                    appState.moreSubscreen != MoreSubscreen.SmartAlbumEditor) ||
                appState.selectedTab == MainTab.Search ||
                (appState.selectedTab == MainTab.More && appState.moreSubscreen == null)
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
                        scrollToTopTick = appState.timelineScrollToTopTick,
                        // El pager principal compone esta página también como
                        // vecina: la tira de Recuerdos solo anima cuando Fotos
                        // es de verdad la pestaña visible.
                        memoriesAutoPlay = appState.selectedTab == MainTab.Timeline &&
                            appState.assetDetail == null,
                        onOpenAsset = { mergedItems, mergedIndex, feed ->
                            appState.assetDetail = AssetDetailContext(
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
                            appState.selectedTab = MainTab.More
                            appState.moreSubscreen = MoreSubscreen.Upload
                        },
                        backupPendingCount = if (deviceBackupState.isBackupEnabled) {
                            deviceBackupState.pendingEntries.size
                        } else 0,
                        onOpenBackup = {
                            appState.selectedTab = MainTab.More
                            appState.moreSubscreen = MoreSubscreen.DeviceBackup
                        },
                        onJumpToDate = { appState.showJumpToDate = true },
                        onOpenSearch = { appState.selectedTab = MainTab.Search },
                        onChromeVisibleChange = { appState.timelineChromeVisible = it },
                        pendingJumpDate = appState.pendingJumpDate,
                        onJumpHandled = { appState.pendingJumpDate = null },
                        memories = memoriesState.items,
                        onOpenMemory = { memory -> appState.memoryDetail = memory },
                        onSeeAllMemories = { appState.moreSubscreen = MoreSubscreen.Memories }
                    )
                    MainTab.Albums -> Column(modifier = Modifier.fillMaxSize()) {
                        // La búsqueda va DENTRO de la cápsula flotante que dibuja
                        // AlbumsListScreen (campo en titleContent), como el buscador;
                        // ya no hay barra acoplada aquí.
                        Box(modifier = Modifier.weight(1f)) {
                            AlbumsListScreen(
                                onAlbumClick = { album ->
                                    if (albumsState.isSelectionActive) {
                                        // Con la selección abierta, tocar suma o
                                        // quita; al quitar la última se cierra.
                                        albumsViewModel.toggleAlbumSelection(album.id)
                                    } else {
                                        appState.selectedAlbum = album
                                    }
                                },
                                onAlbumLongPress = { album ->
                                    albumsViewModel.selectAlbum(album.id)
                                },
                                onCreateAlbum = { appState.showAlbumTypeChooser = true },
                                // Explorar cards open their screen as a modal layer
                                // over the Albums tab (no tab switch) so back
                                // returns here and the bottom nav stays on Álbumes.
                                onOpenPeople = {
                                    appState.selectedPerson = null
                                    appState.moreSubscreen = MoreSubscreen.People
                                },
                                onOpenMap = { appState.moreSubscreen = MoreSubscreen.Map },
                                onOpenScenes = { appState.moreSubscreen = MoreSubscreen.ExploreScenes },
                                onOpenObjects = { appState.moreSubscreen = MoreSubscreen.ExploreObjects },
                                onOpenFilters = { appState.showAlbumsFilters = true },
                                immersive = albumsImmersive,
                                onChromeVisibleChange = { appState.albumsChromeVisible = it },
                                scrollToTopTick = appState.albumsScrollToTopTick
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
                            { appState.showCreateFolder = true }
                        } else null
                        Box(modifier = Modifier.weight(1f)) {
                            com.photonne.app.ui.folder.FoldersListScreen(
                                onFolderClick = { folder ->
                                    if (foldersState.isSelectionActive) {
                                        foldersViewModel.toggleFolderSelection(folder.id)
                                    } else {
                                        appState.selectedFolder = folder
                                    }
                                },
                                onFolderLongPress = { folder ->
                                    foldersViewModel.selectFolder(folder.id)
                                },
                                onOpenOrganize = { appState.moreSubscreen = MoreSubscreen.OrganizeInbox },
                                // Como People/Map desde Álbumes: capa modal sobre la
                                // pestaña, sin cambiar de tab. La tarjeta solo se
                                // muestra donde hay buckets, así que el callback
                                // puede ser incondicional.
                                onOpenDeviceFolders = {
                                    appState.moreSubscreen = MoreSubscreen.DeviceFolders
                                },
                                onOpenFilters = { appState.showFoldersFilters = true },
                                onCreateFolder = foldersCreate,
                                immersive = foldersImmersive,
                                onChromeVisibleChange = { appState.foldersChromeVisible = it },
                                scrollToTopTick = appState.foldersScrollToTopTick
                            )
                        }
                    }
                    // Más pinta su propio cromo flotante dentro de la pantalla
                    // (título + acción Subir), como Fotos: nada de barra acoplada.
                    else -> MoreScreen(
                        user = user.user,
                        onLogout = onLogout,
                        onOpenFavorites = { appState.moreSubscreen = MoreSubscreen.Favorites },
                        onOpenArchived = { appState.moreSubscreen = MoreSubscreen.Archived },
                        onOpenTrash = {
                            appState.trashTab = com.photonne.app.ui.library.TrashTab.Personal
                            appState.moreSubscreen = MoreSubscreen.Trash
                        },
                        onOpenUtilities = { appState.moreSubscreen = MoreSubscreen.Utilities },
                        onOpenMyLinks = { appState.moreSubscreen = MoreSubscreen.MyLinks },
                        onOpenProfile = {
                            appState.profileOpenedFromMore = true
                            appState.moreSubscreen = MoreSubscreen.AccountProfile
                        },
                        onOpenDeviceBackup = { appState.moreSubscreen = MoreSubscreen.DeviceBackup },
                        backupPendingCount = if (deviceBackupState.isBackupEnabled) {
                            deviceBackupState.pendingEntries.size
                        } else 0,
                        backupStatus = remember(deviceBackupState) {
                            deviceBackupState.toMoreBackupStatus()
                        },
                        onOpenNotifications = {
                            appState.moreSubscreen = MoreSubscreen.Notifications
                        },
                        notificationsUnreadCount = notificationsState.unreadCount,
                        onOpenAccountSettings = {
                            appState.moreSubscreen = MoreSubscreen.AccountSettings
                        },
                        onOpenAdministration = if (
                            user.user.role.equals("Admin", ignoreCase = true)
                        ) {
                            { appState.moreSubscreen = MoreSubscreen.Administration }
                        } else {
                            null
                        },
                        onOpenUpload = { appState.moreSubscreen = MoreSubscreen.Upload },
                        onChromeVisibleChange = { appState.moreChromeVisible = it },
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
                val enteringForward = remember { appState.overlayForward }
                var entered by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    entered = true
                    // Consumida: la siguiente navegación vuelve a ser hacia
                    // dentro salvo que el back handler diga lo contrario.
                    appState.overlayForward = true
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
                appState.selectedTab == MainTab.Timeline && appState.moreSubscreen == null -> {
                    // shown by the pager base layer
                }
                appState.selectedTab == MainTab.Albums && appState.moreSubscreen == null -> {
                    val openedAlbum = appState.selectedAlbum
                    if (openedAlbum != null) {
                        AlbumDetailScreen(
                            album = openedAlbum,
                            onItemClick = { index ->
                                appState.assetDetail = AssetDetailContext(
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
                                appState.showShares = true
                            },
                            onEdit = {
                                if (openedAlbum.isSmart && openedAlbum.isOwner) {
                                    appState.editingSmartAlbum = openedAlbum.copy(
                                        name = albumDetailState.albumName ?: openedAlbum.name,
                                        description = albumDetailState.albumDescription ?: openedAlbum.description,
                                    )
                                    appState.moreSubscreen = MoreSubscreen.SmartAlbumEditor
                                } else {
                                    appState.showEditAlbum = true
                                }
                            },
                            onDelete = { appState.showDeleteAlbum = true },
                            onManageMembers = {
                                albumPermissionsViewModel.open(openedAlbum.id)
                                appState.showMembers = true
                            },
                            onLeave = { appState.showLeaveAlbum = true },
                            viewModel = albumDetailViewModel,
                            immersive = albumDetailImmersive,
                            onChromeVisibleChange = { appState.albumDetailChromeVisible = it }
                        )
                    }
                }
                appState.selectedTab == MainTab.Folders && appState.moreSubscreen == null -> {
                    val openedFolder = appState.selectedFolder
                    if (openedFolder != null) {
                        com.photonne.app.ui.folder.FolderDetailScreen(
                            folderId = openedFolder.id,
                            folderName = openedFolder.name.ifBlank { openedFolder.path },
                            parentFolderId = openedFolder.parentFolderId,
                            title = (folderDetailState.folderName ?: openedFolder.name)
                                .ifBlank { openedFolder.path },
                            onBack = { appState.folderBack() },
                            onItemClick = { index ->
                                if (folderDetailState.isSelectionActive) {
                                    folderDetailState.items.getOrNull(index)?.let {
                                        folderDetailViewModel.toggleSelection(it.id)
                                    }
                                } else {
                                    val folderState = folderDetailViewModel.state.value
                                    appState.assetDetail = AssetDetailContext(
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
                                    appState.folderBackStack.add(openedFolder)
                                    appState.selectedFolder = subfolder
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
                                    onEdit = { appState.showEditFolder = true },
                                    onMove = { appState.showMoveFolder = true },
                                    onDelete = { appState.showDeleteFolder = true },
                                    onManageMembers = {
                                        folderPermissionsViewModel.open(openedFolder.id)
                                        appState.showFolderMembers = true
                                    },
                                    canToggleTimeline = openedFolder.isShared &&
                                        openedFolder.externalLibraryId == null,
                                    excludedFromDiscovery = openedFolder.excludedFromDiscovery,
                                    onToggleTimeline = {
                                        val nextIncluded = openedFolder.excludedFromDiscovery
                                        foldersViewModel.setTimelineIncluded(
                                            openedFolder.id, included = nextIncluded
                                        )
                                        appState.selectedFolder = openedFolder.copy(
                                            excludedFromDiscovery = !nextIncluded
                                        )
                                    },
                                    onCreateSubfolder = if (!openedIsExternal && openedFolder.canWrite) {
                                        { appState.showCreateFolder = true }
                                    } else null
                                )
                            },
                            immersive = folderDetailImmersive,
                            onChromeVisibleChange = { appState.folderDetailChromeVisible = it }
                        )
                    }
                }
                appState.selectedTab == MainTab.Search && appState.moreSubscreen == null ->
                    com.photonne.app.ui.search.SearchScreen(
                    viewModel = searchViewModel,
                    onOpenFilters = { appState.showSearchFilters = true },
                    onBack = { appState.searchBack() },
                    onChromeVisibleChange = { appState.searchChromeVisible = it },
                    onItemClick = { index ->
                        if (searchState.isSelectionActive) {
                            searchState.results.getOrNull(index)?.let {
                                searchViewModel.toggleSelection(it.id)
                            }
                        } else {
                            appState.assetDetail = AssetDetailContext(
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
                else -> when (appState.moreSubscreen) {
                    null -> {
                        // The More grid is shown by the pager base layer; a
                        // non-null subscreen renders its screen on top.
                    }
                    MoreSubscreen.SmartAlbumEditor -> com.photonne.app.ui.album.smart.SmartAlbumEditorScreen(
                        editAlbum = appState.editingSmartAlbum,
                        onBack = {
                            appState.moreSubscreen = null
                            appState.editingSmartAlbum = null
                        },
                        onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                        onSaved = { saved ->
                            val wasEditing = appState.editingSmartAlbum != null
                            appState.moreSubscreen = null
                            appState.editingSmartAlbum = null
                            if (wasEditing) {
                                // La respuesta del PUT no trae portada ni miniaturas:
                                // se recarga la lista en vez de pisar la tarjeta.
                                albumsViewModel.refresh()
                                // Nuevas condiciones = otro contenido: si el álbum está
                                // abierto, se recarga al volver a él.
                                appState.selectedAlbum?.takeIf { it.id == saved.id }?.let { opened ->
                                    appState.selectedAlbum = opened.copy(
                                        name = saved.name,
                                        description = saved.description,
                                        assetCount = saved.assetCount,
                                    )
                                    albumDetailViewModel.refresh()
                                }
                            } else {
                                albumsViewModel.refresh()
                                appState.selectedTab = MainTab.Albums
                                appState.selectedAlbum = saved
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
                                appState.assetDetail = AssetDetailContext(
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
                        onAddBatchToAlbum = { appState.bulkAddSource = BulkAddSource.Upload },
                        onDismissBatch = uploadViewModel::dismissBatchSummary
                    )
                    MoreSubscreen.DeviceBackup ->
                        com.photonne.app.ui.devicebackup.BackupScreen(
                            title = stringResource(Res.string.device_backup_title),
                            onBack = { appState.moreSubscreen = null },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = deviceBackupViewModel,
                            enrichmentViewModel = enrichmentStatusViewModel,
                            gallery = deviceGallery,
                            onOpenPending = {
                                appState.moreSubscreen = MoreSubscreen.DeviceBackupPending
                            },
                            onOpenEnrichment = {
                                appState.moreSubscreen = MoreSubscreen.EnrichmentStatus
                            }
                        )
                    MoreSubscreen.DeviceBackupPending ->
                        com.photonne.app.ui.devicebackup.BackupPendingScreen(
                            title = stringResource(Res.string.backup_pending_screen_title),
                            onBack = { appState.moreSubscreen = MoreSubscreen.DeviceBackup },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = deviceBackupViewModel,
                            gallery = deviceGallery,
                            onOpenAsset = { item ->
                                appState.assetDetail = AssetDetailContext(
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.DeviceBackup },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = enrichmentStatusViewModel
                        )
                    MoreSubscreen.MyLinks ->
                        com.photonne.app.ui.album.MyLinksScreen(
                            title = stringResource(Res.string.my_links_title),
                            onBack = { appState.moreSubscreen = null },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.Utilities },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
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
                                    appState.assetDetail = AssetDetailContext(
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
                                appState.moreSubscreen = null
                                foldersViewModel.refreshOrganizeCount()
                            },
                            onOpenRules = { appState.moreSubscreen = MoreSubscreen.OrganizeRule },
                            onPickSuggestion = organizeInboxViewModel::selectSuggestion,
                            onSeeAllItems = organizeInboxViewModel::showAllItems,
                            onBackToSuggestions = organizeInboxViewModel::showSuggestions,
                            onApplySelection = organizeInboxViewModel::applySelection,
                            onOpenExcluded = {
                                organizeExcludedViewModel.refresh()
                                appState.moreSubscreen = MoreSubscreen.OrganizeExcluded
                            },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
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
                                    appState.assetDetail = AssetDetailContext(
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.OrganizeInbox },
                            onApplySelection = organizeExcludedViewModel::applySelection,
                            onErrorShown = organizeExcludedViewModel::clearError,
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                        )
                    MoreSubscreen.OrganizeRule ->
                        com.photonne.app.ui.organize.OrganizeRuleScreen(
                            title = stringResource(Res.string.organize_rule_title),
                            onBack = { appState.moreSubscreen = MoreSubscreen.OrganizeInbox },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            destinations = foldersState.moveDestinations,
                            viewModel = organizeRuleViewModel,
                            reviewOpen = organizeRuleState.reviewGroups != null
                        )
                    MoreSubscreen.Utilities ->
                        com.photonne.app.ui.utilities.UtilitiesHubScreen(
                            title = stringResource(Res.string.utilities_title),
                            onBack = { appState.moreSubscreen = null },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            onOpen = { entry ->
                                appState.moreSubscreen = when (entry) {
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.Utilities },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = utilitiesDuplicatesViewModel,
                            baseUrl = apiBaseUrl,
                            confirmOpen = appState.showDuplicatesConfirm,
                            onConfirmOpenChange = { appState.showDuplicatesConfirm = it },
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
                                appState.assetDetail = AssetDetailContext(
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.Utilities },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = utilitiesLargeFilesViewModel,
                            baseUrl = apiBaseUrl,
                            onAssetClick = { index, items ->
                                appState.assetDetail = AssetDetailContext(
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.Utilities },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
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
                                    appState.folderReturnTo = appState.selectedTab to MoreSubscreen.UtilitiesLocations
                                    appState.folderBackStack.clear()
                                    appState.selectedFolder = folder
                                    appState.moreSubscreen = null
                                    appState.selectedTab = MainTab.Folders
                                }
                            }
                        )
                    MoreSubscreen.Memories ->
                        com.photonne.app.ui.memories.MemoriesScreen(
                            viewModel = memoryFeedViewModel,
                            baseUrl = apiBaseUrl,
                            onOpenMemory = { detail ->
                                appState.memoryDetail = com.photonne.app.ui.memories.MemoryDetailContext(
                                    title = detail.title,
                                    subtitle = detail.subtitle,
                                    coverAssetId = detail.coverAssetId,
                                    items = detail.assets
                                )
                            },
                            onBack = { appState.moreSubscreen = null },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                        )
                    MoreSubscreen.ExploreScenes ->
                        com.photonne.app.ui.explore.ExploreScenesScreen(
                            viewModel = exploreFacetsViewModel,
                            // Tapping a scene jumps to the Search tab pre-filtered
                            // by that label — same flow as the PWA, where Explorar
                            // is just a deep-linking surface for the search engine.
                            onSceneClick = { label ->
                                searchViewModel.showResultsForSceneLabel(label)
                                appState.searchReturnTo = appState.selectedTab to MoreSubscreen.ExploreScenes
                                appState.moreSubscreen = null
                                appState.selectedTab = MainTab.Search
                            },
                            onBack = { appState.moreSubscreen = null },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                        )
                    MoreSubscreen.ExploreObjects ->
                        com.photonne.app.ui.explore.ExploreObjectsScreen(
                            viewModel = exploreFacetsViewModel,
                            onObjectClick = { label ->
                                searchViewModel.showResultsForObjectLabel(label)
                                appState.searchReturnTo = appState.selectedTab to MoreSubscreen.ExploreObjects
                                appState.moreSubscreen = null
                                appState.selectedTab = MainTab.Search
                            },
                            onBack = { appState.moreSubscreen = null },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
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
                            appState.assetDetail = AssetDetailContext(
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
                        onSheetPhotoOpen = { sheetPoints, index ->
                            // Thumbnail tap in the persistent sheet → open
                            // the viewer seeded with the sheet's whole list
                            // (viewport or tapped cluster) so it swipes.
                            val items = sheetPoints.map { it.toSyntheticTimelineItem() }
                            appState.assetDetail = AssetDetailContext(
                                items = items,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = { id, isFav ->
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                        },
                        onBulkAddToAlbum = { appState.bulkAddSource = BulkAddSource.Map },
                        onBack = { appState.moreSubscreen = null }
                    )
                    MoreSubscreen.PeopleSuggestions ->
                        com.photonne.app.ui.people.PersonSuggestionsScreen(
                            state = suggestionsState,
                            title = (suggestionsState.personName ?: appState.selectedPerson?.name).orEmpty(),
                            isBulkMutating = suggestionsState.isBulkMutating,
                            onAccept = personSuggestionsViewModel::acceptFace,
                            onDismissFace = personSuggestionsViewModel::dismissFace,
                            onLoadMore = personSuggestionsViewModel::loadMore,
                            onOpen = {
                                appState.selectedPerson?.let {
                                    personSuggestionsViewModel.open(it.id, it.name)
                                }
                            },
                            onBack = { appState.moreSubscreen = MoreSubscreen.People },
                            // Actúan también sobre las páginas no cargadas, así
                            // que primero confirman con el recuento del servidor.
                            onAcceptAll = { appState.showAcceptAllSuggestions = true },
                            onDismissAll = { appState.showDismissAllSuggestions = true },
                            onRefresh = personSuggestionsViewModel::refresh,
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                        )
                    MoreSubscreen.People -> {
                        val person = appState.selectedPerson
                        if (person == null) {
                            com.photonne.app.ui.people.PeopleScreen(
                                state = peopleState,
                                onPersonClick = { picked ->
                                    appState.selectedPerson = picked
                                    personDetailViewModel.open(picked.id, picked.name)
                                },
                                onLoadMore = peopleViewModel::loadMore,
                                onLoad = peopleViewModel::ensureLoaded,
                                onRefresh = peopleViewModel::refresh,
                                onBack = { appState.moreSubscreen = null },
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
                                onChromeVisibleChange = { appState.subscreenChromeVisible = it }
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
                                        appState.assetDetail = AssetDetailContext(
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
                                onBack = appState::personBack,
                                onRename = { appState.showRenamePerson = true },
                                onSuggestions = {
                                    personSuggestionsViewModel.open(person.id, person.name)
                                    appState.moreSubscreen = MoreSubscreen.PeopleSuggestions
                                },
                                onMerge = { appState.showMergePicker = true },
                                onToggleHidden = {
                                    if (person.isHidden) {
                                        peopleViewModel.unhide(person.id) {
                                            appState.selectedPerson = person.copy(isHidden = false)
                                        }
                                    } else {
                                        peopleViewModel.hide(person.id) {
                                            appState.selectedPerson = null
                                        }
                                    }
                                },
                                onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                            )
                        }
                    }
                    MoreSubscreen.DeviceFolders -> com.photonne.app.ui.folder.DeviceFoldersScreen(
                        backedUpUris = remember(deviceBackupState.folders) {
                            deviceBackupState.folders.map { it.uri }.toSet()
                        },
                        onAddToBackup = deviceBackupViewModel::onFolderPicked,
                        onOpenBucket = { bucket ->
                            appState.deviceFolderBucket = bucket
                            appState.moreSubscreen = MoreSubscreen.DeviceFolderDetail
                        },
                        onBack = { appState.moreSubscreen = null },
                        onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                    )
                    MoreSubscreen.DeviceFolderDetail -> appState.deviceFolderBucket?.let { bucket ->
                        com.photonne.app.ui.folder.DeviceFolderDetailScreen(
                            bucket = bucket,
                            onOpenAsset = { items, index ->
                                appState.assetDetail = AssetDetailContext(
                                    items = items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { _, _ -> }
                                )
                            },
                            onBack = { appState.moreSubscreen = MoreSubscreen.DeviceFolders },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
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
                                appState.assetDetail = AssetDetailContext(
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
                        onBack = { appState.moreSubscreen = null },
                        onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                    )
                    MoreSubscreen.Archived -> com.photonne.app.ui.library.ArchivedScreen(
                        state = archivedState,
                        onItemClick = { index ->
                            if (archivedState.isSelectionActive) {
                                archivedState.items.getOrNull(index)?.let {
                                    archivedViewModel.toggleSelection(it.id)
                                }
                            } else {
                                appState.assetDetail = AssetDetailContext(
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
                        onBack = { appState.moreSubscreen = null },
                        onUnarchiveAll = { appState.showUnarchiveAll = true },
                        onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                    )
                    MoreSubscreen.Trash -> Box(modifier = Modifier.fillMaxSize()) {
                        // La pantalla reserva siempre el hueco del cromo flotante:
                        // sin selección lo dibuja ella misma y, con selección, lo
                        // ocupa la cápsula de selección (rama del topBar).
                        val trashSelecting = trashState.isSelectionActive
                        // La rejilla de la papelera personal (hermana Haze +
                        // fuente de scroll) para que el cromo se acople en reposo.
                        val trashHazeState = remember { HazeState() }
                        val trashGridState = rememberLazyGridState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = subscreenChromeReservedTop())
                        ) {
                        when (appState.trashTab) {
                            com.photonne.app.ui.library.TrashTab.Personal ->
                                com.photonne.app.ui.library.TrashScreen(
                                    state = trashState,
                                    onItemClick = { index ->
                                        if (trashState.isSelectionActive) {
                                            trashState.items.getOrNull(index)?.let {
                                                trashViewModel.toggleSelection(it.id)
                                            }
                                        } else {
                                            appState.assetDetail = AssetDetailContext(
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
                                // Con el ámbito escondido en la hoja, el título
                                // dice cuál de las dos papeleras se ve.
                                title = stringResource(
                                    if (appState.trashTab == com.photonne.app.ui.library.TrashTab.Shared) {
                                        Res.string.admin_shared_trash
                                    } else Res.string.trash_title
                                ),
                                onBack = { appState.moreSubscreen = null },
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
                                onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                                // Restaurar todo / vaciar solo aplican a la papelera
                                // personal; en la compartida la propia pantalla pinta
                                // sus acciones, así que el ⋮ no sale. El filtro de
                                // ámbito sí, en las dos.
                                actions = {
                                    TrashChromeActions(
                                        tab = appState.trashTab,
                                        showBulkActions = appState.trashTab ==
                                            com.photonne.app.ui.library.TrashTab.Personal && count > 0,
                                        onOpenScope = { appState.showTrashScope = true },
                                        onRestoreAll = { appState.showRestoreAllTrash = true },
                                        onEmptyTrash = { appState.showEmptyTrash = true }
                                    )
                                }
                            )
                        }
                        if (appState.showTrashScope) {
                            com.photonne.app.ui.library.TrashScopeSheet(
                                selected = appState.trashTab,
                                onSelect = { tab ->
                                    if (tab != appState.trashTab) {
                                        // Leaving the personal tab drops its selection
                                        // so the top bar/back don't act on a hidden tab.
                                        trashViewModel.clearSelection()
                                        appState.trashTab = tab
                                    }
                                    appState.showTrashScope = false
                                },
                                onDismiss = { appState.showTrashScope = false }
                            )
                        }
                    }
                    MoreSubscreen.Notifications -> {
                        val noScreenMessage =
                            stringResource(Res.string.notifications_no_screen)
                        com.photonne.app.ui.notifications.NotificationsScreen(
                            title = stringResource(Res.string.notifications_title),
                            onBack = { appState.moreSubscreen = null },
                            canMarkAllRead = notificationsState.unreadCount > 0 &&
                                !notificationsState.isMarkingAllRead,
                            onMarkAllRead = notificationsViewModel::markAllRead,
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = notificationsViewModel,
                            onNavigate = { url ->
                                // Map known server actionUrls to in-app
                                // subscreens; unknown routes are a no-op.
                                val path = url.substringBefore('?').trimEnd('/')
                                when {
                                    path == "/shared-trash" ||
                                        path.endsWith("/shared-trash") -> {
                                        appState.trashTab = com.photonne.app.ui.library.TrashTab.Shared
                                        appState.moreSubscreen = MoreSubscreen.Trash
                                    }
                                    path == "/admin/enrichment-failures" ||
                                        path.endsWith("/admin/enrichment-failures") -> {
                                        appState.adminEnrichmentInitialType = url
                                            .substringAfter('?', "")
                                            .split('&')
                                            .firstOrNull { it.startsWith("type=") }
                                            ?.substringAfter('=')
                                            ?.takeIf { it.isNotBlank() }
                                        appState.adminEnrichmentReturnTo = MoreSubscreen.AdminSystemHub
                                        appState.moreSubscreen = MoreSubscreen.AdminSystemEnrichmentFailures
                                    }
                                    path == "/admin/stats" ||
                                        path.endsWith("/admin/stats") -> {
                                        appState.moreSubscreen = MoreSubscreen.AdminStats
                                    }
                                    path == "/people" || path.endsWith("/people") -> {
                                        appState.moreSubscreen = MoreSubscreen.People
                                    }
                                    // Ruta sin pantalla nativa: decirlo vale
                                    // más que un toque que no hace nada.
                                    else -> snackbarController.show(noScreenMessage)
                                }
                            }
                        )
                    }
                    MoreSubscreen.AccountSettings -> {
                        // Resumen de cuota en la fila de Almacenamiento: se pide
                        // una vez y se reutiliza (el viewmodel vive con la app).
                        LaunchedEffect(Unit) { accountStorageViewModel.loadIfNeeded() }
                        val accountStorageState by accountStorageViewModel.state
                            .collectAsStateWithLifecycle()
                        com.photonne.app.ui.settings.AccountSettingsScreen(
                            title = stringResource(Res.string.account_settings_title),
                            storage = accountStorageState.info,
                            onBack = { appState.moreSubscreen = null },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            activityNotificationsEnabled = activityNotificationsEnabled
                                .takeIf { activityNotifications.isSupported },
                            onActivityNotificationsChange = activityNotifications::setEnabled,
                            onOpen = { section ->
                                appState.profileOpenedFromMore = false
                                appState.moreSubscreen = when (section) {
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
                    }
                    MoreSubscreen.AccountProfile ->
                        com.photonne.app.ui.settings.AccountProfileScreen(
                            title = stringResource(Res.string.account_section_profile),
                            onBack = {
                                appState.moreSubscreen = if (appState.profileOpenedFromMore) null
                                else MoreSubscreen.AccountSettings
                            },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = accountProfileViewModel
                        )
                    MoreSubscreen.AccountSecurity ->
                        com.photonne.app.ui.settings.AccountSecurityScreen(
                            title = stringResource(Res.string.account_section_security),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AccountSettings },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = accountSecurityViewModel
                        )
                    MoreSubscreen.AccountAppearance ->
                        com.photonne.app.ui.settings.AccountAppearanceScreen(
                            title = stringResource(Res.string.account_section_appearance),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AccountSettings },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = appearanceViewModel
                        )
                    MoreSubscreen.AccountStorage ->
                        com.photonne.app.ui.settings.AccountStorageScreen(
                            title = stringResource(Res.string.account_section_storage),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AccountSettings },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = accountStorageViewModel
                        )
                    MoreSubscreen.AccountConnection ->
                        com.photonne.app.ui.settings.AccountConnectionScreen(
                            title = stringResource(Res.string.account_section_connection),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AccountSettings },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = deviceConnectionViewModel
                        )
                    MoreSubscreen.Administration ->
                        com.photonne.app.ui.admin.AdministrationScreen(
                            title = stringResource(Res.string.administration_title),
                            onBack = { appState.moreSubscreen = null },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            onOpen = { section ->
                                appState.moreSubscreen = when (section) {
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                            onCreateNew = {
                                appState.adminUserEditorId = null
                                adminUsersViewModel.clearMessages()
                                appState.moreSubscreen = MoreSubscreen.AdminUserEditor
                            },
                            viewModel = adminUsersViewModel,
                            onEdit = { user ->
                                appState.adminUserEditorId = user.id
                                adminUsersViewModel.clearMessages()
                                appState.moreSubscreen = MoreSubscreen.AdminUserEditor
                            },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                        )
                    MoreSubscreen.AdminUserEditor ->
                        com.photonne.app.ui.admin.AdminUserEditorScreen(
                            title = stringResource(
                                if (appState.adminUserEditorId == null) Res.string.admin_user_action_new
                                else Res.string.admin_user_edit_title
                            ),
                            onBack = {
                                appState.adminUserEditorId = null
                                adminUsersViewModel.clearMessages()
                                appState.moreSubscreen = MoreSubscreen.AdminUsers
                            },
                            viewModel = adminUsersViewModel,
                            userId = appState.adminUserEditorId,
                            onDone = {
                                appState.adminUserEditorId = null
                                appState.moreSubscreen = MoreSubscreen.AdminUsers
                            },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                        )
                    MoreSubscreen.AdminLibraries -> {
                        val usersState by adminUsersViewModel.state.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { adminUsersViewModel.ensureLoaded() }
                        com.photonne.app.ui.admin.AdminLibrariesScreen(
                            title = stringResource(Res.string.admin_section_libraries),
                            onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                            onCreateNew = {
                                appState.adminLibraryEditorId = null
                                adminLibrariesViewModel.clearMessages()
                                appState.moreSubscreen = MoreSubscreen.AdminLibraryEditor
                            },
                            viewModel = adminLibrariesViewModel,
                            knownUsers = usersState.users,
                            onEdit = { library ->
                                appState.adminLibraryEditorId = library.id
                                adminLibrariesViewModel.clearMessages()
                                appState.moreSubscreen = MoreSubscreen.AdminLibraryEditor
                            },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                        )
                    }
                    MoreSubscreen.AdminLibraryEditor ->
                        com.photonne.app.ui.admin.AdminLibraryEditorScreen(
                            title = stringResource(
                                if (appState.adminLibraryEditorId == null) Res.string.admin_libraries_action_new
                                else Res.string.admin_libraries_edit_title
                            ),
                            onBack = {
                                appState.adminLibraryEditorId = null
                                adminLibrariesViewModel.clearMessages()
                                appState.moreSubscreen = MoreSubscreen.AdminLibraries
                            },
                            viewModel = adminLibrariesViewModel,
                            libraryId = appState.adminLibraryEditorId,
                            onDone = {
                                appState.adminLibraryEditorId = null
                                appState.moreSubscreen = MoreSubscreen.AdminLibraries
                            },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                        )
                    MoreSubscreen.AdminStats ->
                        com.photonne.app.ui.admin.AdminStatsScreen(
                            title = stringResource(Res.string.admin_section_stats),
                            onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminStatsViewModel
                        )
                    MoreSubscreen.AdminSettingsHub ->
                        com.photonne.app.ui.admin.AdminSettingsHubScreen(
                            title = stringResource(Res.string.admin_section_settings),
                            onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            onOpen = { entry ->
                                appState.moreSubscreen = when (entry) {
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsObjectDetection -> {
                        val vm: com.photonne.app.ui.admin.AdminObjectDetectionSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminObjectDetectionSettingsScreen(
                            title = stringResource(Res.string.admin_settings_object_detection),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsSceneClassification -> {
                        val vm: com.photonne.app.ui.admin.AdminSceneClassificationSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminSceneClassificationSettingsScreen(
                            title = stringResource(Res.string.admin_settings_scene_classification),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsTextRecognition -> {
                        val vm: com.photonne.app.ui.admin.AdminTextRecognitionSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminTextRecognitionSettingsScreen(
                            title = stringResource(Res.string.admin_settings_text_recognition),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsImageEmbedding -> {
                        val vm: com.photonne.app.ui.admin.AdminImageEmbeddingSettingsViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminImageEmbeddingSettingsScreen(
                            title = stringResource(Res.string.admin_settings_image_embedding),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenNightly = {
                                appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                            },
                        )
                    }
                    MoreSubscreen.AdminSettingsImage ->
                        com.photonne.app.ui.admin.AdminImageSettingsScreen(
                            title = stringResource(Res.string.admin_settings_image),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminImageSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsMetadata ->
                        com.photonne.app.ui.admin.AdminMetadataSettingsScreen(
                            title = stringResource(Res.string.admin_settings_metadata),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminMetadataSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsNightly ->
                        com.photonne.app.ui.admin.AdminNightlySettingsScreen(
                            title = stringResource(Res.string.admin_settings_nightly),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminNightlySettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsNotifications ->
                        com.photonne.app.ui.admin.AdminNotificationSettingsScreen(
                            title = stringResource(Res.string.admin_settings_notifications),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminNotificationSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsServer ->
                        com.photonne.app.ui.admin.AdminServerSettingsScreen(
                            title = stringResource(Res.string.admin_settings_server),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminServerSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsTrash ->
                        com.photonne.app.ui.admin.AdminTrashSettingsScreen(
                            title = stringResource(Res.string.admin_settings_trash),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminTrashSettingsViewModel
                        )
                    MoreSubscreen.AdminSettingsUserDefaults ->
                        com.photonne.app.ui.admin.AdminUserDefaultsScreen(
                            title = stringResource(Res.string.admin_settings_user_defaults),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminUserDefaultsViewModel
                        )
                    MoreSubscreen.AdminSettingsVersion ->
                        com.photonne.app.ui.admin.AdminServerScreen(
                            title = stringResource(Res.string.admin_settings_version),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminVersionViewModel
                        )
                    MoreSubscreen.AdminSystemHub ->
                        com.photonne.app.ui.admin.AdminSystemHubScreen(
                            title = stringResource(Res.string.admin_section_system),
                            onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            onOpen = { entry ->
                                appState.moreSubscreen = when (entry) {
                                    com.photonne.app.ui.admin.AdminSystemEntry.RunTasks ->
                                        MoreSubscreen.AdminSystemRunTasks
                                    com.photonne.app.ui.admin.AdminSystemEntry.EnrichmentFailures -> {
                                        appState.adminEnrichmentInitialType = null
                                        appState.adminEnrichmentReturnTo = MoreSubscreen.AdminSystemHub
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSystemHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = vm,
                            // Only Duplicates still drills into its own
                            // screen; pipeline + AI rows handle their
                            // entire UX inline on the hub. Other taps are
                            // silently ignored because the hub doesn't
                            // currently expose any `onOpen` for them.
                            onOpenTask = { task ->
                                if (task == com.photonne.app.ui.admin.AdminRunTask.DetectDuplicates) {
                                    appState.moreSubscreen = MoreSubscreen.AdminSystemDuplicates
                                }
                            },
                            // A backfill skips assets that used up their
                            // retries, so a row whose queue is full of them has
                            // no button left to press. The registry is the only
                            // place they can be retried or suppressed.
                            onOpenFailures = { type ->
                                appState.adminEnrichmentInitialType = type
                                appState.adminEnrichmentReturnTo = MoreSubscreen.AdminSystemRunTasks
                                appState.moreSubscreen = MoreSubscreen.AdminSystemEnrichmentFailures
                            },
                        )
                    }
                    MoreSubscreen.AdminSystemDuplicates ->
                        com.photonne.app.ui.admin.AdminDuplicatesScreen(
                            title = stringResource(Res.string.admin_system_duplicates),
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSystemRunTasks },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = adminDuplicatesViewModel
                        )
                    MoreSubscreen.AdminSystemEnrichmentFailures -> {
                        val vm: com.photonne.app.ui.admin.AdminEnrichmentFailuresViewModel =
                            koinViewModel()
                        com.photonne.app.ui.admin.AdminEnrichmentFailuresScreen(
                            title = stringResource(Res.string.admin_system_enrichment_failures),
                            initialType = appState.adminEnrichmentInitialType,
                            onBack = { appState.moreSubscreen = appState.adminEnrichmentReturnTo },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                            viewModel = vm,
                            onOpenAsset = { failure ->
                                appState.assetDetail = AssetDetailContext(
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
                            onBack = { appState.moreSubscreen = MoreSubscreen.AdminSystemHub },
                            onChromeVisibleChange = { appState.subscreenChromeVisible = it },
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
        appState.memoryDetail?.let { memory ->
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
                        appState.assetDetail = AssetDetailContext(
                            items = memory.items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = timelineViewModel::setFavorite
                        )
                    }
                },
                onBack = { appState.memoryDetail = null },
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
                        },
                        statusBarScrim = false
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
                        onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Memory },
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
                                    appState.moveSelectionError = null
                                    appState.moveSelectionRequest = MoveSelectionRequest(
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

        val ctx = appState.assetDetail
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
                    appState.assetDetail = null
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
                        onBack = { appState.closeAssetDetail() },
                        onPageChanged = { id -> appState.currentDetailAssetId = id },
                        animatedVisibilityScope = this@AnimatedVisibility,
                        onFavoriteChanged = displayCtx.onFavoriteChanged,
                        onAddToAlbum = { item -> appState.addToAlbum = AddToAlbumState(asset = item) },
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
                            appState.assetDetailStack = emptyList()
                            appState.assetDetail = null
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
                            appState.assetDetailStack = emptyList()
                            appState.assetDetail = null
                            coroutineScope.launch {
                                snackbarController.show(
                                    org.jetbrains.compose.resources.getPluralString(
                                        Res.plurals.selection_restore_done, 1, 1
                                    )
                                )
                            }
                        },
                        onAssetPurged = { _ ->
                            appState.assetDetailStack = emptyList()
                            appState.assetDetail = null
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
                            appState.assetDetailStack = emptyList()
                            appState.assetDetail = null
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
                            appState.assetDetailStack = emptyList()
                            appState.assetDetail = null
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
                            appState.showAssetFacesSheet = true
                        },
                        onOpenPerson = { personId -> appState.openPersonFromViewer(personId) },
                        onSearchScene = { label ->
                            appState.openSearchFromViewer { searchViewModel.showResultsForSceneLabel(label) }
                        },
                        onSearchObject = { label ->
                            appState.openSearchFromViewer { searchViewModel.showResultsForObjectLabel(label) }
                        },
                        facesRevision = appState.assetFacesRevision,
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
                            appState.assetDetailStack = appState.assetDetailStack + displayCtx
                            appState.assetDetail = AssetDetailContext(
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

    AuthenticatedDialogs(
        AuthenticatedDialogsHost(
            appState = appState,
            timelineState = timelineStateRef,
            albumsState = albumsStateRef,
            albumDetailState = albumDetailStateRef,
            searchState = searchStateRef,
            foldersState = foldersStateRef,
            folderDetailState = folderDetailStateRef,
            folderPermissionsState = folderPermissionsStateRef,
            albumSharesState = albumSharesStateRef,
            albumPermissionsState = albumPermissionsStateRef,
            archivedState = archivedStateRef,
            trashState = trashStateRef,
            favoritesState = favoritesStateRef,
            organizeInboxState = organizeInboxStateRef,
            organizeRuleState = organizeRuleStateRef,
            peopleState = peopleStateRef,
            personDetailState = personDetailStateRef,
            suggestionsState = suggestionsStateRef,
            assetFacesState = assetFacesStateRef,
            uploadState = uploadStateRef,
            actionsState = actionsStateRef,
            deviceBackupState = deviceBackupStateRef,
            memorySelectionState = memorySelectionStateRef,
            sharedLinkState = sharedLinkStateRef,
            recentDestinations = recentDestinationsRef,
            authRepository = authRepository,
            albumsRepository = albumsRepository,
            peopleRepository = peopleRepository,
            foldersRepository = foldersRepository,
            errorFactory = errorFactory,
            appVersionStore = appVersionStore,
            recentDestinationsStore = recentDestinationsStore,
            timelineViewModel = timelineViewModel,
            albumsViewModel = albumsViewModel,
            albumDetailViewModel = albumDetailViewModel,
            searchViewModel = searchViewModel,
            foldersViewModel = foldersViewModel,
            folderDetailViewModel = folderDetailViewModel,
            folderPermissionsViewModel = folderPermissionsViewModel,
            albumSharesViewModel = albumSharesViewModel,
            albumPermissionsViewModel = albumPermissionsViewModel,
            archivedViewModel = archivedViewModel,
            trashViewModel = trashViewModel,
            favoritesViewModel = favoritesViewModel,
            organizeInboxViewModel = organizeInboxViewModel,
            organizeRuleViewModel = organizeRuleViewModel,
            uploadViewModel = uploadViewModel,
            actionsViewModel = actionsViewModel,
            mapViewModel = mapViewModel,
            peopleViewModel = peopleViewModel,
            personDetailViewModel = personDetailViewModel,
            personSuggestionsViewModel = personSuggestionsViewModel,
            assetFacesViewModel = assetFacesViewModel,
            memorySelectionViewModel = memorySelectionViewModel,
            sharedLinkViewModel = sharedLinkViewModel,
            snackbarController = snackbarController,
            coroutineScope = coroutineScope,
            apiBaseUrl = apiBaseUrl,
            sharedLinkOpen = sharedLinkOpen,
            showAddedToAlbumSnackbar = { count, albumName -> showAddedToAlbumSnackbar(count, albumName) },
            showMovedToFolderSnackbar = { count, folderName -> showMovedToFolderSnackbar(count, folderName) },
        )
    )
}
