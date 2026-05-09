package com.photonne.app.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.AlbumSummary

@Composable
fun AddToAlbumDialog(
    albums: List<AlbumSummary>,
    isLoadingAlbums: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCreateNew: () -> Unit,
    onAlbumSelected: (AlbumSummary) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Add to album") },
        text = {
            Column(modifier = Modifier.heightIn(min = 120.dp, max = 360.dp)) {
                when {
                    isLoadingAlbums && albums.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    albums.isEmpty() -> Text(
                        "You don't have any albums yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(albums, key = { it.id }) { album ->
                            AlbumPickerRow(album = album, onClick = { onAlbumSelected(album) })
                        }
                    }
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew, enabled = !isSubmitting) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text("New album")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Close") }
        }
    )
}

@Composable
private fun AlbumPickerRow(album: AlbumSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(album.name, style = MaterialTheme.typography.titleSmall)
        Text(
            text = "${album.assetCount} items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
