package com.salisalvia.subsurface.core.geometry

import com.salisalvia.subsurface.core.model.GeoPoint
import kotlin.math.abs
import kotlin.math.sin

/** Approximate geodesic metrics for small survey polygons. */
object PolygonMetrics {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /**
     * Returns an approximate spherical area in square metres.
     * The caller must validate the open polygon boundary first.
     */
    fun areaSquareMeters(vertices: List<GeoPoint>): Double {
        require(vertices.size >= 3) { "At least three vertices are required." }
        require(!crossesAntimeridian(vertices)) { "Polygons crossing the antimeridian are not supported yet." }

        var sum = 0.0
        for (index in vertices.indices) {
            val current = vertices[index]
            val next = vertices[(index + 1) % vertices.size]
            val lonDelta = Math.toRadians(next.longitude - current.longitude)
            val lat1 = Math.toRadians(current.latitude)
            val lat2 = Math.toRadians(next.latitude)
            sum += lonDelta * (2.0 + sin(lat1) + sin(lat2))
        }
        return abs(sum) * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2.0
    }

    /**
     * Returns a user-readable validation error for a simple, single-ring survey polygon.
     * The backend repeats these checks and remains the authoritative validator.
     */
    fun validationError(vertices: List<GeoPoint>): String? {
        if (vertices.size < 3) return "At least three vertices are required."
        if (crossesAntimeridian(vertices)) {
            return "Polygons crossing the 180° meridian are not supported yet."
        }
        if (vertices.distinct().size < 3) return "At least three distinct vertices are required."
        if (vertices.indices.any { index ->
                vertices[index] == vertices[(index + 1) % vertices.size]
            }
        ) return "Consecutive duplicate vertices are not allowed."
        if (hasSelfIntersection(vertices)) return "The boundary must not cross itself."
        if (areaSquareMeters(vertices) <= 1.0) return "The region area is too small or zero."
        return null
    }

    private fun hasSelfIntersection(vertices: List<GeoPoint>): Boolean {
        val count = vertices.size
        for (index in 0 until count) {
            val previous = vertices[(index - 1 + count) % count]
            val current = vertices[index]
            val next = vertices[(index + 1) % count]
            val incomingX = current.longitude - previous.longitude
            val incomingY = current.latitude - previous.latitude
            val outgoingX = next.longitude - current.longitude
            val outgoingY = next.latitude - current.latitude
            val cross = incomingX * outgoingY - incomingY * outgoingX
            val dot = incomingX * outgoingX + incomingY * outgoingY
            if (abs(cross) <= 1e-12 && dot < -1e-12) return true
        }
        for (firstIndex in 0 until count) {
            val a = vertices[firstIndex]
            val b = vertices[(firstIndex + 1) % count]
            for (secondIndex in firstIndex + 1 until count) {
                if (secondIndex == firstIndex + 1 ||
                    (firstIndex == 0 && secondIndex == count - 1)
                ) continue
                val c = vertices[secondIndex]
                val d = vertices[(secondIndex + 1) % count]
                if (segmentsIntersect(a, b, c, d)) return true
            }
        }
        return false
    }

    private fun orientation(a: GeoPoint, b: GeoPoint, c: GeoPoint): Double =
        (b.longitude - a.longitude) * (c.latitude - a.latitude) -
            (b.latitude - a.latitude) * (c.longitude - a.longitude)

    private fun onSegment(a: GeoPoint, b: GeoPoint, point: GeoPoint): Boolean {
        val epsilon = 1e-12
        return point.longitude >= minOf(a.longitude, b.longitude) - epsilon &&
            point.longitude <= maxOf(a.longitude, b.longitude) + epsilon &&
            point.latitude >= minOf(a.latitude, b.latitude) - epsilon &&
            point.latitude <= maxOf(a.latitude, b.latitude) + epsilon
    }

    private fun segmentsIntersect(
        a: GeoPoint,
        b: GeoPoint,
        c: GeoPoint,
        d: GeoPoint,
    ): Boolean {
        val epsilon = 1e-12
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)
        val crosses = ((o1 > epsilon && o2 < -epsilon) ||
            (o1 < -epsilon && o2 > epsilon)) &&
            ((o3 > epsilon && o4 < -epsilon) ||
                (o3 < -epsilon && o4 > epsilon))
        if (crosses) return true
        return (abs(o1) <= epsilon && onSegment(a, b, c)) ||
            (abs(o2) <= epsilon && onSegment(a, b, d)) ||
            (abs(o3) <= epsilon && onSegment(c, d, a)) ||
            (abs(o4) <= epsilon && onSegment(c, d, b))
    }

    /** A simple anti-meridian guard avoids misleading bounding-box and area results in the MVP. */
    fun crossesAntimeridian(vertices: List<GeoPoint>): Boolean {
        if (vertices.size < 2) return false
        val longitudes = vertices.map(GeoPoint::longitude)
        return (longitudes.maxOrNull() ?: 0.0) - (longitudes.minOrNull() ?: 0.0) > 180.0
    }
}
