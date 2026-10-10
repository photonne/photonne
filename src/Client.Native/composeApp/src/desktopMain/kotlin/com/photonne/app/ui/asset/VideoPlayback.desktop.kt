package com.photonne.app.ui.asset

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

// El render va por callback a un bitmap pintado por Compose, así que el vídeo
// se compone en z-order como cualquier otra capa y los controles flotan encima.
actual val videoSurfaceRendersOnTop: Boolean = false

/**
 * libvlc compartido de toda la app: descubrir la instalación de VLC y crear la
 * factory son operaciones nativas caras que solo deben pasar una vez. Si no hay
 * VLC instalado [available] queda false y nada de este fichero llega a tocarse
 * (isVideoPlaybackSupported gatea todos los montajes en el código común).
 */
internal object VlcEngine {
    val available: Boolean by lazy {
        runCatching { NativeDiscovery().discover() }.getOrDefault(false)
    }

    val factory: MediaPlayerFactory by lazy { MediaPlayerFactory() }
}

/**
 * libvlc no puede enviar cabeceras HTTP arbitrarias, así que el Bearer del mapa
 * de cabeceras viaja como `?access_token=` — el servidor lo acepta por query
 * solo en las rutas de streaming de medios. Los medios locales van tal cual.
 */
internal fun authorizedMediaUrl(url: String, headers: Map<String, String>): String {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return url
    val token = headers["Authorization"]?.removePrefix("Bearer ")?.trim().orEmpty()
    if (token.isEmpty()) return url
    val separator = if ('?' in url) '&' else '?'
    return "$url${separator}access_token=$token"
}

/**
 * [VideoPlayback] sobre un EmbeddedMediaPlayer de vlcj con superficie de
 * callback: libvlc decodifica a RV32 (BGRA little-endian) y cada frame se copia
 * a uno de dos Bitmaps de Skia alternados — la alternancia hace que cada frame
 * sea un valor de estado distinto, y ese cambio invalida el draw del [VideoSurface]
 * sin recomposición. Los eventos de vlcj llegan en hilos nativos; escribir
 * snapshot state desde ellos es seguro, pero cualquier llamada de vuelta a
 * libvlc desde un callback debe ir por `submit`.
 */
internal class DesktopVideoPlayback(
    private val url: String,
    autoPlay: Boolean,
    private val muted: Boolean,
    loop: Boolean,
    private val onFinished: () -> Unit
) : VideoPlayback {
    override var isReady by mutableStateOf(false)
        private set
    override var isPlaying by mutableStateOf(false)
        private set
    override var positionMs by mutableStateOf(0L)
        private set
    override var durationMs by mutableStateOf(0L)
        private set

    /** Último frame decodificado; null hasta que llega el primero. */
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set

    private val player: EmbeddedMediaPlayer = VlcEngine.factory.mediaPlayers().newEmbeddedMediaPlayer()

    // Doble búfer: mientras Skia pinta uno, el hilo de render de libvlc escribe
    // el otro. Se (re)crean al conocerse las dimensiones del clip.
    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null
    private var useFront = true
    private var pixels = ByteArray(0)
    private var imageInfo: ImageInfo? = null

    private val bufferFormatCallback = object : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            pixels = ByteArray(sourceWidth * sourceHeight * 4)
            imageInfo = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            frontBitmap = null
            backBitmap = null
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }
    }

    private val renderCallback = object : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) = Unit

        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<ByteBuffer>,
            bufferFormat: BufferFormat,
            displayWidth: Int,
            displayHeight: Int
        ) {
            val info = imageInfo ?: return
            val buffer = nativeBuffers.firstOrNull() ?: return
            buffer.rewind()
            if (buffer.remaining() < pixels.size) return
            buffer.get(pixels, 0, pixels.size)
            val target = if (useFront) {
                frontBitmap ?: Bitmap().also { frontBitmap = it }
            } else {
                backBitmap ?: Bitmap().also { backBitmap = it }
            }
            useFront = !useFront
            if (target.installPixels(info, pixels, info.width * 4)) {
                frame = target.asComposeImageBitmap()
            }
        }

        override fun unlock(mediaPlayer: MediaPlayer) = Unit
    }

    private val listener = object : MediaPlayerEventAdapter() {
        override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
            isReady = true
            mediaPlayer.status().length().takeIf { it > 0 }?.let { durationMs = it }
            if (muted) {
                mediaPlayer.submit {
                    mediaPlayer.audio().isMute = true
                }
            }
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            isPlaying = true
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            isPlaying = false
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            isPlaying = false
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            isPlaying = false
            onFinished()
        }

        override fun error(mediaPlayer: MediaPlayer) {
            isPlaying = false
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            positionMs = newTime.coerceAtLeast(0L)
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            if (newLength > 0) durationMs = newLength
        }
    }

    init {
        player.videoSurface().set(
            VlcEngine.factory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true)
        )
        player.events().addMediaPlayerEventListener(listener)
        if (muted) player.audio().isMute = true
        if (loop) player.controls().repeat = true
        if (autoPlay) {
            player.media().play(url)
        } else {
            player.media().startPaused(url)
        }
    }

    override fun play() {
        player.controls().play()
    }

    override fun pause() {
        player.controls().setPause(true)
    }

    override fun togglePlay() {
        if (isPlaying) pause() else play()
    }

    override fun seekTo(ms: Long) {
        val target = if (durationMs > 0) ms.coerceIn(0L, durationMs) else ms.coerceAtLeast(0L)
        player.controls().setTime(target)
        positionMs = target
    }

    fun release() {
        player.events().removeMediaPlayerEventListener(listener)
        player.release()
    }
}

@Composable
actual fun rememberVideoPlayback(
    url: String,
    headers: Map<String, String>,
    autoPlay: Boolean
): VideoPlayback {
    val playback = remember(url, headers) {
        DesktopVideoPlayback(
            url = authorizedMediaUrl(url, headers),
            autoPlay = autoPlay,
            muted = false,
            loop = false,
            onFinished = {}
        )
    }
    DisposableEffect(playback) {
        onDispose { playback.release() }
    }
    return playback
}

@Composable
actual fun VideoSurface(
    playback: VideoPlayback,
    modifier: Modifier,
    fillCrop: Boolean
) {
    val desktopPlayback = playback as DesktopVideoPlayback
    Canvas(modifier = modifier.clipToBounds()) {
        // Leer `frame` dentro del draw scope invalida solo el dibujado por frame.
        desktopPlayback.frame?.let { drawVideoFrame(it, fillCrop) }
    }
}

private fun DrawScope.drawVideoFrame(image: ImageBitmap, fillCrop: Boolean) {
    val imageWidth = image.width.toFloat()
    val imageHeight = image.height.toFloat()
    if (imageWidth <= 0f || imageHeight <= 0f || size.width <= 0f || size.height <= 0f) return
    val scale = if (fillCrop) {
        max(size.width / imageWidth, size.height / imageHeight)
    } else {
        min(size.width / imageWidth, size.height / imageHeight)
    }
    val dstWidth = (imageWidth * scale).roundToInt()
    val dstHeight = (imageHeight * scale).roundToInt()
    drawImage(
        image = image,
        dstOffset = IntOffset(
            ((size.width - dstWidth) / 2f).roundToInt(),
            ((size.height - dstHeight) / 2f).roundToInt()
        ),
        dstSize = IntSize(dstWidth, dstHeight),
        filterQuality = FilterQuality.Low
    )
}
