package com.salisalvia.subsurface.data.remote

import com.salisalvia.subsurface.BuildConfig
import com.salisalvia.subsurface.core.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

/** HTTP implementation for the backend's bounded, provenance-preserving STAC search. */
class HttpSentinel2CatalogApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) : Sentinel2CatalogApi {

    override suspend fun search(
        vertices: List<GeoPoint>,
        datetimeStart: Instant,
        datetimeEnd: Instant,
        maxCloudCoverPercent: Int,
        cursor: String?,
        limit: Int,
    ): Sentinel2CatalogResult = withContext(Dispatchers.IO) {
        require(vertices.size in MIN_VERTICES..MAX_VERTICES) {
            "A survey region must contain $MIN_VERTICES–$MAX_VERTICES vertices."
        }
        require(datetimeStart < datetimeEnd) { "The start date must be earlier than the end date." }
        require(maxCloudCoverPercent in 0..100) { "The cloud-cover limit must be between 0 and 100%." }
        require(limit in 1..MAX_PAGE_SIZE) { "A catalogue page must contain 1–$MAX_PAGE_SIZE scenes." }

        val responseBody = HttpJsonClient.postJson(
            endpoint = HttpJsonClient.endpointUrl(baseUrl, "/v1/catalog/sentinel-2/search"),
            body = createRequestBody(
                vertices,
                datetimeStart,
                datetimeEnd,
                maxCloudCoverPercent,
                cursor,
                limit,
            ),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            failurePrefix = "Sentinel-2 catalogue search failed",
        )
        parseResponse(responseBody)
    }

    private fun createRequestBody(
        vertices: List<GeoPoint>,
        datetimeStart: Instant,
        datetimeEnd: Instant,
        maxCloudCoverPercent: Int,
        cursor: String?,
        limit: Int,
    ): String {
        val request = JSONObject()
            .put("geometry", HttpJsonClient.polygonGeometry(vertices))
            .put("datetime_start", datetimeStart.toString())
            .put("datetime_end", datetimeEnd.toString())
            .put("max_cloud_cover_pct", maxCloudCoverPercent)
            .put("limit", limit)
        if (cursor != null) request.put("cursor", cursor)
        return request.toString()
    }

    private fun parseResponse(body: String): Sentinel2CatalogResult {
        try {
            val json = JSONObject(body)
            val rawItems = json.getJSONArray("items")
            val scenes = buildList {
                for (index in 0 until rawItems.length()) {
                    val item = rawItems.getJSONObject(index)
                    val rawAssets = item.optJSONArray("assets") ?: JSONArray()
                    val assets = buildList {
                        for (assetIndex in 0 until rawAssets.length()) {
                            val asset = rawAssets.getJSONObject(assetIndex)
                            val gsd = if (asset.isNull("gsd_m")) {
                                null
                            } else {
                                asset.getInt("gsd_m")
                            }
                            add(
                                Sentinel2Asset(
                                    key = asset.getString("key"),
                                    groundSamplingDistanceMeters = gsd,
                                ),
                            )
                        }
                    }
                    add(
                        Sentinel2Scene(
                            productId = item.getString("product_id"),
                            acquiredAt = item.getString("acquired_at"),
                            cloudCoverPercent = if (item.isNull("cloud_cover_pct")) {
                                null
                            } else {
                                item.getDouble("cloud_cover_pct")
                            },
                            platform = optionalString(item, "platform"),
                            processingVersion = optionalString(item, "processing_version"),
                            boundingBoxWestSouthEastNorth = item.getJSONArray("bbox_wsen").let { bounds ->
                                if (bounds.length() != 4) {
                                    throw JSONException("Invalid product bounding box length.")
                                }
                                List(bounds.length()) { bounds.getDouble(it) }
                            },
                            assets = assets,
                            requiresProviderAuthentication =
                                item.optString("asset_access") == "provider_authentication_required",
                            catalogItemUrl = item.getString("catalog_item_url"),
                        ),
                    )
                }
            }
            return Sentinel2CatalogResult(
                scenes = scenes,
                nextCursor = optionalString(json, "next_cursor"),
                omittedItemCount = json.optInt("omitted_item_count", 0),
                cloudCoverNote = json.getString("cloud_cover_note"),
                assetAccessNote = json.getString("asset_access_note"),
                scientificNotice = json.getString("scientific_notice"),
            )
        } catch (error: JSONException) {
            throw IOException("The API returned an invalid Sentinel-2 catalogue response.", error)
        }
    }

    private fun optionalString(json: JSONObject, key: String): String? {
        if (json.isNull(key)) return null
        return json.optString(key).takeUnless { it.isBlank() || it == "null" }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 20_000
        const val MIN_VERTICES = 3
        const val MAX_VERTICES = 10
        const val MAX_PAGE_SIZE = 50
    }
}
