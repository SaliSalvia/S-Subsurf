package com.salisalvia.subsurface.data.remote

import com.salisalvia.subsurface.BuildConfig
import com.salisalvia.subsurface.core.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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
        require(vertices.size in 3..10) { "A survey region must contain 3–10 vertices." }
        require(datetimeStart < datetimeEnd) { "The start date must be earlier than the end date." }
        require(maxCloudCoverPercent in 0..100) { "The cloud-cover limit must be between 0 and 100%." }
        require(limit in 1..50) { "A catalogue page must contain 1–50 scenes." }

        val endpoint = URL("${baseUrl.trimEnd('/')}/v1/catalog/sentinel-2/search")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "SubsurfaceExplorer-Android/0.1.0")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(
                    createRequestBody(
                        vertices,
                        datetimeStart,
                        datetimeEnd,
                        maxCloudCoverPercent,
                        cursor,
                        limit,
                    ),
                )
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = responseStream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (statusCode !in 200..299) {
                throw IOException(readServerError(responseBody, statusCode))
            }
            parseResponse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun createRequestBody(
        vertices: List<GeoPoint>,
        datetimeStart: Instant,
        datetimeEnd: Instant,
        maxCloudCoverPercent: Int,
        cursor: String?,
        limit: Int,
    ): String {
        val ring = JSONArray()
        vertices.forEach { point ->
            ring.put(JSONArray().put(point.longitude).put(point.latitude))
        }
        val first = vertices.first()
        ring.put(JSONArray().put(first.longitude).put(first.latitude))

        val request = JSONObject()
            .put(
                "geometry",
                JSONObject()
                    .put("type", "Polygon")
                    .put("coordinates", JSONArray().put(ring)),
            )
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

    private fun readServerError(body: String, statusCode: Int): String {
        val detail = runCatching { JSONObject(body).opt("detail") }.getOrNull()
        val message = when (detail) {
            is String -> detail
            is JSONArray -> buildList {
                for (index in 0 until detail.length()) {
                    val item = detail.optJSONObject(index) ?: continue
                    val location = item.optJSONArray("loc")
                    val field = location?.let { values ->
                        (0 until values.length()).joinToString(".") { values.optString(it) }
                    }
                    val explanation = item.optString("msg")
                    add(if (field.isNullOrBlank()) explanation else "$field: $explanation")
                }
            }.joinToString("; ")
            else -> null
        }
        return message?.takeIf(String::isNotBlank)
            ?: "Sentinel-2 catalogue search failed (HTTP $statusCode). Check the API and try again."
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
