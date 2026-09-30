package com.photonne.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.theme.ChromeElevation
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.PillShape
import com.photonne.app.ui.theme.Spacing

/**
 * La acción de confirmar de una pantalla, como cápsula flotante en el sitio de
 * la nav: comparte forma, altura y márgenes con la nav y con las barras de
 * selección (misma familia) y, como ellas, la *sustituye* mientras hay algo que
 * confirmar. Va rellena de `primary` (o de `error` si [destructive]) porque es
 * LA acción de la pantalla, no un contenedor de iconos. No lleva cristal —
 * encima de una rejilla de fotos, un botón primario translúcido deja de leerse
 * como botón.
 *
 * La usan la revisión de "Mover" de Para organizar y el borrado de Duplicados.
 */
@Composable
fun ConfirmCapsule(
    label: String,
    enabled: Boolean,
    isWorking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val container = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val content = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(
                start = FloatingNavBarHorizontalMargin,
                end = FloatingNavBarHorizontalMargin,
                bottom = FloatingNavBarBottomMargin,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = PillShape,
            color = container,
            contentColor = content,
            shadowElevation = ChromeElevation.nav,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CompactNavBarContentHeight)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .padding(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isWorking) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(IconSize.chip),
                        color = content,
                    )
                    Spacer(Modifier.width(Spacing.md))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.md))
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}
