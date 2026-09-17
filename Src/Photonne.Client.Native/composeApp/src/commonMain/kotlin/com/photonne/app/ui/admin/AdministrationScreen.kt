package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
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
fun AdministrationScreen(
    title: String,
    onBack: () -> Unit,
    onOpen: (AdministrationSection) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val entries = listOf(
        AdminEntry(
            AdministrationSection.Users,
            Res.string.admin_section_users,
            Res.string.admin_section_users_subtitle,
            Icons.Outlined.People
        ),
        AdminEntry(
            AdministrationSection.Libraries,
            Res.string.admin_section_libraries,
            Res.string.admin_section_libraries_subtitle,
            Icons.Outlined.FolderSpecial
        ),
        AdminEntry(
            AdministrationSection.Stats,
            Res.string.admin_section_stats,
            Res.string.admin_section_stats_subtitle,
            Icons.Outlined.QueryStats
        ),
        AdminEntry(
            AdministrationSection.Settings,
            Res.string.admin_section_settings,
            Res.string.admin_section_settings_subtitle,
            Icons.Outlined.Settings
        ),
        AdminEntry(
            AdministrationSection.System,
            Res.string.admin_section_system,
            Res.string.admin_section_system_subtitle,
            Icons.Outlined.Storage
        )
    )

    // The same list the Ajustes and Sistema hubs draw: this screen had its own
    // copy of it, down to the row, with a 28 dp icon where theirs is 24.
    AdminHubList(
        title = title,
        onBack = onBack,
        entries = entries.map { entry ->
            AdminHubEntry(
                key = entry.section.name,
                title = stringResource(entry.title),
                subtitle = stringResource(entry.subtitle),
                icon = entry.icon
            )
        },
        onClick = { key -> onOpen(AdministrationSection.valueOf(key)) },
        onChromeVisibleChange = onChromeVisibleChange
    )
}
