package com.photonne.app.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.photonne.app.resources.Res
import androidx.compose.material3.TextButton
import com.photonne.app.resources.action_collaborators
import com.photonne.app.resources.action_rename
import com.photonne.app.resources.action_share
import com.photonne.app.resources.album_card_action_leave
import com.photonne.app.resources.albums_action_filters
import com.photonne.app.resources.albums_action_search
import com.photonne.app.resources.folders_action_filters
import com.photonne.app.resources.folders_action_search
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
import com.photonne.app.resources.action_more
import com.photonne.app.resources.action_refresh
import com.photonne.app.resources.album_action_members
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.app_name
import com.photonne.app.resources.asset_action_set_cover
import com.photonne.app.resources.selection_action_add_to_album
import com.photonne.app.resources.selection_action_archive
import com.photonne.app.resources.selection_action_close
import com.photonne.app.resources.selection_action_more
import com.photonne.app.resources.selection_action_remove_from_album
import com.photonne.app.resources.selection_action_trash
import com.photonne.app.resources.selection_count
import com.photonne.app.resources.selection_label_add_to_album
import com.photonne.app.resources.selection_label_deselect_all
import com.photonne.app.resources.selection_label_download
import com.photonne.app.resources.selection_label_leave
import com.photonne.app.resources.selection_label_members
import com.photonne.app.resources.selection_label_more
import com.photonne.app.resources.selection_label_move
import com.photonne.app.resources.selection_label_remove
import com.photonne.app.resources.selection_label_select_all
import com.photonne.app.resources.selection_label_set_cover
import com.photonne.app.resources.selection_label_share
import com.photonne.app.resources.selection_label_trash
import com.photonne.app.resources.tab_albums
import com.photonne.app.resources.tab_folders
import com.photonne.app.resources.tab_search
import com.photonne.app.resources.tab_more
import com.photonne.app.resources.tab_timeline
import com.photonne.app.resources.upload_title
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.theme.photonneLogoPainter
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
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar ?: { MainNavigationBar(selectedTab, onTabSelected) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}

@Composable
private fun MainNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationBar {
        val timelineActive = selectedTab == MainTab.Timeline
        NavigationBarItem(
            selected = timelineActive,
            onClick = { onTabSelected(MainTab.Timeline) },
            icon = {
                Icon(
                    if (timelineActive) Icons.Filled.PhotoLibrary
                    else Icons.Outlined.PhotoLibrary,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(Res.string.tab_timeline)) }
        )
        val searchActive = selectedTab == MainTab.Search
        NavigationBarItem(
            selected = searchActive,
            onClick = { onTabSelected(MainTab.Search) },
            icon = {
                Icon(
                    if (searchActive) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(Res.string.tab_search)) }
        )
        val albumsActive = selectedTab == MainTab.Albums
        NavigationBarItem(
            selected = albumsActive,
            onClick = { onTabSelected(MainTab.Albums) },
            icon = {
                Icon(
                    if (albumsActive) Icons.Filled.Collections
                    else Icons.Outlined.Collections,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(Res.string.tab_albums)) }
        )
        val foldersActive = selectedTab == MainTab.Folders
        NavigationBarItem(
            selected = foldersActive,
            onClick = { onTabSelected(MainTab.Folders) },
            icon = {
                Icon(
                    if (foldersActive) Icons.Filled.Folder else Icons.Outlined.Folder,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(Res.string.tab_folders)) }
        )
        val moreActive = selectedTab == MainTab.More
        NavigationBarItem(
            selected = moreActive,
            onClick = { onTabSelected(MainTab.More) },
            icon = {
                Icon(
                    if (moreActive) Icons.Filled.GridView else Icons.Outlined.GridView,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(Res.string.tab_more)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineTopBar(
    onRefresh: () -> Unit,
    onJumpToDate: () -> Unit,
    onUpload: () -> Unit
) {
    TopAppBar(
        title = {
            Image(
                painter = photonneLogoPainter(),
                contentDescription = stringResource(Res.string.app_name),
                modifier = Modifier.height(32.dp)
            )
        },
        actions = {
            IconButton(onClick = onUpload) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = stringResource(Res.string.upload_title)
                )
            }
            IconButton(onClick = onJumpToDate) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = stringResource(Res.string.action_jump_to_date)
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
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
                    Icons.Outlined.AddToPhotos,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onTrash, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Delete,
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
                        leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
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
 * Slim selection top bar: only the close (X) navigation icon and the
 * selection counter. All action buttons live in [AssetSelectionBottomBar]
 * so they stay within easy thumb reach on mobile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit
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
                text = pluralStringResource(
                    Res.plurals.selection_count,
                    selectedCount,
                    selectedCount
                ),
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}

/**
 * Unified selection bottom bar used across every screen that supports
 * multi-asset selection. Primary slots: Select all, Add to album, Download, Trash.
 * Optional context actions live in the overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSelectionBottomBar(
    selectedCount: Int,
    totalCount: Int,
    isMutating: Boolean,
    archiveMode: ArchiveMode = ArchiveMode.Archive,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDownload: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onMove: (() -> Unit)? = null,
    onUnlink: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null,
    onSetAsCover: (() -> Unit)? = null
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    // When any context-specific action is wired (Move/Remove/SetCover/Unlink),
    // Download moves to the overflow so the bar keeps a stable 4-item primary
    // count + More. The context action takes Download's slot.
    val hasContextAction = onMove != null || onRemoveFromAlbum != null ||
        onSetAsCover != null || onUnlink != null
    NavigationBar {
        NavigationBarItem(
            selected = allSelected,
            onClick = onSelectAll,
            enabled = !isMutating,
            icon = {
                Icon(
                    Icons.Outlined.SelectAll,
                    contentDescription = stringResource(
                        if (allSelected) Res.string.selection_action_deselect_all
                        else Res.string.selection_action_select_all
                    )
                )
            },
            label = {
                SelectionLabel(
                    stringResource(
                        if (allSelected) Res.string.selection_label_deselect_all
                        else Res.string.selection_label_select_all
                    )
                )
            }
        )
        NavigationBarItem(
            selected = false,
            onClick = onShare,
            enabled = !isMutating,
            icon = {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = stringResource(Res.string.action_share)
                )
            },
            label = { SelectionLabel(stringResource(Res.string.selection_label_share)) }
        )
        NavigationBarItem(
            selected = false,
            onClick = onAddToAlbum,
            enabled = !isMutating,
            icon = {
                Icon(
                    Icons.Outlined.AddToPhotos,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            },
            label = { SelectionLabel(stringResource(Res.string.selection_label_add_to_album)) }
        )
        if (!hasContextAction) {
            NavigationBarItem(
                selected = false,
                onClick = onDownload,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = stringResource(Res.string.selection_action_download)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.selection_label_download)) }
            )
        }
        if (onMove != null) {
            NavigationBarItem(
                selected = false,
                onClick = onMove,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.DriveFileMove,
                        contentDescription = stringResource(Res.string.folder_selection_move)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.selection_label_move)) }
            )
        }
        if (onRemoveFromAlbum != null) {
            NavigationBarItem(
                selected = false,
                onClick = onRemoveFromAlbum,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.RemoveCircleOutline,
                        contentDescription = stringResource(Res.string.selection_action_remove_from_album)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.selection_label_remove)) }
            )
        }
        if (onSetAsCover != null) {
            NavigationBarItem(
                selected = false,
                onClick = onSetAsCover,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.PhotoAlbum,
                        contentDescription = stringResource(Res.string.asset_action_set_cover)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.selection_label_set_cover)) }
            )
        }
        if (onUnlink != null) {
            NavigationBarItem(
                selected = false,
                onClick = onUnlink,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.LinkOff,
                        contentDescription = stringResource(Res.string.people_action_unlink)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.people_action_unlink)) }
            )
        }
        NavigationBarItem(
            selected = false,
            onClick = { menuOpen = true },
            enabled = !isMutating,
            icon = {
                Box {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.selection_action_more)
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (hasContextAction) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.selection_action_download)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Download, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onDownload() }
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
                            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
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
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menuOpen = false; onTrash() }
                        )
                    }
                }
            },
            label = { SelectionLabel(stringResource(Res.string.selection_label_more)) }
        )
    }
}

/**
 * Label used by every selection bottom-bar item. Uses [MaterialTheme.typography.labelSmall]
 * so longer Spanish labels (e.g. "Deseleccionar") fit on a single line, and clamps to one
 * line with ellipsis as a safety net.
 */
@Composable
private fun SelectionLabel(text: String, color: Color = Color.Unspecified) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsListTopBar(
    onCreateAlbum: () -> Unit,
    onOpenFilters: () -> Unit,
    isSearchActive: Boolean = false,
    onToggleSearch: () -> Unit = {}
) {
    TopAppBar(
        title = { Text("Álbumes", style = MaterialTheme.typography.titleMedium) },
        actions = {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = stringResource(Res.string.albums_action_search)
                )
            }
            IconButton(onClick = onOpenFilters) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = stringResource(Res.string.albums_action_filters)
                )
            }
            IconButton(onClick = onCreateAlbum) {
                Icon(
                    Icons.Outlined.AddBox,
                    contentDescription = stringResource(Res.string.album_action_new)
                )
            }
        }
    )
}

/**
 * Slim top bar shown when a single album card is selected from the list:
 * just Close (X) and the album name. Actions live in [AlbumCardSelectionBottomBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCardSelectionTopBar(
    albumName: String,
    isMutating: Boolean,
    onClose: () -> Unit
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
            Text(albumName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    )
}

/**
 * Bottom bar for the single-album-card selection. Mirrors the PWA's Albums
 * selection toolbar (Members, Edit, Leave, Delete) gated by permissions.
 * Delete carries the error tint since it's destructive.
 */
@Composable
fun AlbumCardSelectionBottomBar(
    canManageMembers: Boolean,
    canEdit: Boolean,
    canLeave: Boolean,
    canDelete: Boolean,
    isMutating: Boolean,
    onManageMembers: () -> Unit,
    onEdit: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit
) {
    NavigationBar {
        if (canManageMembers) {
            NavigationBarItem(
                selected = false,
                onClick = onManageMembers,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.Group,
                        contentDescription = stringResource(Res.string.action_collaborators)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.selection_label_members)) }
            )
        }
        if (canEdit) {
            NavigationBarItem(
                selected = false,
                onClick = onEdit,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(Res.string.action_edit)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.action_edit)) }
            )
        }
        if (canLeave) {
            NavigationBarItem(
                selected = false,
                onClick = onLeave,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(Res.string.album_card_action_leave)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.selection_label_leave)) }
            )
        }
        if (canDelete) {
            NavigationBarItem(
                selected = false,
                onClick = onDelete,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(Res.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                label = {
                    SelectionLabel(
                        stringResource(Res.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersListTopBar(
    onCreateFolder: () -> Unit,
    onOpenFilters: () -> Unit,
    isSearchActive: Boolean = false,
    onToggleSearch: () -> Unit = {}
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.folders_title), style = MaterialTheme.typography.titleMedium) },
        actions = {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = stringResource(Res.string.folders_action_search)
                )
            }
            IconButton(onClick = onOpenFilters) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = stringResource(Res.string.folders_action_filters)
                )
            }
            IconButton(onClick = onCreateFolder) {
                Icon(
                    Icons.Outlined.CreateNewFolder,
                    contentDescription = stringResource(Res.string.folder_action_new)
                )
            }
        }
    )
}

/**
 * Slim top bar for folder card selection: Close (X) + folder name only.
 * Actions live in [FolderCardSelectionBottomBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderCardSelectionTopBar(
    folderName: String,
    isMutating: Boolean,
    onClose: () -> Unit
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
            Text(folderName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    )
}

/**
 * Bottom bar for the single-folder-card selection. Mirrors the PWA's Folders
 * selection toolbar (Members, Rename, Delete) gated by permissions.
 */
@Composable
fun FolderCardSelectionBottomBar(
    canManageMembers: Boolean,
    canRename: Boolean,
    canDelete: Boolean,
    isMutating: Boolean,
    onManageMembers: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    NavigationBar {
        if (canManageMembers) {
            NavigationBarItem(
                selected = false,
                onClick = onManageMembers,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.Group,
                        contentDescription = stringResource(Res.string.action_collaborators)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.selection_label_members)) }
            )
        }
        if (canRename) {
            NavigationBarItem(
                selected = false,
                onClick = onRename,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.DriveFileRenameOutline,
                        contentDescription = stringResource(Res.string.action_rename)
                    )
                },
                label = { SelectionLabel(stringResource(Res.string.action_rename)) }
            )
        }
        if (canDelete) {
            NavigationBarItem(
                selected = false,
                onClick = onDelete,
                enabled = !isMutating,
                icon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(Res.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                label = {
                    SelectionLabel(
                        stringResource(Res.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
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
    onManageMembers: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val hasMenu = canEdit || canDelete || canManageMembers || canMove
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
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
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; onEdit() }
                            )
                        }
                        if (canMove) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.folder_action_move)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onMove() }
                            )
                        }
                        if (canManageMembers) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.album_action_members)) },
                                leadingIcon = { Icon(Icons.Outlined.People, contentDescription = null) },
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
                                        Icons.Outlined.Delete,
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
fun SearchTopBar() {
    TopAppBar(
        title = {
            Text(
                stringResource(Res.string.tab_search),
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreTopBar() {
    TopAppBar(
        title = { Text("Más", style = MaterialTheme.typography.titleMedium) }
    )
}

/** Generic title + optional subtitle + back button used by every settings sub-page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
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
    onUnarchiveAll: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
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
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
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
    onEmptyTrash: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
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
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
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
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
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
    onToggleHidden: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
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
    onToggleHidden: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
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
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
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
    onDismissAll: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
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
                    Icons.Outlined.AddToPhotos,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onArchive, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Archive,
                    contentDescription = stringResource(Res.string.selection_action_archive)
                )
            }
            IconButton(onClick = onTrash, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Delete,
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
                    Icons.Outlined.AddToPhotos,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onArchive, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Archive,
                    contentDescription = stringResource(Res.string.selection_action_archive)
                )
            }
            IconButton(onClick = onTrash, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Delete,
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
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.trash_action_delete_forever),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}
