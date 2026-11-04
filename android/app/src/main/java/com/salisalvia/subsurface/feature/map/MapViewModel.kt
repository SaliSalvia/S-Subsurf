package com.salisalvia.subsurface.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salisalvia.subsurface.core.model.GeoPoint
import com.salisalvia.subsurface.data.remote.HttpRegionValidationApi
import com.salisalvia.subsurface.data.remote.RegionValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RegionValidationUiState {
    data object Idle : RegionValidationUiState
    data object Loading : RegionValidationUiState
    data class Success(val result: RegionValidationResult) : RegionValidationUiState
    data class Error(val message: String) : RegionValidationUiState
}

/** Owns the API request lifecycle for region validation. */
class MapViewModel : ViewModel() {
    private val api = HttpRegionValidationApi()
    private val _validationState = MutableStateFlow<RegionValidationUiState>(
        RegionValidationUiState.Idle,
    )
    val validationState: StateFlow<RegionValidationUiState> = _validationState.asStateFlow()

    fun validateRegion(vertices: List<GeoPoint>) {
        if (_validationState.value == RegionValidationUiState.Loading) return
        _validationState.value = RegionValidationUiState.Loading
        viewModelScope.launch {
            try {
                _validationState.value = RegionValidationUiState.Success(api.validate(vertices))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _validationState.value = RegionValidationUiState.Error(
                    error.message ?: "Could not connect to the validation API.",
                )
            }
        }
    }

    fun dismissValidation() {
        if (_validationState.value != RegionValidationUiState.Loading) {
            _validationState.value = RegionValidationUiState.Idle
        }
    }
}
