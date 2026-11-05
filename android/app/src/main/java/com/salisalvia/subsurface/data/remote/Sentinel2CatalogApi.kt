package com.salisalvia.subsurface.data.remote

import com.salisalvia.subsurface.core.model.GeoPoint
import java.time.Instant

data class Sentinel2Asset(
    val key: String,
    val groundSamplingDistanceMeters: Int?,
)

data class Sentinel2Scene(
    val productId: String,
    val acquiredAt: String,
    val cloudCoverPercent: Double?,
    val platform: String?,
    val processingVersion: String?,
    val boundingBoxWestSouthEastNorth: List<Double>,
    val assets: List<Sentinel2Asset>,
    val requiresProviderAuthentication: Boolean,
    val catalogItemUrl: String,
)

data class Sentinel2CatalogResult(
    val scenes: List<Sentinel2Scene>,
    val nextCursor: String?,
    val omittedItemCount: Int,
    val cloudCoverNote: String,
    val assetAccessNote: String,
    val scientificNotice: String,
)

/** Client contract for real Sentinel-2 product metadata; image assets are not downloaded here. */
interface Sentinel2CatalogApi {
    suspend fun search(
        vertices: List<GeoPoint>,
        datetimeStart: Instant,
        datetimeEnd: Instant,
        maxCloudCoverPercent: Int,
        cursor: String? = null,
        limit: Int = 20,
    ): Sentinel2CatalogResult
}
