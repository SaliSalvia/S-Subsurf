package com.salisalvia.subsurface.feature.map

import androidx.lifecycle.SavedStateHandle
import com.salisalvia.subsurface.core.model.GeoPoint
import com.salisalvia.subsurface.data.remote.RegionValidationApi
import com.salisalvia.subsurface.data.remote.RegionValidationResult
import com.salisalvia.subsurface.data.remote.Sentinel2CatalogApi
import com.salisalvia.subsurface.data.remote.Sentinel2CatalogResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class MapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        regionApi: RegionValidationApi = FakeRegionApi(),
        catalogApi: Sentinel2CatalogApi = FakeCatalogApi(),
    ) = MapViewModel(handle, regionApi, catalogApi)

    private fun validTriangle() = listOf(
        GeoPoint(35.0, 51.0),
        GeoPoint(35.0, 51.01),
        GeoPoint(35.01, 51.0),
    )

    // draft state ------------------------------------------------------------

    @Test
    fun startDrawingClearsFinishedRegionButResumesUnfinishedOne() {
        val vm = viewModel()
        vm.startDrawing()
        validTriangle().forEach(vm::addVertex)
        vm.finishDrawing()

        vm.startDrawing()
        assertTrue(vm.vertices.value.isEmpty())
        assertTrue(vm.isDrawing.value)

        vm.addVertex(GeoPoint(35.0, 51.0))
        vm.finishDrawing()
        vm.startDrawing()
        assertEquals(1, vm.vertices.value.size)
    }

    @Test
    fun addVertexIgnoresTapsWhenNotDrawingAndCapsAtTen() {
        val vm = viewModel()
        vm.addVertex(GeoPoint(35.0, 51.0))
        assertTrue(vm.vertices.value.isEmpty())

        vm.startDrawing()
        repeat(12) { index ->
            vm.addVertex(GeoPoint(35.0 + index * 0.001, 51.0 + index * 0.001))
        }
        assertEquals(10, vm.vertices.value.size)
        assertFalse(vm.isDrawing.value)
    }

    @Test
    fun undoAndClearUpdateDraft() {
        val vm = viewModel()
        vm.startDrawing()
        validTriangle().forEach(vm::addVertex)

        vm.undoLastVertex()
        assertEquals(2, vm.vertices.value.size)

        vm.clearVertices()
        assertTrue(vm.vertices.value.isEmpty())

        vm.undoLastVertex()
        assertTrue(vm.vertices.value.isEmpty())
    }

    @Test
    fun draftSurvivesViewModelRecreationThroughSavedStateHandle() {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        first.startDrawing()
        validTriangle().forEach(first::addVertex)

        val recreated = viewModel(handle = handle)
        assertEquals(validTriangle(), recreated.vertices.value)
        assertTrue(recreated.isDrawing.value)
    }

    @Test
    fun corruptedSavedDraftIsIgnoredInsteadOfCrashing() {
        val handle = SavedStateHandle(mapOf("region_draft_vertices" to listOf("not-a-point", "91,0")))
        val vm = viewModel(handle = handle)
        assertTrue(vm.vertices.value.isEmpty())
    }

    // validation --------------------------------------------------------------

    @Test
    fun validateRegionEmitsErrorWithoutCallingApiWhenDraftIsIncomplete() = runTest {
        var calls = 0
        val vm = viewModel(regionApi = FakeRegionApi(onValidate = { calls++ }))
        vm.validateRegion()
        advanceUntilIdle()

        assertEquals(0, calls)
        val state = vm.validationState.value
        assertTrue(state is RegionValidationUiState.Error)
    }

    @Test
    fun validateRegionSuccess() = runTest {
        val vm = viewModel()
        vm.startDrawing()
        validTriangle().forEach(vm::addVertex)

        vm.validateRegion()
        advanceUntilIdle()

        val state = vm.validationState.value
        assertTrue(state is RegionValidationUiState.Success)
        assertEquals(3, (state as RegionValidationUiState.Success).result.vertexCount)
    }

    @Test
    fun validateRegionSurfacesApiErrors() = runTest {
        val vm = viewModel(regionApi = FakeRegionApi(failure = "boom"))
        vm.startDrawing()
        validTriangle().forEach(vm::addVertex)

        vm.validateRegion()
        advanceUntilIdle()

        val state = vm.validationState.value
        assertTrue(state is RegionValidationUiState.Error)
        assertEquals("boom", (state as RegionValidationUiState.Error).message)
    }

    // catalogue search ----------------------------------------------------------

    @Test
    fun searchWithUnsupportedLookbackEmitsErrorInsteadOfCrashing() = runTest {
        val vm = viewModel()
        vm.startDrawing()
        validTriangle().forEach(vm::addVertex)

        vm.searchSentinel2Scenes(lookbackDays = 7, maxCloudCoverPercent = 30)
        advanceUntilIdle()

        val state = vm.catalogSearchState.value
        assertTrue(state is CatalogSearchUiState.Error)
    }

    @Test
    fun searchSuccessAndPagination() = runTest {
        val vm = viewModel()
        vm.startDrawing()
        validTriangle().forEach(vm::addVertex)

        vm.searchSentinel2Scenes(lookbackDays = 90, maxCloudCoverPercent = 30)
        advanceUntilIdle()
        val first = vm.catalogSearchState.value
        assertTrue(first is CatalogSearchUiState.Success)

        vm.loadMoreSentinel2Scenes()
        advanceUntilIdle()
        val paged = vm.catalogSearchState.value as CatalogSearchUiState.Success
        assertEquals(2, paged.result.scenes.size)
        assertEquals(null, paged.result.nextCursor)
        assertFalse(paged.isLoadingMore)
    }

    @Test
    fun dismissCatalogSearchResetsState() = runTest {
        val vm = viewModel()
        vm.startDrawing()
        validTriangle().forEach(vm::addVertex)

        vm.searchSentinel2Scenes(lookbackDays = 90, maxCloudCoverPercent = 30)
        advanceUntilIdle()
        vm.dismissCatalogSearch()
        assertEquals(CatalogSearchUiState.Idle, vm.catalogSearchState.value)
    }

    // map errors -----------------------------------------------------------------

    @Test
    fun mapErrorCanBeReportedAndDismissed() {
        val vm = viewModel()
        assertEquals(null, vm.mapError.value)
        vm.reportMapError("style failed")
        assertEquals("style failed", vm.mapError.value)
        vm.dismissMapError()
        assertEquals(null, vm.mapError.value)
    }

    // fakes -------------------------------------------------------------------

    private class FakeRegionApi(
        private val failure: String? = null,
        private val onValidate: () -> Unit = {},
    ) : RegionValidationApi {
        override suspend fun validate(vertices: List<GeoPoint>): RegionValidationResult {
            onValidate()
            failure?.let { throw java.io.IOException(it) }
            return RegionValidationResult(
                areaSquareMeters = 1_000_000.0,
                areaHectares = 100.0,
                west = 51.0,
                south = 35.0,
                east = 51.01,
                north = 35.01,
                vertexCount = vertices.size,
                note = "fake",
            )
        }
    }

    private class FakeCatalogApi : Sentinel2CatalogApi {
        override suspend fun search(
            vertices: List<GeoPoint>,
            datetimeStart: Instant,
            datetimeEnd: Instant,
            maxCloudCoverPercent: Int,
            cursor: String?,
            limit: Int,
        ): Sentinel2CatalogResult = Sentinel2CatalogResult(
            scenes = listOf(
                com.salisalvia.subsurface.data.remote.Sentinel2Scene(
                    productId = "S2_FAKE_${cursor ?: "first"}",
                    acquiredAt = "2026-11-01T00:00:00Z",
                    cloudCoverPercent = 10.0,
                    platform = "sentinel-2a",
                    processingVersion = "05.00",
                    boundingBoxWestSouthEastNorth = listOf(51.0, 35.0, 51.01, 35.01),
                    assets = emptyList(),
                    requiresProviderAuthentication = false,
                    catalogItemUrl = "https://example.invalid/item",
                ),
            ),
            nextCursor = if (cursor == null) "cursor-1" else null,
            omittedItemCount = 0,
            cloudCoverNote = "fake cloud note",
            assetAccessNote = "fake asset note",
            scientificNotice = "fake science notice",
        )
    }
}
