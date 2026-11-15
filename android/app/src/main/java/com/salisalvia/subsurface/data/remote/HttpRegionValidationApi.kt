package com.salisalvia.subsurface.data.remote

import com.salisalvia.subsurface.BuildConfig
import com.salisalvia.subsurface.core.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** HTTP implementation. All blocking network work is isolated from the UI thread. */
class HttpRegionValidationApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) : RegionValidationApi {

    override suspend fun validate(vertices: List<GeoPoint>): RegionValidationResult =
        withContext(Dispatchers.IO) {
            require(vertices.size in MIN_VERTICES..MAX_VERTICES) {
                "A survey region must contain $MIN_VERTICES–$MAX_VERTICES vertices."
            }
            val responseBody = HttpJsonClient.postJson(
                endpoint = HttpJsonClient.endpointUrl(baseUrl, "/v1/regions/validate"),
                body = createRequestBody(vertices),
                connectTimeoutMs = CONNECT_TIMEOUT_MS,
                readTimeoutMs = READ_TIMEOUT_MS,
                failurePrefix = "Region validation failed",
            )
            parseResponse(responseBody)
        }

    private fun createRequestBody(vertices: List<GeoPoint>): String =
        JSONObject()
            .put("geometry", HttpJsonClient.polygonGeometry(vertices))
            .toString()

    private fun parseResponse(body: String): RegionValidationResult {
        try {
            val json = JSONObject(body)
            val bounds = json.getJSONArray("bbox_wsen")
            if (bounds.length() != 4) throw JSONException("Invalid bounding box length.")
            return RegionValidationResult(
                areaSquareMeters = json.getDouble("area_m2_approx"),
                areaHectares = json.getDouble("area_ha_approx"),
                west = bounds.getDouble(0),
                south = bounds.getDouble(1),
                east = bounds.getDouble(2),
                north = bounds.getDouble(3),
                vertexCount = json.getInt("vertex_count"),
                note = json.optString("note", "Region validated by the API."),
            )
        } catch (error: JSONException) {
            throw IOException("The API returned an invalid region-validation response.", error)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        const val MIN_VERTICES = 3
        const val MAX_VERTICES = 10
    }
}
