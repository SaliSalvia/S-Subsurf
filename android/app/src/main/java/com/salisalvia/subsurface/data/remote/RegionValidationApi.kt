package com.salisalvia.subsurface.data.remote

import com.salisalvia.subsurface.core.model.GeoPoint

/** Result returned by the authoritative server-side geometry validator. */
data class RegionValidationResult(
    val areaSquareMeters: Double,
    val areaHectares: Double,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val vertexCount: Int,
    val note: String,
)

/** Client for the small region-validation API used by the first MVP. */
interface RegionValidationApi {
    suspend fun validate(vertices: List<GeoPoint>): RegionValidationResult
}
