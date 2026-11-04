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

/** HTTP implementation. All blocking network work is isolated from the UI thread. */
class HttpRegionValidationApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) : RegionValidationApi {

    override suspend fun validate(vertices: List<GeoPoint>): RegionValidationResult =
        withContext(Dispatchers.IO) {
            require(vertices.size in 3..10) { "A survey region must contain 3–10 vertices." }
            val endpoint = URL("${baseUrl.trimEnd('/')}/v1/regions/validate")
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
                val requestBody = createRequestBody(vertices)
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
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

    private fun createRequestBody(vertices: List<GeoPoint>): String {
        val ring = JSONArray()
        vertices.forEach { point ->
            ring.put(JSONArray().put(point.longitude).put(point.latitude))
        }
        val first = vertices.first()
        ring.put(JSONArray().put(first.longitude).put(first.latitude))
        return JSONObject()
            .put(
                "geometry",
                JSONObject()
                    .put("type", "Polygon")
                    .put("coordinates", JSONArray().put(ring)),
            )
            .toString()
    }

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
            ?: "Region validation failed (HTTP $statusCode). Check the API address and try again."
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
    }
}
