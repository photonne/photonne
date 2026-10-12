package com.photonne.app.ui.actions

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.actions_working_download
import com.photonne.app.resources.actions_working_download_progress
import com.photonne.app.resources.actions_working_link
import com.photonne.app.resources.actions_working_share
import com.photonne.app.resources.actions_working_share_progress
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.ChromeElevation
import com.photonne.app.ui.theme.PillShape
import com.photonne.app.ui.theme.Spacing

/**
 * Píldora flotante de operación masiva en curso (descarga/ZIP, compartir,
 * crear enlace) con Cancelar. Antes el único indicio era la barra de
 * selección atenuada al 38 %, sin forma de abortar un ZIP de minutos.
 * Con [progress] (0..1, cuando el servidor dice el tamaño) el anillo pasa a
 * determinado y el texto lleva el porcentaje.
 */
@Composable
fun WorkingPill(
    working: AssetActionWorking,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    if (working == AssetActionWorking.Idle) return
    val percent = progress?.let { (it * 100).toInt() }
    val message = when {
        working == AssetActionWorking.CreatingLink ->
            stringResource(Res.string.actions_working_link)
        working == AssetActionWorking.Sharing && percent != null ->
            stringResource(Res.string.actions_working_share_progress, percent)
        working == AssetActionWorking.Sharing ->
            stringResource(Res.string.actions_working_share)
        percent != null ->
            stringResource(Res.string.actions_working_download_progress, percent)
        else -> stringResource(Res.string.actions_working_download)
    }
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = ChromeElevation.bar
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    }
}
