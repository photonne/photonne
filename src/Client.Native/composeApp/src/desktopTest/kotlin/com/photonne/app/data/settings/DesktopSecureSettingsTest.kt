package com.photonne.app.data.settings

import java.io.File
import java.util.prefs.Preferences
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSecureSettingsTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "photonne-test-${System.nanoTime()}")
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val legacyNode = "com/photonne/test-legacy-${System.nanoTime()}"

    private fun settings() = DesktopSecureSettings(File(dir, "settings.enc"), key)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
        runCatching {
            Preferences.userRoot().node(legacyNode).removeNode()
            Preferences.userRoot().flush()
        }
    }

    @Test
    fun typed_values_round_trip() {
        val s = settings()
        s.putString("s", "hola")
        s.putInt("i", 42)
        s.putLong("l", 1L shl 40)
        s.putBoolean("b", true)
        s.putFloat("f", 1.5f)
        s.putDouble("d", 2.25)
        assertEquals("hola", s.getStringOrNull("s"))
        assertEquals(42, s.getIntOrNull("i"))
        assertEquals(1L shl 40, s.getLongOrNull("l"))
        assertEquals(true, s.getBooleanOrNull("b"))
        assertEquals(1.5f, s.getFloatOrNull("f"))
        assertEquals(2.25, s.getDoubleOrNull("d"))
    }

    @Test
    fun values_survive_a_new_instance_and_are_not_plaintext_on_disk() {
        settings().putString("photonne.auth.access", "token-secreto")
        assertEquals("token-secreto", settings().getStringOrNull("photonne.auth.access"))
        val raw = File(dir, "settings.enc").readBytes().decodeToString()
        assertFalse(raw.contains("token-secreto"), "el token no debe estar en claro en disco")
    }

    @Test
    fun remove_and_clear_persist() {
        val s = settings()
        s.putString("a", "1")
        s.remove("a")
        assertNull(settings().getStringOrNull("a"))
        s.putString("b", "2")
        s.clear()
        assertEquals(0, settings().size)
    }

    @Test
    fun migrates_legacy_prefs_and_retypes_lazily() {
        val legacy = Preferences.userRoot().node(legacyNode)
        legacy.put("photonne.auth.access", "jwt")
        legacy.putBoolean("photonne.auth.remember.enabled", true)
        legacy.flush()

        val s = settings()
        s.migrateFrom(legacy)

        // Los getters tipados retipan la entrada raw migrada.
        assertEquals("jwt", s.getStringOrNull("photonne.auth.access"))
        assertEquals(true, s.getBooleanOrNull("photonne.auth.remember.enabled"))
        // El legado queda limpio.
        assertEquals(0, legacy.keys().size)
        // Y la migración no machaca valores ya escritos.
        s.putString("photonne.auth.access", "jwt-nuevo")
        legacy.put("photonne.auth.access", "jwt-viejo")
        s.migrateFrom(legacy)
        assertEquals("jwt-nuevo", s.getStringOrNull("photonne.auth.access"))
    }

    @Test
    fun a_wrong_type_read_returns_null_instead_of_crashing() {
        val s = settings()
        s.putString("k", "texto")
        assertNull(s.getIntOrNull("k"))
        assertTrue(s.hasKey("k"))
    }
}
