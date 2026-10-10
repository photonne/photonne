package com.photonne.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Filas de ajustes con el estilo de Más como canon: filas agrupadas dentro de
 * una sola tarjeta, separadas por divisores que arrancan donde el texto, y el
 * icono dentro de un círculo tintado. Antes había seis implementaciones (una
 * tarjeta por fila, iconos de 24, 28, 36 y 40 dp, cuadrados y círculos) y
 * Más, Ajustes, Utilidades, Administración, Copia de seguridad y Apariencia
 * parecían apps distintas.
 */

/** Diámetro del círculo de icono de una fila de ajustes. */
private val IconCircleSize: Dp = 40.dp

/** Diámetro del círculo compacto (mosaicos de la biblioteca en Más). */
private val IconCircleCompactSize: Dp = 34.dp

/**
 * Título de sección de una página (Más, Ajustes, Almacenamiento…): va encima
 * de un [SettingsGroup] o de un bloque de tarjetas. [contentPadding] por
 * defecto lo mete un poco respecto al borde de la tarjeta y le da aire arriba.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = Spacing.sm,
        end = Spacing.sm,
        top = Spacing.md,
        bottom = Spacing.xs
    )
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .semantics { heading() }
            .padding(contentPadding)
    )
}

/**
 * Etiqueta de un grupo de campos DENTRO de una hoja o un formulario (Orden,
 * Dirección, Vista…). Más pequeña que [SectionHeader]: no titula una página,
 * titula un control.
 */
@Composable
fun FieldGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() }
    )
}

/**
 * Icono dentro de un círculo tintado, el de las filas de Más. Los colores se
 * pueden cambiar para estados semánticos (Copia de seguridad).
 */
@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier = modifier
            .size(if (compact) IconCircleCompactSize else IconCircleSize)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(if (compact) 18.dp else IconSize.md)
        )
    }
}

/**
 * Tarjeta que agrupa filas de ajustes relacionadas. Los divisores los pinta
 * cada [SettingsItem] con `showDivider = true` (todas menos la primera): así
 * el divisor sabe si la fila lleva icono y dónde arranca el texto.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(content = content)
    }
}

/** Lo que va a la derecha de un [SettingsItem]. */
sealed interface SettingsTrailing {
    /** Interruptor: toda la fila lo conmuta (rol Switch). */
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : SettingsTrailing

    /** Radio: toda la fila lo selecciona (rol RadioButton). Pasa el `onClick` a la fila. */
    data class Radio(val selected: Boolean) : SettingsTrailing

    /** Valor actual en texto, antes del chevron si la fila navega. */
    data class Value(val text: String) : SettingsTrailing

    /** Contenido libre (insignia, botón). Va antes del chevron si la fila navega. */
    class Custom(val content: @Composable () -> Unit) : SettingsTrailing
}

/**
 * Fila de ajustes: icono en [IconCircle], título, línea secundaria opcional y
 * [trailing]. Si la fila tiene [onClick] y el trailing no es un control de
 * selección, lleva chevron. Con [SettingsTrailing.Toggle] toda la fila conmuta
 * el interruptor; con [SettingsTrailing.Radio] toda la fila selecciona.
 * [destructive] la pinta en rojo (cerrar sesión, borrar). [supportingContent]
 * va bajo la línea secundaria (la barra de cuota de Almacenamiento).
 */
@Composable
fun SettingsItem(
    headline: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    supporting: String? = null,
    trailing: SettingsTrailing? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    showDivider: Boolean = false,
    headlineMaxLines: Int = 2,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    if (showDivider) {
        HorizontalDivider(
            // Arranca donde el texto: bajo el círculo no hay línea.
            modifier = Modifier.padding(
                start = if (leadingIcon != null) Spacing.lg + IconCircleSize + Spacing.lg else Spacing.lg
            ),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
    val interaction = when {
        trailing is SettingsTrailing.Toggle -> Modifier.toggleable(
            value = trailing.checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = trailing.onCheckedChange
        )
        trailing is SettingsTrailing.Radio && onClick != null -> Modifier.selectable(
            selected = trailing.selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick
        )
        onClick != null -> Modifier.clickable(enabled = enabled, onClick = onClick)
        else -> Modifier
    }
    val disabledAlpha = if (enabled) 1f else 0.38f
    val headlineColor = if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(interaction)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            if (destructive) {
                IconCircle(
                    icon = leadingIcon,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                IconCircle(icon = leadingIcon)
            }
            Spacer(Modifier.size(Spacing.lg))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = headlineColor.copy(alpha = headlineColor.alpha * disabledAlpha),
                maxLines = headlineMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor.copy(alpha = secondaryColor.alpha * disabledAlpha)
                )
            }
            supportingContent?.invoke(this)
        }
        when (trailing) {
            is SettingsTrailing.Toggle -> {
                Spacer(Modifier.size(Spacing.md))
                // La fila entera es el control: el Switch solo se dibuja.
                Switch(checked = trailing.checked, onCheckedChange = null, enabled = enabled)
            }
            is SettingsTrailing.Radio -> {
                Spacer(Modifier.size(Spacing.md))
                RadioButton(selected = trailing.selected, onClick = null, enabled = enabled)
            }
            is SettingsTrailing.Value -> {
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    text = trailing.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor.copy(alpha = secondaryColor.alpha * disabledAlpha)
                )
            }
            is SettingsTrailing.Custom -> {
                Spacer(Modifier.size(Spacing.sm))
                trailing.content()
            }
            null -> Unit
        }
        val navigates = onClick != null &&
            trailing !is SettingsTrailing.Toggle && trailing !is SettingsTrailing.Radio
        if (navigates) {
            if (trailing != null) Spacer(Modifier.size(Spacing.sm))
            Icon(
                imageVector = PhotonneIcons.Chevron,
                contentDescription = null,
                tint = secondaryColor.copy(alpha = secondaryColor.alpha * disabledAlpha)
            )
        }
    }
}
