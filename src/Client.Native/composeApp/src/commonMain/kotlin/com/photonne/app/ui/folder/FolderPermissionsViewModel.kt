package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.AlbumMemberRole
import com.photonne.app.ui.util.sortedByNatural
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.ShareableUser
import com.photonne.app.util.suspendRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderPermissionsUiState(
    val folderId: String? = null,
    val members: List<AlbumPermission> = emptyList(),
    val candidates: List<ShareableUser> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
) {
    val invitableUsers: List<ShareableUser>
        get() {
            val taken = members.map { it.userId }.toHashSet()
            return candidates.filterNot { it.id in taken }
                .sortedByNatural { it.username }
        }
}

class FolderPermissionsViewModel(
    private val foldersRepository: FoldersRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(FolderPermissionsUiState())
    val state: StateFlow<FolderPermissionsUiState> = _state.asStateFlow()

    /** The member list being fetched. Replaced on every open/refresh: a slow
     *  answer for the previous sheet must not land on (and retarget) this one. */
    private var loadJob: Job? = null

    fun open(folderId: String) {
        if (_state.value.folderId == folderId && _state.value.members.isNotEmpty()) return
        _state.value = FolderPermissionsUiState(folderId = folderId, isLoading = true)
        loadJob?.cancel()
        loadJob = viewModelScope.launch { reloadInternal(folderId) }
    }

    fun refresh() {
        val folderId = _state.value.folderId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { reloadInternal(folderId) }
    }

    fun grant(
        user: ShareableUser,
        role: AlbumMemberRole,
        onMembershipChanged: (memberCount: Int) -> Unit = {},
        /** Siempre que la petición sale bien, también al re-invitar a un miembro. */
        onSuccess: () -> Unit = {}
    ) {
        val folderId = _state.value.folderId ?: return
        if (_state.value.isMutating) return
        val wasMember = _state.value.members.any { it.userId == user.id }
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { foldersRepository.grantMember(folderId, user.id, role) }
                .onSuccess { permission ->
                    _state.update {
                        val without = it.members.filterNot { p -> p.userId == permission.userId }
                        it.copy(members = without + permission, isMutating = false)
                    }
                    if (!wasMember) onMembershipChanged(_state.value.members.size)
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo invitar al miembro")
                        )
                    }
                }
        }
    }

    fun changeRole(member: AlbumPermission, role: AlbumMemberRole) {
        if (member.role == role) return
        grant(
            user = ShareableUser(id = member.userId, username = member.username, email = member.email),
            role = role
        )
    }

    fun revoke(
        member: AlbumPermission,
        onMembershipChanged: (memberCount: Int) -> Unit = {}
    ) {
        val folderId = _state.value.folderId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { foldersRepository.revokeMember(folderId, member.userId) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            members = it.members.filterNot { p -> p.userId == member.userId },
                            isMutating = false
                        )
                    }
                    onMembershipChanged(_state.value.members.size)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo quitar al miembro")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun close() {
        loadJob?.cancel()
        _state.value = FolderPermissionsUiState()
    }

    private suspend fun reloadInternal(folderId: String) {
        suspendRunCatching {
            val members = foldersRepository.listMembers(folderId)
            val candidates = runCatching { albumsRepository.shareableUsers() }.getOrDefault(emptyList())
            members to candidates
        }
            .onSuccess { (members, candidates) ->
                _state.update {
                    if (it.folderId != folderId) return@update it
                    it.copy(
                        members = members,
                        candidates = candidates,
                        isLoading = false
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    if (it.folderId != folderId) return@update it
                    it.copy(
                        isLoading = false,
                        error = errorFactory.from(error, "No se pudieron cargar los miembros")
                    )
                }
            }
    }
}
