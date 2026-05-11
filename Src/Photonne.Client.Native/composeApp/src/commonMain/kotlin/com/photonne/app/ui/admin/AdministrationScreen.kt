package com.photonne.app.ui.admin

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import com.photonne.app.resources.admin_section_libraries
import com.photonne.app.resources.admin_section_libraries_subtitle
import com.photonne.app.resources.admin_section_settings
import com.photonne.app.resources.admin_section_settings_subtitle
import com.photonne.app.resources.admin_section_stats
import com.photonne.app.resources.admin_section_stats_subtitle
import com.photonne.app.resources.admin_section_system
import com.photonne.app.resources.admin_section_system_subtitle
import com.photonne.app.resources.admin_section_users
import com.photonne.app.resources.admin_section_users_subtitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class AdministrationSection {
    Users,
    Libraries,
    Stats,
    Settings,
    System
}

private data class AdminEntry(
    val section: AdministrationSection,
    val title: StringResource,
    val subtitle: StringResource,
    val icon: ImageVector
)

@Composable
fun AdministrationScreen(onOpen: (AdministrationSection) -> Unit) {
    val entries = listOf(
        AdminEntry(
            AdministrationSection.Users,
            Res.string.admin_section_users,
            Res.string.admin_section_users_subtitle,
            Icons.Filled.Person
        ),
        AdminEntry(
            AdministrationSection.Libraries,
            Res.string.admin_section_libraries,
            Res.string.admin_section_libraries_subtitle,
            Icons.Filled.Folder
        ),
        AdminEntry(
            AdministrationSection.Stats,
            Res.string.admin_section_stats,
            Res.string.admin_section_stats_subtitle,
            Icons.Filled.Info
        ),
        AdminEntry(
            AdministrationSection.Settings,
            Res.string.admin_section_settings,
            Res.string.admin_section_settings_subtitle,
            Icons.Filled.Settings
        ),
        AdminEntry(
            AdministrationSection.System,
            Res.string.admin_section_system,
            Res.string.admin_section_system_subtitle,
            Icons.Filled.Build
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
            AdminEntryRow(entry = entry, onClick = { onOpen(entry.section) })
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AdminEntryRow(entry: AdminEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
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
