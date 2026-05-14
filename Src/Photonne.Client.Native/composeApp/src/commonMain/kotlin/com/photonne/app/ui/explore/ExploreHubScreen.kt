package com.photonne.app.ui.explore

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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Public
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
import com.photonne.app.resources.explore_section_memories
import com.photonne.app.resources.explore_section_memories_subtitle
import com.photonne.app.resources.explore_section_objects
import com.photonne.app.resources.explore_section_objects_subtitle
import com.photonne.app.resources.explore_section_places
import com.photonne.app.resources.explore_section_places_subtitle
import com.photonne.app.resources.explore_section_scenes
import com.photonne.app.resources.explore_section_scenes_subtitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class ExploreEntry { Memories, Places, Scenes, Objects }

private data class ExploreEntryDef(
    val entry: ExploreEntry,
    val title: StringResource,
    val subtitle: StringResource,
    val icon: ImageVector
)

@Composable
fun ExploreHubScreen(onOpen: (ExploreEntry) -> Unit) {
    val entries = listOf(
        ExploreEntryDef(
            ExploreEntry.Memories,
            Res.string.explore_section_memories,
            Res.string.explore_section_memories_subtitle,
            Icons.Outlined.HistoryEdu
        ),
        ExploreEntryDef(
            ExploreEntry.Places,
            Res.string.explore_section_places,
            Res.string.explore_section_places_subtitle,
            Icons.Outlined.Public
        ),
        ExploreEntryDef(
            ExploreEntry.Scenes,
            Res.string.explore_section_scenes,
            Res.string.explore_section_scenes_subtitle,
            Icons.Outlined.Landscape
        ),
        ExploreEntryDef(
            ExploreEntry.Objects,
            Res.string.explore_section_objects,
            Res.string.explore_section_objects_subtitle,
            Icons.Outlined.Category
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.forEach { entry ->
            ExploreEntryRow(entry = entry, onClick = { onOpen(entry.entry) })
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ExploreEntryRow(entry: ExploreEntryDef, onClick: () -> Unit) {
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
