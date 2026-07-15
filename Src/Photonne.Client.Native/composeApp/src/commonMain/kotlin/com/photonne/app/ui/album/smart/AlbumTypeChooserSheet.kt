package com.photonne.app.ui.album.smart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.album_type_manual_subtitle
import com.photonne.app.resources.album_type_manual_title
import com.photonne.app.resources.album_type_smart_subtitle
import com.photonne.app.resources.album_type_smart_title
import org.jetbrains.compose.resources.stringResource

/**
 * Chooser behind the albums-list create action: pick a classic manual album or a
 * smart (conditional) one. The smart option is the first-class entry into the
 * rule editor (docs/smart-albums/creation-ux.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumTypeChooserSheet(
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onSmart: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.album_action_new),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            ChooserRow(
                icon = Icons.Outlined.PhotoAlbum,
                title = stringResource(Res.string.album_type_manual_title),
                subtitle = stringResource(Res.string.album_type_manual_subtitle),
                onClick = onManual,
            )
            ChooserRow(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(Res.string.album_type_smart_title),
                subtitle = stringResource(Res.string.album_type_smart_subtitle),
                onClick = onSmart,
            )
        }
    }
}

@Composable
private fun ChooserRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
