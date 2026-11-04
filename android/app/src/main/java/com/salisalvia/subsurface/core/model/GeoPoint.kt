package com.salisalvia.subsurface.core.model

/** WGS84 coordinate owned by the domain layer (longitude, latitude order in GeoJSON). */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
    }
}
