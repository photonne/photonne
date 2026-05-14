package com.photonne.app.data.auth

import com.russhwolf.settings.Settings

data class RememberedCredentials(val username: String, val password: String)

class RememberedCredentialsStore(private val settings: Settings) {

    fun get(): RememberedCredentials? {
        if (!settings.getBoolean(KEY_ENABLED, false)) return null
        val username = settings.getStringOrEmpty(KEY_USERNAME)
        val password = settings.getStringOrEmpty(KEY_PASSWORD)
        if (username.isEmpty() || password.isEmpty()) return null
        return RememberedCredentials(username, password)
    }

    fun isEnabled(): Boolean = settings.getBoolean(KEY_ENABLED, false)

    fun save(username: String, password: String) {
        settings.putBoolean(KEY_ENABLED, true)
        settings.putString(KEY_USERNAME, username)
        settings.putString(KEY_PASSWORD, password)
    }

    fun clear() {
        settings.remove(KEY_ENABLED)
        settings.remove(KEY_USERNAME)
        settings.remove(KEY_PASSWORD)
    }

    private fun Settings.getStringOrEmpty(key: String): String =
        if (hasKey(key)) getString(key, "") else ""

    private companion object {
        const val KEY_ENABLED = "photonne.auth.remember.enabled"
        const val KEY_USERNAME = "photonne.auth.remember.username"
        const val KEY_PASSWORD = "photonne.auth.remember.password"
    }
}
