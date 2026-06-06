package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApiClient
import com.photonne.app.data.api.buildPhotonneHttpClient
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.auth.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class BucketStubTokenStorage : TokenStorage {
    override fun getAccessToken(): String? = "token"
    override fun getRefreshToken(): String? = "refresh"
    override fun getDeviceId(): String = "device-1"
    override fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override fun clear() = Unit
}

class TimelineBucketStoreTest {

    private val skeletonJson = """
        [
          { "key": "2026-02", "count": 2 },
          { "key": "2026-01", "count": 1 },
          { "key": "2025-12", "count": 1 }
        ]
    """.trimIndent()

    private fun itemJson(id: String, capturedAt: String) = """
        {
          "id": "$id",
          "fileName": "$id.jpg",
          "fullPath": "/photos/$id.jpg",
          "fileSize": 10,
          "fileCreatedAt": "$capturedAt",
          "fileModifiedAt": "$capturedAt",
          "extension": ".jpg",
          "scannedAt": "$capturedAt",
          "type": "IMAGE",
          "isFavorite": false,
          "isArchived": false,
          "isFileMissing": false,
          "isReadOnly": false
        }
    """.trimIndent()

    private val bucketBodies = mapOf(
        "2026-02" to "[${itemJson("feb-1", "2026-02-20T10:00:00Z")},${itemJson("feb-2", "2026-02-10T10:00:00Z")}]",
        "2026-01" to "[${itemJson("jan-1", "2026-01-15T10:00:00Z")}]",
        "2025-12" to "[${itemJson("dec-1", "2025-12-25T10:00:00Z")}]"
    )

    private val yearsJson = """
        [
          { "year": 2026, "count": 3, "items": [${itemJson("feb-1", "2026-02-20T10:00:00Z")}] },
          { "year": 2025, "count": 1, "items": [${itemJson("dec-1", "2025-12-25T10:00:00Z")}] }
        ]
    """.trimIndent()

    private val gridIndexJson = """
        [
          {
            "yearMonth": "2026-02",
            "items": [
              { "id": "feb-1", "type": "Image", "aspectRatio": 1.5, "date": "2026-02-20",
                "dominantColor": "#aabbcc", "width": 3000, "height": 2000, "isReadOnly": false },
              { "id": "feb-2", "type": "Video", "aspectRatio": 1.0, "date": "2026-02-10",
                "width": 1000, "height": 1000, "isReadOnly": false }
            ]
          },
          { "yearMonth": "2026-01", "items": [
              { "id": "jan-1", "type": "Image", "aspectRatio": 1.0, "date": "2026-01-15" }
          ] }
        ]
    """.trimIndent()

    /**
     * Routes /timeline/buckets to the skeleton, /timeline/buckets/{key} to
     * that month's content and /timeline/years to the year summaries,
     * recording every request path (years requests include the sample size).
     */
    private fun buildStore(
        maxLoadedBuckets: Int = TimelineBucketStore.DEFAULT_MAX_LOADED_BUCKETS,
        requests: MutableList<String> = mutableListOf()
    ): Pair<TimelineBucketStore, MutableList<String>> {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requests += if (path.endsWith("/timeline/years")) {
                "$path?sample=${request.url.parameters["sample"]}"
            } else path
            val body = when {
                path.endsWith("/timeline/buckets") -> skeletonJson
                path.endsWith("/timeline/years") -> yearsJson
                path.endsWith("/timeline/grid") -> gridIndexJson
                else -> {
                    val key = path.substringAfterLast('/')
                    bucketBodies[key] ?: error("Unexpected bucket request: $path")
                }
            }
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = BucketStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val store = TimelineBucketStore(
            api = PhotonneApiClient(client, "http://test.local"),
            maxLoadedBuckets = maxLoadedBuckets
        )
        return store to requests
    }

    @Test
    fun refresh_parses_skeleton_with_counts_but_no_items() = runTest {
        val (store, _) = buildStore()

        store.refresh()

        val buckets = store.buckets.value
        assertEquals(listOf("2026-02", "2026-01", "2025-12"), buckets.map { it.key })
        assertEquals(listOf(2, 1, 1), buckets.map { it.count })
        assertTrue(buckets.none { it.isLoaded })
    }

    @Test
    fun ensureLoaded_fetches_content_once_and_caches_it() = runTest {
        val (store, requests) = buildStore()
        store.refresh()

        store.ensureLoaded(listOf("2026-02"))
        store.ensureLoaded(listOf("2026-02"))

        val bucket = store.buckets.value.first { it.key == "2026-02" }
        assertEquals(listOf("feb-1", "feb-2"), bucket.items?.map { it.id })
        assertFalse(bucket.isLoading)
        assertEquals(1, requests.count { it.endsWith("/buckets/2026-02") })
    }

    @Test
    fun ensureLoaded_dedups_concurrent_requests_for_the_same_key() = runTest {
        val (store, requests) = buildStore()
        store.refresh()

        listOf(
            async { store.ensureLoaded(listOf("2026-02", "2026-01")) },
            async { store.ensureLoaded(listOf("2026-02")) }
        ).awaitAll()

        assertEquals(1, requests.count { it.endsWith("/buckets/2026-02") })
        assertEquals(1, requests.count { it.endsWith("/buckets/2026-01") })
    }

    @Test
    fun ensureLoaded_ignores_unknown_keys() = runTest {
        val (store, requests) = buildStore()
        store.refresh()

        store.ensureLoaded(listOf("1999-01"))

        assertTrue(requests.none { it.endsWith("/buckets/1999-01") })
    }

    @Test
    fun eviction_drops_least_recently_touched_content_but_keeps_counts() = runTest {
        val (store, _) = buildStore(maxLoadedBuckets = 2)
        store.refresh()

        store.ensureLoaded(listOf("2026-02"))
        store.ensureLoaded(listOf("2026-01"))
        store.ensureLoaded(listOf("2025-12"))

        val buckets = store.buckets.value
        val evicted = buckets.first { it.key == "2026-02" }
        assertNull(evicted.items)
        assertEquals(2, evicted.count) // count survives eviction
        assertTrue(buckets.first { it.key == "2026-01" }.isLoaded)
        assertTrue(buckets.first { it.key == "2025-12" }.isLoaded)
    }

    @Test
    fun ensureLoaded_refreshes_recency_so_visible_buckets_survive_eviction() = runTest {
        val (store, _) = buildStore(maxLoadedBuckets = 2)
        store.refresh()

        store.ensureLoaded(listOf("2026-02"))
        store.ensureLoaded(listOf("2026-01"))
        // Touch February again — January becomes the LRU candidate.
        store.ensureLoaded(listOf("2026-02"))
        store.ensureLoaded(listOf("2025-12"))

        val buckets = store.buckets.value
        assertTrue(buckets.first { it.key == "2026-02" }.isLoaded)
        assertNull(buckets.first { it.key == "2026-01" }.items)
    }

    @Test
    fun removeItem_drops_the_asset_and_decrements_count() = runTest {
        val (store, _) = buildStore()
        store.refresh()
        store.ensureLoaded(listOf("2026-02"))

        store.removeItem("feb-1")

        val bucket = store.buckets.value.first { it.key == "2026-02" }
        assertEquals(listOf("feb-2"), bucket.items?.map { it.id })
        assertEquals(1, bucket.count)
    }

    @Test
    fun updateItem_transforms_the_asset_in_place() = runTest {
        val (store, _) = buildStore()
        store.refresh()
        store.ensureLoaded(listOf("2026-02"))

        store.updateItem("feb-2") { it.copy(isFavorite = true) }

        val bucket = store.buckets.value.first { it.key == "2026-02" }
        assertTrue(bucket.items!!.first { it.id == "feb-2" }.isFavorite)
        assertFalse(bucket.items!!.first { it.id == "feb-1" }.isFavorite)
    }

    @Test
    fun refresh_drops_loaded_content() = runTest {
        val (store, _) = buildStore()
        store.refresh()
        store.ensureLoaded(listOf("2026-02"))

        store.refresh()

        assertTrue(store.buckets.value.none { it.isLoaded })
    }

    @Test
    fun yearSummaries_fetch_once_and_reuse_for_equal_or_smaller_samples() = runTest {
        val (store, requests) = buildStore()
        store.refresh()

        store.ensureYearSummaries(40)
        store.ensureYearSummaries(40)
        store.ensureYearSummaries(25) // smaller — cache covers it

        val summaries = store.yearSummaries.value
        assertEquals(listOf(2026, 2025), summaries?.map { it.year })
        assertEquals(listOf(3, 1), summaries?.map { it.count })
        assertEquals(1, requests.count { it.contains("/timeline/years") })
        assertTrue(requests.any { it.endsWith("/timeline/years?sample=40") })
    }

    @Test
    fun yearSummaries_refetch_when_a_larger_sample_is_needed() = runTest {
        val (store, requests) = buildStore()
        store.refresh()

        store.ensureYearSummaries(20)
        store.ensureYearSummaries(60)

        assertEquals(2, requests.count { it.contains("/timeline/years") })
        assertTrue(requests.any { it.endsWith("/timeline/years?sample=60") })
    }

    @Test
    fun gridIndex_fetches_once_and_refresh_clears_it() = runTest {
        val (store, requests) = buildStore()
        store.refresh()

        store.ensureGridIndex()
        store.ensureGridIndex()

        val index = store.gridIndex.value
        assertEquals(setOf("2026-02", "2026-01"), index?.keys)
        assertEquals(listOf("feb-1", "feb-2"), index?.get("2026-02")?.map { it.id })
        assertEquals(1, requests.count { it.endsWith("/timeline/grid") })

        store.refresh()
        assertNull(store.gridIndex.value)
    }

    @Test
    fun refresh_clears_yearSummaries() = runTest {
        val (store, _) = buildStore()
        store.refresh()
        store.ensureYearSummaries(20)

        store.refresh()

        assertNull(store.yearSummaries.value)
    }

    @Test
    fun failed_fetch_clears_isLoading_and_allows_retry() = runTest {
        var failNext = true
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requests += path
            when {
                path.endsWith("/timeline/buckets") -> respond(
                    content = ByteReadChannel(skeletonJson),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                failNext -> {
                    failNext = false
                    respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
                }
                else -> respond(
                    content = ByteReadChannel(bucketBodies.getValue(path.substringAfterLast('/'))),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = buildPhotonneHttpClient(
            engine = engine,
            baseUrl = "http://test.local",
            tokenStorage = BucketStubTokenStorage(),
            authState = AuthStateHolder()
        )
        val store = TimelineBucketStore(api = PhotonneApiClient(client, "http://test.local"))
        store.refresh()

        assertFailsWith<Exception> { store.ensureLoaded(listOf("2026-02")) }
        val afterFailure = store.buckets.value.first { it.key == "2026-02" }
        assertFalse(afterFailure.isLoading)
        assertNull(afterFailure.items)

        store.ensureLoaded(listOf("2026-02"))
        assertTrue(store.buckets.value.first { it.key == "2026-02" }.isLoaded)
    }
}
