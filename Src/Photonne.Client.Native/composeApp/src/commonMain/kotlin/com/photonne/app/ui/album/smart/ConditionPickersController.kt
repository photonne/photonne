package com.photonne.app.ui.album.smart

import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.folder.filterPersonalFolders
import com.photonne.app.data.folder.filterSharedFolders
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.search.SearchRepository
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
 */
class ConditionPickersController(
    private val search: SearchRepository,
    private val folders: FoldersRepository,
    private val authState: AuthStateHolder,
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
        _pickers.update { it.copy(people = it.people.copy(query = query, isLoading = true)) }
        peopleJob?.cancel()
        peopleJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val res = runCatching {
                search.people(limit = 200, search = query.trim().ifBlank { null })
                    .filter { !it.name.isNullOrBlank() }
                    .map { PersonRef(it.id, it.name!!, it.coverFaceId) }
            }.getOrDefault(emptyList())
            _pickers.update { it.copy(people = it.people.copy(results = res, isLoading = false, loadedOnce = true)) }
        }
    }

    fun setSceneQuery(query: String) {
        _pickers.update { it.copy(scenes = it.scenes.copy(query = query, isLoading = true)) }
        sceneJob?.cancel()
        sceneJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val res = runCatching {
                search.sceneLabels(limit = 120, search = query.trim().ifBlank { null })
                    .map { LabelRef(it.label, it.coverAssetId) }
            }.getOrDefault(emptyList())
            _pickers.update { it.copy(scenes = it.scenes.copy(results = res, isLoading = false, loadedOnce = true)) }
        }
    }

    fun setObjectQuery(query: String) {
        _pickers.update { it.copy(objects = it.objects.copy(query = query, isLoading = true)) }
        objectJob?.cancel()
        objectJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val res = runCatching {
                search.objectLabels(limit = 120, search = query.trim().ifBlank { null })
                    .map { LabelRef(it.label, it.coverAssetId) }
            }.getOrDefault(emptyList())
            _pickers.update { it.copy(objects = it.objects.copy(results = res, isLoading = false, loadedOnce = true)) }
        }
    }

    /** Loads the folder list once (no server search) and builds both the flat
     * list (search mode) and the tree (browse mode) client-side. */
    fun ensureFolders() {
        if (_pickers.value.foldersLoaded || _pickers.value.foldersLoading) return
        _pickers.update { it.copy(foldersLoading = true) }
        scope.launch {
            val all = runCatching {
                folders.list().filterNot { it.path.contains("/_trash") || it.path.contains("/_archive") }
            }.getOrDefault(emptyList())

            val flat = all.map { FolderRef(it.id, it.name, it.isShared, it.path) }
            val roots = buildFolderTree(all)
            _pickers.update {
                it.copy(folders = flat, folderRoots = roots, foldersLoading = false, foldersLoaded = true)
            }
        }
    }

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
