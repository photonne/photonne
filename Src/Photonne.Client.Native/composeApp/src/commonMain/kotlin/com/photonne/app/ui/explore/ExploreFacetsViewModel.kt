package com.photonne.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.data.search.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreFacetsUiState(
    val objects: List<ObjectLabel> = emptyList(),
    val scenes: List<SceneLabel> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val attempted: Boolean = false,
)

class ExploreFacetsViewModel(
    private val repository: SearchRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreFacetsUiState())
    val state: StateFlow<ExploreFacetsUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        val current = _state.value
        if (current.isLoading || current.attempted) return
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val objects = repository.objectLabels(limit = 200)
                val scenes = repository.sceneLabels(limit = 200)
                objects to scenes
            }
                .onSuccess { (objects, scenes) ->
                    _state.value = ExploreFacetsUiState(
                        objects = objects,
                        scenes = scenes,
                        isLoading = false,
                        attempted = true
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            attempted = true,
                            error = errorFactory.from(error, "No se pudieron cargar las etiquetas")
                        )
                    }
                }
        }
    }
}
