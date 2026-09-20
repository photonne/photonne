package com.photonne.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.Person
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.people_picker_empty
import com.photonne.app.resources.people_picker_faces_format
import com.photonne.app.resources.people_picker_search_placeholder
import com.photonne.app.resources.people_picker_title
import com.photonne.app.resources.people_unnamed
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.photonne.app.ui.theme.Spacing

/**
 * Bottom sheet that lists every other person the caller has and returns
 * the one they tap. Used by the Merge flow on PersonDetail.
 *
 * Con buscador: la lista puede tener cientos de entradas "Sin nombre" y sin
 * filtro la fusión obligaba a scrollear a ciegas. El caller es responsable de
 * cargar TODAS las páginas ([PeopleViewModel.loadAllPages]) y de pasar
 * [isLoading] mientras llegan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonPickerDialog(
    people: List<Person>,
    baseUrl: String,
    excludeId: String? = null,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (Person) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val unnamedLabel = stringResource(Res.string.people_unnamed)
    val candidates = remember(people, excludeId, query, unnamedLabel) {
        val base = if (excludeId == null) people else people.filter { it.id != excludeId }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) base
        else base.filter { person ->
            val name = person.name?.takeIf { it.isNotBlank() } ?: unnamedLabel
            name.contains(trimmed, ignoreCase = true)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                stringResource(Res.string.people_picker_title),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.people_picker_search_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
            if (candidates.isEmpty() && !isLoading) {
                Text(
                    stringResource(Res.string.people_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    items(candidates, key = { it.id }) { person ->
                        PersonRow(
                            person = person,
                            baseUrl = baseUrl,
                            onClick = { onSelect(person) }
                        )
                    }
                    if (isLoading) {
                        item("loading") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun PersonRow(person: Person, baseUrl: String, onClick: () -> Unit) {
    val displayName = person.name?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.people_unnamed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        PersonAvatar(person = person, baseUrl = baseUrl, sizeDp = 56)
        Column {
            Text(displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (person.faceCount > 0) {
                Text(
                    pluralStringResource(
                        Res.plurals.people_picker_faces_format,
                        person.faceCount,
                        person.faceCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Avatar circular reutilizado por el selector y la confirmación de fusión. */
@Composable
internal fun PersonAvatar(person: Person, baseUrl: String, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        person.coverFaceId?.let { faceId ->
            AsyncImage(
                model = "$baseUrl/api/faces/$faceId/thumbnail",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp.dp)
            )
        }
    }
}
