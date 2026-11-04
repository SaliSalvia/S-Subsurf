"""Strict API schemas; all coordinates follow GeoJSON [longitude, latitude] order."""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

Coordinate = tuple[
    Annotated[float, Field(ge=-180.0, le=180.0)],
    Annotated[float, Field(ge=-90.0, le=90.0)],
]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, allow_inf_nan=False)


def _orientation(a: Coordinate, b: Coordinate, c: Coordinate) -> float:
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def _on_segment(a: Coordinate, b: Coordinate, p: Coordinate) -> bool:
    epsilon = 1e-12
    return (
        min(a[0], b[0]) - epsilon <= p[0] <= max(a[0], b[0]) + epsilon
        and min(a[1], b[1]) - epsilon <= p[1] <= max(a[1], b[1]) + epsilon
    )


def _segments_intersect(a: Coordinate, b: Coordinate, c: Coordinate, d: Coordinate) -> bool:
    o1, o2 = _orientation(a, b, c), _orientation(a, b, d)
    o3, o4 = _orientation(c, d, a), _orientation(c, d, b)
    epsilon = 1e-12
    if ((o1 > epsilon and o2 < -epsilon) or (o1 < -epsilon and o2 > epsilon)) and (
        (o3 > epsilon and o4 < -epsilon) or (o3 < -epsilon and o4 > epsilon)
    ):
        return True
    return (
        (abs(o1) <= epsilon and _on_segment(a, b, c))
        or (abs(o2) <= epsilon and _on_segment(a, b, d))
        or (abs(o3) <= epsilon and _on_segment(c, d, a))
        or (abs(o4) <= epsilon and _on_segment(c, d, b))
    )


def _point_in_ring(point: Coordinate, ring: list[Coordinate]) -> bool:
    x, y = point
    inside = False
    for first, second in zip(ring, ring[1:] + ring[:1], strict=False):
        x1, y1 = first
        x2, y2 = second
        if (y1 > y) != (y2 > y):
            crossing_x = (x2 - x1) * (y - y1) / (y2 - y1) + x1
            if x < crossing_x:
                inside = not inside
    return inside



def _rings_intersect(first: list[Coordinate], second: list[Coordinate]) -> bool:
    """Return whether any edge from two open rings touches or crosses."""
    for first_index, a in enumerate(first):
        b = first[(first_index + 1) % len(first)]
        for second_index, c in enumerate(second):
            d = second[(second_index + 1) % len(second)]
            if _segments_intersect(a, b, c, d):
                return True
    return False


def _ring_self_intersects(ring: list[Coordinate]) -> bool:
    segment_count = len(ring)
    for first in range(segment_count):
        a, b = ring[first], ring[(first + 1) % segment_count]
        for second in range(first + 1, segment_count):
            adjacent = (
                second == first
                or (first + 1) % segment_count == second
                or (second + 1) % segment_count == first
            )
            if adjacent:
                continue
            c, d = ring[second], ring[(second + 1) % segment_count]
            if _segments_intersect(a, b, c, d):
                return True
    return False


class PolygonGeometry(StrictModel):
    type: Literal["Polygon"]
    coordinates: list[list[Coordinate]] = Field(min_length=1, max_length=20)

    @field_validator("coordinates")
    @classmethod
    def validate_rings(cls, rings: list[list[Coordinate]]) -> list[list[Coordinate]]:
        total_vertices = 0
        for ring in rings:
            if len(ring) < 4:
                raise ValueError(
                    "Each linear ring must contain at least four positions, including closure."
                )
            if ring[0] != ring[-1]:
                raise ValueError(
                    "Each linear ring must be closed: first and last positions must match."
                )
            open_ring = ring[:-1]
            if len(set(open_ring)) < 3:
                raise ValueError("Each ring must contain at least three distinct vertices.")
            has_duplicate_edges = any(
                open_ring[index] == open_ring[(index + 1) % len(open_ring)]
                for index in range(len(open_ring))
            )
            if has_duplicate_edges:
                raise ValueError("Consecutive duplicate vertices are not allowed.")
            for index, current in enumerate(open_ring):
                previous = open_ring[index - 1]
                following = open_ring[(index + 1) % len(open_ring)]
                incoming = (current[0] - previous[0], current[1] - previous[1])
                outgoing = (following[0] - current[0], following[1] - current[1])
                cross = incoming[0] * outgoing[1] - incoming[1] * outgoing[0]
                dot = incoming[0] * outgoing[0] + incoming[1] * outgoing[1]
                if abs(cross) <= 1e-12 and dot < -1e-12:
                    raise ValueError("Adjacent polygon edges must not reverse or overlap.")
            if _ring_self_intersects(open_ring):
                raise ValueError("Self-intersecting polygon rings are not supported.")
            total_vertices += len(open_ring)
        if total_vertices > 500:
            raise ValueError("A region may contain at most 500 non-duplicated vertices.")
        return rings

    @model_validator(mode="after")
    def reject_antimeridian_spanning_region(self) -> "PolygonGeometry":
        outer_lons = [point[0] for point in self.coordinates[0][:-1]]
        if outer_lons and max(outer_lons) - min(outer_lons) > 180.0:
            raise ValueError("Polygons crossing the antimeridian are not supported by this MVP.")
        outer_ring = self.coordinates[0][:-1]
        holes = [ring[:-1] for ring in self.coordinates[1:]]
        for index, hole in enumerate(holes):
            if _rings_intersect(outer_ring, hole):
                raise ValueError("Interior rings must not touch or cross the exterior boundary.")
            if not all(_point_in_ring(point, outer_ring) for point in hole):
                raise ValueError("Interior rings must be completely inside the exterior ring.")
            for other in holes[:index]:
                if _rings_intersect(other, hole):
                    raise ValueError("Interior rings must not overlap or touch each other.")
                if _point_in_ring(hole[0], other) or _point_in_ring(other[0], hole):
                    raise ValueError("Nested or overlapping interior rings are not supported.")
        return self


class RegionValidationRequest(StrictModel):
    geometry: PolygonGeometry


class RegionValidationResponse(StrictModel):
    valid: Literal[True] = True
    area_m2_approx: float = Field(ge=0.0)
    area_ha_approx: float = Field(ge=0.0)
    bbox_wsen: tuple[float, float, float, float]
    vertex_count: int = Field(ge=3)
    note: str = "Area is a spherical approximation; no subsurface inference is performed."


class Capability(StrictModel):
    id: str
    status: Literal["available", "planned", "not_configured"]
    description: str


class CapabilitiesResponse(StrictModel):
    project_version: str
    analysis_enabled: bool
    capabilities: list[Capability]
    scientific_notice: str
