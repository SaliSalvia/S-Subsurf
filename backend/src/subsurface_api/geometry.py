"""Deterministic geometry checks and approximate spherical area calculations."""

from __future__ import annotations

from math import isfinite, radians, sin

from .schemas import PolygonGeometry

EARTH_RADIUS_M = 6_371_008.8


def ring_area_m2(ring: list[tuple[float, float]]) -> float:
    """Calculate the absolute spherical area of a closed GeoJSON ring."""
    if len(ring) < 4 or ring[0] != ring[-1]:
        raise ValueError("A closed ring with at least three vertices is required.")

    total = 0.0
    for current, following in zip(ring, ring[1:], strict=False):
        lon1, lat1 = current
        lon2, lat2 = following
        total += radians(lon2 - lon1) * (2.0 + sin(radians(lat1)) + sin(radians(lat2)))
    area = abs(total) * EARTH_RADIUS_M**2 / 2.0
    if not isfinite(area):
        raise ValueError("Could not calculate a finite region area.")
    return area


def polygon_metrics(
    geometry: PolygonGeometry,
) -> tuple[float, tuple[float, float, float, float], int]:
    """Return area, [west, south, east, north], and non-duplicated outer-ring vertex count."""
    outer = geometry.coordinates[0]
    area = ring_area_m2(outer)
    for hole in geometry.coordinates[1:]:
        area -= ring_area_m2(hole)
    if area <= 1e-6:
        raise ValueError(
            "Polygon area must be positive; interior rings may not consume the exterior area."
        )

    longitudes = [point[0] for point in outer[:-1]]
    latitudes = [point[1] for point in outer[:-1]]
    bbox = (min(longitudes), min(latitudes), max(longitudes), max(latitudes))
    return area, bbox, len(outer) - 1
