package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.folder.filterPersonalFolders
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.data.models.FolderSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FoldersTab { Personal, Shared, Libraries }

data class FoldersUiState(
    val personalFolders: List<FolderSummary> = emptyList(),
    val sharedFolders: List<FolderSummary> = emptyList(),
    val libraries: List<ExternalLibraryDto> = emptyList(),
    val libraryRoots: Map<String, FolderSummary> = emptyMap(),
    val selectedTab: FoldersTab = FoldersTab.Personal,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null
)

class FoldersViewModel(
    private val repository: FoldersRepository,
    private val authState: AuthStateHolder
) : ViewModel() {

    private val _state = MutableStateFlow(FoldersUiState())
    val state: StateFlow<FoldersUiState> = _state.asStateFlow()

    private var allFolders: List<FolderSummary> = emptyList()

    init { refresh() }

    fun selectTab(tab: FoldersTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun refresh() {
        val userId = (authState.state.value as? AuthState.Authenticated)?.user?.id
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val folders = async { repository.list() }
                val libs = async {
                    runCatching { repository.listExternalLibraries() }.getOrDefault(emptyList())
                }
                awaitAll(folders, libs)
                folders.await() to libs.await()
            }
                .onSuccess { (folders, libs) ->
                    allFolders = folders
                    val baseScope = if (userId.isNullOrBlank()) folders
                        else filterPersonalFolders(folders, userId)
                    val personal = baseScope.filter { !it.isShared }
                    val shared = folders.filter { isSharedFolder(it) }
                    val libRoots = folders
                        .filter { it.externalLibraryId != null && it.parentFolderId == null }
                        .associateBy { it.externalLibraryId!! }
                    _state.update {
                        it.copy(
                            personalFolders = personal.sortedBy { f -> f.path.lowercase() },
                            sharedFolders = shared.sortedBy { f -> f.path.lowercase() },
                            libraries = libs.sortedBy { l -> l.name.lowercase() },
                            libraryRoots = libRoots,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load folders"
                        )
                    }
                }
        }
    }

    fun resolveLibraryRoot(libraryId: String): FolderSummary? =
        _state.value.libraryRoots[libraryId]

    fun create(name: String, parentFolderId: String?, onCreated: (FolderSummary) -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.create(
                    name = name.trim(),
                    parentFolderId = parentFolderId,
                    isSharedSpace = false
                )
            }
                .onSuccess { folder ->
                    allFolders = listOf(folder) + allFolders
                    repartition()
                    _state.update { it.copy(isMutating = false) }
                    onCreated(folder)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to create folder"
                        )
                    }
                }
        }
    }

    fun applyUpdate(updated: FolderSummary) {
        allFolders = allFolders.map { if (it.id == updated.id) updated else it }
        repartition()
    }

    fun applyDelete(folderId: String) {
        allFolders = allFolders.filterNot { it.id == folderId }
        repartition()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun repartition() {
        val userId = (authState.state.value as? AuthState.Authenticated)?.user?.id
        val baseScope = if (userId.isNullOrBlank()) allFolders
            else filterPersonalFolders(allFolders, userId)
        val personal = baseScope.filter { !it.isShared }
        val shared = allFolders.filter { isSharedFolder(it) }
        _state.update {
            it.copy(
                personalFolders = personal.sortedBy { f -> f.path.lowercase() },
                sharedFolders = shared.sortedBy { f -> f.path.lowercase() }
            )
        }
    }
}

private fun isSharedFolder(folder: FolderSummary): Boolean {
    if (folder.isShared) return true
    val normalized = folder.path.replace('\\', '/')
    return normalized.startsWith("/assets/shared", ignoreCase = true)
}
