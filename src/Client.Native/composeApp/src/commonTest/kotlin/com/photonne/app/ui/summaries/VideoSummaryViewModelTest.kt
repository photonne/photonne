package com.photonne.app.ui.summaries

import com.photonne.app.data.models.VideoSummary
import com.photonne.app.ui.TrackedViewModels
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun summaryJson(
    id: String,
    year: Int,
    status: String = "completed",
    durationMinutes: Int = 5,
    includePhotos: Boolean = false,
    orientation: String = "all",
) = """{"id":"$id","source":"year","year":$year,"albumId":null,"title":"Resumen $year",""" +
    """"includePhotos":$includePhotos,"orientation":"$orientation","durationMinutes":$durationMinutes,""" +
    """"showTitles":true,"musicId":null,"musicName":null,"review":false,"hasPlan":true,""" +
    """"status":"$status","progress":0.4,"error":null,"assetId":null,"clipCount":3,""" +
    """"requestedAt":"2026-10-18T10:00:00Z","completedAt":null}"""

private fun HttpRequestData.bodyJson(): JsonObject {
    val text = when (val content = body) {
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> "{}"
    }
    return Json.parseToJsonElement(text).jsonObject
}

@OptIn(ExperimentalCoroutinesApi::class)
class VideoSummaryViewModelTest {

    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a year summary starts on the latest year with the options of its last request`() = vms.runTest {
        val api = testApi { request ->
            when (request.url.encodedPath) {
                "/api/video-summaries/years" -> json("""[{"year":2026,"videos":42,"photos":9},{"year":2025,"videos":1,"photos":3}]""")
                "/api/video-summaries" -> json("[${summaryJson("s1", 2026, durationMinutes = 8, orientation = "portrait")}]")
                "/api/settings" -> json("""{"key":"VideoSummarySettings.AutoYear","value":"true"}""")
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }
        val vm = vms.track(VideoSummaryViewModel(api, testErrorFactory(api)))

        vm.open(album = null)
        val state = vm.state.first { !it.isLoading }

        assertEquals(SummarySource.Year(2026), state.source)
        assertEquals(listOf(2026, 2025), state.years.map { it.year })
        assertEquals(8, state.options.durationMinutes)
        assertEquals(SummaryOrientation.Portrait, state.options.orientation)
        assertEquals(true, state.autoYear)

        // A year never asked for starts from the defaults.
        vm.selectYear(2025)
        assertEquals(SummaryOptions(), vm.state.value.options)
    }

    @Test
    fun `asking sends the chosen options and follows the summary`() = vms.runTest {
        var sent: JsonObject? = null
        val api = testApi { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath == "/api/video-summaries" -> {
                    sent = request.bodyJson()
                    json(summaryJson("s2", 2026, status = "pending"), HttpStatusCode.Accepted)
                }
                request.url.encodedPath == "/api/video-summaries/years" -> json("""[{"year":2026,"videos":42,"photos":9}]""")
                request.url.encodedPath == "/api/video-summaries" -> json("[]")
                else -> json("""{"key":"VideoSummarySettings.AutoYear","value":""}""")
            }
        }
        val vm = vms.track(VideoSummaryViewModel(api, testErrorFactory(api)))
        vm.open(album = null)
        vm.state.first { !it.isLoading }

        vm.updateOptions { it.copy(includePhotos = true, durationMinutes = 3, review = true) }
        vm.submit()
        val state = vm.state.first { it.requested != null }

        val body = sent!!
        assertEquals("year", body["source"]!!.jsonPrimitive.content)
        assertEquals(2026, body["year"]!!.jsonPrimitive.int)
        assertTrue(body["includePhotos"]!!.jsonPrimitive.boolean)
        assertEquals(3, body["durationMinutes"]!!.jsonPrimitive.int)
        assertTrue(body["review"]!!.jsonPrimitive.boolean)
        assertEquals("Resumen 2026", state.requested)
        assertTrue(state.active)
        assertFalse(state.canSubmit)
        assertEquals(false, state.autoYear)
    }

    @Test
    fun `the server's reason is shown when there is not enough material`() = vms.runTest {
        val api = testApi { request ->
            when {
                request.method == HttpMethod.Post -> json(
                    """{"error":"No hay suficientes vídeos para montar un resumen. Prueba a incluir las fotos.","code":"not_enough_media"}""",
                    HttpStatusCode.BadRequest
                )
                else -> json("[]")
            }
        }
        val vm = vms.track(VideoSummaryViewModel(api, testErrorFactory(api)))
        vm.open(album = SummarySource.Album("album-1", "Vacaciones"))
        vm.state.first { !it.isLoading }
        assertFalse(vm.state.value.pickYear)
        assertNull(vm.state.value.autoYear)

        vm.submit()
        val state = vm.state.first { it.error != null }

        assertEquals("No hay suficientes vídeos para montar un resumen. Prueba a incluir las fotos.", state.error)
        assertFalse(state.isSubmitting)
    }
}

class VideoSummaryOptionsTest {

    private fun summary(status: String = "completed", progress: Double = 1.0) = VideoSummary(
        id = "s", source = "album", albumId = "a1", title = "Resumen", includePhotos = true,
        orientation = "diagonal", durationMinutes = 42, showTitles = false,
        musicId = "m1", musicName = "Verano", review = true, status = status, progress = progress,
    )

    @Test
    fun `options start from an earlier request and fall back for what they do not know`() {
        assertEquals(
            SummaryOptions(
                includePhotos = true,
                orientation = SummaryOrientation.All,
                durationMinutes = SummaryOptions.DEFAULT_MINUTES,
                showTitles = false,
                music = SummaryMusic("m1", "Verano"),
                review = true,
            ),
            SummaryOptions.of(summary())
        )
    }

    @Test
    fun `a summary is found by its year or album, and only pending or running is active`() {
        val list = listOf(summary())
        assertEquals("s", list.forSource(SummarySource.Album("a1", "x"))?.id)
        assertNull(list.forSource(SummarySource.Year(2024)))
        assertTrue(summary("pending").isActive)
        assertTrue(summary("running").isActive)
        assertFalse(summary("planned").isActive)
        assertEquals(46, summary("running", 0.456).percent)
        assertEquals(100, summary("running", 1.4).percent)
    }

    @Test
    fun `the request carries the year or the album, never both`() {
        val options = SummaryOptions(orientation = SummaryOrientation.Landscape, music = SummaryMusic("m1", "Verano"))
        val year = requestBody(SummarySource.Year(2024), options)
        assertEquals("year", year.source)
        assertEquals(2024, year.year)
        assertNull(year.albumId)
        assertEquals("landscape", year.orientation)
        assertEquals("m1", year.musicId)
        val album = requestBody(SummarySource.Album("a1", "Vacaciones"), SummaryOptions())
        assertEquals("album", album.source)
        assertNull(album.year)
        assertEquals("a1", album.albumId)
        assertFalse(album.review)
    }
}
