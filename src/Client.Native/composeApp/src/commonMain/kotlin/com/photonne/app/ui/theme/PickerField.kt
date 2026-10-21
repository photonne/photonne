package com.photonne.app.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

/**
 * Campo que abre un selector (fecha, hora): se ve como el resto de campos del
 * formulario, con su etiqueta y un icono al final, y tocarlo abre el diálogo.
 * Antes la caducidad de un enlace era un TextButton que parecía un enlace y las
 * fechas de búsqueda, reglas y captura, OutlinedButton que parecían botones.
 *
 * Con [value] nulo solo se ve la etiqueta, en el sitio del valor.
 */
@Composable
fun PickerField(
    label: String,
    value: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(icon, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // El campo de solo lectura se queda el foco y no avisa del toque: la
        // capa de encima lo recoge para todo el campo.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        )
    }
}
