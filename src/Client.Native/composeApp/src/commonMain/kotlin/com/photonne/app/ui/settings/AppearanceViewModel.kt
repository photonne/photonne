package com.photonne.app.ui.settings

import androidx.lifecycle.ViewModel
import com.photonne.app.data.settings.ThemePreference
import com.photonne.app.data.settings.ThemePreferenceStore
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin facade over [ThemePreferenceStore] so the appearance screen can
 * read and persist the colour-scheme choice via the standard view-model
 * idiom used elsewhere in the app.
 */
class AppearanceViewModel(
    private val store: ThemePreferenceStore
) : ViewModel() {

    val preference: StateFlow<ThemePreference> = store.value

    fun choose(preference: ThemePreference) {
        store.update(preference)
    }
}
