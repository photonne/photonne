package com.photonne.app.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.Person
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.people_action_merge
import com.photonne.app.resources.people_merge_confirm_message
import com.photonne.app.resources.people_merge_confirm_title
import com.photonne.app.resources.people_unnamed
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmación de fusión con las dos caras delante: fusionar es irreversible
 * y antes se disparaba con un solo toque en el selector, sin `onFailure`.
 * [source] desaparece; sus caras pasan a [target].
 */
@Composable
fun ConfirmMergeDialog(
    target: Person,
    source: Person,
    baseUrl: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val unnamed = stringResource(Res.string.people_unnamed)
    val sourceName = source.name?.takeIf { it.isNotBlank() } ?: unnamed
    val targetName = target.name?.takeIf { it.isNotBlank() } ?: unnamed
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(Res.string.people_merge_confirm_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PersonAvatar(person = source, baseUrl = baseUrl, sizeDp = 64)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            sourceName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PersonAvatar(person = target, baseUrl = baseUrl, sizeDp = 64)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            targetName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(
                        Res.string.people_merge_confirm_message,
                        sourceName,
                        targetName
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting) {
                Text(
                    stringResource(Res.string.people_action_merge),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
