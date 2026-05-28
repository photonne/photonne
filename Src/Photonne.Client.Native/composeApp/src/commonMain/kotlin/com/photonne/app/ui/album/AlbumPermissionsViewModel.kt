package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.AlbumMemberRole
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.ShareableUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumPermissionsUiState(
    val albumId: String? = null,
    val members: List<AlbumPermission> = emptyList(),
    val candidates: List<ShareableUser> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
) {
    /** Users not yet members of the album, ready to be invited. */
    val invitableUsers: List<ShareableUser>
        get() {
            val taken = members.map { it.userId }.toHashSet()
            return candidates.filterNot { it.id in taken }
                .sortedBy { it.username.lowercase() }
        }
}

class AlbumPermissionsViewModel(
    private val repository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumPermissionsUiState())
    val state: StateFlow<AlbumPermissionsUiState> = _state.asStateFlow()

    fun open(albumId: String) {
        if (_state.value.albumId == albumId && _state.value.members.isNotEmpty()) return
        _state.value = AlbumPermissionsUiState(albumId = albumId, isLoading = true)
        viewModelScope.launch { reloadInternal(albumId) }
    }

    fun refresh() {
        val albumId = _state.value.albumId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { reloadInternal(albumId) }
    }

    fun grant(
        user: ShareableUser,
        role: AlbumMemberRole,
        onMembershipChanged: (memberCount: Int) -> Unit = {}
    ) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        val wasMember = _state.value.members.any { it.userId == user.id }
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.grantMember(albumId, user.id, role) }
                .onSuccess { permission ->
                    _state.update {
                        val withoutSameUser = it.members.filterNot { p -> p.userId == permission.userId }
                        it.copy(
                            members = withoutSameUser + permission,
                            isMutating = false
                        )
                    }
                    if (!wasMember) onMembershipChanged(_state.value.members.size)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to invite member")
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
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.revokeMember(albumId, member.userId) }
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
                            error = errorFactory.from(error, "Failed to remove member")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun close() {
        _state.value = AlbumPermissionsUiState()
    }

    private suspend fun reloadInternal(albumId: String) {
        runCatching {
            val members = repository.listMembers(albumId)
            val candidates = runCatching { repository.shareableUsers() }.getOrDefault(emptyList())
            members to candidates
        }
            .onSuccess { (members, candidates) ->
                _state.update {
                    it.copy(
                        albumId = albumId,
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
                        error = errorFactory.from(error, "Failed to load members")
                    )
                }
            }
    }
}
