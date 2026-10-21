package com.photonne.app.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/*
 * Cabecera común de las hojas inferiores (ModalBottomSheet). Antes había 24
 * hojas con `titleLarge` y 9 con `titleMedium`, con márgenes `lg` o `xl` y una
 * sin título. El canon es el de la mayoría: el cuerpo de la hoja es una
 * columna con `padding(horizontal = Spacing.lg)` y la cabecera es su primer
 * hijo, sin margen propio. Las hojas a sangre (listas, rejillas) le pasan
 * `Modifier.padding(horizontal = Spacing.lg)` para quedar alineadas igual.
 */

/**
 * Título de una hoja inferior (`titleLarge`), con una línea secundaria
 * opcional (`bodyMedium`, `onSurfaceVariant`) y acciones a la derecha del
 * título (p. ej. "Limpiar todo" o "Copiar").
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            actions()
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Pie de una hoja inferior: los botones compactos ([CompactSecondaryButton] y
 * [CompactPrimaryButton], en ese orden) alineados a la derecha, como el pie de
 * un diálogo. Antes convivían cuatro pies: botón de 56 dp a todo el ancho, fila
 * de TextButton, FilledTonalButton suelto y nada.
 *
 * Una hoja con formulario lleva "Cancelar" + la acción; una de gestión o lista,
 * "Cerrar" + la acción de crear si la tiene. Las de filtros, orden o menú no
 * llevan pie: se aplican al tocar.
 */
@Composable
fun SheetActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Fila de opción de una hoja (formato de descarga, compartir, tipo de álbum,
 * añadir condición): icono en `primary`, título y una línea opcional debajo, y
 * tocarla la elige. Había cuatro copias privadas con rellenos (8 o 14 dp) y
 * estilos (bodyMedium o bodyLarge) distintos.
 *
 * Por defecto va a sangre en una hoja sin margen, con el margen de la hoja
 * dentro para alinear con la cabecera. En una hoja con la columna ya en
 * `Spacing.lg`, [contentPadding] solo lleva el vertical.
 */
@Composable
fun SheetOptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSize.lg)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Relleno de [SheetOptionRow] dentro de una hoja cuya columna ya lleva margen. */
val SheetOptionRowInsetPadding: PaddingValues = PaddingValues(vertical = Spacing.md)
