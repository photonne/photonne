package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.VersionInfoResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminServerUiState(
    val info: VersionInfoResponse? = null,
    val isLoading: Boolean = false,
    val error: UiError? = null,
)

class AdminServerViewModel(
    private val repository: AdminRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminServerUiState())
    val state: StateFlow<AdminServerUiState> = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.getVersion(refresh) }
                .onSuccess { info ->
                    _state.update { it.copy(info = info, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "No se pudo cargar la información de versión")
                        )
                    }
                }
        }
    }
}
