package com.photonne.app.ui.folder

import androidx.compose.runtime.Composable
import com.photonne.app.data.models.AlbumMemberRole
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.ShareableUser
import com.photonne.app.resources.Res
import com.photonne.app.resources.folder_permissions_empty
import com.photonne.app.resources.folder_permissions_title
import com.photonne.app.ui.share.InviteMemberSheet
import com.photonne.app.ui.share.ManageMembersSheet
import org.jetbrains.compose.resources.stringResource

// Envoltorios de las hojas de miembros comunes (ui/share/PermissionsUi.kt)
// con los textos de la carpeta.

@Composable
fun ManageFolderPermissionsDialog(
    state: FolderPermissionsUiState,
    onDismiss: () -> Unit,
    onInvite: () -> Unit,
    onChangeRole: (AlbumPermission, AlbumMemberRole) -> Unit,
    onRevoke: (AlbumPermission) -> Unit
) {
    ManageMembersSheet(
        title = stringResource(Res.string.folder_permissions_title),
        emptyText = stringResource(Res.string.folder_permissions_empty),
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
fun InviteFolderMemberDialog(
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
