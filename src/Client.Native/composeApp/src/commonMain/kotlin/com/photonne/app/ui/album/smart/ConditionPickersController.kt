package com.photonne.app.ui.album.smart

import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.folder.filterPersonalFolders
import com.photonne.app.data.folder.filterSharedFolders
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.ui.folder.FolderNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads and holds the data feeding the condition pickers (people / scenes /
 * objects search + the folder tree). Extracted from the smart-album editor so
 * the same searchable, debounced pickers back both "Nuevo álbum inteligente"
 * and the "Para organizar → Mover por condiciones" screen without duplication.
 *
 * Owned by a ViewModel and driven by its [scope]; the ViewModel just forwards
 * the picker calls and exposes [pickers].
 *
 * A failed load is kept as that picker's error — never turned into an empty
 * list nor marked as loaded — so the sheet shows the cause with Reintentar
 * instead of "no hay…", and reopening it asks again.
 */
class ConditionPickersController(
    private val search: SearchRepository,
    private val folders: FoldersRepository,
    private val authState: AuthStateHolder,
    private val errorFactory: UiErrorFactory,
    private val scope: CoroutineScope,
) {
    private val _pickers = MutableStateFlow(SmartPickerData())
    val pickers: StateFlow<SmartPickerData> = _pickers.asStateFlow()

    private var peopleJob: Job? = null
    private var sceneJob: Job? = null
    private var objectJob: Job? = null

    fun reset() {
        peopleJob?.cancel(); sceneJob?.cancel(); objectJob?.cancel()
        _pickers.value = SmartPickerData()
    }

    // ── Picker data (searchable, debounced) ──────────────────────────────────
    // A blank query is sent to the server as null, so each picker opens showing
    // the default top-N; typing narrows it server-side (people/scenes/objects).

    fun setPeopleQuery(query: String) {
        _pickers.update { it.copy(people = it.people.copy(query = query, isLoading = true, error = null)) }
        peopleJob?.cancel()
        peopleJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val res = load {
                search.people(limit = 200, search = query.trim().ifBlank { null })
                    .filter { !it.name.isNullOrBlank() }
                    .map { PersonRef(it.id, it.name!!, it.coverFaceId) }
            }
            _pickers.update { it.copy(people = it.people.settle(res, "No se pudieron cargar las personas")) }
        }
    }

    fun setSceneQuery(query: String) {
        _pickers.update { it.copy(scenes = it.scenes.copy(query = query, isLoading = true, error = null)) }
        sceneJob?.cancel()
        sceneJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val res = load {
                search.sceneLabels(limit = 120, search = query.trim().ifBlank { null })
                    .map { LabelRef(it.label, it.coverAssetId) }
            }
            _pickers.update { it.copy(scenes = it.scenes.settle(res, "No se pudieron cargar las escenas")) }
        }
    }

    fun setObjectQuery(query: String) {
        _pickers.update { it.copy(objects = it.objects.copy(query = query, isLoading = true, error = null)) }
        objectJob?.cancel()
        objectJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val res = load {
                search.objectLabels(limit = 120, search = query.trim().ifBlank { null })
                    .map { LabelRef(it.label, it.coverAssetId) }
            }
            _pickers.update { it.copy(objects = it.objects.settle(res, "No se pudieron cargar los objetos")) }
        }
    }

    /** Loads the folder list once (no server search) and builds both the flat
     * list (search mode) and the tree (browse mode) client-side. A failure
     * leaves it unloaded, so the next call (Reintentar, reopening) asks again. */
    fun ensureFolders() {
        if (_pickers.value.foldersLoaded || _pickers.value.foldersLoading) return
        _pickers.update { it.copy(foldersLoading = true, foldersError = null) }
        scope.launch {
            load {
                folders.list().filterNot { it.path.contains("/_trash") || it.path.contains("/_archive") }
            }.onSuccess { all ->
                val flat = all.map { FolderRef(it.id, it.name, it.isShared, it.path) }
                val roots = buildFolderTree(all)
                _pickers.update {
                    it.copy(folders = flat, folderRoots = roots, foldersLoading = false, foldersLoaded = true)
                }
            }.onFailure { error ->
                _pickers.update {
                    it.copy(foldersLoading = false, foldersError = errorFactory.from(error, "No se pudieron cargar las carpetas"))
                }
            }
        }
    }

    /** Runs [block], rethrowing cancellation (a newer query replaced this one). */
    private suspend fun <T> load(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

    /** The picker after its load: the results, or the error (not marked loaded). */
    private fun <T> PickerResults<T>.settle(res: Result<List<T>>, fallback: String): PickerResults<T> =
        res.fold(
            onSuccess = { copy(results = it, isLoading = false, loadedOnce = true, error = null) },
            onFailure = { copy(isLoading = false, error = errorFactory.from(it, fallback)) },
        )

    /** Builds the personal + shared folder tree from the flat list. Roots come
     * from [filterPersonalFolders]/[filterSharedFolders]; children are attached
     * recursively by parentFolderId. */
    private fun buildFolderTree(all: List<FolderSummary>): List<FolderNode> {
        val username = (authState.state.value as? AuthState.Authenticated)?.user?.username ?: ""
        val childrenByParent = all.groupBy { it.parentFolderId }

        fun node(f: FolderSummary): FolderNode = FolderNode(
            id = f.id,
            name = f.name,
            path = f.path,
            isShared = f.isShared,
            children = (childrenByParent[f.id] ?: emptyList())
                .sortedBy { it.name.lowercase() }
                .map { node(it) },
        )

        val personal = filterPersonalFolders(all, username).sortedBy { it.name.lowercase() }.map { node(it) }
        val shared = filterSharedFolders(all).sortedBy { it.name.lowercase() }.map { node(it) }
        return personal + shared
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
