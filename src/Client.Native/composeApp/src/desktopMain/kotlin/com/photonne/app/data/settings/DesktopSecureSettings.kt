package com.photonne.app.data.settings

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import com.russhwolf.settings.Settings
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Settings cifrados en reposo para escritorio: los tokens y la contraseña de
 * "recordarme" viven aquí, el equivalente al EncryptedSharedPreferences de
 * Android. Fichero AES-256-GCM en ~/.photonne (junto a la BD), con la clave en
 * el llavero del SO (macOS Keychain / Windows Credential Manager / Secret
 * Service) vía java-keyring; si no hay llavero, una clave por máquina en un
 * fichero 0600 — no protege contra otro proceso del mismo usuario, pero
 * tampoco lo hacía java.util.prefs y al menos deja de estar en claro en un
 * plist/registro compartido.
 *
 * Cada entrada guarda su tipo ("s:", "i:", "b:"…) para que los getters
 * tipados de multiplatform-settings hagan round-trip. Las entradas migradas
 * del java.util.prefs legado llegan como "r:" (raw: prefs guarda todo como
 * string sin tipo) y se retipan perezosamente en el primer get tipado.
 */
class DesktopSecureSettings internal constructor(
    private val file: File,
    private val key: SecretKey
) : Settings {

    private val map: MutableMap<String, String> = load()

    override val keys: Set<String> get() = synchronized(this) { map.keys.toSet() }
    override val size: Int get() = synchronized(this) { map.size }

    override fun clear(): Unit = synchronized(this) {
        map.clear()
        persist()
    }

    override fun remove(key: String): Unit = synchronized(this) {
        if (map.remove(key) != null) persist()
    }

    override fun hasKey(key: String): Boolean = synchronized(this) { map.containsKey(key) }

    override fun putString(key: String, value: String) = put(key, 's', value)
    override fun getString(key: String, defaultValue: String): String =
        getStringOrNull(key) ?: defaultValue

    override fun getStringOrNull(key: String): String? = typed(key, 's') { it }

    override fun putInt(key: String, value: Int) = put(key, 'i', value.toString())
    override fun getInt(key: String, defaultValue: Int): Int = getIntOrNull(key) ?: defaultValue
    override fun getIntOrNull(key: String): Int? = typed(key, 'i') { it.toIntOrNull() }

    override fun putLong(key: String, value: Long) = put(key, 'l', value.toString())
    override fun getLong(key: String, defaultValue: Long): Long = getLongOrNull(key) ?: defaultValue
    override fun getLongOrNull(key: String): Long? = typed(key, 'l') { it.toLongOrNull() }

    override fun putFloat(key: String, value: Float) = put(key, 'f', value.toString())
    override fun getFloat(key: String, defaultValue: Float): Float =
        getFloatOrNull(key) ?: defaultValue

    override fun getFloatOrNull(key: String): Float? = typed(key, 'f') { it.toFloatOrNull() }

    override fun putDouble(key: String, value: Double) = put(key, 'd', value.toString())
    override fun getDouble(key: String, defaultValue: Double): Double =
        getDoubleOrNull(key) ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = typed(key, 'd') { it.toDoubleOrNull() }

    override fun putBoolean(key: String, value: Boolean) = put(key, 'b', value.toString())
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        getBooleanOrNull(key) ?: defaultValue

    override fun getBooleanOrNull(key: String): Boolean? =
        typed(key, 'b') { it.toBooleanStrictOrNull() }

    /** Vuelca [legacy] como entradas raw ("r:") y limpia el nodo. Idempotente. */
    internal fun migrateFrom(legacy: Preferences) {
        synchronized(this) {
            val legacyKeys = runCatching { legacy.keys() }.getOrNull() ?: return
            if (legacyKeys.isEmpty()) return
            var copied = false
            for (k in legacyKeys) {
                if (map.containsKey(k)) continue
                val raw = legacy.get(k, null) ?: continue
                map[k] = "r:$raw"
                copied = true
            }
            if (copied) persist()
            // Solo tras persistir con éxito: el legado deja de existir.
            runCatching {
                for (k in legacyKeys) legacy.remove(k)
                legacy.flush()
            }
        }
    }

    private fun put(key: String, type: Char, encoded: String) = synchronized(this) {
        map[key] = "$type:$encoded"
        persist()
    }

    private fun <T> typed(key: String, type: Char, parse: (String) -> T?): T? =
        synchronized(this) {
            val entry = map[key] ?: return null
            val prefix = entry.getOrNull(0) ?: return null
            val value = entry.drop(2)
            when (prefix) {
                type -> parse(value)
                // Migrada sin tipo: retipar en el primer acceso tipado.
                'r' -> parse(value)?.also { map[key] = "$type:$value"; persist() }
                else -> null
            }
        }

    private fun load(): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        if (!file.exists()) return result
        runCatching {
            val blob = file.readBytes()
            if (blob.size <= GCM_IV_BYTES) return result
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, blob, 0, GCM_IV_BYTES)
            )
            val plain = cipher.doFinal(blob, GCM_IV_BYTES, blob.size - GCM_IV_BYTES)
            val props = Properties()
            props.load(plain.inputStream())
            props.forEach { (k, v) -> result[k.toString()] = v.toString() }
        }
        return result
    }

    private fun persist() {
        runCatching {
            val props = Properties()
            map.forEach { (k, v) -> props.setProperty(k, v) }
            val out = java.io.ByteArrayOutputStream()
            props.store(out, null)
            val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val sealed = cipher.doFinal(out.toByteArray())
            file.parentFile?.mkdirs()
            // Escritura atómica: un cierre a mitad no corrompe el almacén.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(iv + sealed)
            Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val KEYRING_DOMAIN = "Photonne"
        private const val KEYRING_ACCOUNT = "settings-key"

        /**
         * Almacén de producción: ~/.photonne/settings.enc con la clave del
         * llavero (o del fichero de reserva), migrando el java.util.prefs
         * legado la primera vez.
         */
        fun createDefault(): DesktopSecureSettings {
            val dir = File(System.getProperty("user.home"), ".photonne")
            dir.mkdirs()
            val settings = DesktopSecureSettings(File(dir, "settings.enc"), obtainKey(dir))
            settings.migrateFrom(Preferences.userRoot().node("com/photonne/app"))
            return settings
        }

        private fun obtainKey(dir: File): SecretKey {
            keyFromKeyring()?.let { return it }
            return keyFromFallbackFile(dir)
        }

        private fun keyFromKeyring(): SecretKey? = runCatching {
            Keyring.create().use { keyring ->
                val existing = try {
                    keyring.getPassword(KEYRING_DOMAIN, KEYRING_ACCOUNT)
                } catch (_: PasswordAccessException) {
                    null
                }
                val encoded = existing ?: Base64.getEncoder()
                    .encodeToString(randomKeyBytes())
                    .also { keyring.setPassword(KEYRING_DOMAIN, KEYRING_ACCOUNT, it) }
                SecretKeySpec(Base64.getDecoder().decode(encoded), "AES")
            }
        }.getOrNull()

        private fun keyFromFallbackFile(dir: File): SecretKey {
            val keyFile = File(dir, ".settings.key")
            if (!keyFile.exists()) {
                keyFile.writeBytes(randomKeyBytes())
                runCatching {
                    Files.setPosixFilePermissions(
                        keyFile.toPath(),
                        PosixFilePermissions.fromString("rw-------")
                    )
                }
            }
            return SecretKeySpec(keyFile.readBytes(), "AES")
        }

        private fun randomKeyBytes(): ByteArray =
            ByteArray(32).also { SecureRandom().nextBytes(it) }
    }
}
