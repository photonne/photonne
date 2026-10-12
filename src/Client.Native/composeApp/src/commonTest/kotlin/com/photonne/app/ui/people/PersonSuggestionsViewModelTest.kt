package com.photonne.app.ui.people

import com.photonne.app.data.error.UiError
import com.photonne.app.data.people.PeopleRepository
import com.photonne.app.ui.TrackedViewModels
import com.photonne.app.ui.json
import com.photonne.app.ui.testApi
import com.photonne.app.ui.testErrorFactory
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersonSuggestionsViewModelTest {

    private val vms = TrackedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failed accept reaches the caller and keeps the face`() = vms.runTest {
        val api = testApi { request ->
            if (request.method == HttpMethod.Get) {
                json("""{"total":1,"items":[{"id":"f-1","assetId":"a-1"}]}""")
            } else respond("", HttpStatusCode.InternalServerError)
        }
        val vm = vms.track(PersonSuggestionsViewModel(PeopleRepository(api), testErrorFactory(api)))
        vm.open("p-1", "Marc")
        vm.state.first { !it.isInitialLoading }

        val reported = CompletableDeferred<UiError>()
        vm.acceptFace("f-1", onError = { reported.complete(it) })
        reported.await()
        val state = vm.state.value

        assertEquals(listOf("f-1"), state.items.map { it.id })
        assertTrue(state.pendingFaceIds.isEmpty())
        assertNull(state.error, "only painted over an empty list - the snackbar says it")
    }
}
