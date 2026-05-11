package com.photonne.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.settings.ThemePreference
import com.photonne.app.resources.Res
import com.photonne.app.resources.appearance_dark
import com.photonne.app.resources.appearance_light
import com.photonne.app.resources.appearance_system
import com.photonne.app.resources.appearance_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val OPTIONS = listOf(
    ThemePreference.System to Res.string.appearance_system,
    ThemePreference.Light to Res.string.appearance_light,
    ThemePreference.Dark to Res.string.appearance_dark
)

@Composable
fun AccountAppearanceScreen(viewModel: AppearanceViewModel) {
    val current by viewModel.preference.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(Res.string.appearance_title),
            style = MaterialTheme.typography.titleMedium
        )
        OPTIONS.forEach { (preference, label) ->
            AppearanceRow(
                label = label,
                isSelected = current == preference,
                onSelect = { viewModel.choose(preference) }
            )
        }
    }
}

@Composable
private fun AppearanceRow(
    label: StringResource,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = isSelected, onClick = onSelect)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}
