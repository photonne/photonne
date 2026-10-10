package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A controllable, observable handle on a single video, decoupled from any
 * rendering surface or transport chrome. The AssetDetail viewer drives playback
 * from its own Compose controls (a floating play/pause + time readout and the
 * bottom strip turned into a scrubber), so it needs the raw state — position,
 * duration, playing — and the raw commands, not a self-contained native player.
 *
 * All properties are Compose snapshot state: reading them in a composable makes
 * it recompose as playback advances. Created with [rememberVideoPlayback] and
 * painted with [VideoSurface]; the same instance feeds both the surface and the
 * controls so a single tap or scrub stays in sync everywhere.
 */
interface VideoPlayback {
    /** True once the clip is buffered enough to show its first frame. */
    val isReady: Boolean

    /** True while the clip is actively advancing (not paused, not stalled). */
    val isPlaying: Boolean

    /** Current playhead position in milliseconds. */
    val positionMs: Long

    /** Total clip length in milliseconds, or 0 until known. */
    val durationMs: Long

    fun play()

    fun pause()

    fun togglePlay()

    /** Seek to [ms], clamped to the clip bounds by the implementation. */
    fun seekTo(ms: Long)
}

/**
 * Creates (and ties to composition) a [VideoPlayback] for [url] with [headers].
 * The player is prepared eagerly and, when [autoPlay] is true, starts as soon as
 * it's ready — matching the native gallery, where landing on a video plays it
 * without a tap. Released automatically when it leaves composition, and paused
 * when the app is backgrounded.
 */
@Composable
expect fun rememberVideoPlayback(
    url: String,
    headers: Map<String, String>,
    autoPlay: Boolean = true
): VideoPlayback

/**
 * Renders [playback]'s video frames with no transport chrome of its own — just
 * the picture. Aspect-fit by default (portrait clips fill, landscape clips
 * letterbox); [fillCrop] zoom-fills instead. The surface adds no gestures, so
 * taps and horizontal swipes fall through to the Compose viewer (chrome toggle
 * and pager paging).
 */
@Composable
expect fun VideoSurface(
    playback: VideoPlayback,
    modifier: Modifier = Modifier,
    fillCrop: Boolean = false
)

/**
 * Whether [VideoSurface] paints OVER all Compose content and so must be inset to
 * avoid covering the viewer chrome.
 *
 * iOS = true: the surface is an AVPlayerLayer, a hardware layer that only renders
 * when composited natively on top of the Compose scene — Compose can't draw over
 * it. So the caller shrinks the surface to sit between the top bar and the bottom
 * controls (full-screen only once the chrome is hidden).
 *
 * Android = false: the player view composes in z-order like any view, so the
 * controls can float over a full-bleed surface.
 */
expect val videoSurfaceRendersOnTop: Boolean
