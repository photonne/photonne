package com.photonne.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.AlbumShareLink
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.Person
import com.photonne.app.ui.main.MainTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Crea (una vez por composición de [AuthenticatedApp]) el [AuthenticatedAppState].
 *
 * Los pocos estados que sobreviven a la recreación de la Activity y a la muerte
 * de proceso (pestaña, subpantalla de Más, perfil abierto desde Más y los ids del
 * álbum/carpeta/persona abiertos) siguen siendo un `rememberSaveable` cada uno,
 * con el mismo valor inicial y el mismo Saver automático que tenían en línea; el
 * holder recibe esos [MutableState] y los expone como propiedades. Así cada uno
 * se guarda y restaura exactamente igual que antes, y el holder no necesita un
 * Saver propio para un estado que en su mayoría no es guardable.
 *
 * El resto es `mutableStateOf` dentro del holder, que se recuerda con `remember`
 * sin claves: vive lo mismo que vivían los `remember { mutableStateOf(…) }` de
 * AuthenticatedApp. Los ViewModels, el repositorio y el scope que recibe son
 * estables durante toda esa vida (koinViewModel/koinInject/rememberCoroutineScope).
 */
@Composable
internal fun rememberAuthenticatedAppState(
    peopleRepository: com.photonne.app.data.people.PeopleRepository,
    personDetailViewModel: com.photonne.app.ui.people.PersonDetailViewModel,
    albumsViewModel: com.photonne.app.ui.album.AlbumsViewModel,
    foldersViewModel: com.photonne.app.ui.folder.FoldersViewModel,
    organizeInboxViewModel: com.photonne.app.ui.organize.OrganizeInboxViewModel,
    coroutineScope: CoroutineScope,
): AuthenticatedAppState {
    // rememberSaveable: la pestaña y la subpantalla de Más sobreviven a la
    // muerte de proceso y a la recreación de la Activity (punto 49; los
    // álbumes/carpetas abiertos guardan objetos completos y quedan pendientes).
    val selectedTab = rememberSaveable { mutableStateOf(MainTab.Timeline) }
    val moreSubscreen = rememberSaveable { mutableStateOf<MoreSubscreen?>(null) }
    // Perfil abierto desde la cabecera de Más: Atrás vuelve a Más, no a Ajustes.
    val profileOpenedFromMore = rememberSaveable { mutableStateOf(false) }
    // Punto 49: el álbum, la carpeta y la persona abiertos son objetos
    // completos (no Saveable). Se guarda solo su id y, tras una recreación
    // (rotación en tablet, muerte de proceso), se vuelven a pedir al
    // servidor antes de que la pantalla los necesite. La pila de carpetas
    // no se rehidrata: atrás desde la carpeta restaurada vuelve a la raíz.
    val savedAlbumId = rememberSaveable { mutableStateOf<String?>(null) }
    val savedFolderId = rememberSaveable { mutableStateOf<String?>(null) }
    val savedPersonId = rememberSaveable { mutableStateOf<String?>(null) }
    return remember {
        AuthenticatedAppState(
            selectedTabState = selectedTab,
            moreSubscreenState = moreSubscreen,
            profileOpenedFromMoreState = profileOpenedFromMore,
            savedAlbumIdState = savedAlbumId,
            savedFolderIdState = savedFolderId,
            savedPersonIdState = savedPersonId,
            peopleRepository = peopleRepository,
            personDetailViewModel = personDetailViewModel,
            albumsViewModel = albumsViewModel,
            foldersViewModel = foldersViewModel,
            organizeInboxViewModel = organizeInboxViewModel,
            coroutineScope = coroutineScope,
        )
    }
}

/**
 * Estado de UI y navegación de [AuthenticatedApp]: pestaña, álbum/carpeta/persona
 * abiertos, visor, subpantalla de Más, banderas de diálogos y sus objetivos
 * pendientes. Antes eran decenas de `var x by remember { mutableStateOf(…) }`
 * locales; aquí son las mismas celdas de snapshot, así que leerlas en composición
 * suscribe igual y escribirlas recompone igual.
 *
 * Los efectos (LaunchedEffect/DisposableEffect) NO viven aquí: siguen en
 * AuthenticatedApp leyendo este estado. Solo se trasladan las funciones de
 * navegación que únicamente tocan este estado (y los ViewModels del constructor).
 */
@Stable
internal class AuthenticatedAppState(
    selectedTabState: MutableState<MainTab>,
    moreSubscreenState: MutableState<MoreSubscreen?>,
    profileOpenedFromMoreState: MutableState<Boolean>,
    savedAlbumIdState: MutableState<String?>,
    savedFolderIdState: MutableState<String?>,
    savedPersonIdState: MutableState<String?>,
    private val peopleRepository: com.photonne.app.data.people.PeopleRepository,
    private val personDetailViewModel: com.photonne.app.ui.people.PersonDetailViewModel,
    private val albumsViewModel: com.photonne.app.ui.album.AlbumsViewModel,
    private val foldersViewModel: com.photonne.app.ui.folder.FoldersViewModel,
    private val organizeInboxViewModel: com.photonne.app.ui.organize.OrganizeInboxViewModel,
    private val coroutineScope: CoroutineScope,
) {
    // ---- Guardables (rememberSaveable en rememberAuthenticatedAppState) ----
    var selectedTab by selectedTabState
    var moreSubscreen by moreSubscreenState
    var profileOpenedFromMore by profileOpenedFromMoreState
    var savedAlbumId by savedAlbumIdState
    var savedFolderId by savedFolderIdState
    var savedPersonId by savedPersonIdState

    // ---- Cromo inmersivo ----
    // Immersive tabs: the active list reports when its chrome hides on scroll
    // so the shared bottom navigation can slide away in the same rhythm. Fotos,
    // Álbumes and Carpetas each drive their own flag.
    var timelineChromeVisible by mutableStateOf(true)
    var albumsChromeVisible by mutableStateOf(true)
    var collectionsChromeVisible by mutableStateOf(true)
    var foldersChromeVisible by mutableStateOf(true)
    var moreChromeVisible by mutableStateOf(true)
    var searchChromeVisible by mutableStateOf(true)
    // Same, but for the photo grids inside an open album / folder.
    var albumDetailChromeVisible by mutableStateOf(true)
    // Compartido por las subpantallas con cromo flotante propio (Personas, Mapa,
    // Escenas, Objetos, Para organizar, Recuerdos): sólo hay una visible a la vez,
    // y ImmersiveChromeEffect lo restaura a `true` al salir de composición.
    var subscreenChromeVisible by mutableStateOf(true)
    var folderDetailChromeVisible by mutableStateOf(true)
    // Dirección de la última navegación del overlay (ver PlatformBackHandler).
    var overlayForward by mutableStateOf(true)

    // ---- Navegación: álbum, carpeta, persona, visor ----
    var selectedAlbum by mutableStateOf<AlbumSummary?>(null)
    var selectedFolder by mutableStateOf<FolderSummary?>(null)
    val folderBackStack = mutableStateListOf<FolderSummary>()
    var selectedPerson by mutableStateOf<Person?>(null)
    var openIdsRehydrated by mutableStateOf(false)
    var assetDetail by mutableStateOf<AssetDetailContext?>(null)
    // Pila de contextos del visor: abrir una foto relacionada apila el contexto
    // actual para que atrás vuelva a la foto (y la lista) de la que se venía,
    // en lugar de cerrar el visor y perder el sitio.
    var assetDetailStack by mutableStateOf<List<AssetDetailContext>>(emptyList())
    // Tracks the asset shown by the viewer's pager — drives the
    // grid → detail shared-element morph. Null when the viewer is closed
    // so all grid thumbnails return to their normal visible state.
    var currentDetailAssetId by mutableStateOf<String?>(null)
    // Qué rejilla abrió el visor: esa sigue a la foto vista para que al cerrar
    // la miniatura esté en pantalla (tanda 2 de funciones nuevas).
    val viewerReturnState = com.photonne.app.ui.grid.ViewerReturnState()
    // An open memory, shown as an album. An overlay rather than a MoreSubscreen:
    // it's reached from the Fotos strip too, not just from Más → Recuerdos, so it
    // can't hang off the Más hierarchy.
    var memoryDetail by mutableStateOf<com.photonne.app.ui.memories.MemoryDetailContext?>(null)
    // Retocar la pestaña Fotos activa vuelve arriba (consumido por TimelineScreen).
    var timelineScrollToTopTick by mutableStateOf(0)
    var albumsScrollToTopTick by mutableStateOf(0)
    var foldersScrollToTopTick by mutableStateOf(0)
    var collectionsScrollToTopTick by mutableStateOf(0)
    // The bucket the "Mi dispositivo" detail subscreen shows. Survives going
    // back to the bucket list (harmless), reset on every open.
    var deviceFolderBucket by mutableStateOf<com.photonne.app.data.devicelibrary.DeviceBucket?>(null)
    // Buscar abierto desde una etiqueta de Explorar: Atrás vuelve a esa
    // subpantalla (y a la pestaña que había debajo), no a Fotos.
    var searchReturnTo by mutableStateOf<Pair<MainTab, MoreSubscreen?>?>(null)
    // Búsqueda abierta desde una escena u objeto del visor: Atrás vuelve a la foto.
    var searchViewerReturn by mutableStateOf<ViewerReturn?>(null)
    // Carpeta abierta desde Ubicaciones (Lote N8): al salir de su raíz, Atrás
    // vuelve a esa subpantalla (y a la pestaña de debajo), no a la lista de
    // Carpetas. Cualquier toque en la barra de navegación lo olvida.
    var folderReturnTo by mutableStateOf<Pair<MainTab, MoreSubscreen?>?>(null)
    // Álbum abierto desde Colecciones (su fila o Fijados): Atrás vuelve allí y
    // no a "Todos los álbumes", que es donde vive el detalle.
    var albumReturnTo by mutableStateOf<Pair<MainTab, MoreSubscreen?>?>(null)
    // Persona abierta desde una cara del visor: Atrás vuelve a la foto.
    var personReturnTo by mutableStateOf<ViewerReturn?>(null)
    // Subpantalla a la que vuelve Atrás desde Escenas, Objetos o un tema abiertos
    // desde Explorar (por defecto vuelven a la capa de debajo, sin subpantalla).
    var subscreenReturnTo by mutableStateOf<MoreSubscreen?>(null)
    // Tema de Explorar abierto en MoreSubscreen.MemoryTheme.
    var exploreThemeKey by mutableStateOf<String?>(null)
    // Persona abierta desde la fila de Personas de Colecciones (ver personBack).
    var personFromCollections by mutableStateOf(false)

    // ---- Administración ----
    // Type filter the failures registry opens with when reached from a
    // notification actionUrl ("/admin/enrichment-failures?type=Exif").
    var adminEnrichmentInitialType by mutableStateOf<String?>(null)
    // The failures registry is reachable from three places now — the System
    // hub, a notification's actionUrl, and a task row whose queue is stuck —
    // so "volver" has to remember which one, instead of always landing on the
    // hub the way it did when the hub was the only door.
    var adminEnrichmentReturnTo by mutableStateOf(MoreSubscreen.AdminSystemHub)
    var adminUserEditorId by mutableStateOf<String?>(null)
    var adminLibraryEditorId by mutableStateOf<String?>(null)

    // ---- Álbumes ----
    var showCreateAlbum by mutableStateOf(false)
    var showAlbumTypeChooser by mutableStateOf(false)
    var showEditAlbum by mutableStateOf(false)
    // Smart album opened in the rule editor (SmartAlbumEditor subscreen); null = new album.
    var editingSmartAlbum by mutableStateOf<AlbumSummary?>(null)
    var showDeleteAlbum by mutableStateOf(false)
    var showLeaveAlbum by mutableStateOf(false)
    // Acciones en bloque sobre varias tarjetas seleccionadas en la lista.
    var showBulkDeleteAlbums by mutableStateOf(false)
    var showBulkLeaveAlbums by mutableStateOf(false)
    var showShares by mutableStateOf(false)
    var showCreateShare by mutableStateOf(false)
    var editingShareLink by mutableStateOf<AlbumShareLink?>(null)
    var showMembers by mutableStateOf(false)
    var showInviteMember by mutableStateOf(false)
    var addToAlbum by mutableStateOf<AddToAlbumState?>(null)
    var bulkAddToAlbum by mutableStateOf(false)
    var bulkAddSource by mutableStateOf<BulkAddSource?>(null)
    var pendingAddTarget by mutableStateOf<PendingAddTarget?>(null)
    // Álbum ya creado cuyo alta falló: reintentar solo repite el alta, nunca
    // crea un segundo álbum.
    var pendingAddAlbum by mutableStateOf<AlbumSummary?>(null)
    var pendingAssetAddSubmitting by mutableStateOf(false)
    var pendingAssetAddError by mutableStateOf<String?>(null)
    var pendingActionAlbum by mutableStateOf<AlbumSummary?>(null)
    // Confirmaciones de revocar enlace / quitar miembro (antes, un toque).
    var revokingShareToken by mutableStateOf<String?>(null)
    var revokingAlbumMember by mutableStateOf<AlbumPermission?>(null)
    var revokingFolderMember by mutableStateOf<AlbumPermission?>(null)

    // ---- Personas ----
    var showRenamePerson by mutableStateOf(false)
    var showMergePicker by mutableStateOf(false)
    // Confirmación de fusión: la persona elegida (que desaparecerá) y el
    // estado de la petición.
    var mergeSource by mutableStateOf<Person?>(null)
    var isMerging by mutableStateOf(false)
    var mergeError by mutableStateOf<String?>(null)
    var showAcceptAllSuggestions by mutableStateOf(false)
    var showDismissAllSuggestions by mutableStateOf(false)
    var showAssetFacesSheet by mutableStateOf(false)
    var assetFacesRevision by mutableStateOf(0)

    // ---- Timeline, carpetas y mover ----
    var showJumpToDate by mutableStateOf(false)
    var pendingJumpDate by mutableStateOf<kotlin.time.Instant?>(null)
    var showCreateFolder by mutableStateOf(false)
    var showEditFolder by mutableStateOf(false)
    var showDeleteFolder by mutableStateOf(false)
    var showEditSubfolder by mutableStateOf(false)
    var showDeleteSubfolder by mutableStateOf(false)
    var showFolderMembers by mutableStateOf(false)
    var showInviteFolderMember by mutableStateOf(false)
    var showMoveFolder by mutableStateOf(false)
    var showBulkDeleteFolders by mutableStateOf(false)
    var showBulkMoveFolders by mutableStateOf(false)
    var showMoveSelectedAssets by mutableStateOf(false)
    var showMoveSelectedAssetsTimeline by mutableStateOf(false)
    var showMoveSelectedAssetsInbox by mutableStateOf(false)
    var moveSelectionRequest by mutableStateOf<MoveSelectionRequest?>(null)
    var moveSelectionSubmitting by mutableStateOf(false)
    var moveSelectionError by mutableStateOf<String?>(null)
    var pendingActionFolder by mutableStateOf<FolderSummary?>(null)
    // Non-null while the inbox move "Revisar" grid is open: the chosen destination.
    var inboxReviewTarget by mutableStateOf<String?>(null)
    // Resumen del reparto por año tras mover por condiciones: se confirma antes
    // de volver a la bandeja.
    var organizeRuleSummary by mutableStateOf<com.photonne.app.data.models.MoveOutcome?>(null)
    var showSearchFilters by mutableStateOf(false)
    var showAlbumsFilters by mutableStateOf(false)
    var showFoldersFilters by mutableStateOf(false)

    // ---- Biblioteca, utilidades y sesión ----
    var showDuplicatesConfirm by mutableStateOf(false)
    var showUnarchiveAll by mutableStateOf(false)
    var showRestoreAllTrash by mutableStateOf(false)
    var showEmptyTrash by mutableStateOf(false)
    var showTrashScope by mutableStateOf(false)
    var showPurgeSelected by mutableStateOf(false)
    // Active tab of the unified Trash screen (Personal / Compartida).
    var trashTab by mutableStateOf(com.photonne.app.ui.library.TrashTab.Personal)
    // Cerrar sesión era un solo toque directo a authRepository.logout();
    // ahora confirma, y la confirmación avisa si quedan copias pendientes.
    var showLogoutConfirm by mutableStateOf(false)

    // ---- Funciones de navegación ----

    fun closeAssetDetail() {
        val previous = assetDetailStack.lastOrNull()
        if (previous != null) {
            assetDetailStack = assetDetailStack.dropLast(1)
            assetDetail = previous
        } else {
            assetDetail = null
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

    fun albumBack() {
        selectedAlbum = null
        val returnTo = albumReturnTo
        albumReturnTo = null
        if (returnTo != null) {
            selectedTab = returnTo.first
            moreSubscreen = returnTo.second
        }
    }

    /**
     * Abre un álbum desde Colecciones. El detalle vive en "Todos los álbumes"
     * (selectedTab = Albums), pero Atrás vuelve a donde se tocó: Colecciones o
     * la página de Fijados.
     */
    fun openAlbumFromCollections(album: AlbumSummary) {
        albumReturnTo = selectedTab to moreSubscreen
        moreSubscreen = null
        selectedAlbum = album
        selectedTab = MainTab.Albums
    }

    /** Igual que [openAlbumFromCollections], para una carpeta. */
    fun openFolderFromCollections(folder: FolderSummary) {
        folderReturnTo = selectedTab to moreSubscreen
        moreSubscreen = null
        folderBackStack.clear()
        selectedFolder = folder
        selectedTab = MainTab.Folders
    }

    /** Atrás desde Escenas, Objetos o un tema: a Explorar si se vino de allí. */
    fun subscreenBack() {
        val returnTo = subscreenReturnTo
        subscreenReturnTo = null
        moreSubscreen = returnTo
    }

    /** Abre [target] desde Explorar recordando volver a Explorar. */
    fun openFromExplore(target: MoreSubscreen) {
        subscreenReturnTo = if (moreSubscreen == MoreSubscreen.Explore) MoreSubscreen.Explore else null
        moreSubscreen = target
    }

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

    fun personBack() {
        selectedPerson = null
        if (personFromCollections) {
            // Abierta desde la fila de Personas de Colecciones: Atrás vuelve
            // allí, no a la lista completa de Personas que nunca se vio.
            personFromCollections = false
            moreSubscreen = null
            return
        }
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
        personFromCollections = false
        assetDetailStack = emptyList()
        assetDetail = null
        selectedTab = MainTab.More
        moreSubscreen = MoreSubscreen.People
        selectedPerson = Person(id = personId)
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

    /** Vuelta a la bandeja tras un movimiento por condiciones, con el contador y
     *  la rejilla al día. */
    fun organizeRuleMoved() {
        moreSubscreen = MoreSubscreen.OrganizeInbox
        organizeInboxViewModel.refresh()
        foldersViewModel.refreshOrganizeCount()
    }

    /**
     * Shared tab-switch side effects, run by both a nav tap and a settled swipe:
     * drop any open subscreen / person layer, collapse an open detail when the
     * same tab is re-selected, and clear the other tabs' multi-selection.
     */
    fun switchTab(tab: MainTab) {
        // Retocar Fotos ya activa (y sin subpantalla que cerrar) vuelve arriba,
        // como en cualquier app de galería; antes no hacía nada.
        if (tab == MainTab.Timeline && selectedTab == MainTab.Timeline &&
            moreSubscreen == null && selectedPerson == null
        ) {
            timelineScrollToTopTick++
        }
        // Lo mismo en Colecciones, solo en su raíz.
        if (tab == MainTab.Collections && selectedTab == MainTab.Collections &&
            moreSubscreen == null && selectedPerson == null
        ) {
            collectionsScrollToTopTick++
        }
        // "Todos los álbumes", "Todas las carpetas" y lo que tengan abierto son
        // páginas de Colecciones: tocar cualquier pestaña (también Colecciones
        // misma) las cierra y deja Colecciones en su raíz.
        if (selectedTab == MainTab.Albums || selectedTab == MainTab.Folders) {
            selectedAlbum = null
            selectedFolder = null
            folderBackStack.clear()
        }
        moreSubscreen = null
        selectedPerson = null
        folderReturnTo = null
        albumReturnTo = null
        personReturnTo = null
        personFromCollections = false
        subscreenReturnTo = null
        albumsViewModel.clearSelection()
        foldersViewModel.clearSelection()
        selectedTab = tab
    }
}
