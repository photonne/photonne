package com.photonne.app.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.photonne.app.ui.theme.SectionHeader
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.SettingsItem

/** A single tappable entry in a hub list (Ajustes / Sistema). */
data class AdminHubEntry(
    val key: String,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector
)

/** A titled group of entries in a hub (Ajustes: General, Procesado, IA). */
data class AdminHubSection(
    val title: String?,
    val entries: List<AdminHubEntry>
)

/** Shared scaffold used by the Ajustes and Sistema hub screens to render
 *  a vertically scrolling list of large entry rows. Draws its own floating
 *  subscreen chrome so both hubs match the rest of the app. Kept generic so
 *  any hub can grow without adding more layout boilerplate. */
@Composable
fun AdminHubList(
    title: String,
    onBack: () -> Unit,
    entries: List<AdminHubEntry>,
    onClick: (String) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) = AdminHubList(
    title = title,
    onBack = onBack,
    sections = listOf(AdminHubSection(title = null, entries = entries)),
    onClick = onClick,
    onChromeVisibleChange = onChromeVisibleChange
)

/** The same list split into titled [sections], each its own card, as Más. */
@Composable
fun AdminHubList(
    title: String,
    onBack: () -> Unit,
    sections: List<AdminHubSection>,
    onClick: (String) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    AdminPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
        page {
            sections.forEach { section ->
                section.title?.let { SectionHeader(it) }
                // One card split by dividers, like Más and Ajustes.
                SettingsGroup {
                    section.entries.forEachIndexed { index, entry ->
                        SettingsItem(
                            headline = entry.title,
                            supporting = entry.subtitle,
                            leadingIcon = entry.icon,
                            onClick = { onClick(entry.key) },
                            showDivider = index > 0
                        )
                    }
                }
            }
        }
    }
}
