package com.photonne.app.ui.folder

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_invite
import com.photonne.app.resources.action_remove
import com.photonne.app.resources.folder_permissions_empty
import com.photonne.app.resources.folder_permissions_title
import com.photonne.app.resources.permissions_invite_empty
import com.photonne.app.resources.permissions_invite_people
import com.photonne.app.resources.permissions_invite_role
import com.photonne.app.resources.permissions_invite_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ManageFolderPermissionsDialog(
    state: FolderPermissionsUiState,
    onDismiss: () -> Unit,
    onInvite: () -> Unit,
    onChangeRole: (AlbumPermission, AlbumMemberRole) -> Unit,
    onRevoke: (AlbumPermission) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isMutating) onDismiss() },
        title = { Text(stringResource(Res.string.folder_permissions_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(min = 140.dp, max = 360.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    state.isLoading && state.members.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    state.members.isEmpty() -> Text(
                        stringResource(Res.string.folder_permissions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.members, key = { it.id }) { member ->
                            MemberRow(
                                member = member,
                                onChangeRole = { role -> onChangeRole(member, role) },
                                onRevoke = { onRevoke(member) }
                            )
                        }
                    }
                }
                if (state.errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInvite, enabled = !state.isMutating) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.action_invite))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isMutating) {
                Text(stringResource(Res.string.action_close))
            }
        }
    )
}

@Composable
private fun MemberRow(
    member: AlbumPermission,
    onChangeRole: (AlbumMemberRole) -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.username, style = MaterialTheme.typography.bodyMedium)
            Text(
                member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        RoleChip(role = member.role, onChangeRole = onChangeRole)
        IconButton(onClick = onRevoke) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(Res.string.action_remove),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun RoleChip(role: AlbumMemberRole, onChangeRole: (AlbumMemberRole) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(role.label) })
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

@Composable
fun InviteFolderMemberDialog(
    candidates: List<ShareableUser>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onInvite: (ShareableUser, AlbumMemberRole) -> Unit
) {
    var role by remember { mutableStateOf(AlbumMemberRole.Viewer) }
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(Res.string.permissions_invite_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(min = 200.dp, max = 360.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(Res.string.permissions_invite_role),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            } else AssistChipDefaults.assistChipColors()
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(Res.string.permissions_invite_people),
                    style = MaterialTheme.typography.labelMedium
                )
                if (candidates.isEmpty()) {
                    Text(
                        stringResource(Res.string.permissions_invite_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
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
                if (errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_close))
            }
        }
    )
}

@Composable
private fun UserRow(user: ShareableUser, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
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
