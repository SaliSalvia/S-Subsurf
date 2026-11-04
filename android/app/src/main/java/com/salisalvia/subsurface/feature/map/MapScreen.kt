package com.salisalvia.subsurface.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salisalvia.subsurface.core.geometry.PolygonMetrics
import com.salisalvia.subsurface.core.model.GeoPoint
import com.salisalvia.subsurface.data.remote.RegionValidationResult
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    validationState: RegionValidationUiState,
    onValidateRegion: (List<GeoPoint>) -> Unit,
    onDismissValidation: () -> Unit,
) {
    val vertices = remember { mutableStateListOf<GeoPoint>() }
    var isDrawing by rememberSaveable { mutableStateOf(false) }

    val validationError = remember(vertices.toList()) {
        PolygonMetrics.validationError(vertices)
    }
    val areaSquareMeters = remember(vertices.toList(), validationError) {
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
                                isDrawing = false
                            } else {
                                if (vertices.size >= 3) vertices.clear()
                                isDrawing = true
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

                    if (isDrawing || vertices.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = vertices.isNotEmpty(),
                                onClick = { vertices.removeAt(vertices.lastIndex) },
                            ) { Text("Undo point") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = vertices.isNotEmpty(),
                                onClick = { vertices.clear() },
                            ) { Text("Clear") }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = vertices.size >= 3 && validationError == null &&
                                    areaSquareMeters != null && !isDrawing &&
                                    validationState != RegionValidationUiState.Loading,
                                onClick = { onValidateRegion(vertices.toList()) },
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
                vertices = vertices.toList(),
                onMapTap = { point ->
                    if (isDrawing && vertices.size < 10) {
                        vertices.add(point)
                        if (vertices.size == 10) isDrawing = false
                    }
                },
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
                        "Region validation is connected. Satellite data ingestion is not connected yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    when (val state = validationState) {
        is RegionValidationUiState.Success -> ValidationResultDialog(
            result = state.result,
            onDismiss = onDismissValidation,
        )
        is RegionValidationUiState.Error -> AlertDialog(
            onDismissRequest = onDismissValidation,
            title = { Text("Region validation failed") },
            text = { Text(state.message) },
            confirmButton = {
                Button(onClick = onDismissValidation) { Text("Close") }
            },
        )
        RegionValidationUiState.Idle, RegionValidationUiState.Loading -> Unit
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
