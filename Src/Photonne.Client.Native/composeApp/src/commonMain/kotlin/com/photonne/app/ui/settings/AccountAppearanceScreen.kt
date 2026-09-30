package com.photonne.app.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.photonne.app.data.settings.ThemePreference
import com.photonne.app.resources.Res
import com.photonne.app.resources.appearance_dark
import com.photonne.app.resources.appearance_light
import com.photonne.app.resources.appearance_system
import com.photonne.app.resources.appearance_title
import com.photonne.app.ui.main.FormPageScaffold
import com.photonne.app.ui.theme.SectionHeader
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.SettingsItem
import com.photonne.app.ui.theme.SettingsTrailing
import org.jetbrains.compose.resources.stringResource

private val OPTIONS = listOf(
    ThemePreference.System to Res.string.appearance_system,
    ThemePreference.Light to Res.string.appearance_light,
    ThemePreference.Dark to Res.string.appearance_dark
)

@Composable
fun AccountAppearanceScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AppearanceViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val current by viewModel.preference.collectAsStateWithLifecycle()

    FormPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
        page {
            SectionHeader(stringResource(Res.string.appearance_title))
            // Las opciones como filas de ajustes: toda la fila selecciona.
            SettingsGroup {
                OPTIONS.forEachIndexed { index, (preference, label) ->
                    SettingsItem(
                        headline = stringResource(label),
                        onClick = { viewModel.choose(preference) },
                        trailing = SettingsTrailing.Radio(selected = current == preference),
                        showDivider = index > 0
                    )
                }
            }
        }
    }
}
