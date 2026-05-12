package com.photonne.app.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_edit
import com.photonne.app.resources.action_leave
import com.photonne.app.resources.action_share
import com.photonne.app.resources.album_action_album_actions
import com.photonne.app.resources.album_action_members
import com.photonne.app.resources.album_empty_subtitle
import com.photonne.app.resources.album_empty_title
import com.photonne.app.resources.album_hero_photos
import com.photonne.app.resources.album_hero_shared
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.formatLocalizedMonth
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AlbumDetailScreen(
    album: AlbumSummary,
    onItemClick: (Int) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    onLeave: () -> Unit,
    viewModel: AlbumDetailViewModel
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(album.id) { viewModel.open(album.id, album.name, album.description) }

    val heroTitle = state.albumName ?: album.name
    val heroDescription = state.albumDescription ?: album.description
    // While items load, use the cached count from the AlbumSummary so the
    // hero doesn't flash "0 fotos"; once loaded, the live list is the source
    // of truth (so removing everything correctly reads 0).
    val heroAssetCount = if (state.albumId == album.id && !state.isLoading) {
        state.items.size
    } else {
        album.assetCount
    }

    val hero: @Composable () -> Unit = {
        Column {
            AlbumHero(
                title = heroTitle,
                description = heroDescription,
                assetCount = heroAssetCount,
                createdAt = album.createdAt,
                isShared = album.isShared,
                coverUrl = album.coverThumbnailUrl?.let { resolveAlbumCover(it, apiBaseUrl) },
                canEdit = album.canWrite || album.isOwner,
                canDelete = album.isOwner,
                canShare = album.canWrite || album.isOwner,
                canManageMembers = album.isOwner || album.canManagePermissions,
                canLeave = !album.isOwner,
                onBack = onBack,
                onShare = onShare,
                onEdit = onEdit,
                onDelete = onDelete,
                onManageMembers = onManageMembers,
                onLeave = onLeave
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.items.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize()) {
                    hero()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            state.errorMessage != null && state.items.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize()) {
                    hero()
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            state.items.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize()) {
                    hero()
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(Res.string.album_empty_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(Res.string.album_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            else -> AssetGrid(
                items = state.items,
                baseUrl = apiBaseUrl,
                onItemClick = { index ->
                    if (state.isSelectionActive) {
                        state.items.getOrNull(index)?.let { viewModel.toggleSelection(it.id) }
                    } else {
                        onItemClick(index)
                    }
                },
                onItemLongClick = { index ->
                    state.items.getOrNull(index)?.let { viewModel.toggleSelection(it.id) }
                },
                selectedIds = state.selection,
                modifier = Modifier.fillMaxWidth(),
                header = hero
            )
        }
    }
}

/**
 * Edge-to-edge album cover hero with the back/overflow controls floating
 * on top of a darkened, blurred cover. Mirrors the PWA mobile layout
 * (cover background → gradient → title + count + date → description).
 */
@Composable
private fun AlbumHero(
    title: String,
    description: String?,
    assetCount: Int,
    createdAt: Instant,
    isShared: Boolean,
    coverUrl: String?,
    canEdit: Boolean,
    canDelete: Boolean,
    canShare: Boolean,
    canManageMembers: Boolean,
    canLeave: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    onLeave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .background(MaterialTheme.colorScheme.primary)
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(20.dp)
            )
        }

        // Darkening gradient so the white title/icons stay readable on any cover.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = Color.White
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canShare) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(Res.string.action_share),
                            tint = Color.White
                        )
                    }
                }
                if (canEdit || canDelete || canManageMembers || canLeave) {
                    AlbumHeroOverflowMenu(
                        canEdit = canEdit,
                        canDelete = canDelete,
                        canManageMembers = canManageMembers,
                        canLeave = canLeave,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onManageMembers = onManageMembers,
                        onLeave = onLeave
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (isShared) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = stringResource(Res.string.album_hero_shared),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroMetaItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    text = pluralStringResource(
                        Res.plurals.album_hero_photos,
                        assetCount,
                        assetCount
                    )
                )
                HeroMetaItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    text = formatLocalizedMonth(
                        createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    )
                )
            }

            if (!description.isNullOrBlank()) {
                Spacer(Modifier.size(12.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun HeroMetaItem(icon: @Composable () -> Unit, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun AlbumHeroOverflowMenu(
    canEdit: Boolean,
    canDelete: Boolean,
    canManageMembers: Boolean,
    canLeave: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    onLeave: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(Res.string.album_action_album_actions),
                tint = Color.White
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_edit)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() }
                )
            }
            if (canManageMembers) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.album_action_members)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.People, contentDescription = null)
                    },
                    onClick = { menuOpen = false; onManageMembers() }
                )
            }
            if (canLeave) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_leave)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    },
                    onClick = { menuOpen = false; onLeave() }
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

private fun resolveAlbumCover(coverUrl: String, baseUrl: String): String {
    if (coverUrl.startsWith("http://", ignoreCase = true) ||
        coverUrl.startsWith("https://", ignoreCase = true)
    ) {
        return coverUrl
    }
    val sep = if (coverUrl.startsWith("/")) "" else "/"
    return "$baseUrl$sep$coverUrl"
}
