package com.photonne.app.ui.album

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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.AlbumShareLink

@Composable
fun ManageSharesDialog(
    state: AlbumSharesUiState,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onRevoke: (token: String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { if (!state.isMutating) onDismiss() },
        title = { Text("Public share links") },
        text = {
            Column(
                modifier = Modifier.heightIn(min = 120.dp, max = 360.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    state.isLoading && state.links.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    state.links.isEmpty() -> Text(
                        "No share links yet. Anyone with the link will be able to view this album.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.links, key = { it.token }) { link ->
                            ShareLinkRow(
                                link = link,
                                onCopy = { clipboard.setText(AnnotatedString(link.shareUrl)) },
                                onRevoke = { onRevoke(link.token) }
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
            TextButton(onClick = onCreate, enabled = !state.isMutating) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text("New link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isMutating) { Text("Close") }
        }
    )
}

@Composable
private fun ShareLinkRow(
    link: AlbumShareLink,
    onCopy: () -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.shareUrl.ifBlank { "/share/${link.token}" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            val attrs = buildList {
                if (link.hasPassword) add("password")
                link.expiresAt?.let { add("expires ${it}") }
                link.maxViews?.let { add("max ${it}") }
                add("${link.viewCount} views")
                if (!link.allowDownload) add("no downloads")
            }
            Text(
                text = attrs.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Filled.Share, contentDescription = "Copy link")
        }
        IconButton(onClick = onRevoke) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Revoke",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun CreateShareDialog(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?
    ) -> Unit
) {
    var passwordEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var allowDownload by remember { mutableStateOf(true) }
    var maxViewsEnabled by remember { mutableStateOf(false) }
    var maxViews by remember { mutableStateOf("") }

    val canSubmit = !isSubmitting &&
        (!passwordEnabled || password.trim().isNotEmpty()) &&
        (!maxViewsEnabled || maxViews.toIntOrNull()?.let { it > 0 } == true)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Create share link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(
                    label = "Allow downloads",
                    checked = allowDownload,
                    onCheckedChange = { allowDownload = it },
                    enabled = !isSubmitting
                )
                ToggleRow(
                    label = "Protect with password",
                    checked = passwordEnabled,
                    onCheckedChange = { passwordEnabled = it },
                    enabled = !isSubmitting
                )
                if (passwordEnabled) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ToggleRow(
                    label = "Limit number of views",
                    checked = maxViewsEnabled,
                    onCheckedChange = { maxViewsEnabled = it },
                    enabled = !isSubmitting
                )
                if (maxViewsEnabled) {
                    OutlinedTextField(
                        value = maxViews,
                        onValueChange = { input -> maxViews = input.filter { it.isDigit() } },
                        label = { Text("Max views") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        if (passwordEnabled) password else null,
                        allowDownload,
                        if (maxViewsEnabled) maxViews.toIntOrNull() else null
                    )
                },
                enabled = canSubmit
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}

@Composable
fun LeaveAlbumDialog(
    albumName: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Leave album") },
        text = {
            Column {
                Text("You will lose access to \"$albumName\". The owner can invite you back.")
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting) { Text("Leave") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
