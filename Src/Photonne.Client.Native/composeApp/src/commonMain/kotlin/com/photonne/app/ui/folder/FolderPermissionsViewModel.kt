package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.AlbumMemberRole
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.ShareableUser
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
    val errorMessage: String? = null
) {
    val invitableUsers: List<ShareableUser>
        get() {
            val taken = members.map { it.userId }.toHashSet()
            return candidates.filterNot { it.id in taken }
                .sortedBy { it.username.lowercase() }
        }
}

class FolderPermissionsViewModel(
    private val foldersRepository: FoldersRepository,
    private val albumsRepository: AlbumsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FolderPermissionsUiState())
    val state: StateFlow<FolderPermissionsUiState> = _state.asStateFlow()

    fun open(folderId: String) {
        if (_state.value.folderId == folderId && _state.value.members.isNotEmpty()) return
        _state.value = FolderPermissionsUiState(folderId = folderId, isLoading = true)
        viewModelScope.launch { reloadInternal(folderId) }
    }

    fun refresh() {
        val folderId = _state.value.folderId ?: return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch { reloadInternal(folderId) }
    }

    fun grant(user: ShareableUser, role: AlbumMemberRole) {
        val folderId = _state.value.folderId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { foldersRepository.grantMember(folderId, user.id, role) }
                .onSuccess { permission ->
                    _state.update {
                        val without = it.members.filterNot { p -> p.userId == permission.userId }
                        it.copy(members = without + permission, isMutating = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to invite member"
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

    fun revoke(member: AlbumPermission) {
        val folderId = _state.value.folderId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { foldersRepository.revokeMember(folderId, member.userId) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            members = it.members.filterNot { p -> p.userId == member.userId },
                            isMutating = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to remove member"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun close() {
        _state.value = FolderPermissionsUiState()
    }

    private suspend fun reloadInternal(folderId: String) {
        runCatching {
            val members = foldersRepository.listMembers(folderId)
            val candidates = runCatching { albumsRepository.shareableUsers() }.getOrDefault(emptyList())
            members to candidates
        }
            .onSuccess { (members, candidates) ->
                _state.update {
                    it.copy(
                        folderId = folderId,
                        members = members,
                        candidates = candidates,
                        isLoading = false
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load members"
                    )
                }
            }
    }
}
