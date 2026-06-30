package com.photonne.app.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeNetworkMonitor : NetworkMonitor {
    override val changes: Flow<Unit> = emptyFlow()
}

class LocalReachabilityProbeTest {

    private fun store(local: String?): ServerUrlStore =
        ServerUrlStore(InMemorySettings()).apply {
            setPublic("https://photos.example.com")
            if (local != null) setLocal(local)
        }

    @Test
    fun marks_unreachable_when_no_local_configured() = runTest {
        val store = store(local = null)
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.OK) })
        val probe = LocalReachabilityProbe(client, store, FakeNetworkMonitor())

        assertFalse(probe.runProbe())
        assertFalse(store.isLocalReachable())
        assertEquals(0, calls) // never dials when there's no LAN URL
    }

    @Test
    fun marks_reachable_when_head_succeeds() = runTest {
        val store = store(local = "http://192.168.1.10:5000")
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val probe = LocalReachabilityProbe(client, store, FakeNetworkMonitor())

        assertTrue(probe.runProbe())
        assertTrue(store.isLocalReachable())
        // The LAN URL now wins over the public one.
        assertEquals("http://192.168.1.10:5000", store.requireBaseUrl())
    }

    @Test
    fun retries_then_succeeds() = runTest {
        val store = store(local = "http://192.168.1.10:5000")
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            if (calls == 1) throw RuntimeException("slow first handshake")
            respond("", HttpStatusCode.OK)
        })
        val probe = LocalReachabilityProbe(client, store, FakeNetworkMonitor())

        assertTrue(probe.runProbe())
        assertTrue(store.isLocalReachable())
        assertEquals(2, calls)
    }

    @Test
    fun marks_unreachable_after_all_attempts_miss() = runTest {
        val store = store(local = "http://192.168.1.10:5000")
        var calls = 0
        val client = HttpClient(MockEngine { calls++; throw RuntimeException("unreachable") })
        val probe = LocalReachabilityProbe(client, store, FakeNetworkMonitor())

        assertFalse(probe.runProbe())
        assertFalse(store.isLocalReachable())
        assertEquals(3, calls) // PROBE_ATTEMPTS
        // Falls back to the public URL.
        assertEquals("https://photos.example.com", store.requireBaseUrl())
    }
}
