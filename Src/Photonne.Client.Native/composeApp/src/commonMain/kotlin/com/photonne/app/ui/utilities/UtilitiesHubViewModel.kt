package com.photonne.app.ui.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.models.UtilitiesSummary
import com.photonne.app.data.utilities.UtilitiesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cifras vivas del hub de Utilidades. [summary] a null = sin cifras (aún no
 * cargadas, servidor antiguo sin el endpoint o fallo): el hub pinta entonces
 * los subtítulos fijos. Mientras recarga se conserva la cifra anterior para
 * que las filas no parpadeen.
 */
class UtilitiesHubViewModel(
    private val repository: UtilitiesRepository,
    mutationBus: AssetMutationBus,
) : ViewModel() {

    private val _summary = MutableStateFlow<UtilitiesSummary?>(null)
    val summary: StateFlow<UtilitiesSummary?> = _summary.asStateFlow()

    private var loadJob: Job? = null
    private var debounceJob: Job? = null
    private var loadedOnce = false

    init {
        // Borrar, archivar o restaurar desde otra pantalla cambia duplicados y
        // archivos grandes. Las ráfagas (selección masiva) se agrupan en una
        // sola petición, y solo si el hub ya se había abierto alguna vez.
        viewModelScope.launch {
            mutationBus.events.collect { event ->
                when (event) {
                    is AssetMutation.Removed,
                    is AssetMutation.Purged,
                    is AssetMutation.Restored,
                    AssetMutation.AllChanged -> if (loadedOnce) scheduleRefresh()
                    is AssetMutation.FavoriteChanged,
                    is AssetMutation.DateChanged -> Unit
                }
            }
        }
    }

    /** Al abrir el hub y al volver a él desde una subpantalla. */
    fun refresh() {
        debounceJob?.cancel()
        load()
    }

    private fun scheduleRefresh() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE_MS)
            load()
        }
    }

    private fun load() {
        loadedOnce = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _summary.value = repository.summary()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _summary.value = null
            }
        }
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 600L
    }
}
