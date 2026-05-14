package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_metadata_task_explanation
import org.jetbrains.compose.resources.stringResource

/**
 * The server runs metadata extraction as part of the index pipeline, so
 * this screen reuses [AdminIndexAssetsScreen] under an informational
 * preamble that points the admin to it. The progress and stats below
 * therefore mirror an index run — see AdminIndexAssetsTask for details.
 */
@Composable
fun AdminMetadataTaskScreen(viewModel: AdminIndexAssetsViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(
                stringResource(Res.string.admin_metadata_task_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        AdminIndexAssetsScreen(viewModel = viewModel)
    }
}
