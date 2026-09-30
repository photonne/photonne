@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.photonne.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.resources.organize_excluded_included_done
import com.photonne.app.resources.organize_excluded_action_include
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import com.photonne.app.resources.Res
import com.photonne.app.resources.device_backup_action_select_all
import com.photonne.app.resources.upload_subtitle_pending
import com.photonne.app.resources.upload_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumPermissionsViewModel
import com.photonne.app.ui.album.AlbumsViewModel
import com.photonne.app.ui.folder.FolderPermissionsViewModel
import com.photonne.app.ui.folder.FoldersViewModel
import com.photonne.app.ui.actions.AssetActionWorking
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.main.AssetSelectionTopBar
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.timeline.TimelineViewModel
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

/**
 * Lo que necesitan los constructores del cromo de [AuthenticatedApp] (cápsula
 * superior de selección, barra superior y barra inferior), sacados a
 * AuthenticatedTopChrome.kt y AuthenticatedBottomChrome.kt para que el cuerpo de
 * AuthenticatedApp no rebase el límite de 64 KB por método de la JVM.
 *
 * Mismo patrón que [AuthenticatedDialogsHost]: el estado de UI y navegación es el
 * mismo [AuthenticatedAppState], los [State] que el padre recoge de sus ViewModels
 * se exponen como propiedades delegadas (se leen donde antes se leía la variable,
 * sin colectores duplicados) y las funciones locales que siguen en AuthenticatedApp
 * llegan como lambdas. Se reconstruye en cada composición del padre.
 */
internal class AuthenticatedChromeHost(
    val appState: AuthenticatedAppState,
    val user: AuthState.Authenticated,
    timelineState: State<com.photonne.app.ui.timeline.TimelineUiState>,
    albumsState: State<com.photonne.app.ui.album.AlbumsUiState>,
    albumDetailState: State<com.photonne.app.ui.album.AlbumDetailUiState>,
    searchState: State<com.photonne.app.ui.search.SearchUiState>,
    foldersState: State<com.photonne.app.ui.folder.FoldersUiState>,
    folderDetailState: State<com.photonne.app.ui.folder.FolderDetailUiState>,
    archivedState: State<com.photonne.app.ui.library.ArchivedUiState>,
    trashState: State<com.photonne.app.ui.library.TrashUiState>,
    favoritesState: State<com.photonne.app.ui.library.FavoritesUiState>,
    organizeInboxState: State<com.photonne.app.ui.organize.OrganizeInboxUiState>,
    organizeExcludedState: State<com.photonne.app.ui.organize.OrganizeExcludedUiState>,
    personDetailState: State<com.photonne.app.ui.people.PersonDetailUiState>,
    deviceBackupState: State<com.photonne.app.ui.devicebackup.DeviceBackupUiState>,
    uploadState: State<com.photonne.app.ui.upload.UploadUiState>,
    actionsState: State<com.photonne.app.ui.actions.AssetActionsUiState>,
    utilitiesDuplicatesState: State<com.photonne.app.ui.utilities.UtilitiesDuplicatesUiState>,
    val timelineViewModel: com.photonne.app.ui.timeline.TimelineViewModel,
    val albumsViewModel: com.photonne.app.ui.album.AlbumsViewModel,
    val albumDetailViewModel: com.photonne.app.ui.album.AlbumDetailViewModel,
    val searchViewModel: com.photonne.app.ui.search.SearchViewModel,
    val foldersViewModel: com.photonne.app.ui.folder.FoldersViewModel,
    val folderDetailViewModel: com.photonne.app.ui.folder.FolderDetailViewModel,
    val folderPermissionsViewModel: com.photonne.app.ui.folder.FolderPermissionsViewModel,
    val albumPermissionsViewModel: com.photonne.app.ui.album.AlbumPermissionsViewModel,
    val archivedViewModel: com.photonne.app.ui.library.ArchivedViewModel,
    val trashViewModel: com.photonne.app.ui.library.TrashViewModel,
    val favoritesViewModel: com.photonne.app.ui.library.FavoritesViewModel,
    val organizeInboxViewModel: com.photonne.app.ui.organize.OrganizeInboxViewModel,
    val organizeExcludedViewModel: com.photonne.app.ui.organize.OrganizeExcludedViewModel,
    val personDetailViewModel: com.photonne.app.ui.people.PersonDetailViewModel,
    val deviceBackupViewModel: com.photonne.app.ui.devicebackup.DeviceBackupViewModel,
    val actionsViewModel: com.photonne.app.ui.actions.AssetSelectionActionsViewModel,
    val albumsRepository: AlbumsRepository,
    val coroutineScope: kotlinx.coroutines.CoroutineScope,
    val runBulkFavorite: (
        items: List<TimelineItem>,
        selection: Set<String>,
        snackbar: com.photonne.app.ui.main.SnackbarController?,
        clearSelection: () -> Unit,
    ) -> Unit,
    val selectionAllFavorite: (items: List<TimelineItem>, selection: Set<String>) -> Boolean,
) {
    val timelineState by timelineState
    val albumsState by albumsState
    val albumDetailState by albumDetailState
    val searchState by searchState
    val foldersState by foldersState
    val folderDetailState by folderDetailState
    val archivedState by archivedState
    val trashState by trashState
    val favoritesState by favoritesState
    val organizeInboxState by organizeInboxState
    val organizeExcludedState by organizeExcludedState
    val personDetailState by personDetailState
    val deviceBackupState by deviceBackupState
    val uploadState by uploadState
    val actionsState by actionsState
    val utilitiesDuplicatesState by utilitiesDuplicatesState
}

/**
 * Cápsula superior de selección de la pantalla visible, o null sin selección.
 * Es un composable que DEVUELVE la lambda (no la pinta): AuthenticatedApp sigue
 * decidiendo con `selectionTopChrome != null` (edgeToEdgeTop) exactamente igual
 * que cuando el `when` estaba en línea, en la misma composición.
 */
@Composable
internal fun buildSelectionTopChrome(host: AuthenticatedChromeHost): (@Composable () -> Unit)? {
    with(host) {

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
        return selectionTopChrome
    }
}

/**
 * Barra superior del Scaffold: la cápsula de selección si la hay; si no, la
 * barra acoplada de las pocas pantallas que aún la usan (Subir) o nada.
 */
@Composable
internal fun buildTopBar(
    host: AuthenticatedChromeHost,
    selectionTopChrome: (@Composable () -> Unit)?,
): @Composable () -> Unit {
    with(host) {
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
        return topBar
    }
}
