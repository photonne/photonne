package com.photonne.app.ui.asset

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.photonne.app.resources.Res
import com.photonne.app.resources.error_banner_retry
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val DOUBLE_TAP_ANIM_MS = com.photonne.app.ui.theme.MotionDurations.EMPHASIS_MS

/**
 * Image that supports pinch-to-zoom, pan and double-tap to toggle
 * between 1x and 2.5x. While [scale] is greater than 1 the parent
 * pager should disable user scroll so horizontal pan is not
 * intercepted — the caller observes the scale via [onScaleChange].
 *
 * - El doble toque anima (antes saltaba de golpe) y el pellizco se ancla al
 *   centroide de los dedos, no al centro de la caja.
 * - El arrastre se limita a la IMAGEN encajada (con `ContentScale.Fit` la foto
 *   no llena la caja y antes se podía arrastrar hasta sacarla de pantalla).
 * - [placeholderCacheKey] pinta la miniatura ya cacheada mientras carga la
 *   grande (sin fogonazo negro), con spinner discreto y estado de error con
 *   reintento.
 */
@Composable
fun ZoomablePagerImage(
    model: Any?,
    contentDescription: String?,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    onTap: (() -> Unit)? = null,
    /** Incrementar para animar el zoom de vuelta a 1x (capa del botón atrás). */
    resetZoomTick: Int = 0,
    /** Clave de memoria de Coil de una versión menor ya cargada (p. ej. la Small). */
    placeholderCacheKey: String? = null,
    /** Clave de memoria para ESTA imagen, para que otras superficies la reutilicen. */
    memoryCacheKey: String? = null,
    // Reports the live scale + pan so an overlay (e.g. a Live Photo clip drawn
    // on top of the still) can mirror the exact same transform.
    onTransformChange: ((scale: Float, offset: Offset) -> Unit)? = null
) {
    val scale = remember { Animatable(MIN_SCALE) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Tamaño intrínseco de la imagen cargada, para acotar el pan a la foto.
    var intrinsic by remember(model) { mutableStateOf<Size?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resetZoomTick) {
        if (resetZoomTick > 0 && scale.value > 1f) {
            launch { scale.animateTo(MIN_SCALE, tween(DOUBLE_TAP_ANIM_MS)) }
            launch { offset.animateTo(Offset.Zero, tween(DOUBLE_TAP_ANIM_MS)) }
        }
    }

    LaunchedEffect(scale.value) { onScaleChange(scale.value) }
    LaunchedEffect(scale.value, offset.value) {
        onTransformChange?.invoke(scale.value, offset.value)
    }

    // Límite del desplazamiento: el borde de la imagen ENCAJADA, no el de la
    // caja. Con Crop (o sin intrínseco aún) la imagen llena la caja y ambos
    // coinciden.
    fun clampOffset(target: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || size == IntSize.Zero) return Offset.Zero
        val content = intrinsic
        val (contentW, contentH) = if (contentScale == ContentScale.Fit &&
            content != null && content.width > 0f && content.height > 0f
        ) {
            val fit = min(size.width / content.width, size.height / content.height)
            content.width * fit to content.height * fit
        } else {
            size.width.toFloat() to size.height.toFloat()
        }
        val maxX = ((contentW * currentScale - size.width) / 2f).coerceAtLeast(0f)
        val maxY = ((contentH * currentScale - size.height) / 2f).coerceAtLeast(0f)
        return Offset(
            x = target.x.coerceIn(-maxX, maxX),
            y = target.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(onTap, zoomEnabled) {
                detectTapGestures(
                    onTap = onTap?.let { { _ -> it() } },
                    // Double-tap zoom only when zooming is enabled (it's disabled
                    // while the asset is acting as the info header/left pane).
                    onDoubleTap = if (!zoomEnabled) null else { tap ->
                        scope.launch {
                            if (scale.value > 1f) {
                                launch { scale.animateTo(MIN_SCALE, tween(DOUBLE_TAP_ANIM_MS)) }
                                launch { offset.animateTo(Offset.Zero, tween(DOUBLE_TAP_ANIM_MS)) }
                            } else {
                                // Zoom toward the tapped point (not the centre):
                                // shift the content so the pixel under the finger
                                // stays put. For centre-anchored scaling, the
                                // required translation is (centre - tap) * (s - 1).
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val target = DOUBLE_TAP_SCALE
                                val targetOffset =
                                    clampOffset((center - tap) * (target - 1f), target)
                                launch { scale.animateTo(target, tween(DOUBLE_TAP_ANIM_MS)) }
                                launch { offset.animateTo(targetOffset, tween(DOUBLE_TAP_ANIM_MS)) }
                            }
                        }
                    }
                )
            }
            .then(
                if (zoomEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectPinchAndPanGestures(
                            isZoomed = { scale.value > 1f }
                        ) { centroid, pan, zoom ->
                            val oldScale = scale.value
                            val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            val k = if (oldScale == 0f) 1f else newScale / oldScale
                            val center = Offset(size.width / 2f, size.height / 2f)
                            // Mantiene fijo el punto bajo el centroide: para la
                            // transformación centrada p = (q-centro)·s + centro + t,
                            // t' = (c-centro) − (c−centro−t)·k, y luego el pan.
                            val anchored = (centroid - center) -
                                ((centroid - center - offset.value) * k) + pan
                            val newOffset =
                                if (newScale > 1f) clampOffset(anchored, newScale) else Offset.Zero
                            scope.launch {
                                scale.snapTo(newScale)
                                offset.snapTo(newOffset)
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // retryKey fuerza una petición nueva tras un fallo.
        var retryKey by remember(model) { mutableIntStateOf(0) }
        var painterState by remember(model) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }
        val platformContext = LocalPlatformContext.current
        val request = remember(model, retryKey) {
            ImageRequest.Builder(platformContext)
                .data(model)
                .apply {
                    memoryCacheKey?.let { memoryCacheKey(it) }
                    placeholderCacheKey?.let { placeholderMemoryCacheKey(it) }
                }
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onState = { state ->
                painterState = state
                if (state is AsyncImagePainter.State.Success) {
                    intrinsic = state.painter.intrinsicSize
                        .takeIf { it.width > 0f && it.height > 0f }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                }
        )
        when (painterState) {
            is AsyncImagePainter.State.Loading ->
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                )
            is AsyncImagePainter.State.Error ->
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp).padding(bottom = 4.dp)
                    )
                    TextButton(onClick = { retryKey++ }) {
                        Text(
                            stringResource(Res.string.error_banner_retry),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            else -> Unit
        }
    }
}

// Like detectTransformGestures, but doesn't claim single-finger pan while at
// the resting scale — that lets the parent HorizontalPager keep handling
// horizontal swipes between assets. We only take over once the user pinches
// or once we're already zoomed in.
private suspend fun PointerInputScope.detectPinchAndPanGestures(
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (canceled) break

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            val centroid = event.calculateCentroid(useCurrent = true)
            val pointerCount = event.changes.count { it.pressed }

            if (!pastTouchSlop) {
                zoom *= zoomChange
                pan += panChange

                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1 - zoom) * centroidSize
                val panMotion = pan.getDistance()

                val activate = zoomMotion > touchSlop ||
                    (pointerCount > 1 && panMotion > touchSlop) ||
                    (isZoomed() && panMotion > touchSlop)
                if (activate) pastTouchSlop = true
            }

            if (pastTouchSlop) {
                if ((zoomChange != 1f || panChange != Offset.Zero) &&
                    centroid != Offset.Unspecified
                ) {
                    onGesture(centroid, panChange, zoomChange)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
