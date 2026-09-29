package com.photonne.app.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.DownloadFormat
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_share
import com.photonne.app.resources.asset_action_download
import com.photonne.app.resources.download_format_jpeg
import com.photonne.app.resources.download_format_jpeg_subtitle
import com.photonne.app.resources.download_format_original
import com.photonne.app.resources.download_format_original_extension
import com.photonne.app.resources.download_format_original_subtitle
import com.photonne.app.resources.download_format_scope_all
import com.photonne.app.resources.download_format_scope_some
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * "Original or JPG?", asked every time a download or a share includes a RAW
 * or a HEIC/HEIF. Same shape as [ShareAssetsDialog], the other question the
 * selection actions ask.
 *
 * There is no "remember my answer": the right one depends on where the photo
 * is going (a RAW to edit, a JPG to send), not on who is asking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFormatSheet(
    chooser: DownloadFormatChooser,
    onDismiss: () -> Unit,
    onChoose: (DownloadFormat) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(
                        when (chooser.action) {
                            FormatChooserAction.Download -> Res.string.asset_action_download
                            FormatChooserAction.Share -> Res.string.action_share
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                val total = chooser.assetIds.size
                if (total > 1) {
                    Text(
                        text = if (chooser.convertibleCount >= total) {
                            stringResource(Res.string.download_format_scope_all, total)
                        } else {
                            stringResource(
                                Res.string.download_format_scope_some,
                                chooser.convertibleCount,
                                total
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FormatOptionRow(
                icon = Icons.Outlined.InsertDriveFile,
                title = originalTitle(chooser.extensions),
                subtitle = stringResource(Res.string.download_format_original_subtitle),
                onClick = { onChoose(DownloadFormat.Original) }
            )
            FormatOptionRow(
                icon = Icons.Outlined.Image,
                title = stringResource(Res.string.download_format_jpeg),
                subtitle = stringResource(Res.string.download_format_jpeg_subtitle),
                onClick = { onChoose(DownloadFormat.Jpeg) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        }
    }
}

/** "Original (.DNG)" when every file shares one extension, plain "Original" otherwise. */
@Composable
private fun originalTitle(extensions: List<String>): String {
    val single = extensions.singleOrNull()
    return if (single != null) {
        stringResource(Res.string.download_format_original_extension, single.uppercase())
    } else {
        stringResource(Res.string.download_format_original)
    }
}

@Composable
private fun FormatOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.padding(end = Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
