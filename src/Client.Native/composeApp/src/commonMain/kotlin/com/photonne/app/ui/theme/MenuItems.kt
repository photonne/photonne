package com.photonne.app.ui.theme

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Hueco de icono delantero para las opciones excluyentes de un menú ⋮ (orden,
 * modo): el check en la elegida y un hueco del mismo tamaño en las demás. Los
 * menús de colección llevan todos icono delante, así que las opciones de radio
 * también lo ocupan para que los textos queden alineados con el resto.
 */
@Composable
fun MenuCheckSlot(checked: Boolean) {
    if (checked) {
        Icon(PhotonneIcons.Check, contentDescription = null)
    } else {
        Spacer(Modifier.size(IconSize.lg))
    }
}
