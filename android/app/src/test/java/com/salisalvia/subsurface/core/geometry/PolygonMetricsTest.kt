package com.salisalvia.subsurface.core.geometry

import com.salisalvia.subsurface.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolygonMetricsTest {
    @Test
    fun smallSquareHasPositiveApproximateArea() {
        val points = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(0.0, 0.01),
            GeoPoint(0.01, 0.01),
            GeoPoint(0.01, 0.0),
        )
        val area = PolygonMetrics.areaSquareMeters(points)
        assertTrue(area in 1_200_000.0..1_300_000.0)
    }

    @Test
    fun detectsAntimeridianCrossing() {
        val points = listOf(
            GeoPoint(10.0, 179.0),
            GeoPoint(10.0, -179.0),
            GeoPoint(11.0, -179.0),
        )
        assertTrue(PolygonMetrics.crossesAntimeridian(points))
    }

    @Test
    fun rejectsSelfIntersectingPolygon() {
        val points = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(1.0, 1.0),
            GeoPoint(0.0, 1.0),
            GeoPoint(1.0, 0.0),
        )
        assertEquals("The boundary must not cross itself.", PolygonMetrics.validationError(points))
    }

    @Test
    fun rejectsConsecutiveDuplicateVertices() {
        val points = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(0.0, 1.0),
            GeoPoint(0.0, 1.0),
            GeoPoint(1.0, 0.0),
        )
        assertEquals(
            "Consecutive duplicate vertices are not allowed.",
            PolygonMetrics.validationError(points),
        )
    }

    @Test
    fun rejectsAdjacentCollinearBacktrackingEdges() {
        val points = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(2.0, 0.0),
            GeoPoint(1.0, 0.0),
            GeoPoint(1.0, 1.0),
        )
        assertEquals("The boundary must not cross itself.", PolygonMetrics.validationError(points))
    }

    @Test
    fun acceptsAValidSmallPolygon() {
        val points = listOf(
            GeoPoint(35.0, 51.0),
            GeoPoint(35.0, 51.01),
            GeoPoint(35.01, 51.01),
            GeoPoint(35.01, 51.0),
        )
        assertEquals(null, PolygonMetrics.validationError(points))
    }
}
