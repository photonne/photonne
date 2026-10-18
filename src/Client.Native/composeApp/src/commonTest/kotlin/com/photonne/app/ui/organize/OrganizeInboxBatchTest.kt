package com.photonne.app.ui.organize

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.models.OrganizeSuggestion
import com.photonne.app.data.organize.OrganizeRepository
import com.photonne.app.ui.TrackedViewModels
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import com.photonne.app.ui.timelineItemJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La vista de un lote en "Para organizar": el lote se ve solo y marcado, se
 * puede pasar a la bandeja entera con la misma selección, y cuando ya no queda
 * nada de él se vuelve a los lotes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrganizeInboxBatchTest {

    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun HttpRequestData.bodyText(): String = when (val content = body) {
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> ""
    }

    private fun idsIn(body: String) =
        Regex("\"([a-z])\"").findAll(body.substringAfter("assetIds")).map { it.groupValues[1] }.toList()

    private fun page(ids: List<String>, hasMore: Boolean) =
        """{"items":[${ids.joinToString(",") { timelineItemJson(it) }}],"hasMore":$hasMore,""" +
            """"nextCursor":${if (hasMore) "\"2026-01-01T00:00:00Z\"" else "null"}}"""

    /**
     * Bandeja en dos páginas: a,b y c,d,e. Pendientes del lote: d y e ("x" ya
     * no está en la bandeja, como en un lote que se ha quedado viejo).
     */
    private fun build(
        pending: MutableSet<String> = mutableSetOf("a", "b", "c", "d", "e"),
        inboxCursors: MutableList<String?> = mutableListOf(),
        // Un servidor anterior a /api/organize/inbox/items responde 404.
        itemsEndpoint: Boolean = true,
    ): OrganizeInboxViewModel {
        val api = testApi { request ->
            when (request.url.encodedPath) {
                "/api/organize/inbox" -> {
                    val cursor = request.url.parameters["cursor"]
                    inboxCursors += cursor
                    if (cursor == null) json(page(listOf("a", "b").filter { it in pending }, hasMore = true))
                    else json(page(listOf("c", "d", "e").filter { it in pending }, hasMore = false))
                }
                "/api/organize/inbox/items" ->
                    if (!itemsEndpoint) json("{}", HttpStatusCode.NotFound)
                    else json(page(idsIn(request.bodyText()).filter { it in pending }, hasMore = false))
                "/api/organize/exclude" -> {
                    pending -= idsIn(request.bodyText()).toSet()
                    json("{}")
                }
                "/api/organize/inbox/count" -> json("""{"count":${pending.size}}""")
                "/api/organize/suggestions" -> json("[]")
                else -> json("{}")
            }
        }
        return vms.track(
            OrganizeInboxViewModel(
                repository = OrganizeRepository(api),
                assetRepository = AssetDetailRepository(api),
                albumsRepository = AlbumsRepository(api),
                errorFactory = testErrorFactory(api),
            )
        )
    }

    private val batch = OrganizeSuggestion(
        kind = "trip", key = "trip:roma", title = "Roma", count = 3, assetIds = listOf("x", "d", "e"),
    )

    private suspend fun OrganizeInboxViewModel.loaded() {
        ensureLoaded()
        state.first { it.loaded && !it.isInitialLoading }
    }

    @Test
    fun a_batch_opens_alone_with_its_pending_photos_selected() = vms.runTest {
        val vm = build()
        vm.loaded()

        vm.selectSuggestion(batch)
        val state = vm.state.first { !it.isLoadingBatch }

        assertEquals(true, state.isBatchOnly)
        assertEquals(listOf("d", "e"), state.gridItems.map { it.id })
        // "x" ya no está pendiente: no puede contar como seleccionada.
        assertEquals(setOf("d", "e"), state.selection)
    }

    @Test
    fun the_whole_inbox_keeps_the_selection_and_pages_down_to_the_batch() = vms.runTest {
        val cursors = mutableListOf<String?>()
        val vm = build(inboxCursors = cursors)
        vm.loaded()
        vm.selectSuggestion(batch)
        vm.state.first { !it.isLoadingBatch }

        vm.toggleSelection("a") // una foto cercana que no estaba en el lote
        vm.setBatchScope(OrganizeBatchScope.Inbox)
        val state = vm.state.first { it.batchRevealId != null }

        assertEquals("d", state.batchRevealId)
        assertEquals(listOf("a", "b", "c", "d", "e"), state.gridItems.map { it.id })
        assertEquals(setOf("a", "d", "e"), state.selection)
        assertEquals(2, cursors.size)

        vm.consumeBatchReveal()
        assertNull(vm.state.value.batchRevealId)
    }

    @Test
    fun setting_the_whole_batch_aside_goes_back_to_the_batches() = vms.runTest {
        val vm = build()
        vm.loaded()
        vm.selectSuggestion(batch)
        vm.state.first { !it.isLoadingBatch }

        vm.excludeSelected()
        val state = vm.state.first { it.batch == null }

        assertEquals(false, state.showAllItems)
        assertEquals(emptySet(), state.selection)
    }

    @Test
    fun without_the_items_endpoint_the_batch_uses_what_the_inbox_has_loaded() = vms.runTest {
        val vm = build(itemsEndpoint = false)
        vm.loaded()

        // "b" está en la primera página; "d", no.
        vm.selectSuggestion(batch.copy(assetIds = listOf("b", "d")))
        val state = vm.state.first { !it.isLoadingBatch }

        assertEquals(true, state.isBatchOnly)
        assertEquals(listOf("b"), state.gridItems.map { it.id })
        assertEquals(setOf("b", "d"), state.selection)
        assertNull(state.error)
    }

    @Test
    fun without_the_items_endpoint_and_nothing_loaded_the_batch_stays_in_the_inbox() = vms.runTest {
        val vm = build(itemsEndpoint = false)
        vm.loaded()

        vm.selectSuggestion(batch)
        val state = vm.state.first { !it.isLoadingBatch }

        assertEquals(OrganizeBatchScope.Inbox, state.batchScope)
        assertEquals(emptyList(), state.batchItems)
        assertEquals(setOf("x", "d", "e"), state.selection)
    }
}
