package com.photonne.app.ui.utilities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.photonne.app.data.models.UtilitiesSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.unsupported_files_subtitle
import com.photonne.app.resources.unsupported_files_title
import com.photonne.app.resources.utilities_section_duplicates
import com.photonne.app.resources.utilities_section_duplicates_subtitle
import com.photonne.app.resources.utilities_section_large_files
import com.photonne.app.resources.utilities_section_large_files_subtitle
import com.photonne.app.resources.utilities_section_locations
import com.photonne.app.resources.utilities_section_locations_subtitle
import com.photonne.app.resources.utilities_summary_duplicates
import com.photonne.app.resources.utilities_summary_large_files
import com.photonne.app.resources.utilities_summary_nothing
import com.photonne.app.resources.utilities_summary_unsupported
import com.photonne.app.ui.format.humanBytes
import com.photonne.app.ui.main.FormPageScaffold
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.SettingsItem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
    onChromeVisibleChange: (Boolean) -> Unit = {},
    viewModel: UtilitiesHubViewModel = koinViewModel()
) {
    val summary by viewModel.summary.collectAsState()
    // Al abrir el hub y al volver de una subpantalla (el hub sale de la
    // composición mientras otra está a la vista), con la cifra anterior
    // visible hasta que llega la nueva.
    LaunchedEffect(viewModel) { viewModel.refresh() }

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
                    val figure = summary?.let { liveFigure(entry.entry, it) }
                    SettingsItem(
                        headline = stringResource(entry.title),
                        // Sin cifra (cargando la primera vez, servidor antiguo o
                        // fallo, o Ubicaciones, que no tiene) queda el subtítulo fijo.
                        supporting = if (figure == null) stringResource(entry.subtitle) else null,
                        supportingContent = figure?.let { { LiveFigureText(it) } },
                        leadingIcon = entry.icon,
                        onClick = { onOpen(entry.entry) },
                        showDivider = index > 0
                    )
                }
            }
        }
    }
}

/** Cifra viva de una fila del hub; [pending] = hay algo que revisar. */
private data class LiveFigure(val text: String, val pending: Boolean)

@Composable
private fun liveFigure(entry: UtilitiesEntry, summary: UtilitiesSummary): LiveFigure? {
    val nothing = LiveFigure(stringResource(Res.string.utilities_summary_nothing), pending = false)
    return when (entry) {
        UtilitiesEntry.Duplicates ->
            if (summary.duplicateGroups == 0) nothing
            else LiveFigure(
                pluralStringResource(
                    Res.plurals.utilities_summary_duplicates,
                    summary.duplicateGroups,
                    summary.duplicateGroups,
                    humanBytes(summary.duplicateRecoverableBytes)
                ),
                pending = true
            )
        UtilitiesEntry.LargeFiles ->
            if (summary.largeFilesCount == 0) nothing
            else LiveFigure(
                pluralStringResource(
                    Res.plurals.utilities_summary_large_files,
                    summary.largeFilesCount,
                    summary.largeFilesCount,
                    humanBytes(summary.largeFilesBytes)
                ),
                pending = true
            )
        UtilitiesEntry.UnsupportedFiles ->
            if (summary.unsupportedCount == 0) nothing
            else LiveFigure(
                pluralStringResource(
                    Res.plurals.utilities_summary_unsupported,
                    summary.unsupportedCount,
                    summary.unsupportedCount
                ),
                pending = true
            )
        UtilitiesEntry.Locations -> null
    }
}

/**
 * Mismo estilo que la línea secundaria de [SettingsItem]: sin color de aviso
 * (sería demasiado ruido para algo opcional), solo un peso algo mayor cuando
 * hay algo pendiente.
 */
@Composable
private fun LiveFigureText(figure: LiveFigure) {
    Text(
        text = figure.text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (figure.pending) FontWeight.Medium else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
