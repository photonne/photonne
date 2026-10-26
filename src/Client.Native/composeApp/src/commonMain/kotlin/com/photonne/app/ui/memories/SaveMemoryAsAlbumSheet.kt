package com.photonne.app.ui.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_save
import com.photonne.app.resources.memory_album_name
import com.photonne.app.resources.memory_save_as_album
import com.photonne.app.resources.memory_save_as_album_hint
import com.photonne.app.ui.theme.CompactPrimaryButton
import com.photonne.app.ui.theme.CompactSecondaryButton
import com.photonne.app.ui.theme.SheetActions
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.util.suspendRunCatching
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Keeps a memory (a trip, a year's favourites…) as an ordinary album: the
 * memory is regenerated every night and may change or go; the album stays,
 * and can be edited and shared. Creates the album, then adds the photos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveMemoryAsAlbumSheet(
    title: String,
    assetIds: List<String>,
    onDismiss: () -> Unit,
    onSaved: (AlbumSummary) -> Unit,
) {
    val repository: AlbumsRepository = koinInject()
    val errors: UiErrorFactory = koinInject()
    val scope = rememberCoroutineScope()
    var name by remember(title) { mutableStateOf(title) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val wanted = name.trim()
        if (wanted.isEmpty() || saving || assetIds.isEmpty()) return
        saving = true
        error = null
        scope.launch {
            suspendRunCatching {
                val album = repository.create(name = wanted, description = null)
                repository.addAssetsBatch(album.id, assetIds)
                album
            }
                .onSuccess { onSaved(it) }
                .onFailure { error = errors.from(it, "No se pudo crear el álbum").userMessage }
            saving = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SheetHeader(stringResource(Res.string.memory_save_as_album))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.memory_album_name)) },
                supportingText = {
                    Text(pluralStringResource(Res.plurals.memory_save_as_album_hint, assetIds.size, assetIds.size))
                },
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SheetActions {
                CompactSecondaryButton(
                    label = stringResource(Res.string.action_cancel),
                    onClick = onDismiss,
                    enabled = !saving
                )
                CompactPrimaryButton(
                    label = stringResource(Res.string.action_save),
                    onClick = { save() },
                    isLoading = saving
                )
            }
        }
    }
}
