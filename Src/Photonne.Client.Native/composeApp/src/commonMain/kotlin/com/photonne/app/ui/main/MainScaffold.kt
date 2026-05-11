package com.photonne.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_account
import androidx.compose.material3.TextButton
import com.photonne.app.resources.action_share
import com.photonne.app.resources.archive_action_unarchive
import com.photonne.app.resources.archive_action_unarchive_all
import com.photonne.app.resources.selection_action_deselect_all
import com.photonne.app.resources.selection_action_download
import com.photonne.app.resources.selection_action_select_all
import com.photonne.app.resources.folder_action_actions
import com.photonne.app.resources.folder_action_move
import com.photonne.app.resources.folder_action_new
import com.photonne.app.resources.folder_selection_move
import com.photonne.app.resources.people_action_hide
import com.photonne.app.resources.people_action_hide_hidden
import com.photonne.app.resources.people_action_merge
import com.photonne.app.resources.people_action_recluster
import com.photonne.app.resources.people_action_rename
import com.photonne.app.resources.people_action_show_hidden
import com.photonne.app.resources.people_action_suggestions
import com.photonne.app.resources.people_action_suggestions_accept_all
import com.photonne.app.resources.people_action_suggestions_dismiss_all
import com.photonne.app.resources.people_action_unhide
import com.photonne.app.resources.people_action_unlink
import com.photonne.app.resources.people_suggestions_title
import com.photonne.app.resources.trash_action_delete_forever
import com.photonne.app.resources.trash_action_empty
import com.photonne.app.resources.trash_action_restore
import com.photonne.app.resources.trash_action_restore_all
import com.photonne.app.resources.folders_title
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_edit
import com.photonne.app.resources.action_jump_to_date
import com.photonne.app.resources.action_leave
import com.photonne.app.resources.action_logout
import com.photonne.app.resources.action_more
import com.photonne.app.resources.action_refresh
import com.photonne.app.resources.action_share
import com.photonne.app.resources.album_action_album_actions
import com.photonne.app.resources.album_action_members
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.app_name
import com.photonne.app.resources.selection_action_add_to_album
import com.photonne.app.resources.selection_action_archive
import com.photonne.app.resources.selection_action_close
import com.photonne.app.resources.selection_action_more
import com.photonne.app.resources.selection_action_remove_from_album
import com.photonne.app.resources.selection_action_trash
import com.photonne.app.resources.selection_count
import androidx.compose.material.icons.filled.List
import com.photonne.app.resources.tab_albums
import com.photonne.app.resources.tab_folders
import com.photonne.app.resources.tab_search
import com.photonne.app.resources.tab_more
import com.photonne.app.resources.tab_timeline
import com.photonne.app.resources.upload_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class MainTab {
    Timeline,
    Search,
    Albums,
    Folders,
    More
}

@Composable
fun MainScaffold(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    topBar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = topBar,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Timeline,
                    onClick = { onTabSelected(MainTab.Timeline) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(Res.string.tab_timeline)) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Search,
                    onClick = { onTabSelected(MainTab.Search) },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text(stringResource(Res.string.tab_search)) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Albums,
                    onClick = { onTabSelected(MainTab.Albums) },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    label = { Text(stringResource(Res.string.tab_albums)) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Folders,
                    onClick = { onTabSelected(MainTab.Folders) },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(Res.string.tab_folders)) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.More,
                    onClick = { onTabSelected(MainTab.More) },
                    icon = { Icon(Icons.Filled.MoreVert, contentDescription = null) },
                    label = { Text(stringResource(Res.string.tab_more)) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}

/** Account menu shared by every top bar so logout is always one tap away. */
@Composable
fun AccountMenu(user: UserDto, onLogout: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = stringResource(Res.string.action_account)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = user.firstName?.takeIf { it.isNotBlank() } ?: user.username,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                onClick = { open = false },
                enabled = false
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_logout)) },
                onClick = {
                    open = false
                    onLogout()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineTopBar(
    user: UserDto,
    onRefresh: () -> Unit,
    onJumpToDate: () -> Unit,
    onUpload: () -> Unit,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            IconButton(onClick = onUpload) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.upload_title)
                )
            }
            IconButton(onClick = onJumpToDate) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = stringResource(Res.string.action_jump_to_date)
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onTrash, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(Res.string.selection_action_trash),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = !isMutating) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.selection_action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.selection_action_archive)) },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onArchive()
                        }
                    )
                }
            }
        }
    )
}

/** Whether the unified selection top bar shows "Archive" or "Unarchive" on the archive icon. */
enum class ArchiveMode { Archive, Unarchive }

/**
 * Unified selection top bar used across every screen that supports
 * multi-asset selection. Standard actions are always rendered;
 * `onMove` and `onUnlink` are optional and only show when wired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    isMutating: Boolean,
    archiveMode: ArchiveMode = ArchiveMode.Archive,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDownload: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onMove: (() -> Unit)? = null,
    onUnlink: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(
                    Res.plurals.selection_count,
                    selectedCount,
                    selectedCount
                ),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            IconButton(onClick = onSelectAll, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(
                        if (allSelected) Res.string.selection_action_deselect_all
                        else Res.string.selection_action_select_all
                    ),
                    tint = if (allSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onShare, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(Res.string.action_share)
                )
            }
            IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onDownload, enabled = !isMutating) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.selection_action_download)
                )
            }
            // Overflow holds the destructive / context-specific bits so the
            // primary bar stays at a fixed visual length.
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = !isMutating) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.selection_action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onMove != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.folder_selection_move)) },
                            onClick = { menuOpen = false; onMove() }
                        )
                    }
                    if (onUnlink != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.people_action_unlink)) },
                            onClick = { menuOpen = false; onUnlink() }
                        )
                    }
                    if (onRemoveFromAlbum != null) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(Res.string.selection_action_remove_from_album))
                            },
                            onClick = { menuOpen = false; onRemoveFromAlbum() }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (archiveMode == ArchiveMode.Unarchive)
                                        Res.string.archive_action_unarchive
                                    else Res.string.selection_action_archive
                                )
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        onClick = { menuOpen = false; onArchive() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(Res.string.selection_action_trash),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { menuOpen = false; onTrash() }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsListTopBar(user: UserDto, onCreateAlbum: () -> Unit, onLogout: () -> Unit) {
    TopAppBar(
        title = { Text("Álbumes", style = MaterialTheme.typography.titleMedium) },
        actions = {
            IconButton(onClick = onCreateAlbum) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.album_action_new)
                )
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailTopBar(
    title: String,
    subtitle: String?,
    canEdit: Boolean,
    canDelete: Boolean,
    canShare: Boolean,
    canManageMembers: Boolean,
    canLeave: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onManageMembers: () -> Unit,
    onLeave: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val hasMenu = canEdit || canDelete || canShare || canManageMembers || canLeave
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_close))
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            if (canShare) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Compartir álbum")
                }
            }
            if (hasMenu) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.album_action_album_actions)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_edit)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                }
                            )
                        }
                        if (canShare) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_share)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onShare()
                                }
                            )
                        }
                        if (canManageMembers) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.album_action_members)) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onManageMembers()
                                }
                            )
                        }
                        if (canDelete) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(Res.string.action_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                }
                            )
                        }
                        if (canLeave) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_leave)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.ExitToApp, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onLeave()
                                }
                            )
                        }
                    }
                }
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersListTopBar(
    user: UserDto,
    onCreateFolder: () -> Unit,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.folders_title), style = MaterialTheme.typography.titleMedium) },
        actions = {
            IconButton(onClick = onCreateFolder) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.folder_action_new)
                )
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailTopBar(
    title: String,
    subtitle: String?,
    canEdit: Boolean,
    canDelete: Boolean,
    canManageMembers: Boolean,
    canMove: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val hasMenu = canEdit || canDelete || canManageMembers || canMove
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            if (hasMenu) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.folder_action_actions)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_edit)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; onEdit() }
                            )
                        }
                        if (canMove) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.folder_action_move)) },
                                onClick = { menuOpen = false; onMove() }
                            )
                        }
                        if (canManageMembers) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.album_action_members)) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                onClick = { menuOpen = false; onManageMembers() }
                            )
                        }
                        if (canDelete) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(Res.string.action_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onMoveToFolder: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            TextButton(onClick = onMoveToFolder, enabled = !isMutating) {
                Text(stringResource(Res.string.folder_selection_move))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(user: UserDto, onLogout: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                stringResource(Res.string.tab_search),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = { AccountMenu(user = user, onLogout = onLogout) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreTopBar(user: UserDto, onLogout: () -> Unit) {
    TopAppBar(
        title = { Text("Más", style = MaterialTheme.typography.titleMedium) },
        actions = { AccountMenu(user = user, onLogout = onLogout) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = { AccountMenu(user = user, onLogout = onLogout) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedTopBar(
    title: String,
    subtitle: String?,
    canUnarchiveAll: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUnarchiveAll: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_close))
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
            if (canUnarchiveAll) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.action_more)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.archive_action_unarchive_all)) },
                            onClick = { menuOpen = false; onUnarchiveAll() }
                        )
                    }
                }
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashTopBar(
    title: String,
    subtitle: String?,
    canActOnAll: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_close))
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
            if (canActOnAll) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.action_more)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.trash_action_restore_all)) },
                            onClick = { menuOpen = false; onRestoreAll() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(Res.string.trash_action_empty),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menuOpen = false; onEmptyTrash() }
                        )
                    }
                }
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_close))
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRecluster: () -> Unit,
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.people_action_recluster)) },
                        onClick = { menuOpen = false; onRecluster() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (showHidden) stringResource(Res.string.people_action_hide_hidden)
                                else stringResource(Res.string.people_action_show_hidden)
                            )
                        },
                        onClick = { menuOpen = false; onToggleHidden() }
                    )
                }
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailTopBar(
    title: String,
    subtitle: String?,
    isHidden: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onSuggestions: () -> Unit,
    onMerge: () -> Unit,
    onToggleHidden: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.people_action_rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.people_action_suggestions)) },
                        onClick = { menuOpen = false; onSuggestions() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.people_action_merge)) },
                        onClick = { menuOpen = false; onMerge() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isHidden) stringResource(Res.string.people_action_unhide)
                                else stringResource(Res.string.people_action_hide)
                            )
                        },
                        onClick = { menuOpen = false; onToggleHidden() }
                    )
                }
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSuggestionsTopBar(
    title: String,
    subtitle: String?,
    isBulkMutating: Boolean,
    onBack: () -> Unit,
    onAcceptAll: () -> Unit,
    onDismissAll: () -> Unit,
    user: UserDto,
    onLogout: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(
                    stringResource(Res.string.people_suggestions_title, title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = !isBulkMutating) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(Res.string.people_action_suggestions_accept_all))
                        },
                        onClick = { menuOpen = false; onAcceptAll() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(Res.string.people_action_suggestions_dismiss_all))
                        },
                        onClick = { menuOpen = false; onDismissAll() }
                    )
                }
            }
            AccountMenu(user = user, onLogout = onLogout)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onUnlink: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            TextButton(onClick = onUnlink, enabled = !isMutating) {
                Text(stringResource(Res.string.people_action_unlink))
            }
            IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onArchive, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(Res.string.selection_action_archive)
                )
            }
            IconButton(onClick = onTrash, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(Res.string.selection_action_trash),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/**
 * Selection top bar for the Favorites screen — the same bulk vocabulary
 * as the Timeline selection bar (Add to album, Archive, Trash), since
 * "Unfavorite" can be done from the asset viewer per item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onArchive, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(Res.string.selection_action_archive)
                )
            }
            IconButton(onClick = onTrash, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(Res.string.selection_action_trash),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/** Selection top bar tailored to the Archived screen — only exposes Unarchive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onUnarchive: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            TextButton(onClick = onUnarchive, enabled = !isMutating) {
                Text(stringResource(Res.string.archive_action_unarchive))
            }
        }
    )
}

/** Selection top bar tailored to the Trash screen — Restore + Delete forever. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            TextButton(onClick = onRestore, enabled = !isMutating) {
                Text(stringResource(Res.string.trash_action_restore))
            }
            IconButton(onClick = onPurge, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(Res.string.trash_action_delete_forever),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}
