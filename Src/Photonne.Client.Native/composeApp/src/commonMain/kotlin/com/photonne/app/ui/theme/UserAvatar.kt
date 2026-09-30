package com.photonne.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Avatar de iniciales del usuario: círculo primario con hasta dos letras (nombre
 * y apellido, o la primera del nombre de usuario). Lo comparten la cabecera de
 * Más (48 dp) y el resumen de Perfil (80 dp).
 */
@Composable
fun UserAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = userInitials(name),
            // La letra escala con el círculo: grande en Perfil, discreta en Más.
            style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Iniciales de un nombre: primera letra de la primera y la última palabra. */
internal fun userInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val first = words.first().first().uppercaseChar()
    val last = words.drop(1).lastOrNull()?.first()?.uppercaseChar()
    return if (last != null) "$first$last" else "$first"
}
