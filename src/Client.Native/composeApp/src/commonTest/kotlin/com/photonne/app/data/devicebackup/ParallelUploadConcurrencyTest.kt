package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.UploadAssetResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the adaptive upload fan-out: photos and videos draw from separate
 * pools, so a wide photo cap never lets more than [UploadConcurrency.video]
 * large videos stream at once (the OOM guard), while Turbo can raise both.
 *
 * Peak concurrency is measured by counting how many `upload` calls are in
 * flight simultaneously per media kind. `uploadInParallel` dispatches on
 * `Dispatchers.Default` (real threads), so the counters are guarded by a Mutex;
 * `delay` suspends without holding a thread, so all permit-holders overlap and
 * the observed peak equals the pool size.
 */
class ParallelUploadConcurrencyTest {

    private fun media(index: Int, type: DeviceMediaType) = DeviceMedia(
        uri = "uri://$index",
        displayName = "file$index",
        relativePath = "",
        mimeType = if (type == DeviceMediaType.Video) "video/mp4" else "image/jpeg",
        sizeBytes = 1_000,
        dateModifiedMillis = 0,
        type = type
    )

    /** Tracks current/peak in-flight counts for one media kind, thread-safely. */
    private class PeakMeter {
        val lock = Mutex()
        var current = 0
        var peak = 0
        suspend fun enter() = lock.withLock {
            current++
            if (current > peak) peak = current
        }
        suspend fun exit() = lock.withLock { current-- }
    }

    private suspend fun run(
        photos: Int,
        videos: Int,
        concurrency: UploadConcurrency
    ): Pair<Int, Int> {
        val photoMeter = PeakMeter()
        val videoMeter = PeakMeter()
        val pending =
            (0 until photos).map { media(it, DeviceMediaType.Image) } +
                (0 until videos).map { media(1000 + it, DeviceMediaType.Video) }

        uploadInParallel(
            pending = pending,
            concurrency = concurrency,
            upload = { media, _ ->
                val meter = if (media.type == DeviceMediaType.Video) videoMeter else photoMeter
                meter.enter()
                // Long enough that every permit-holder overlaps in flight.
                delay(60)
                meter.exit()
                UploadAssetResponse(message = "ok", assetId = media.uri)
            },
            onItemDone = { _, _, _ -> }
        )
        return photoMeter.peak to videoMeter.peak
    }

    @Test
    fun videosStayTightWhilePhotosFanOut() = runTest {
        val (photoPeak, videoPeak) = run(
            photos = 12,
            videos = 6,
            concurrency = UploadConcurrency(photo = 4, video = 2)
        )
        // Never exceed the pool — the core semaphore guarantee. The video bound
        // is the OOM guard that must hold no matter how wide photos go.
        assertTrue(photoPeak <= 4, "photo peak $photoPeak exceeded its pool of 4")
        assertTrue(videoPeak <= 2, "video peak $videoPeak exceeded its pool of 2")
        // …and actually use the pools (not over-serialized like the old cap of 3).
        assertEquals(4, photoPeak, "photos should fan out to the full pool")
        assertEquals(2, videoPeak, "videos should fan out to the full pool")
    }

    @Test
    fun turboWidensThePhotoPool() = runTest {
        val (photoPeak, _) = run(
            photos = 12,
            videos = 0,
            concurrency = UploadConcurrency(photo = 8, video = 3)
        )
        assertTrue(photoPeak <= 8, "photo peak $photoPeak exceeded the turbo pool of 8")
        assertEquals(8, photoPeak, "turbo should let more photos upload at once")
    }

    @Test
    fun emptyInputIsANoop() = runTest {
        var calls = 0
        uploadInParallel(
            pending = emptyList(),
            concurrency = UploadConcurrency(photo = 6, video = 2),
            upload = { media, _ -> calls++; UploadAssetResponse(assetId = media.uri) },
            onItemDone = { _, _, _ -> }
        )
        assertEquals(0, calls)
    }
}
