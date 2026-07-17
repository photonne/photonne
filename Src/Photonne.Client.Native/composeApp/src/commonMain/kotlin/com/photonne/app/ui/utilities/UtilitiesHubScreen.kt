package com.photonne.app.ui.utilities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.utilities_section_duplicates
import com.photonne.app.resources.utilities_section_duplicates_subtitle
import com.photonne.app.resources.utilities_section_large_files
import com.photonne.app.resources.utilities_section_large_files_subtitle
import com.photonne.app.resources.utilities_section_locations
import com.photonne.app.resources.utilities_section_locations_subtitle
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class UtilitiesEntry { Duplicates, LargeFiles, Locations }

private data class UtilitiesEntryDef(
    val entry: UtilitiesEntry,
    val title: StringResource,
    val subtitle: StringResource,
    val icon: ImageVector
)

@Composable
fun UtilitiesHubScreen(onOpen: (UtilitiesEntry) -> Unit) {
    val entries = listOf(
        UtilitiesEntryDef(
            UtilitiesEntry.Duplicates,
            Res.string.utilities_section_duplicates,
            Res.string.utilities_section_duplicates_subtitle,
            Icons.Outlined.ContentCopy
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
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + floatingNavBarReservedHeight()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.forEach { entry ->
            UtilitiesEntryRow(entry = entry, onClick = { onOpen(entry.entry) })
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UtilitiesEntryRow(entry: UtilitiesEntryDef, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(entry.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(entry.subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(contentAlignment = Alignment.CenterEnd) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
