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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.photonne.app.data.api.ConnectivityStatus
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_retry
import com.photonne.app.resources.connectivity_offline
import com.photonne.app.resources.connectivity_server_unreachable
import com.photonne.app.ui.theme.ChromeElevation
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.PillShape
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Píldora global "Sin conexión" / "Sin conexión con <host>" bajo el cromo
 * flotante, en la misma franja que el snackbar (que entra debajo de ella). Sale
 * sola al recuperar la conexión; "Reintentar" fuerza la comprobación. Las
 * pantallas siguen enseñando su propio error: esto dice por qué fallan todas.
 */
@Composable
fun ConnectivityPill(
    status: ConnectivityStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Conserva el último estado mientras sale animada.
    var shown by remember { mutableStateOf<ConnectivityStatus?>(null) }
    if (status != ConnectivityStatus.Online) shown = status

    AnimatedVisibility(
        visible = status != ConnectivityStatus.Online,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val label = when (val current = shown) {
            is ConnectivityStatus.ServerUnreachable ->
                stringResource(Res.string.connectivity_server_unreachable, current.host)
            else -> stringResource(Res.string.connectivity_offline)
        }
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shadowElevation = ChromeElevation.pill,
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Outlined.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.chip)
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TextButton(onClick = onRetry) {
                    Text(
                        stringResource(Res.string.action_retry),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
