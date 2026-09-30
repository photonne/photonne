package com.photonne.app.ui.utilities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.photonne.app.resources.Res
import com.photonne.app.resources.unsupported_files_subtitle
import com.photonne.app.resources.unsupported_files_title
import com.photonne.app.resources.utilities_section_duplicates
import com.photonne.app.resources.utilities_section_duplicates_subtitle
import com.photonne.app.resources.utilities_section_large_files
import com.photonne.app.resources.utilities_section_large_files_subtitle
import com.photonne.app.resources.utilities_section_locations
import com.photonne.app.resources.utilities_section_locations_subtitle
import com.photonne.app.ui.main.FormPageScaffold
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.SettingsItem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class UtilitiesEntry { Duplicates, LargeFiles, Locations, UnsupportedFiles }

private data class UtilitiesEntryDef(
    val entry: UtilitiesEntry,
    val title: StringResource,
    val subtitle: StringResource,
    val icon: ImageVector
)

@Composable
fun UtilitiesHubScreen(
    title: String,
    onBack: () -> Unit,
    onOpen: (UtilitiesEntry) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val entries = listOf(
        UtilitiesEntryDef(
            UtilitiesEntry.Duplicates,
            Res.string.utilities_section_duplicates,
            Res.string.utilities_section_duplicates_subtitle,
            PhotonneIcons.Copy
        ),
        UtilitiesEntryDef(
            UtilitiesEntry.LargeFiles,
            Res.string.utilities_section_large_files,
            Res.string.utilities_section_large_files_subtitle,
            Icons.Outlined.PhotoSizeSelectLarge
        ),
        UtilitiesEntryDef(
            UtilitiesEntry.Locations,
            Res.string.utilities_section_locations,
            Res.string.utilities_section_locations_subtitle,
            Icons.Outlined.FolderOpen
        ),
        UtilitiesEntryDef(
            UtilitiesEntry.UnsupportedFiles,
            Res.string.unsupported_files_title,
            Res.string.unsupported_files_subtitle,
            Icons.Outlined.InsertDriveFile
        )
    )

    FormPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
        page {
            SettingsGroup {
                entries.forEachIndexed { index, entry ->
                    SettingsItem(
                        headline = stringResource(entry.title),
                        supporting = stringResource(entry.subtitle),
                        leadingIcon = entry.icon,
                        onClick = { onOpen(entry.entry) },
                        showDivider = index > 0
                    )
                }
            }
        }
    }
}
