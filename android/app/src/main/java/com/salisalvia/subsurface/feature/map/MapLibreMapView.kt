package com.salisalvia.subsurface.feature.map

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.salisalvia.subsurface.core.model.GeoPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val REGION_SOURCE_ID = "survey-region-source"
private const val REGION_LINE_LAYER_ID = "survey-region-line"
private const val REGION_VERTICES_LAYER_ID = "survey-region-vertices"

/** MapLibre adapter. The app domain remains independent from MapLibre types. */
@Composable
internal fun MapLibreMapView(
    vertices: List<GeoPoint>,
    onMapTap: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnMapTap by rememberUpdatedState(onMapTap)
    val latestVertices by rememberUpdatedState(vertices)
    val sourceState = remember { mutableStateOf<GeoJsonSource?>(null) }
    val mapView = remember(context) {
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var resumed = false
        var destroyed = false

        fun pauseIfNeeded() {
            if (resumed && !destroyed) {
                mapView.onPause()
                resumed = false
            }
        }

        fun stopIfNeeded() {
            pauseIfNeeded()
            if (started && !destroyed) {
                mapView.onStop()
                started = false
            }
        }

        fun destroyIfNeeded() {
            stopIfNeeded()
            if (!destroyed && !mapView.isDestroyed()) {
                mapView.onDestroy()
                destroyed = true
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started && !destroyed) {
                    mapView.onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> if (!resumed && !destroyed) {
                    mapView.onResume()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> pauseIfNeeded()
                Lifecycle.Event.ON_STOP -> stopIfNeeded()
                Lifecycle.Event.ON_DESTROY -> destroyIfNeeded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroyIfNeeded()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            mapView.apply {
                getMapAsync { map ->
                    val styleJson = viewContext.assets.open("map/style.json")
                        .bufferedReader()
                        .use { it.readText() }
                    map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(35.6892, 51.3890))
                            .zoom(5.0)
                            .build()

                        val source = GeoJsonSource(
                            REGION_SOURCE_ID,
                            FeatureCollection.fromFeatures(emptyArray()),
                        )
                        style.addSource(source)
                        style.addLayer(
                            LineLayer(REGION_LINE_LAYER_ID, REGION_SOURCE_ID).withProperties(
                                lineColor(Color.rgb(241, 170, 71)),
                                lineWidth(3.0f),
                                lineJoin(LINE_JOIN_ROUND),
                            ),
                        )
                        style.addLayer(
                            CircleLayer(REGION_VERTICES_LAYER_ID, REGION_SOURCE_ID).withProperties(
                                circleRadius(5.0f),
                                circleColor(Color.WHITE),
                                circleStrokeColor(Color.rgb(241, 170, 71)),
                                circleStrokeWidth(2.0f),
                            ),
                        )
                        sourceState.value = source
                        map.addOnMapClickListener { coordinate ->
                            latestOnMapTap(GeoPoint(coordinate.latitude, coordinate.longitude))
                            true
                        }
                        updateRegionSource(source, latestVertices)
                    }
                }
            }
        },
        update = {
            sourceState.value?.let { updateRegionSource(it, latestVertices) }
        },
    )

    LaunchedEffect(vertices, sourceState.value) {
        sourceState.value?.let { updateRegionSource(it, vertices) }
    }
}

private fun updateRegionSource(source: GeoJsonSource, vertices: List<GeoPoint>) {
    val features = mutableListOf<Feature>()
    if (vertices.size >= 2) {
        val points = vertices.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        if (vertices.size >= 3) points.add(points.first())
        features += Feature.fromGeometry(LineString.fromLngLats(points))
    }
    vertices.forEach { point ->
        features += Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}
