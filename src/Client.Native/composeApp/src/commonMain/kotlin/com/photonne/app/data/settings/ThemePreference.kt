package com.photonne.app.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePreference { System, Light, Dark }

/**
 * Holds the user's chosen colour scheme and persists it via the
 * platform [Settings] backend (Keychain / EncryptedSharedPreferences /
 * java.util.prefs). Reads the value on construction so the theme is
 * available the first time `App()` composes.
 */
class ThemePreferenceStore(private val settings: Settings) {
    private val _value = MutableStateFlow(load())
    val value: StateFlow<ThemePreference> = _value.asStateFlow()

    fun update(preference: ThemePreference) {
        if (_value.value == preference) return
        settings.putString(KEY, preference.name)
        _value.value = preference
    }

    private fun load(): ThemePreference {
        val raw = settings.getStringOrNull(KEY) ?: return ThemePreference.System
        return runCatching { ThemePreference.valueOf(raw) }
            .getOrDefault(ThemePreference.System)
    }

    private companion object {
        const val KEY = "photonne.theme_preference"
    }
}
