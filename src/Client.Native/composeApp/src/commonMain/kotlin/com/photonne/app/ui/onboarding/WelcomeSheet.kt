package com.photonne.app.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.photonne.app.resources.Res
import com.photonne.app.resources.welcome_backup_text
import com.photonne.app.resources.welcome_backup_title
import com.photonne.app.resources.welcome_lead
import com.photonne.app.resources.welcome_memories_text
import com.photonne.app.resources.welcome_memories_title
import com.photonne.app.resources.welcome_people_text
import com.photonne.app.resources.welcome_people_title
import com.photonne.app.resources.welcome_start
import com.photonne.app.resources.welcome_title
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PrimaryActionButton
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/** Where the welcome can take you. */
enum class WelcomeDestination { Backup, People, Memories }

/**
 * Shown once per account after signing in: the three things worth doing
 * first, each one tap away. Any choice, "Empezar" or dismissing it counts as
 * seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeSheet(
    name: String,
    onOpen: (WelcomeDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(stringResource(Res.string.welcome_title, name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.welcome_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WelcomeRow(
                icon = PhotonneIcons.Upload,
                title = stringResource(Res.string.welcome_backup_title),
                text = stringResource(Res.string.welcome_backup_text),
                onClick = { onOpen(WelcomeDestination.Backup) }
            )
            WelcomeRow(
                icon = PhotonneIcons.People,
                title = stringResource(Res.string.welcome_people_title),
                text = stringResource(Res.string.welcome_people_text),
                onClick = { onOpen(WelcomeDestination.People) }
            )
            WelcomeRow(
                icon = PhotonneIcons.Memories,
                title = stringResource(Res.string.welcome_memories_title),
                text = stringResource(Res.string.welcome_memories_text),
                onClick = { onOpen(WelcomeDestination.Memories) }
            )
            PrimaryActionButton(
                label = stringResource(Res.string.welcome_start),
                onClick = onDismiss,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }
    }
}

@Composable
private fun WelcomeRow(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
