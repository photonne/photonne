package com.photonne.native.data.auth

import com.benasher44.uuid.uuid4
import com.russhwolf.settings.Settings

interface TokenStorage {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getDeviceId(): String
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clear()
}

class SettingsTokenStorage(private val settings: Settings) : TokenStorage {

    override fun getAccessToken(): String? = settings.getStringOrNull(KEY_ACCESS)

    override fun getRefreshToken(): String? = settings.getStringOrNull(KEY_REFRESH)

    override fun getDeviceId(): String {
        val existing = settings.getStringOrNull(KEY_DEVICE)
        if (existing != null) return existing
        val generated = uuid4().toString()
        settings.putString(KEY_DEVICE, generated)
        return generated
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        settings.putString(KEY_ACCESS, accessToken)
        settings.putString(KEY_REFRESH, refreshToken)
    }

    override fun clear() {
        settings.remove(KEY_ACCESS)
        settings.remove(KEY_REFRESH)
    }

    private companion object {
        const val KEY_ACCESS = "photonne.auth.access"
        const val KEY_REFRESH = "photonne.auth.refresh"
        const val KEY_DEVICE = "photonne.device.id"
    }
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) getString(key, "").takeIf { it.isNotEmpty() } else null
