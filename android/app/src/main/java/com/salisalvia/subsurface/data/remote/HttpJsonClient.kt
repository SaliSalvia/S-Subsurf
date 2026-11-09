package com.salisalvia.subsurface.data.remote

import com.salisalvia.subsurface.BuildConfig
import com.salisalvia.subsurface.core.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared HTTP plumbing for the small JSON APIs.
 *
 * Keeps connection setup, timeout handling, error-body parsing, and GeoJSON ring
 * encoding in one tested place so each endpoint client stays focused on its own
 * request/response contract. All functions are blocking and must be called from
 * [kotlinx.coroutines.Dispatchers.IO].
 */
internal object HttpJsonClient {
    val userAgent: String = "SubsurfaceExplorer-Android/${BuildConfig.VERSION_NAME}"

    /**
     * Builds the endpoint URL for [path] (for example `/v1/regions/validate`).
     *
     * @throws IOException when the configured base URL is not usable. A `.invalid`
     * host means this build was shipped without a real endpoint (RFC 2606) and gets
     * a dedicated, actionable message instead of a raw DNS failure.
     */
    fun endpointUrl(baseUrl: String, path: String): URL {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val url = runCatching { URL("${baseUrl.trimEnd('/')}$normalizedPath") }.getOrElse { error ->
            throw IOException(
                "The configured API address is not a valid URL: ${error.message}",
                error,
            )
        }
        if (url.host.isNullOrBlank() || url.host.endsWith(".invalid", ignoreCase = true)) {
            throw IOException(
                "The API endpoint is not configured for this build. " +
                    "Rebuild with -PAPI_BASE_URL=<reachable-server-url> " +
                    "(HTTPS for release builds).",
            )
        }
        return url
    }

    /** POSTs [body] as JSON and returns the response body, or throws [IOException]. */
    fun postJson(
        endpoint: URL,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        failurePrefix: String,
    ): String {
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
            val statusCode = connection.responseCode
            val responseBody = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (statusCode !in 200..299) {
                throw IOException(serverErrorMessage(responseBody, statusCode, failurePrefix))
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    /** Encodes open [vertices] as a closed GeoJSON linear ring (first point repeated). */
    fun closedRing(vertices: List<GeoPoint>): JSONArray {
        val ring = JSONArray()
        vertices.forEach { point ->
            ring.put(JSONArray().put(point.longitude).put(point.latitude))
        }
        val first = vertices.first()
        ring.put(JSONArray().put(first.longitude).put(first.latitude))
        return ring
    }

    fun polygonGeometry(vertices: List<GeoPoint>): JSONObject =
        JSONObject()
            .put("type", "Polygon")
            .put("coordinates", JSONArray().put(closedRing(vertices)))

    /**
     * Extracts a human-readable message from a FastAPI error body.
     * FastAPI returns either `{"detail": "..."}` or a list of validation errors.
     */
    fun serverErrorMessage(body: String, statusCode: Int, failurePrefix: String): String {
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
            ?: "$failurePrefix (HTTP $statusCode). Check the API address and try again."
    }
}
