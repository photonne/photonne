package com.photonne.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_logout
import com.photonne.app.resources.archive_title
import com.photonne.app.resources.map_title
import com.photonne.app.resources.more_settings_hint
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.upload_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun MoreScreen(
    user: UserDto,
    onLogout: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenTrash: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
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
        HorizontalDivider()

        MoreEntry(
            icon = Icons.Filled.Add,
            label = stringResource(Res.string.upload_title),
            onClick = onOpenUpload
        )
        HorizontalDivider()
        MoreEntry(
            icon = Icons.Filled.LocationOn,
            label = stringResource(Res.string.map_title),
            onClick = onOpenMap
        )
        HorizontalDivider()
        MoreEntry(
            icon = Icons.Filled.Lock,
            label = stringResource(Res.string.archive_title),
            onClick = onOpenArchived
        )
        HorizontalDivider()
        MoreEntry(
            icon = Icons.Filled.Delete,
            label = stringResource(Res.string.trash_title),
            onClick = onOpenTrash
        )
        HorizontalDivider()

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
private fun MoreEntry(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
