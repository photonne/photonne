package com.photonne.app.ui.main

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_settings_title
import com.photonne.app.resources.action_logout
import com.photonne.app.resources.archive_title
import com.photonne.app.resources.map_title
import com.photonne.app.resources.more_settings_hint
import com.photonne.app.resources.favorites_title
import com.photonne.app.resources.people_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.upload_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Library shortcut shown on the More tab. Each entry resolves to a
 * subscreen in [App] (Upload, Favorites, Archive, Trash, …).
 */
private data class MoreShortcut(
    val key: String,
    val labelRes: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun MoreScreen(
    user: UserDto,
    onLogout: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAccountSettings: () -> Unit
) {
    val shortcuts = remember(
        onOpenUpload,
        onOpenMap,
        onOpenFavorites,
        onOpenPeople,
        onOpenArchived,
        onOpenTrash
    ) {
        listOf(
            MoreShortcut("upload", Res.string.upload_title, Icons.Filled.Add, onOpenUpload),
            MoreShortcut(
                "favorites",
                Res.string.favorites_title,
                Icons.Filled.Favorite,
                onOpenFavorites
            ),
            MoreShortcut("people", Res.string.people_title, Icons.Filled.Person, onOpenPeople),
            MoreShortcut("map", Res.string.map_title, Icons.Filled.LocationOn, onOpenMap),
            MoreShortcut("archive", Res.string.archive_title, Icons.Filled.Lock, onOpenArchived),
            MoreShortcut("trash", Res.string.trash_title, Icons.Filled.Delete, onOpenTrash)
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = user.firstName?.takeIf { it.isNotBlank() } ?: user.username,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(shortcuts, key = { it.key }) { shortcut ->
                MoreShortcutCard(
                    label = stringResource(shortcut.labelRes),
                    icon = shortcut.icon,
                    onClick = shortcut.onClick
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        AccountSettingsRow(onClick = onOpenAccountSettings)

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(Res.string.more_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = onLogout) { Text(stringResource(Res.string.action_logout)) }
        }
    }
}

@Composable
private fun AccountSettingsRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(Res.string.account_settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MoreShortcutCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).size(28.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
