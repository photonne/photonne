package com.photonne.app.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/*
 * Filas y tarjetas de "una colección": álbum, carpeta, subcarpeta, carpeta del
 * dispositivo y destino de "Añadir a álbum". Antes cada pantalla tenía la suya
 * (miniaturas de 40 y 56 dp, radios 10 y 12, `bodyLarge` o `titleSmall`, con
 * y sin chevron) y la misma carpeta se veía distinta en la lista y dentro de
 * otra carpeta. También las tarjetas de entrada (Explorar, Para organizar,
 * Mi dispositivo), que tenían tres estilos.
 */

/** Lado de la miniatura de una [CollectionRow]. */
private val CollectionRowThumbSize: Dp = 56.dp

/** Diámetro del check de selección (el de la rejilla de fotos). */
private val SelectionCheckSize: Dp = 20.dp

/** Marca dentro del check de selección. */
private val SelectionCheckIconSize: Dp = 14.dp

/** Diámetro del check de selección sobre una miniatura pequeña. */
private val SelectionCheckCompactSize: Dp = 16.dp

/** Tamaño del icono de carpeta que rellena una tarjeta sin portada. */
private val CollectionCardGlyphSize: Dp = 56.dp

/**
 * Check de elemento seleccionado: círculo `primary` con la marca en
 * `onPrimary`. Es el de la rejilla de fotos; las colecciones lo reutilizan
 * para que "seleccionado" se vea igual en toda la app.
 */
@Composable
fun SelectionCheck(modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier
            .size(if (compact) SelectionCheckCompactSize else SelectionCheckSize)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = PhotonneIcons.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(if (compact) IconSize.xs else SelectionCheckIconSize)
        )
    }
}

/**
 * Portada de una colección: la imagen [model] recortada o, sin ella,
 * [fallback] centrado (inicial del álbum, icono de carpeta…).
 */
@Composable
fun CollectionCover(
    model: Any?,
    contentDescription: String?,
    fallback: @Composable BoxScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            fallback()
        }
    }
}

/** Icono de carpeta como relleno de una portada vacía. */
@Composable
fun FolderGlyph(modifier: Modifier = Modifier, large: Boolean = false) {
    Icon(
        imageVector = PhotonneIcons.Folder,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (large) modifier.size(CollectionCardGlyphSize) else modifier
    )
}

/**
 * Fila de colección: miniatura de 56 dp recortada a `shapes.small`, título
 * `titleSmall`, subtítulo `bodySmall` y una línea de [badges] (recuento y
 * `MetaBadge`). Sin chevron: si la pantalla quiere uno lo pasa en [trailing].
 * Seleccionada, la miniatura lleva borde y el check de la rejilla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionRow(
    title: String,
    thumbnail: @Composable BoxScope.() -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    badges: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(CollectionRowThumbSize)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            thumbnail()
            if (selected) {
                SelectionCheck(
                    compact = true,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs)
                )
            }
        }
        Spacer(Modifier.size(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badges != null) {
                Row(
                    modifier = Modifier.padding(top = Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    content = badges
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(Spacing.sm))
            trailing()
        }
    }
}

/**
 * Recuento de elementos sobre una portada (abajo a la izquierda), el de las
 * tarjetas de álbum y carpeta. Blanco sobre velo: va sobre una foto.
 */
@Composable
fun CollectionCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(PhotonneColors.scrimMedium, shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
    ) {
        Text(
            text = "$count",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Tarjeta de colección para la vista en rejilla: portada cuadrada recortada a
 * `shapes.medium`, recuento abajo a la izquierda, [badges] (`OverlayIconBadge`)
 * arriba a la derecha y el nombre debajo (`titleSmall`, una línea). Con
 * [subtitle] añade una segunda línea. Seleccionada, borde y check de la rejilla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionCard(
    title: String,
    thumbnail: @Composable BoxScope.() -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    count: Int? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    badges: (@Composable RowScope.() -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            thumbnail()
            if (count != null) {
                CollectionCountBadge(
                    count = count,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.sm)
                )
            }
            if (badges != null) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    content = badges
                )
            }
            if (selected) {
                SelectionCheck(modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm))
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Tarjeta de entrada a otra sección (Para organizar, Mi dispositivo): tarjeta
 * `shapes.large` con [IconCircle], título, subtítulo, [trailing] opcional
 * (un recuento) y chevron. [emphasized] la pinta en el contenedor primario
 * para lo que pide acción (una bandeja con pendientes); si no, `surfaceVariant`.
 */
@Composable
fun EntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val container = if (emphasized) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurface
    val secondary = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (emphasized) {
            IconCircle(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            IconCircle(icon = icon)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke(this)
        Icon(
            imageVector = PhotonneIcons.Chevron,
            contentDescription = null,
            tint = secondary
        )
    }
}

