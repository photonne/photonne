package com.photonne.app.ui.actions

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.photonne.app.resources.actions_working_link
import com.photonne.app.resources.actions_working_share
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.Spacing

/**
 * Píldora flotante de operación masiva en curso (descarga/ZIP, compartir,
 * crear enlace) con Cancelar. Antes el único indicio era la barra de
 * selección atenuada al 38 %, sin forma de abortar un ZIP de minutos.
 */
@Composable
fun WorkingPill(
    working: AssetActionWorking,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (working == AssetActionWorking.Idle) return
    val message = stringResource(
        when (working) {
            AssetActionWorking.Sharing -> Res.string.actions_working_share
            AssetActionWorking.CreatingLink -> Res.string.actions_working_link
            else -> Res.string.actions_working_download
        }
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
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
