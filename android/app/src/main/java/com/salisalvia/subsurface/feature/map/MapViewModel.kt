package com.salisalvia.subsurface.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salisalvia.subsurface.core.model.GeoPoint
import com.salisalvia.subsurface.data.remote.HttpRegionValidationApi
import com.salisalvia.subsurface.data.remote.HttpSentinel2CatalogApi
import com.salisalvia.subsurface.data.remote.RegionValidationResult
import com.salisalvia.subsurface.data.remote.Sentinel2CatalogResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

sealed interface RegionValidationUiState {
    data object Idle : RegionValidationUiState
    data object Loading : RegionValidationUiState
    data class Success(val result: RegionValidationResult) : RegionValidationUiState
    data class Error(val message: String) : RegionValidationUiState
}

/** Owns the API request lifecycle for region validation. */
class MapViewModel : ViewModel() {
    private val api = HttpRegionValidationApi()
    private val catalogApi = HttpSentinel2CatalogApi()
    private var activeCatalogQuery: CatalogSearchQuery? = null
    private var catalogJob: Job? = null
    private val _validationState = MutableStateFlow<RegionValidationUiState>(
        RegionValidationUiState.Idle,
    )
    val validationState: StateFlow<RegionValidationUiState> = _validationState.asStateFlow()
    private val _catalogSearchState = MutableStateFlow<CatalogSearchUiState>(
        CatalogSearchUiState.Idle,
    )
    val catalogSearchState: StateFlow<CatalogSearchUiState> = _catalogSearchState.asStateFlow()

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

    fun searchSentinel2Scenes(
        vertices: List<GeoPoint>,
        lookbackDays: Int,
        maxCloudCoverPercent: Int,
    ) {
        if (_catalogSearchState.value == CatalogSearchUiState.Loading) return
        require(lookbackDays in SUPPORTED_LOOKBACK_DAYS)
        require(maxCloudCoverPercent in 0..100)

        val end = Instant.now()
        val query = CatalogSearchQuery(
            vertices = vertices.toList(),
            datetimeStart = end.minus(Duration.ofDays(lookbackDays.toLong())),
            datetimeEnd = end,
            maxCloudCoverPercent = maxCloudCoverPercent,
        )
        activeCatalogQuery = query
        catalogJob?.cancel()
        _catalogSearchState.value = CatalogSearchUiState.Loading
        catalogJob = viewModelScope.launch {
            try {
                val result = catalogApi.search(
                    vertices = query.vertices,
                    datetimeStart = query.datetimeStart,
                    datetimeEnd = query.datetimeEnd,
                    maxCloudCoverPercent = query.maxCloudCoverPercent,
                )
                _catalogSearchState.value = CatalogSearchUiState.Success(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _catalogSearchState.value = CatalogSearchUiState.Error(
                    error.message ?: "Could not search the Sentinel-2 catalogue.",
                )
            }
        }
    }

    fun loadMoreSentinel2Scenes() {
        val current = _catalogSearchState.value as? CatalogSearchUiState.Success ?: return
        if (current.isLoadingMore) return
        val query = activeCatalogQuery ?: return
        val cursor = current.result.nextCursor ?: return
        _catalogSearchState.value = current.copy(isLoadingMore = true, paginationError = null)
        catalogJob = viewModelScope.launch {
            try {
                val page = catalogApi.search(
                    vertices = query.vertices,
                    datetimeStart = query.datetimeStart,
                    datetimeEnd = query.datetimeEnd,
                    maxCloudCoverPercent = query.maxCloudCoverPercent,
                    cursor = cursor,
                )
                _catalogSearchState.value = CatalogSearchUiState.Success(
                    result = current.result.copy(
                        scenes = current.result.scenes + page.scenes,
                        nextCursor = page.nextCursor,
                        omittedItemCount = current.result.omittedItemCount + page.omittedItemCount,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _catalogSearchState.value = current.copy(
                    isLoadingMore = false,
                    paginationError = error.message ?: "Could not load the next catalogue page.",
                )
            }
        }
    }

    fun dismissCatalogSearch() {
        catalogJob?.cancel()
        catalogJob = null
        activeCatalogQuery = null
        _catalogSearchState.value = CatalogSearchUiState.Idle
    }

    private data class CatalogSearchQuery(
        val vertices: List<GeoPoint>,
        val datetimeStart: Instant,
        val datetimeEnd: Instant,
        val maxCloudCoverPercent: Int,
    )

    private companion object {
        val SUPPORTED_LOOKBACK_DAYS = setOf(30, 90, 180)
    }
}

sealed interface CatalogSearchUiState {
    data object Idle : CatalogSearchUiState
    data object Loading : CatalogSearchUiState
    data class Success(
        val result: Sentinel2CatalogResult,
        val isLoadingMore: Boolean = false,
        val paginationError: String? = null,
    ) : CatalogSearchUiState
    data class Error(val message: String) : CatalogSearchUiState
}
