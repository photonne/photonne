package com.photonne.app.ui.album

import androidx.compose.runtime.Composable
import com.photonne.app.data.models.AlbumMemberRole
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.ShareableUser
import com.photonne.app.resources.Res
import com.photonne.app.resources.permissions_empty
import com.photonne.app.resources.permissions_title
import com.photonne.app.ui.share.InviteMemberSheet
import com.photonne.app.ui.share.ManageMembersSheet
import org.jetbrains.compose.resources.stringResource

// Envoltorios de las hojas de miembros comunes (ui/share/PermissionsUi.kt)
// con los textos del álbum.

@Composable
fun ManagePermissionsDialog(
    state: AlbumPermissionsUiState,
    onDismiss: () -> Unit,
    onInvite: () -> Unit,
    onChangeRole: (AlbumPermission, AlbumMemberRole) -> Unit,
    onRevoke: (AlbumPermission) -> Unit
) {
    ManageMembersSheet(
        title = stringResource(Res.string.permissions_title),
        emptyText = stringResource(Res.string.permissions_empty),
        members = state.members,
        isLoading = state.isLoading,
        isMutating = state.isMutating,
        error = state.error,
        onDismiss = onDismiss,
        onInvite = onInvite,
        onChangeRole = onChangeRole,
        onRevoke = onRevoke
    )
}

@Composable
fun InviteMemberDialog(
    candidates: List<ShareableUser>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onInvite: (ShareableUser, AlbumMemberRole) -> Unit
) {
    InviteMemberSheet(
        candidates = candidates,
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onInvite = onInvite
    )
}
