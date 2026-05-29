package com.photonne.app.ui.folder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.folder_external_badge
import com.photonne.app.resources.folder_shared_badge
import com.photonne.app.resources.folders_action_search
import com.photonne.app.resources.folders_empty_subtitle
import com.photonne.app.resources.folders_empty_title
import com.photonne.app.resources.folders_libraries_empty
import com.photonne.app.resources.folders_search_empty_subtitle
import com.photonne.app.resources.folders_search_empty_title
import com.photonne.app.resources.folders_search_placeholder
import com.photonne.app.resources.folders_shared_empty
import com.photonne.app.resources.folders_tab_libraries
import com.photonne.app.resources.folders_tab_personal
import com.photonne.app.resources.folders_tab_shared
import com.photonne.app.ui.theme.EmptyState as SharedEmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoldersListScreen(
    onFolderClick: (FolderSummary) -> Unit,
    onFolderLongPress: (FolderSummary) -> Unit
) {
    val viewModel: FoldersViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        FoldersTabBar(
            selected = state.selectedTab,
            onSelect = viewModel::selectTab
        )
        if (state.isSearchActive) {
            FoldersSearchField(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                onClose = viewModel::toggleSearch
            )
        }
        PhotonneRefreshableScreen(
            isRefreshing = state.isLoading && state.personalFolders.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (state.selectedTab) {
                    FoldersTab.Personal -> FolderListContent(
                        folders = state.visiblePersonalFolders,
                        state = state,
                        isLoading = state.isLoading,
                        errorMessage = state.error?.userMessage,
                        emptyTitle = stringResource(Res.string.folders_empty_title),
                        emptySubtitle = stringResource(Res.string.folders_empty_subtitle),
                        onFolderClick = onFolderClick,
                        onFolderLongPress = onFolderLongPress
                    )
                    FoldersTab.Shared -> FolderListContent(
                        folders = state.visibleSharedFolders,
                        state = state,
                        isLoading = state.isLoading,
                        errorMessage = state.error?.userMessage,
                        emptyTitle = stringResource(Res.string.folders_empty_title),
                        emptySubtitle = stringResource(Res.string.folders_shared_empty),
                        onFolderClick = onFolderClick,
                        onFolderLongPress = onFolderLongPress
                    )
                    FoldersTab.Libraries -> if (state.hasActiveQuery) {
                        FolderListContent(
                            folders = state.visibleLibraryFolders,
                            state = state,
                            isLoading = state.isLoading,
                            errorMessage = state.error?.userMessage,
                            emptyTitle = stringResource(Res.string.folders_empty_title),
                            emptySubtitle = stringResource(Res.string.folders_libraries_empty),
                            onFolderClick = onFolderClick,
                            onFolderLongPress = onFolderLongPress
                        )
                    } else {
                        LibrariesContent(
                            libraries = state.visibleLibraries,
                            isLoading = state.isLoading,
                            errorMessage = state.error?.userMessage,
                            onLibraryClick = { lib ->
                                viewModel.resolveLibraryRoot(lib.id)?.let(onFolderClick)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(Res.string.folders_search_placeholder)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = {
                if (query.isEmpty()) onClose() else onQueryChange("")
            }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.folders_action_search)
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .focusRequester(focusRequester)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoldersTabBar(selected: FoldersTab, onSelect: (FoldersTab) -> Unit) {
    val tabs = FoldersTab.values()
    PrimaryTabRow(selectedTabIndex = tabs.indexOf(selected)) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        when (tab) {
                            FoldersTab.Personal -> stringResource(Res.string.folders_tab_personal)
                            FoldersTab.Shared -> stringResource(Res.string.folders_tab_shared)
                            FoldersTab.Libraries -> stringResource(Res.string.folders_tab_libraries)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun FolderListContent(
    folders: List<FolderSummary>,
    state: FoldersUiState,
    isLoading: Boolean,
    errorMessage: String?,
    emptyTitle: String,
    emptySubtitle: String,
    onFolderClick: (FolderSummary) -> Unit,
    onFolderLongPress: (FolderSummary) -> Unit
) {
    when {
        isLoading && folders.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        folders.isEmpty() && state.hasActiveQuery ->
            EmptySearchState(query = state.searchQuery.trim())
        folders.isEmpty() && errorMessage == null ->
            EmptyState(title = emptyTitle, subtitle = emptySubtitle)
        errorMessage != null && folders.isEmpty() ->
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        else -> when (state.viewMode) {
            FolderViewMode.List -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(folders, key = { it.id }) { folder ->
                    FolderRow(
                        folder = folder,
                        isSelected = state.selectedFolderId == folder.id,
                        onClick = { onFolderClick(folder) },
                        onLongPress = { onFolderLongPress(folder) }
                    )
                }
            }
            FolderViewMode.Grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(folders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        isSelected = state.selectedFolderId == folder.id,
                        onClick = { onFolderClick(folder) },
                        onLongPress = { onFolderLongPress(folder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrariesContent(
    libraries: List<ExternalLibraryDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onLibraryClick: (ExternalLibraryDto) -> Unit
) {
    when {
        isLoading && libraries.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        libraries.isEmpty() && errorMessage == null ->
            SharedEmptyState(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringResource(Res.string.folders_tab_libraries),
                subtitle = stringResource(Res.string.folders_libraries_empty)
            )
        errorMessage != null && libraries.isEmpty() ->
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        else -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(libraries, key = { it.id }) { lib ->
                LibraryRow(library = lib, onClick = { onLibraryClick(lib) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isSelected) Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle
                else Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name.ifBlank { "/" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = folder.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.albums_count_format, folder.assetCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (folder.isShared) {
                    Badge(stringResource(Res.string.folder_shared_badge), Icons.Filled.Person)
                }
                if (folder.externalLibraryId != null) {
                    Badge(stringResource(Res.string.folder_external_badge), Icons.AutoMirrored.Filled.LibraryBooks)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: FolderSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isSelected) Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${folder.assetCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (folder.isShared) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(50))
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = stringResource(Res.string.folder_shared_badge),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.White, shape = RoundedCornerShape(50))
                        .padding(2.dp)
                        .size(20.dp)
                )
            }
        }
        Text(
            text = folder.name.ifBlank { folder.path },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun LibraryRow(library: ExternalLibraryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = library.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = stringResource(Res.string.albums_count_format, library.assetCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun Badge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    SharedEmptyState(
        icon = Icons.Outlined.Folder,
        title = title,
        subtitle = subtitle
    )
}

@Composable
private fun EmptySearchState(query: String) {
    SharedEmptyState(
        icon = Icons.Filled.Search,
        title = stringResource(Res.string.folders_search_empty_title),
        subtitle = stringResource(Res.string.folders_search_empty_subtitle, query)
    )
}
