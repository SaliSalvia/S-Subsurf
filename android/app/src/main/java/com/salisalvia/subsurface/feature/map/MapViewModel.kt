package com.salisalvia.subsurface.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.salisalvia.subsurface.core.geometry.PolygonMetrics
import com.salisalvia.subsurface.core.model.GeoPoint
import com.salisalvia.subsurface.data.remote.HttpRegionValidationApi
import com.salisalvia.subsurface.data.remote.HttpSentinel2CatalogApi
import com.salisalvia.subsurface.data.remote.RegionValidationApi
import com.salisalvia.subsurface.data.remote.RegionValidationResult
import com.salisalvia.subsurface.data.remote.Sentinel2CatalogApi
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

/**
 * Owns the region-draft state and the API request lifecycle.
 *
 * The draft (vertices + drawing flag) is mirrored into [SavedStateHandle], so it
 * survives configuration changes (rotation) and process death — previously the
 * draft lived in a `remember {}` block and was silently discarded on rotation.
 *
 * API clients are constructor-injected so unit tests can substitute fakes; the
 * production [Factory] wires the real HTTP clients with a [SavedStateHandle].
 */
class MapViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val regionApi: RegionValidationApi = HttpRegionValidationApi(),
    private val catalogApi: Sentinel2CatalogApi = HttpSentinel2CatalogApi(),
) : ViewModel() {

    private val _vertices = MutableStateFlow(restoreVertices(savedStateHandle.get(KEY_VERTICES)))
    val vertices: StateFlow<List<GeoPoint>> = _vertices.asStateFlow()

    val isDrawing: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_DRAWING, false)

    private val _mapError = MutableStateFlow<String?>(null)
    val mapError: StateFlow<String?> = _mapError.asStateFlow()

    private val _validationState = MutableStateFlow<RegionValidationUiState>(
        RegionValidationUiState.Idle,
    )
    val validationState: StateFlow<RegionValidationUiState> = _validationState.asStateFlow()

    private val _catalogSearchState = MutableStateFlow<CatalogSearchUiState>(
        CatalogSearchUiState.Idle,
    )
    val catalogSearchState: StateFlow<CatalogSearchUiState> = _catalogSearchState.asStateFlow()

    private var activeCatalogQuery: CatalogSearchQuery? = null
    private var validationJob: Job? = null
    private var catalogJob: Job? = null

    // region draft -----------------------------------------------------------

    /** Starts a new draft. A finished region is cleared; an unfinished one is resumed. */
    fun startDrawing() {
        if (_vertices.value.size >= PolygonMetrics.MIN_VERTICES) {
            updateVertices(emptyList())
        }
        savedStateHandle[KEY_DRAWING] = true
    }

    fun finishDrawing() {
        savedStateHandle[KEY_DRAWING] = false
    }

    fun addVertex(point: GeoPoint) {
        if (isDrawing.value != true) return
        if (_vertices.value.size >= PolygonMetrics.MAX_VERTICES) {
            finishDrawing()
            return
        }
        updateVertices(_vertices.value + point)
        if (_vertices.value.size >= PolygonMetrics.MAX_VERTICES) {
            finishDrawing()
        }
    }

    fun undoLastVertex() {
        if (_vertices.value.isNotEmpty()) {
            updateVertices(_vertices.value.dropLast(1))
        }
    }

    fun clearVertices() {
        updateVertices(emptyList())
    }

    private fun updateVertices(next: List<GeoPoint>) {
        _vertices.value = next
        savedStateHandle[KEY_VERTICES] = next.map(::encodeVertex)
    }

    // map --------------------------------------------------------------------

    fun reportMapError(message: String) {
        _mapError.value = message
    }

    fun dismissMapError() {
        _mapError.value = null
    }

    // region validation ------------------------------------------------------

    fun validateRegion() {
        if (_validationState.value == RegionValidationUiState.Loading) return
        val vertices = _vertices.value
        val localError = validateDrawableRegion(vertices)
        if (localError != null) {
            _validationState.value = RegionValidationUiState.Error(localError)
            return
        }
        _validationState.value = RegionValidationUiState.Loading
        validationJob?.cancel()
        validationJob = viewModelScope.launch {
            try {
                _validationState.value = RegionValidationUiState.Success(regionApi.validate(vertices))
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

    // catalogue search --------------------------------------------------------

    fun searchSentinel2Scenes(
        lookbackDays: Int,
        maxCloudCoverPercent: Int,
    ) {
        if (_catalogSearchState.value == CatalogSearchUiState.Loading) return
        val vertices = _vertices.value
        // Invalid input becomes an Error state — never an IllegalArgumentException crash.
        val localError = validateDrawableRegion(vertices)
            ?: validateCatalogFilters(lookbackDays, maxCloudCoverPercent)
        if (localError != null) {
            _catalogSearchState.value = CatalogSearchUiState.Error(localError)
            return
        }

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
        catalogJob?.cancel()
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
                // Restore the non-loading page so a cancelled pagination never
                // leaves the UI stuck on a spinner.
                _catalogSearchState.value = current.copy(isLoadingMore = false)
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

    // validation helpers ------------------------------------------------------

    private fun validateDrawableRegion(vertices: List<GeoPoint>): String? {
        if (vertices.size < PolygonMetrics.MIN_VERTICES) {
            return "Draw at least ${PolygonMetrics.MIN_VERTICES} vertices before continuing."
        }
        return PolygonMetrics.validationError(vertices)
    }

    private fun validateCatalogFilters(lookbackDays: Int, maxCloudCoverPercent: Int): String? =
        when {
            lookbackDays !in SUPPORTED_LOOKBACK_DAYS -> "Unsupported acquisition window."
            maxCloudCoverPercent !in 0..100 -> "The cloud-cover limit must be between 0 and 100%."
            else -> null
        }

    private data class CatalogSearchQuery(
        val vertices: List<GeoPoint>,
        val datetimeStart: Instant,
        val datetimeEnd: Instant,
        val maxCloudCoverPercent: Int,
    )

    companion object {
        /** Production factory: real HTTP clients + framework-provided [SavedStateHandle]. */
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return MapViewModel(extras.createSavedStateHandle()) as T
            }
        }

        val SUPPORTED_LOOKBACK_DAYS = setOf(30, 90, 180)

        private const val KEY_VERTICES = "region_draft_vertices"
        private const val KEY_DRAWING = "region_draft_is_drawing"

        /**
         * Lossless `Double.toString` round-trip encoding. `SavedStateHandle` (Bundle)
         * cannot store [GeoPoint] directly without Parcelable, and this avoids adding
         * the parcelize plugin for a single type.
         */
        private fun encodeVertex(point: GeoPoint): String =
            "${point.latitude},${point.longitude}"

        private fun decodeVertex(raw: String): GeoPoint? {
            val parts = raw.split(",", limit = 2)
            if (parts.size != 2) return null
            val latitude = parts[0].toDoubleOrNull() ?: return null
            val longitude = parts[1].toDoubleOrNull() ?: return null
            return runCatching { GeoPoint(latitude, longitude) }.getOrNull()
        }

        private fun restoreVertices(encoded: List<String>?): List<GeoPoint> =
            encoded?.mapNotNull(::decodeVertex).orEmpty()
    }
}
