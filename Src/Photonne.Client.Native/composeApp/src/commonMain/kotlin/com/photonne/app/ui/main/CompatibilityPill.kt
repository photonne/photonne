package com.photonne.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.version.ServerCompatibility
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.compat_pill_client_too_old
import com.photonne.app.resources.compat_pill_server_too_old
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Píldora global "Actualiza la app" / "Actualiza el servidor" en la franja de la
 * de "Sin conexión": cuando no son compatibles fallan cosas sueltas por toda la
 * app y esto dice por qué. Se puede cerrar (vuelve si cambia el motivo, p. ej.
 * otro servidor); los detalles están en Más.
 */
@Composable
fun CompatibilityPill(
    compatibility: ServerCompatibility,
    modifier: Modifier = Modifier,
) {
    var dismissed by rememberSaveable(compatibility.toString()) { mutableStateOf(false) }
    // Conserva el último estado mientras sale animada.
    var shown by remember { mutableStateOf<ServerCompatibility?>(null) }
    val visible = compatibility != ServerCompatibility.Compatible && !dismissed
    if (compatibility != ServerCompatibility.Compatible) shown = compatibility

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val label = when (val current = shown) {
            is ServerCompatibility.ClientTooOld ->
                stringResource(Res.string.compat_pill_client_too_old, current.minClientVersion)
            is ServerCompatibility.ServerTooOld ->
                stringResource(Res.string.compat_pill_server_too_old, current.minServerVersion)
            else -> ""
        }
        Surface(
            shape = CircleShape,
            color = PhotonneColors.warningContainer,
            contentColor = PhotonneColors.onWarningContainer,
            shadowElevation = 2.dp,
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(onClick = { dismissed = true }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(Res.string.action_close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
