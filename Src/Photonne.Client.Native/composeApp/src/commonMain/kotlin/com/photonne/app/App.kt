@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.photonne.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import com.photonne.app.resources.action_undo
import com.photonne.app.resources.selection_trash_blocked_foreign
import com.photonne.app.resources.selection_move_blocked_read_only
import com.photonne.app.resources.selection_restore_done
import com.photonne.app.resources.selection_added_to_album_done
import com.photonne.app.resources.selection_archive_done
import com.photonne.app.resources.selection_unarchive_done
import com.photonne.app.resources.selection_moved_to_folder_done
import com.photonne.app.resources.selection_trash_done
import com.photonne.app.resources.selection_favorite_added_done
import com.photonne.app.resources.selection_favorite_removed_done
import com.photonne.app.resources.selection_favorite_failed
import com.photonne.app.resources.selection_deleted_permanently_done
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_system_enrichment_failures
import com.photonne.app.resources.admin_section_settings
import com.photonne.app.resources.admin_section_system
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
import org.jetbrains.compose.resources.pluralStringResource
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.navigation.PlatformBackHandler
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumPermissionsViewModel
import com.photonne.app.ui.album.AlbumSharesViewModel
import com.photonne.app.ui.album.AlbumsViewModel
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
import com.photonne.app.ui.main.LocalSnackbarController
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.main.rememberSnackbarController
import com.photonne.app.ui.main.AssetSelectionBottomBar
import com.photonne.app.ui.main.AssetSelectionTopBar
import com.photonne.app.ui.main.MainScaffold
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.main.navTab
import com.photonne.app.ui.theme.PhotonneTheme
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
    /** "Fijados" de Colecciones a pantalla completa. */
    Pinned,
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
internal fun isAdminSettingsSubpage(subscreen: MoreSubscreen?): Boolean = when (subscreen) {
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

internal fun isAdminSystemSubpage(subscreen: MoreSubscreen?): Boolean = when (subscreen) {
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
    MoreSubscreen.Pinned,
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
internal fun com.photonne.app.data.models.MapPoint.toSyntheticTimelineItem():
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
internal fun com.photonne.app.data.api.AdminEnrichmentFailureDto.toSyntheticTimelineItem():
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
    // El feed generado de Recuerdos: lo pinta Colecciones (fila de arriba) y
    // la sección completa.
    val memoryFeedViewModel:
        com.photonne.app.ui.memories.MemoryFeedViewModel = koinViewModel()

    // Third-party data notices for the Más footer. Fetched rather than hardcoded
    // because only the server knows what its image actually bundles: a build that
    // couldn't download GeoNames must not credit data it doesn't have. Failure is
    // silent — an unreachable server has bigger problems than a missing credit,
    // and the README carries the attribution regardless.
    val photonneApi: com.photonne.app.data.api.PhotonneApi = koinInject()
    val attributionsState = remember {
        mutableStateOf<List<com.photonne.app.data.models.Attribution>>(emptyList())
    }
    var attributions by attributionsState
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
    val utilitiesDuplicatesStateRef = utilitiesDuplicatesViewModel.state.collectAsStateWithLifecycle()
    val utilitiesLargeFilesViewModel:
        com.photonne.app.ui.utilities.UtilitiesLargeFilesViewModel = koinViewModel()
    val utilitiesLocationsViewModel:
        com.photonne.app.ui.utilities.UtilitiesLocationsViewModel = koinViewModel()
    val exploreFacetsViewModel:
        com.photonne.app.ui.explore.ExploreFacetsViewModel = koinViewModel()
    val activityNotifications: com.photonne.app.data.notifications.ActivityNotifications =
        koinInject()
    val activityNotificationsEnabledRef = activityNotifications.enabled.collectAsStateWithLifecycle()
    val notificationsViewModel:
        com.photonne.app.ui.notifications.NotificationsViewModel = koinViewModel()
    val notificationsStateRef = notificationsViewModel.state.collectAsStateWithLifecycle()
    val notificationsState by notificationsStateRef
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
    val unsupportedFilesStateRef = unsupportedFilesViewModel.state.collectAsStateWithLifecycle()
    val organizeInboxStateRef = organizeInboxViewModel.state.collectAsStateWithLifecycle()
    val organizeInboxState by organizeInboxStateRef
    val organizeExcludedStateRef = organizeExcludedViewModel.state.collectAsStateWithLifecycle()
    val organizeExcludedState by organizeExcludedStateRef
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
    val albumBack: () -> Unit = { appState.albumBack() }

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
            // "Todos los álbumes" / "Todas las carpetas" son páginas de Colecciones.
            appState.selectedTab == MainTab.Albums || appState.selectedTab == MainTab.Folders ->
                appState.selectedTab = MainTab.Collections
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
        appState.selectedTab == MainTab.Search && appState.moreSubscreen == null ->
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

    // ---- Horizontal swipe between the primary tabs ----
    // The bottom-nav tabs, in bar order, become pages of a HorizontalPager so a
    // left/right drag glides between Fotos · Colecciones · Más. Buscar is not a
    // nav tab (it has no page), and neither are "Todos los álbumes" / "Todas las
    // carpetas": they open as an overlay like the detail screens and subscreens.
    val navTabs = remember {
        listOf(MainTab.Timeline, MainTab.Collections, MainTab.More)
    }
    val mainPagerState = rememberPagerState(
        initialPage = navTabs.indexOf(appState.selectedTab.navTab()).coerceAtLeast(0),
        pageCount = { navTabs.size }
    )
    // Whether an opaque overlay (drill-down / Buscar / More subscreen) is covering
    // the pager base layer. When true the overlay must paint its own solid
    // background, otherwise the tab body underneath shows through — the pager
    // keeps all top-level bodies composed behind it.
    val overlayVisible = appState.moreSubscreen != null ||
        appState.selectedTab == MainTab.Albums ||
        appState.selectedTab == MainTab.Folders ||
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
    //
    // Colecciones no desliza (decisión de producto): sus filas son sliders
    // horizontales y el gesto de pestaña les robaría el arrastre.
    val canSwipeTabs = !overlayVisible &&
        appState.selectedTab != MainTab.Collections &&
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
        appState.selectedTab == MainTab.Albums -> "albums"
        appState.selectedTab == MainTab.Folders -> "folders"
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
        MoreSubscreen.Pinned,
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
    // Cromo del Scaffold (cápsula de selección, barra superior y barra inferior):
    // ver AuthenticatedTopChrome.kt y AuthenticatedBottomChrome.kt. Se construye
    // aquí, en el mismo orden, y null sigue significando "sin barra".
    val chromeHost = AuthenticatedChromeHost(
        appState = appState,
        user = user,
        timelineState = timelineStateRef,
        albumsState = albumsStateRef,
        albumDetailState = albumDetailStateRef,
        searchState = searchStateRef,
        foldersState = foldersStateRef,
        folderDetailState = folderDetailStateRef,
        archivedState = archivedStateRef,
        trashState = trashStateRef,
        favoritesState = favoritesStateRef,
        organizeInboxState = organizeInboxStateRef,
        organizeExcludedState = organizeExcludedStateRef,
        personDetailState = personDetailStateRef,
        deviceBackupState = deviceBackupStateRef,
        uploadState = uploadStateRef,
        actionsState = actionsStateRef,
        utilitiesDuplicatesState = utilitiesDuplicatesStateRef,
        timelineViewModel = timelineViewModel,
        albumsViewModel = albumsViewModel,
        albumDetailViewModel = albumDetailViewModel,
        searchViewModel = searchViewModel,
        foldersViewModel = foldersViewModel,
        folderDetailViewModel = folderDetailViewModel,
        folderPermissionsViewModel = folderPermissionsViewModel,
        albumPermissionsViewModel = albumPermissionsViewModel,
        archivedViewModel = archivedViewModel,
        trashViewModel = trashViewModel,
        favoritesViewModel = favoritesViewModel,
        organizeInboxViewModel = organizeInboxViewModel,
        organizeExcludedViewModel = organizeExcludedViewModel,
        personDetailViewModel = personDetailViewModel,
        deviceBackupViewModel = deviceBackupViewModel,
        actionsViewModel = actionsViewModel,
        albumsRepository = albumsRepository,
        coroutineScope = coroutineScope,
        runBulkFavorite = { items, selection, snackbar, clearSelection ->
            runBulkFavorite(items, selection, snackbar, clearSelection)
        },
        selectionAllFavorite = { items, selection -> selectionAllFavorite(items, selection) },
    )
    val selectionTopChrome: (@Composable () -> Unit)? = buildSelectionTopChrome(chromeHost)
    val topBar: @Composable () -> Unit = buildTopBar(chromeHost, selectionTopChrome)
    val bottomBar: (@Composable () -> Unit)? = buildBottomBar(chromeHost)

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
    val collectionsImmersive = chromeTab == MainTab.Collections && appState.moreSubscreen == null
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
    // Álbumes/Carpetas dejan el pager en Colecciones, que es lo que asoma al cerrarlas.
    LaunchedEffect(appState.selectedTab) {
        val idx = navTabs.indexOf(appState.selectedTab.navTab())
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

    val contentHost = AuthenticatedContentHost(
        appState = appState,
        user = user,
        timelineState = timelineStateRef,
        albumsState = albumsStateRef,
        albumDetailState = albumDetailStateRef,
        searchState = searchStateRef,
        foldersState = foldersStateRef,
        folderDetailState = folderDetailStateRef,
        archivedState = archivedStateRef,
        trashState = trashStateRef,
        favoritesState = favoritesStateRef,
        organizeInboxState = organizeInboxStateRef,
        organizeExcludedState = organizeExcludedStateRef,
        organizeRuleState = organizeRuleStateRef,
        peopleState = peopleStateRef,
        personDetailState = personDetailStateRef,
        suggestionsState = suggestionsStateRef,
        deviceBackupState = deviceBackupStateRef,
        uploadState = uploadStateRef,
        unsupportedFilesState = unsupportedFilesStateRef,
        notificationsState = notificationsStateRef,
        attributions = attributionsState,
        activityNotificationsEnabled = activityNotificationsEnabledRef,
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
        unsupportedFilesViewModel = unsupportedFilesViewModel,
        organizeInboxViewModel = organizeInboxViewModel,
        organizeExcludedViewModel = organizeExcludedViewModel,
        organizeRuleViewModel = organizeRuleViewModel,
        uploadViewModel = uploadViewModel,
        deviceBackupViewModel = deviceBackupViewModel,
        enrichmentStatusViewModel = enrichmentStatusViewModel,
        utilitiesDuplicatesViewModel = utilitiesDuplicatesViewModel,
        utilitiesLargeFilesViewModel = utilitiesLargeFilesViewModel,
        utilitiesLocationsViewModel = utilitiesLocationsViewModel,
        exploreFacetsViewModel = exploreFacetsViewModel,
        memoryFeedViewModel = memoryFeedViewModel,
        notificationsViewModel = notificationsViewModel,
        actionsViewModel = actionsViewModel,
        mapViewModel = mapViewModel,
        peopleViewModel = peopleViewModel,
        personDetailViewModel = personDetailViewModel,
        personSuggestionsViewModel = personSuggestionsViewModel,
        accountProfileViewModel = accountProfileViewModel,
        accountSecurityViewModel = accountSecurityViewModel,
        accountStorageViewModel = accountStorageViewModel,
        appearanceViewModel = appearanceViewModel,
        adminUsersViewModel = adminUsersViewModel,
        adminLibrariesViewModel = adminLibrariesViewModel,
        adminStatsViewModel = adminStatsViewModel,
        adminVersionViewModel = adminVersionViewModel,
        adminImageSettingsViewModel = adminImageSettingsViewModel,
        adminMetadataSettingsViewModel = adminMetadataSettingsViewModel,
        adminNightlySettingsViewModel = adminNightlySettingsViewModel,
        adminNotificationSettingsViewModel = adminNotificationSettingsViewModel,
        adminServerSettingsViewModel = adminServerSettingsViewModel,
        deviceConnectionViewModel = deviceConnectionViewModel,
        adminTrashSettingsViewModel = adminTrashSettingsViewModel,
        adminSharedTrashViewModel = adminSharedTrashViewModel,
        adminUserDefaultsViewModel = adminUserDefaultsViewModel,
        adminDuplicatesViewModel = adminDuplicatesViewModel,
        adminBackupViewModel = adminBackupViewModel,
        activityNotifications = activityNotifications,
        deviceGallery = deviceGallery,
        foldersRepository = foldersRepository,
        snackbarController = snackbarController,
        coroutineScope = coroutineScope,
        apiBaseUrl = apiBaseUrl,
        navTabs = navTabs,
        mainPagerState = mainPagerState,
        canSwipeTabs = canSwipeTabs,
        albumsImmersive = albumsImmersive,
        foldersImmersive = foldersImmersive,
        albumDetailImmersive = albumDetailImmersive,
        folderDetailImmersive = folderDetailImmersive,
        onLogout = onLogout,
        albumBack = albumBack,
    )

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
                albumsImmersive || foldersImmersive ||
                albumDetailImmersive || folderDetailImmersive || searchImmersive ||
                selectionTopChrome != null,
            // On the immersive tabs the bottom nav hides while scrolling down
            // (driven by each screen's chrome), and always shows elsewhere.
            bottomBarVisible = when {
                timelineImmersive -> appState.timelineChromeVisible
                albumsImmersive -> appState.albumsChromeVisible
                foldersImmersive -> appState.foldersChromeVisible
                moreImmersive -> appState.moreChromeVisible
                collectionsImmersive -> appState.collectionsChromeVisible
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
            edgeToEdgeBottom = timelineImmersive || collectionsImmersive ||
                albumsImmersive || foldersImmersive ||
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
            AuthenticatedTabsPager(contentHost)
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
            AuthenticatedOverlayDestination(contentHost)
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
