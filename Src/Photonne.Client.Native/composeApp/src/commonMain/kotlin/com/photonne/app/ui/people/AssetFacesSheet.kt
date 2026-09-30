package com.photonne.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.photonne.app.resources.people_face_assign_to
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.Face
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_save
import com.photonne.app.resources.people_face_assign
import com.photonne.app.resources.people_face_assign_new
import com.photonne.app.resources.people_face_reject
import com.photonne.app.resources.people_face_set_cover
import com.photonne.app.resources.people_face_suggestion
import com.photonne.app.resources.people_face_unassign
import com.photonne.app.resources.people_face_unknown
import com.photonne.app.resources.people_faces_empty
import com.photonne.app.resources.people_faces_title
import com.photonne.app.resources.people_suggestions_accept
import com.photonne.app.resources.people_suggestions_dismiss
import com.photonne.app.resources.people_unnamed
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.SheetHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetFacesSheet(
    state: AssetFacesUiState,
    baseUrl: String,
    onDismiss: () -> Unit,
    onAcceptSuggestion: (faceId: String) -> Unit,
    onDismissSuggestion: (faceId: String) -> Unit,
    onAssign: (faceId: String) -> Unit,
    onAssignToPerson: (faceId: String, personId: String) -> Unit,
    onAssignToNewPerson: (faceId: String, name: String) -> Unit,
    onUnassign: (faceId: String) -> Unit,
    onReject: (faceId: String) -> Unit,
    onSetCover: (personId: String, faceId: String) -> Unit,
    onCancelAssign: () -> Unit,
    onPickerQueryChange: (String) -> Unit = {},
    /** Toque en el nombre de una cara ya asignada: abre esa persona. */
    onOpenPerson: ((personId: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)
        ) {
            SheetHeader(
                stringResource(Res.string.people_faces_title),
                modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md)
            )

            when {
                state.isLoading ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                state.error != null && state.faces.isEmpty() ->
                    com.photonne.app.ui.error.ErrorBanner(
                        error = state.error,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                state.faces.isEmpty() ->
                    Text(
                        stringResource(Res.string.people_faces_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xl)
                    )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(state.faces, key = { it.id }) { face ->
                        FaceRow(
                            face = face,
                            baseUrl = baseUrl,
                            isPending = face.id in state.pendingFaceIds,
                            personName = state.personById(face.personId)?.name,
                            suggestionName = state.personById(face.suggestedPersonId)?.name,
                            onAcceptSuggestion = { onAcceptSuggestion(face.id) },
                            onDismissSuggestion = { onDismissSuggestion(face.id) },
                            onAssign = { onAssign(face.id) },
                            onUnassign = { onUnassign(face.id) },
                            onReject = { onReject(face.id) },
                            onSetCover = {
                                face.personId?.let { onSetCover(it, face.id) }
                            },
                            onOpenPerson = face.personId?.let { personId ->
                                onOpenPerson?.let { open -> { open(personId) } }
                            }
                        )
                    }
                }
            }
        }
    }

    val assigningId = state.assigningFaceId
    if (assigningId != null) {
        AssignPersonDialog(
            people = state.pickerPeople,
            query = state.pickerQuery,
            isSearching = state.pickerSearching,
            exactMatch = state.pickerExactMatch,
            onQueryChange = onPickerQueryChange,
            baseUrl = baseUrl,
            onDismiss = onCancelAssign,
            onPickExisting = { personId -> onAssignToPerson(assigningId, personId) },
            onCreateNew = { name -> onAssignToNewPerson(assigningId, name) }
        )
    }
}

@Composable
private fun FaceRow(
    face: Face,
    baseUrl: String,
    isPending: Boolean,
    personName: String?,
    suggestionName: String?,
    onAcceptSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onAssign: () -> Unit,
    onUnassign: () -> Unit,
    onReject: () -> Unit,
    onSetCover: () -> Unit,
    onOpenPerson: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = "$baseUrl/api/faces/${face.id}/thumbnail",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp)
            )
            if (isPending) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            val labelStyle = MaterialTheme.typography.bodyMedium
            when {
                face.personId != null -> Text(
                    personName?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.people_unnamed),
                    style = labelStyle,
                    // El nombre lleva a la persona: la hoja es para editar, pero
                    // desde aquí también se quiere ver "todas las de Ana".
                    color = if (onOpenPerson != null) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Unspecified,
                    modifier = if (onOpenPerson != null) {
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onOpenPerson)
                            .padding(vertical = Spacing.xxs)
                    } else Modifier
                )
                face.suggestedPersonId != null -> Text(
                    stringResource(
                        Res.string.people_face_suggestion,
                        suggestionName?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.people_unnamed)
                    ),
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.primary
                )
                else -> Text(
                    stringResource(Res.string.people_face_unknown),
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FaceActions(
            face = face,
            isPending = isPending,
            onAcceptSuggestion = onAcceptSuggestion,
            onDismissSuggestion = onDismissSuggestion,
            onAssign = onAssign,
            onUnassign = onUnassign,
            onReject = onReject,
            onSetCover = onSetCover
        )
    }
}

@Composable
private fun FaceActions(
    face: Face,
    isPending: Boolean,
    onAcceptSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onAssign: () -> Unit,
    onUnassign: () -> Unit,
    onReject: () -> Unit,
    onSetCover: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        if (face.suggestedPersonId != null) {
            IconButton(onClick = onDismissSuggestion, enabled = !isPending) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.people_suggestions_dismiss)
                )
            }
            IconButton(onClick = onAcceptSuggestion, enabled = !isPending) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(Res.string.people_suggestions_accept),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else if (face.personId != null) {
            IconButton(onClick = onSetCover, enabled = !isPending) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(Res.string.people_face_set_cover),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onUnassign, enabled = !isPending) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.people_face_unassign)
                )
            }
        } else {
            IconButton(onClick = onAssign, enabled = !isPending) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(Res.string.people_face_assign)
                )
            }
        }
        IconButton(onClick = onReject, enabled = !isPending) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(Res.string.people_face_reject),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AssignPersonDialog(
    people: List<com.photonne.app.data.models.Person>,
    query: String,
    isSearching: Boolean,
    exactMatch: com.photonne.app.data.models.Person?,
    onQueryChange: (String) -> Unit,
    baseUrl: String,
    onDismiss: () -> Unit,
    onPickExisting: (personId: String) -> Unit,
    onCreateNew: (name: String) -> Unit
) {
    // El campo sirve para las dos cosas: filtra la lista (en el servidor) y,
    // si no hay nadie con ese nombre, es el nombre de la persona nueva.
    val newName = query
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.people_face_assign)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(Res.string.people_face_assign_new)) },
                    trailingIcon = if (isSearching) {
                        {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                if (people.isEmpty()) {
                    Text(
                        stringResource(Res.string.people_face_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                    ) {
                        items(people, key = { it.id }) { person ->
                            val displayName = person.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.people_unnamed)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    person.coverFaceId?.let { faceId ->
                                        AsyncImage(
                                            model = "$baseUrl/api/faces/$faceId/thumbnail",
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { onPickExisting(person.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(displayName)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (exactMatch != null) {
                // Ya hay alguien con ese nombre: asignar, no duplicar.
                TextButton(onClick = { onPickExisting(exactMatch.id) }) {
                    Text(
                        stringResource(
                            Res.string.people_face_assign_to,
                            exactMatch.name.orEmpty()
                        )
                    )
                }
            } else {
                TextButton(
                    onClick = { onCreateNew(newName) },
                    enabled = newName.trim().isNotEmpty()
                ) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
