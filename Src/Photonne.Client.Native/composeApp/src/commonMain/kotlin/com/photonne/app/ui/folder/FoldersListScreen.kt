package com.photonne.app.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.folder_external_badge
import com.photonne.app.resources.folder_shared_badge
import com.photonne.app.resources.folders_empty_subtitle
import com.photonne.app.resources.folders_empty_title
import com.photonne.app.resources.folders_libraries_empty
import com.photonne.app.resources.folders_shared_empty
import com.photonne.app.resources.folders_tab_libraries
import com.photonne.app.resources.folders_tab_personal
import com.photonne.app.resources.folders_tab_shared
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoldersListScreen(onFolderClick: (FolderSummary) -> Unit) {
    val viewModel: FoldersViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        FoldersTabBar(
            selected = state.selectedTab,
            onSelect = viewModel::selectTab
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.selectedTab) {
                FoldersTab.Personal -> FolderListContent(
                    folders = state.personalFolders,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    emptyTitle = stringResource(Res.string.folders_empty_title),
                    emptySubtitle = stringResource(Res.string.folders_empty_subtitle),
                    onFolderClick = onFolderClick
                )
                FoldersTab.Shared -> FolderListContent(
                    folders = state.sharedFolders,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    emptyTitle = stringResource(Res.string.folders_empty_title),
                    emptySubtitle = stringResource(Res.string.folders_shared_empty),
                    onFolderClick = onFolderClick
                )
                FoldersTab.Libraries -> LibrariesContent(
                    libraries = state.libraries,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onLibraryClick = { lib ->
                        viewModel.resolveLibraryRoot(lib.id)?.let(onFolderClick)
                    }
                )
            }
        }
    }
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
    isLoading: Boolean,
    errorMessage: String?,
    emptyTitle: String,
    emptySubtitle: String,
    onFolderClick: (FolderSummary) -> Unit
) {
    when {
        isLoading && folders.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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
        else -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(folders, key = { it.id }) { folder ->
                FolderRow(folder = folder, onClick = { onFolderClick(folder) })
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
            EmptyState(
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

@Composable
private fun FolderRow(folder: FolderSummary, onClick: () -> Unit) {
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
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
