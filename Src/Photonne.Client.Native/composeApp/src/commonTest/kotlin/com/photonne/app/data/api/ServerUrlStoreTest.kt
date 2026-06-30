package com.photonne.app.data.api

import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Minimal in-memory [Settings] used by the test. We don't pull
 * `multiplatform-settings-test` in as a runtime dep because the store only
 * exercises string keys.
 */
class InMemorySettings : Settings {
    private val map = mutableMapOf<String, Any?>()
    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String =
        map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String
    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float =
        map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float
    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double =
        map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

class ServerUrlStoreTest {

    @Test
    fun returns_public_url_when_no_local_configured() {
        val store = ServerUrlStore(InMemorySettings())
        store.setPublic("https://photos.example.com")

        assertEquals("https://photos.example.com", store.requireBaseUrl())
        assertFalse(store.isLocalReachable())
    }

    @Test
    fun returns_local_url_only_when_reachable() {
        val store = ServerUrlStore(InMemorySettings())
        store.setPublic("https://photos.example.com")
        store.setLocal("http://192.168.1.10:5000")

        // Default: not reachable -> public wins
        assertEquals("https://photos.example.com", store.requireBaseUrl())

        store.setLocalReachable(true)
        assertEquals("http://192.168.1.10:5000", store.requireBaseUrl())

        store.setLocalReachable(false)
        assertEquals("https://photos.example.com", store.requireBaseUrl())
    }

    @Test
    fun clearing_local_url_also_clears_reachable_flag() {
        val store = ServerUrlStore(InMemorySettings())
        store.setPublic("https://photos.example.com")
        store.setLocal("http://192.168.1.10:5000")
        store.setLocalReachable(true)

        store.setLocal(null)
        assertNull(store.getLocal())
        assertFalse(store.isLocalReachable())
        assertEquals("https://photos.example.com", store.requireBaseUrl())
    }

    @Test
    fun normalize_prefixes_https_and_trims_trailing_slash() {
        assertEquals(
            "https://photos.example.com",
            ServerUrlStore.normalize("photos.example.com/")
        )
        assertEquals(
            "http://192.168.1.10:5000",
            ServerUrlStore.normalize("  http://192.168.1.10:5000/  ")
        )
    }

    @Test
    fun persists_both_urls_across_instances() {
        val settings = InMemorySettings()
        ServerUrlStore(settings).apply {
            setPublic("https://photos.example.com")
            setLocal("http://192.168.1.10:5000")
        }

        val reloaded = ServerUrlStore(settings)
        assertEquals("https://photos.example.com", reloaded.getPublic())
        assertEquals("http://192.168.1.10:5000", reloaded.getLocal())
        // Reachability is not persisted: every cold start probes again.
        assertFalse(reloaded.isLocalReachable())
    }

    @Test
    fun migrates_legacy_single_url_to_public() {
        val settings = InMemorySettings()
        settings.putString("photonne.server.url", "https://photos.example.com")

        val store = ServerUrlStore(settings)
        assertEquals("https://photos.example.com", store.getPublic())
        assertEquals("https://photos.example.com", store.requireBaseUrl())

        // First write through the new API drops the legacy key.
        store.setPublic("https://photos.example.com")
        assertFalse(settings.hasKey("photonne.server.url"))
        assertTrue(settings.hasKey("photonne.server.url.public"))
    }

    @Test
    fun require_base_url_throws_when_unconfigured() {
        val store = ServerUrlStore(InMemorySettings())
        assertFailsWith<ServerUrlNotConfiguredException> { store.requireBaseUrl() }
    }
}
