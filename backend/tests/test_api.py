from __future__ import annotations

from fastapi.testclient import TestClient

from subsurface_api.main import app

client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_capabilities_do_not_claim_subsurface_detection() -> None:
    response = client.get("/v1/capabilities")
    assert response.status_code == 200
    payload = response.json()
    assert payload["analysis_enabled"] is False
    assert any(
        item["id"] == "subsurface_classification" and item["status"] == "not_configured"
        for item in payload["capabilities"]
    )


def test_region_validation_endpoint() -> None:
    response = client.post(
        "/v1/regions/validate",
        json={
            "geometry": {
                "type": "Polygon",
                "coordinates": [[[0, 0], [0.01, 0], [0.01, 0.01], [0, 0.01], [0, 0]]],
            }
        },
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["valid"] is True
    assert payload["area_m2_approx"] > 0
    assert payload["vertex_count"] == 4


def test_region_validation_rejects_open_ring() -> None:
    response = client.post(
        "/v1/regions/validate",
        json={"geometry": {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 1]]]}},
    )
    assert response.status_code == 422


def test_region_validation_rejects_self_intersection() -> None:
    response = client.post(
        "/v1/regions/validate",
        json={
            "geometry": {
                "type": "Polygon",
                "coordinates": [[[0, 0], [1, 1], [0, 1], [1, 0], [0, 0]]],
            }
        },
    )
    assert response.status_code == 422
    errors = response.json()["detail"]
    assert any("Self-intersecting" in item["msg"] for item in errors)


def test_region_validation_rejects_zero_area() -> None:
    response = client.post(
        "/v1/regions/validate",
        json={
            "geometry": {
                "type": "Polygon",
                "coordinates": [[[0, 0], [1, 0], [2, 0], [0, 0]]],
            }
        },
    )
    assert response.status_code == 422
    assert any(
        "reverse or overlap" in item["msg"] for item in response.json()["detail"]
    )
