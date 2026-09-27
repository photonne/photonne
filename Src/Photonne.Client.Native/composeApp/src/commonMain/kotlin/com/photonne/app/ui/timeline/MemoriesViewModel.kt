package com.photonne.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.timeline.MemoriesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class MemoriesUiState(
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val attempted: Boolean = false,
)

class MemoriesViewModel(
    private val repository: MemoriesRepository,
    private val errorFactory: UiErrorFactory,
    mutationBus: AssetMutationBus,
) : ViewModel() {

    private val _state = MutableStateFlow(MemoriesUiState())
    val state: StateFlow<MemoriesUiState> = _state.asStateFlow()

    /** Día de la última carga: "en este día" caduca a medianoche. */
    private var loadedDay: LocalDate? = null
    private var loadJob: Job? = null

    init {
        // Lote L9: la tira no debe seguir enseñando lo que se acaba de borrar
        // o archivar; lo que vuelve (restaurar, fecha cambiada) pide recargar.
        viewModelScope.launch {
            mutationBus.events.collect { event ->
                when (event) {
                    is AssetMutation.Removed -> removeItems(event.assetIds)
                    is AssetMutation.Purged -> removeItems(event.assetIds)
                    is AssetMutation.Restored, AssetMutation.AllChanged,
                    is AssetMutation.DateChanged -> refresh()
                    is AssetMutation.FavoriteChanged -> _state.update { current ->
                        current.copy(items = current.items.map {
                            if (it.id == event.assetId) it.copy(isFavorite = event.isFavorite) else it
                        })
                    }
                }
            }
        }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { items ->
                    loadedDay = today()
                    _state.value = MemoriesUiState(items = items, attempted = true)
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update {
                        it.copy(
                            isLoading = false,
                            attempted = true,
                            error = errorFactory.from(error, "No se pudieron cargar los recuerdos")
                        )
                    }
                }
        }
    }

    /** Al volver a primer plano: recarga solo si ha cambiado el día. */
    fun refreshIfDayChanged() {
        val loaded = loadedDay ?: return
        if (today() != loaded) refresh()
    }

    private fun removeItems(assetIds: List<String>) {
        val ids = assetIds.toSet()
        _state.update { current -> current.copy(items = current.items.filterNot { it.id in ids }) }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
