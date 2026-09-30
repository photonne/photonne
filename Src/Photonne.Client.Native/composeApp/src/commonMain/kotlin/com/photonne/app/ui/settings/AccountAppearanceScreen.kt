package com.photonne.app.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photonne.app.data.settings.ThemePreference
import com.photonne.app.resources.Res
import com.photonne.app.resources.appearance_dark
import com.photonne.app.resources.appearance_light
import com.photonne.app.resources.appearance_system_short
import com.photonne.app.resources.appearance_title
import com.photonne.app.ui.main.FormPageScaffold
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.PhotonneDarkColorScheme
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PhotonneLightColorScheme
import com.photonne.app.ui.theme.PillShape
import com.photonne.app.ui.theme.SectionHeader
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

private val OPTIONS = listOf(
    ThemePreference.System to Res.string.appearance_system_short,
    ThemePreference.Light to Res.string.appearance_light,
    ThemePreference.Dark to Res.string.appearance_dark
)

/** Mitad superior izquierda del rectángulo, cortada en diagonal (Sistema). */
private val UpperLeftTriangle = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(0f, size.height)
    close()
}

/** Mitad inferior derecha, el complemento de [UpperLeftTriangle]. */
private val LowerRightTriangle = GenericShape { size, _ ->
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

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
            // Tres miniaturas de la app, una por tema: se elige viendo el
            // resultado en vez de leyendo una etiqueta. Se aplica al momento.
            SettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    OPTIONS.forEach { (preference, label) ->
                        ThemeTile(
                            preference = preference,
                            label = stringResource(label),
                            selected = current == preference,
                            onClick = { viewModel.choose(preference) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Una opción de tema: miniatura, check si está elegida y etiqueta debajo. */
@Composable
private fun ThemeTile(
    preference: ThemePreference,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape
                )
        ) {
            when (preference) {
                ThemePreference.Light -> ThemeMock(PhotonneLightColorScheme, Modifier.fillMaxSize())
                ThemePreference.Dark -> ThemeMock(PhotonneDarkColorScheme, Modifier.fillMaxSize())
                ThemePreference.System -> {
                    ThemeMock(PhotonneLightColorScheme, Modifier.fillMaxSize().clip(UpperLeftTriangle))
                    ThemeMock(PhotonneDarkColorScheme, Modifier.fillMaxSize().clip(LowerRightTriangle))
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs)
                        .size(IconSize.md)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = PhotonneIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(IconSize.badge)
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Maqueta mínima de la app pintada con [scheme]: barra de cabecera, dos líneas
 * de contenido y la cápsula de la nav flotante abajo. Solo decorativa.
 */
@Composable
private fun ThemeMock(scheme: ColorScheme, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(scheme.background)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Cabecera: cápsula con un punto primario, como el cromo flotante.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.md)
                    .background(scheme.surfaceVariant, PillShape),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(Spacing.sm)
                        .background(scheme.primary, CircleShape)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(Spacing.sm)
                    .background(scheme.onSurface.copy(alpha = 0.7f), PillShape)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(Spacing.sm)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.5f), PillShape)
            )
        }
        // Nav flotante: cápsula ceñida y centrada, con la pestaña activa en oro.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.sm)
                .background(scheme.surfaceContainerHigh, PillShape)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(Spacing.sm)
                        .background(
                            if (index == 0) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            CircleShape
                        )
                )
            }
        }
    }
}
