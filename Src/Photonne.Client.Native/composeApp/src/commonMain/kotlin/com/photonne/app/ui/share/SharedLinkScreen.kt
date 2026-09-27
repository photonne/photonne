package com.photonne.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.PublicSharedAsset
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.album_hero_photos
import com.photonne.app.resources.shared_link_empty
import com.photonne.app.resources.shared_link_expired_subtitle
import com.photonne.app.resources.shared_link_expired_title
import com.photonne.app.resources.shared_link_not_found_subtitle
import com.photonne.app.resources.shared_link_not_found_title
import com.photonne.app.resources.shared_link_password_label
import com.photonne.app.resources.shared_link_password_submit
import com.photonne.app.resources.shared_link_password_subtitle
import com.photonne.app.resources.shared_link_password_title
import com.photonne.app.resources.shared_link_password_wrong
import com.photonne.app.resources.shared_link_server
import com.photonne.app.resources.shared_link_title
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.navigation.PlatformBackHandler
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PrimaryActionButton
import com.photonne.app.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.ktor.http.Url
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Un enlace compartido abierto desde fuera (`photonne://share/{token}`): lo que
 * la página pública `/share/{token}` enseña en el navegador — portada con
 * nombre y recuento, rejilla y un visor sencillo — con su puerta de
 * contraseña. No usa el visor de la app: estas fotos no son de la biblioteca
 * del usuario (a menudo ni de su servidor) y solo se llega a ellas por las URL
 * públicas del enlace. Los vídeos se abren en el reproductor del sistema.
 */
@Composable
fun SharedLinkScreen(
    state: SharedLinkUiState,
    onBack: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val reservedTop = subscreenChromeReservedTop()
    val uriHandler = LocalUriHandler.current
    var openedAsset by remember { mutableStateOf<PublicSharedAsset?>(null) }
    val content = state.content
    fun absolute(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url
        else state.serverBaseUrl.trimEnd('/') + url

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.unavailable != null -> {
                val expired = state.unavailable == SharedLinkUnavailable.Expired
                EmptyState(
                    icon = Icons.Outlined.LinkOff,
                    title = stringResource(
                        if (expired) Res.string.shared_link_expired_title
                        else Res.string.shared_link_not_found_title
                    ),
                    subtitle = stringResource(
                        if (expired) Res.string.shared_link_expired_subtitle
                        else Res.string.shared_link_not_found_subtitle
                    ),
                    modifier = Modifier.padding(top = reservedTop)
                )
            }
            state.requiresPassword -> PasswordGate(
                wrongPassword = state.wrongPassword,
                isLoading = state.isLoading,
                onSubmit = onSubmitPassword,
                modifier = Modifier.padding(top = reservedTop)
            )
            content == null && state.error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = reservedTop + Spacing.lg, start = Spacing.lg, end = Spacing.lg)
            ) {
                ErrorBanner(error = state.error, onRetry = onRetry)
            }
            content == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            else -> {
                val assets = content.assets.orEmpty()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    contentPadding = PaddingValues(
                        top = reservedTop + Spacing.sm,
                        bottom = Spacing.xl,
                        start = 2.dp,
                        end = 2.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SharedLinkHeader(
                            name = content.album?.name.orEmpty(),
                            description = content.album?.description,
                            count = content.album?.assetCount ?: assets.size,
                            foreignHost = if (state.isForeignServer)
                                runCatching { Url(state.serverBaseUrl).host }.getOrNull()
                            else null
                        )
                    }
                    if (assets.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(Res.string.shared_link_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Spacing.lg)
                            )
                        }
                    }
                    items(assets, key = { it.id }) { asset ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (asset.isVideo) uriHandler.openUri(absolute(asset.contentUrl))
                                    else openedAsset = asset
                                }
                        ) {
                            AsyncImage(
                                model = absolute(asset.thumbnailUrl),
                                contentDescription = asset.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (asset.isVideo) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(Spacing.xs)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        SubscreenFloatingChrome(
            title = content?.album?.name?.takeIf { it.isNotBlank() }
                ?: stringResource(Res.string.shared_link_title),
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                isScrollInProgress = { gridState.isScrollInProgress },
                scrollToTopMinIndex = 30,
                onScrollToTop = { gridState.animateScrollToItem(0) }
            ),
            hazeState = hazeState
        )

        openedAsset?.let { asset ->
            // Atrás cierra la foto antes que el enlace.
            PlatformBackHandler(enabled = true) { openedAsset = null }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { openedAsset = null }
            ) {
                AsyncImage(
                    model = absolute(asset.contentUrl),
                    contentDescription = asset.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { openedAsset = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding()
                        .padding(Spacing.sm)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.action_close),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedLinkHeader(
    name: String,
    description: String?,
    count: Int,
    foreignHost: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (name.isNotBlank()) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
        }
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                pluralStringResource(Res.plurals.album_hero_photos, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (foreignHost != null) {
            Text(
                stringResource(Res.string.shared_link_server, foreignHost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PasswordGate(
    wrongPassword: Boolean,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                stringResource(Res.string.shared_link_password_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(Res.string.shared_link_password_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.shared_link_password_label)) },
                singleLine = true,
                isError = wrongPassword,
                supportingText = if (wrongPassword) {
                    { Text(stringResource(Res.string.shared_link_password_wrong)) }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit(password) }),
                modifier = Modifier.fillMaxWidth()
            )
            PrimaryActionButton(
                label = stringResource(Res.string.shared_link_password_submit),
                onClick = { onSubmit(password) },
                enabled = password.isNotEmpty() && !isLoading,
                isLoading = isLoading
            )
        }
    }
}
