package com.photonne.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.models.StorageInfoDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountStorageUiState(
    val info: StorageInfoDto? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val usagePercent: Float?
        get() = info?.let { stored ->
            stored.quotaBytes?.takeIf { it > 0 }?.let { quota ->
                (stored.usedBytes.toFloat() / quota.toFloat()).coerceIn(0f, 1f)
            }
        }
}

class AccountStorageViewModel(
    private val repository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccountStorageUiState())
    val state: StateFlow<AccountStorageUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.getStorageInfo() }
                .onSuccess { info ->
                    _state.update { it.copy(info = info, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load storage info"
                        )
                    }
                }
        }
    }
}
