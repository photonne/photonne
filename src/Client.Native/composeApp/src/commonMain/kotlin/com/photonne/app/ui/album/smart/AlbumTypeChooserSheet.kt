package com.photonne.app.ui.album.smart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.SheetOptionRow
import androidx.compose.material3.rememberModalBottomSheetState

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
            SheetHeader(
                stringResource(Res.string.album_action_new),
                modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md),
            )
            SheetOptionRow(
                icon = PhotonneIcons.Album,
                title = stringResource(Res.string.album_type_manual_title),
                subtitle = stringResource(Res.string.album_type_manual_subtitle),
                onClick = onManual,
            )
            SheetOptionRow(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(Res.string.album_type_smart_title),
                subtitle = stringResource(Res.string.album_type_smart_subtitle),
                onClick = onSmart,
            )
        }
    }
}
