from __future__ import annotations

import pytest
from pydantic import ValidationError

from subsurface_api.geometry import polygon_metrics
from subsurface_api.schemas import PolygonGeometry


def polygon(coords: list[list[float]]) -> PolygonGeometry:
    return PolygonGeometry(type="Polygon", coordinates=[[tuple(point) for point in coords]])


def test_small_square_has_expected_positive_area() -> None:
    geometry = polygon([[0.0, 0.0], [0.01, 0.0], [0.01, 0.01], [0.0, 0.01], [0.0, 0.0]])
    area, bbox, vertices = polygon_metrics(geometry)
    assert 1_200_000 < area < 1_300_000
    assert bbox == (0.0, 0.0, 0.01, 0.01)
    assert vertices == 4


def test_unclosed_ring_is_rejected() -> None:
    with pytest.raises(ValidationError, match="closed"):
        polygon([[0, 0], [1, 0], [1, 1], [0, 1]])


def test_invalid_latitude_is_rejected() -> None:
    with pytest.raises(ValidationError):
        polygon([[0, 91], [1, 91], [1, 92], [0, 92], [0, 91]])


def test_antimeridian_spanning_region_is_rejected() -> None:
    with pytest.raises(ValidationError, match="antimeridian"):
        polygon([[179, 10], [-179, 10], [-179, 11], [179, 11], [179, 10]])


def test_hole_area_is_subtracted() -> None:
    geometry = PolygonGeometry(
        type="Polygon",
        coordinates=[
            [(0, 0), (0.02, 0), (0.02, 0.02), (0, 0.02), (0, 0)],
            [(0.005, 0.005), (0.015, 0.005), (0.015, 0.015), (0.005, 0.015), (0.005, 0.005)],
        ],
    )
    area_with_hole, _, _ = polygon_metrics(geometry)
    outer_only, _, _ = polygon_metrics(
        PolygonGeometry(type="Polygon", coordinates=[geometry.coordinates[0]])
    )
    assert 0 < area_with_hole < outer_only


def test_self_intersecting_ring_is_rejected() -> None:
    with pytest.raises(ValidationError, match="Self-intersecting"):
        polygon([[0, 0], [1, 1], [0, 1], [1, 0], [0, 0]])


def test_consecutive_duplicate_vertices_are_rejected() -> None:
    with pytest.raises(ValidationError, match="duplicate"):
        polygon([[0, 0], [1, 0], [1, 0], [1, 1], [0, 0]])


def test_hole_outside_exterior_is_rejected() -> None:
    with pytest.raises(ValidationError, match="inside the exterior"):
        PolygonGeometry(
            type="Polygon",
            coordinates=[
                [(0, 0), (1, 0), (1, 1), (0, 1), (0, 0)],
                [(2, 2), (3, 2), (3, 3), (2, 3), (2, 2)],
            ],
        )


def test_zero_area_polygon_is_rejected_even_if_schema_is_bypassed() -> None:
    geometry = PolygonGeometry.model_construct(
        type="Polygon",
        coordinates=[[(0, 0), (1, 0), (2, 0), (0, 0)]],
    )
    with pytest.raises(ValueError, match="area must be positive"):
        polygon_metrics(geometry)


def test_hole_crossing_outer_boundary_is_rejected() -> None:
    with pytest.raises(ValidationError, match="touch or cross"):
        PolygonGeometry(
            type="Polygon",
            coordinates=[
                [(0, 0), (2, 0), (2, 2), (0, 2), (0, 0)],
                [(1, 1), (3, 1), (3, 1.5), (1, 1.5), (1, 1)],
            ],
        )


def test_overlapping_holes_are_rejected() -> None:
    with pytest.raises(ValidationError, match="overlap or touch"):
        PolygonGeometry(
            type="Polygon",
            coordinates=[
                [(0, 0), (5, 0), (5, 5), (0, 5), (0, 0)],
                [(1, 1), (3, 1), (3, 3), (1, 3), (1, 1)],
                [(2, 2), (4, 2), (4, 4), (2, 4), (2, 2)],
            ],
        )


def test_adjacent_collinear_edges_may_not_reverse_and_overlap() -> None:
    with pytest.raises(ValidationError, match="reverse or overlap"):
        polygon([[0, 0], [2, 0], [1, 0], [1, 1], [0, 0]])


def test_non_finite_coordinates_are_rejected() -> None:
    with pytest.raises(ValidationError):
        polygon([[0, 0], [1, 0], [1, float("nan")], [0, 1], [0, 0]])
