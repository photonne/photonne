package com.photonne.app.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.AlbumMemberRole
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.ShareableUser
import com.photonne.app.data.error.UiError
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_invite
import com.photonne.app.resources.action_remove
import com.photonne.app.resources.permissions_invite_empty
import com.photonne.app.resources.permissions_invite_people
import com.photonne.app.resources.permissions_invite_role
import com.photonne.app.resources.permissions_invite_title
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.SheetHeader

/*
 * Hojas de miembros compartidas por álbumes y carpetas. Eran dos ficheros
 * clonados (`album/PermissionDialogs.kt` y `folder/FolderPermissionDialogs.kt`)
 * que solo cambiaban el título, el texto de lista vacía y el tipo de estado;
 * los dos usan los mismos roles (`AlbumMemberRole`) y el mismo modelo de
 * miembro. Ahora aquí, y aquellos ficheros son envoltorios finos.
 */

/**
 * Hoja "Miembros": lista con rol cambiable y quitar, más Invitar y Cerrar.
 * [title] y [emptyText] son lo único que cambia entre álbum y carpeta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageMembersSheet(
    title: String,
    emptyText: String,
    members: List<AlbumPermission>,
    isLoading: Boolean,
    isMutating: Boolean,
    error: UiError?,
    onDismiss: () -> Unit,
    onInvite: () -> Unit,
    onChangeRole: (AlbumPermission, AlbumMemberRole) -> Unit,
    onRevoke: (AlbumPermission) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!isMutating) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SheetHeader(title)
            Column(
                modifier = Modifier.heightIn(min = 140.dp, max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                when {
                    isLoading && members.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    members.isEmpty() -> Text(
                        emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(members, key = { it.id }) { member ->
                            MemberRow(
                                member = member,
                                onChangeRole = { role -> onChangeRole(member, role) },
                                onRevoke = { onRevoke(member) }
                            )
                        }
                    }
                }
                error?.let { err ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(err.userMessage, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onInvite, enabled = !isMutating) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(Res.string.action_invite))
                }
                Spacer(Modifier.width(Spacing.sm))
                TextButton(onClick = onDismiss, enabled = !isMutating) {
                    Text(stringResource(Res.string.action_close))
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: AlbumPermission,
    onChangeRole: (AlbumMemberRole) -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.username, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        RoleChip(role = member.role, onChangeRole = onChangeRole)
        IconButton(onClick = onRevoke) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(Res.string.action_remove),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleChip(role: AlbumMemberRole, onChangeRole: (AlbumMemberRole) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(role.label) },
            colors = AssistChipDefaults.assistChipColors()
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AlbumMemberRole.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        open = false
                        onChangeRole(option)
                    }
                )
            }
        }
    }
}

/** Hoja "Invitar": elegir rol y tocar a quién se invita. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteMemberSheet(
    candidates: List<ShareableUser>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onInvite: (ShareableUser, AlbumMemberRole) -> Unit
) {
    var role by remember { mutableStateOf(AlbumMemberRole.Viewer) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SheetHeader(stringResource(Res.string.permissions_invite_title))
            Text(
                stringResource(Res.string.permissions_invite_role),
                style = MaterialTheme.typography.labelMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                AlbumMemberRole.entries.forEach { option ->
                    AssistChip(
                        onClick = { role = option },
                        label = { Text(option.label) },
                        colors = if (option == role) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        }
                    )
                }
            }
            Text(
                stringResource(Res.string.permissions_invite_people),
                style = MaterialTheme.typography.labelMedium
            )
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp)
            ) {
                if (candidates.isEmpty()) {
                    Text(
                        stringResource(Res.string.permissions_invite_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(candidates, key = { it.id }) { user ->
                            UserRow(
                                user = user,
                                onClick = { if (!isSubmitting) onInvite(user, role) }
                            )
                        }
                    }
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text(stringResource(Res.string.action_close))
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: ShareableUser, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm)
    ) {
        Text(user.username, style = MaterialTheme.typography.bodyMedium)
        Text(
            user.email,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
