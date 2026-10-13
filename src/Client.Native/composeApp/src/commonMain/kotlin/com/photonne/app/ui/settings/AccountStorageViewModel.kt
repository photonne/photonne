package com.photonne.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.StorageInfoDto
import com.photonne.app.data.settings.HiddenStorageSpacesStore
import com.photonne.app.data.settings.StorageSpace
import com.photonne.app.ui.format.storageQuotaFraction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountStorageUiState(
    val info: StorageInfoDto? = null,
    val isLoading: Boolean = false,
    val error: UiError? = null,
) {
    /** Not capped at 1: past the quota it says by how much. */
    val usagePercent: Float?
        get() = info?.let { stored -> storageQuotaFraction(stored.usedBytes, stored.quotaBytes) }
}

class AccountStorageViewModel(
    private val repository: AccountRepository,
    private val errorFactory: UiErrorFactory,
    private val hiddenSpacesStore: HiddenStorageSpacesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountStorageUiState())
    val state: StateFlow<AccountStorageUiState> = _state.asStateFlow()

    /** Carpetas compartidas y bibliotecas que el usuario quitó del total. */
    val hiddenSpaces: StateFlow<Set<StorageSpace>> = hiddenSpacesStore.value

    fun toggleSpace(space: StorageSpace) = hiddenSpacesStore.toggle(space)

    /**
     * Carga solo si aún no hay datos: la fila de Almacenamiento de Ajustes
     * resume la cuota sin volver a pedirla cada vez que se entra. La pantalla
     * de Almacenamiento sigue refrescando con [load].
     */
    fun loadIfNeeded() {
        if (_state.value.info == null) load()
    }

    fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.getStorageInfo() }
                .onSuccess { info ->
                    _state.update { it.copy(info = info, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "No se pudo cargar la información de almacenamiento")
                        )
                    }
                }
        }
    }
}
