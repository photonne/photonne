package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_system_backup
import com.photonne.app.resources.admin_system_backup_subtitle
import com.photonne.app.resources.admin_system_run_tasks
import com.photonne.app.resources.admin_system_run_tasks_subtitle
import org.jetbrains.compose.resources.stringResource

enum class AdminSystemEntry {
    RunTasks,
    Backup,
}

@Composable
fun AdminSystemHubScreen(onOpen: (AdminSystemEntry) -> Unit) {
    val entries = listOf(
        AdminHubEntry(
            AdminSystemEntry.RunTasks.name,
            stringResource(Res.string.admin_system_run_tasks),
            stringResource(Res.string.admin_system_run_tasks_subtitle),
            Icons.Outlined.PlayArrow
        ),
        AdminHubEntry(
            AdminSystemEntry.Backup.name,
            stringResource(Res.string.admin_system_backup),
            stringResource(Res.string.admin_system_backup_subtitle),
            Icons.Outlined.Backup
        )
    )

    AdminHubList(
        entries = entries,
        onClick = { key -> onOpen(AdminSystemEntry.valueOf(key)) }
    )
}
