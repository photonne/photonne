package com.photonne.app.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_share
import com.photonne.app.resources.share_choice_direct
import com.photonne.app.resources.share_choice_direct_subtitle
import com.photonne.app.resources.share_choice_link
import com.photonne.app.resources.share_choice_link_subtitle
import com.photonne.app.resources.share_link_album_name_label
import com.photonne.app.resources.share_link_copy
import com.photonne.app.resources.share_link_field_label
import com.photonne.app.resources.share_link_title
import org.jetbrains.compose.resources.stringResource

/**
 * Two-option chooser shown when the user taps "Share" on the selection
 * top bar. Either pipe the assets into the OS share sheet
 * ([onShareDirectly]) or create a Photonne album + public link
 * ([onCreateLink]).
 */
@Composable
fun ShareAssetsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onShareDirectly: () -> Unit,
    onCreateLink: (albumName: String) -> Unit
) {
    var albumName by remember { mutableStateOf(defaultAlbumName(selectedCount)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.action_share)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShareOptionRow(
                    title = stringResource(Res.string.share_choice_direct),
                    subtitle = stringResource(Res.string.share_choice_direct_subtitle),
                    onClick = onShareDirectly
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShareOptionRow(
                        title = stringResource(Res.string.share_choice_link),
                        subtitle = stringResource(Res.string.share_choice_link_subtitle),
                        onClick = { onCreateLink(albumName.trim()) }
                    )
                    OutlinedTextField(
                        value = albumName,
                        onValueChange = { albumName = it },
                        label = { Text(stringResource(Res.string.share_link_album_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ShareOptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ShareLinkResultDialog(
    url: String,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.share_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.share_link_field_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    url,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onCopy(url) }) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Text(
                            stringResource(Res.string.share_link_copy),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_close))
            }
        },
        dismissButton = {}
    )
}

private fun defaultAlbumName(count: Int): String = "Compartir ($count)"
