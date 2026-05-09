package com.photonne.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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

enum class MainTab(val label: String) {
    Timeline("Fotos"),
    Albums("Álbumes"),
    More("Más")
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
                    label = { Text(MainTab.Timeline.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Albums,
                    onClick = { onTabSelected(MainTab.Albums) },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    label = { Text(MainTab.Albums.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.More,
                    onClick = { onTabSelected(MainTab.More) },
                    icon = { Icon(Icons.Filled.MoreVert, contentDescription = null) },
                    label = { Text(MainTab.More.label) }
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
            Icon(Icons.Filled.AccountCircle, contentDescription = "Cuenta")
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
                text = { Text("Cerrar sesión") },
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
fun TimelineTopBar(user: UserDto, onRefresh: () -> Unit, onLogout: () -> Unit) {
    TopAppBar(
        title = { Text("Photonne", style = MaterialTheme.typography.titleMedium) },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refrescar")
            }
            AccountMenu(user = user, onLogout = onLogout)
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
                Icon(Icons.Filled.Add, contentDescription = "Crear álbum")
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
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
                        Icon(Icons.Filled.MoreVert, contentDescription = "Acciones del álbum")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text("Editar") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                }
                            )
                        }
                        if (canShare) {
                            DropdownMenuItem(
                                text = { Text("Compartir") },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onShare()
                                }
                            )
                        }
                        if (canManageMembers) {
                            DropdownMenuItem(
                                text = { Text("Miembros") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onManageMembers()
                                }
                            )
                        }
                        if (canDelete) {
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
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
                                text = { Text("Salir del álbum") },
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
fun MoreTopBar(user: UserDto, onLogout: () -> Unit) {
    TopAppBar(
        title = { Text("Más", style = MaterialTheme.typography.titleMedium) },
        actions = { AccountMenu(user = user, onLogout = onLogout) }
    )
}
