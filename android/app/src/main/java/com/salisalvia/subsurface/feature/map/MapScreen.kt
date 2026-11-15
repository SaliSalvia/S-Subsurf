package com.salisalvia.subsurface.feature.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salisalvia.subsurface.core.geometry.PolygonMetrics
import com.salisalvia.subsurface.data.remote.RegionValidationResult
import com.salisalvia.subsurface.data.remote.Sentinel2CatalogResult
import com.salisalvia.subsurface.data.remote.Sentinel2Scene
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Map workspace. The region draft lives in [MapViewModel] (backed by
 * `SavedStateHandle`) so rotation or process death never discards it; only the
 * catalogue-filter UI preferences are kept locally via `rememberSaveable`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel) {
    val vertices by viewModel.vertices.collectAsStateWithLifecycle()
    val isDrawing by viewModel.isDrawing.collectAsStateWithLifecycle()
    val validationState by viewModel.validationState.collectAsStateWithLifecycle()
    val catalogSearchState by viewModel.catalogSearchState.collectAsStateWithLifecycle()
    val mapError by viewModel.mapError.collectAsStateWithLifecycle()
    var lookbackDays by rememberSaveable { mutableStateOf(90) }
    var maxCloudCoverPercent by rememberSaveable { mutableStateOf(30f) }

    val validationError = remember(vertices) {
        PolygonMetrics.validationError(vertices)
    }
    val areaSquareMeters = remember(vertices, validationError) {
        if (validationError == null) {
            runCatching { PolygonMetrics.areaSquareMeters(vertices) }.getOrNull()
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SubsurfaceExplorer", fontWeight = FontWeight.SemiBold)
                        Text(
                            "GEOSPATIAL OBSERVATION WORKSPACE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isDrawing) "Survey boundary" else "Map ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when {
                                    isDrawing -> "Tap the map to add 3–10 boundary vertices."
                                    vertices.size >= 3 && validationError != null ->
                                        "${vertices.size} vertices · $validationError"
                                    vertices.size >= 3 -> "${vertices.size} vertices · ${formatArea(areaSquareMeters)}"
                                    vertices.isNotEmpty() -> "${vertices.size} points · Add at least ${3 - vertices.size} more."
                                    else -> "Start a survey by defining a region."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = {
                            if (isDrawing) {
                                viewModel.finishDrawing()
                            } else {
                                viewModel.startDrawing()
                            }
                        }) {
                            Text(
                                when {
                                    isDrawing -> "Finish drawing"
                                    vertices.isEmpty() -> "Draw region"
                                    vertices.size < 3 -> "Continue drawing"
                                    else -> "New region"
                                },
                            )
                        }
                    }

                    if (validationState == RegionValidationUiState.Loading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.width(18.dp).height(18.dp),
                            )
                            Text("Validating region with the API…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (catalogSearchState == CatalogSearchUiState.Loading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.width(18.dp).height(18.dp),
                            )
                            Text(
                                "Searching the live Copernicus catalogue…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (vertices.size >= 3 && validationError == null && !isDrawing) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Acquisition window", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30, 90, 180).forEach { days ->
                                    FilterChip(
                                        selected = lookbackDays == days,
                                        onClick = { lookbackDays = days },
                                        label = { Text("$days days") },
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Max scene cloud: ${maxCloudCoverPercent.roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(148.dp),
                                )
                                Slider(
                                    value = maxCloudCoverPercent,
                                    onValueChange = { maxCloudCoverPercent = it },
                                    valueRange = 0f..100f,
                                    steps = 9,
                                    enabled = catalogSearchState != CatalogSearchUiState.Loading,
                                )
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = validationState != RegionValidationUiState.Loading &&
                                    catalogSearchState != CatalogSearchUiState.Loading,
                                onClick = {
                                    viewModel.searchSentinel2Scenes(
                                        lookbackDays,
                                        maxCloudCoverPercent.roundToInt(),
                                    )
                                },
                            ) {
                                Text("Search Sentinel-2 catalogue")
                            }
                            Text(
                                "Product metadata only · no image download or underground detection",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (isDrawing || vertices.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = vertices.isNotEmpty(),
                                onClick = viewModel::undoLastVertex,
                            ) { Text("Undo point") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = vertices.isNotEmpty(),
                                onClick = viewModel::clearVertices,
                            ) { Text("Clear") }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = vertices.size >= 3 && validationError == null &&
                                    areaSquareMeters != null && !isDrawing &&
                                    validationState != RegionValidationUiState.Loading,
                                onClick = viewModel::validateRegion,
                            ) { Text("Review area") }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MapLibreMapView(
                vertices = vertices,
                onMapTap = viewModel::addVertex,
                onMapError = viewModel::reportMapError,
                modifier = Modifier.fillMaxSize(),
            )

            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .width(220.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("BASE MAP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(3.dp))
                    Text("OpenStreetMap", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        "© OpenStreetMap contributors",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Live Sentinel-2 product metadata is searchable. Image assets are not downloaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (mapError != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            mapError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissMapError) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }

    when (val state = validationState) {
        is RegionValidationUiState.Success -> ValidationResultDialog(
            result = state.result,
            onDismiss = viewModel::dismissValidation,
        )
        is RegionValidationUiState.Error -> AlertDialog(
            onDismissRequest = viewModel::dismissValidation,
            title = { Text("Region validation failed") },
            text = { Text(state.message) },
            confirmButton = {
                Button(onClick = viewModel::dismissValidation) { Text("Close") }
            },
        )
        RegionValidationUiState.Idle, RegionValidationUiState.Loading -> Unit
    }

    when (val state = catalogSearchState) {
        is CatalogSearchUiState.Success -> CatalogResultsDialog(
            result = state.result,
            isLoadingMore = state.isLoadingMore,
            paginationError = state.paginationError,
            onLoadMore = viewModel::loadMoreSentinel2Scenes,
            onDismiss = viewModel::dismissCatalogSearch,
        )
        is CatalogSearchUiState.Error -> AlertDialog(
            onDismissRequest = viewModel::dismissCatalogSearch,
            title = { Text("Catalogue search failed") },
            text = { Text(state.message) },
            confirmButton = {
                Button(onClick = viewModel::dismissCatalogSearch) { Text("Close") }
            },
        )
        CatalogSearchUiState.Idle, CatalogSearchUiState.Loading -> Unit
    }
}

@Composable
private fun ValidationResultDialog(
    result: RegionValidationResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Region validated") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    String.format(
                        Locale.US,
                        "%.2f ha (%.0f m²)",
                        result.areaHectares,
                        result.areaSquareMeters,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("${result.vertexCount} boundary vertices")
                Text(
                    String.format(
                        Locale.US,
                        "W %.5f  ·  S %.5f\nE %.5f  ·  N %.5f",
                        result.west,
                        result.south,
                        result.east,
                        result.north,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(result.note, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
    )
}

private fun formatArea(areaSquareMeters: Double?): String {
    if (areaSquareMeters == null) return "Area unavailable"
    return if (areaSquareMeters >= 10_000.0) {
        String.format(Locale.US, "%.2f ha (approx.)", areaSquareMeters / 10_000.0)
    } else {
        String.format(Locale.US, "%.0f m² (approx.)", areaSquareMeters)
    }
}

@Composable
private fun CatalogResultsDialog(
    result: Sentinel2CatalogResult,
    isLoadingMore: Boolean,
    paginationError: String?,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sentinel-2 products (${result.scenes.size})") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    result.scientificNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    result.cloudCoverNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.scenes.isEmpty()) {
                    Text(
                        "No products matched these filters. Widen the date interval or increase the cloud-cover limit.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(result.scenes, key = Sentinel2Scene::productId) { scene ->
                            CatalogSceneCard(scene)
                        }
                    }
                }
                if (result.omittedItemCount > 0) {
                    Text(
                        "${result.omittedItemCount} malformed provider record(s) were omitted.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    result.assetAccessNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (paginationError != null) {
                    Text(
                        paginationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (result.nextCursor != null) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoadingMore,
                        onClick = onLoadMore,
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.width(16.dp).height(16.dp),
                            )
                        } else {
                            Text("Load more products")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun CatalogSceneCard(scene: Sentinel2Scene) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                scene.productId,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${scene.platform ?: "Sentinel-2"} · ${formatUtc(scene.acquiredAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
            scene.processingVersion?.let { version ->
                Text(
                    "Processing baseline: $version",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Scene cloud cover: ${
                    scene.cloudCoverPercent?.let { String.format(Locale.US, "%.1f%%", it) }
                        ?: "not reported"
                }",
                style = MaterialTheme.typography.bodySmall,
            )
            val assetLabels = scene.assets.take(8).joinToString { asset ->
                asset.groundSamplingDistanceMeters?.let { "${asset.key} ${it}m" } ?: asset.key
            }
            if (assetLabels.isNotBlank()) {
                Text(
                    "Assets: $assetLabels",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (scene.requiresProviderAuthentication) {
                Text(
                    "Provider authentication may be required for asset access.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(scene.catalogItemUrl)),
                        )
                    }
                },
            ) {
                Text("Open provider record")
            }
        }
    }
}

private fun formatUtc(value: String): String {
    val normalized = runCatching { Instant.parse(value).toString() }.getOrDefault(value)
    return "${normalized.replace("T", " ").removeSuffix("Z")} UTC"
}
