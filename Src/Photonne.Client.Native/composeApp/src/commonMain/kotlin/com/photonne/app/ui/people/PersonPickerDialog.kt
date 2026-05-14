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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.photonne.app.resources.people_picker_title
import com.photonne.app.resources.people_unnamed
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet that lists every other person the caller has and returns
 * the one they tap. Used by the Merge flow on PersonDetail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonPickerDialog(
    people: List<Person>,
    baseUrl: String,
    excludeId: String? = null,
    onDismiss: () -> Unit,
    onSelect: (Person) -> Unit
) {
    val candidates = remember(people, excludeId) {
        if (excludeId == null) people else people.filter { it.id != excludeId }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.people_picker_title),
                style = MaterialTheme.typography.titleLarge
            )
            if (candidates.isEmpty()) {
                Text(
                    stringResource(Res.string.people_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(candidates, key = { it.id }) { person ->
                        PersonRow(
                            person = person,
                            baseUrl = baseUrl,
                            onClick = { onSelect(person) }
                        )
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
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            person.coverFaceId?.let { faceId ->
                AsyncImage(
                    model = "$baseUrl/api/faces/$faceId/thumbnail",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Column {
            Text(displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (person.faceCount > 0) {
                Text(
                    "${person.faceCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
