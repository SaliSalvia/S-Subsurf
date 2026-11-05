from __future__ import annotations

import asyncio
import json
from typing import Any

import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from subsurface_api.catalog import (
    CatalogProviderError,
    CatalogTimeoutError,
    Sentinel2Catalog,
)
from subsurface_api.main import app, get_sentinel2_catalog
from subsurface_api.schemas import Sentinel2CatalogSearchRequest

GEOMETRY = {
    "type": "Polygon",
    "coordinates": [[[51.36, 35.66], [51.43, 35.66], [51.43, 35.73], [51.36, 35.73], [51.36, 35.66]]],
}

VALID_ITEM = {
    "type": "Feature",
    "id": "S2C_MSIL2A_20261102T072041_N0513_R006_T39SWV_20261102T103105",
    "collection": "sentinel-2-l2a",
    "bbox": [50.99, 35.14, 52.22, 36.14],
    "properties": {
        "datetime": "2026-11-02T07:20:41.025000Z",
        "platform": "sentinel-2c",
        "eo:cloud_cover": 10.81,
        "processing:version": "05.13",
        "auth:schemes": {"s3": {"type": "s3"}},
    },
    "assets": {
        "B04_10m": {
            "roles": ["data", "reflectance"],
            "gsd": 10,
            "auth:refs": ["s3"],
            "href": "s3://private-provider-product",
        },
        "SCL_20m": {
            "roles": ["data", "quality"],
            "gsd": 20,
            "auth:refs": ["s3"],
            "href": "s3://private-provider-product",
        },
        "thumbnail": {"roles": ["thumbnail"], "href": "https://example.invalid/image"},
    },
    "links": [],
}


def make_request(**overrides: Any) -> Sentinel2CatalogSearchRequest:
    values: dict[str, Any] = {
        "geometry": GEOMETRY,
        "datetime_start": "2026-10-01T00:00:00Z",
        "datetime_end": "2026-11-04T00:00:00Z",
        "max_cloud_cover_pct": 20,
        "limit": 3,
    }
    values.update(overrides)
    return Sentinel2CatalogSearchRequest.model_validate(values)


def test_catalog_search_sends_bounded_cql2_request_and_maps_provenance() -> None:
    async def handler(upstream_request: httpx.Request) -> httpx.Response:
        body = json.loads(upstream_request.content)
        assert str(upstream_request.url) == "https://stac.dataspace.copernicus.eu/v1/search"
        assert body["collections"] == ["sentinel-2-l2a"]
        assert body["datetime"] == "2026-10-01T00:00:00Z/2026-11-04T00:00:00Z"
        assert body["limit"] == 3
        assert body["filter-lang"] == "cql2-json"
        assert body["filter"] == {
            "op": "<=",
            "args": [{"property": "eo:cloud_cover"}, 20.0],
        }
        assert body["intersects"] == GEOMETRY
        return httpx.Response(
            200,
            json={
                "type": "FeatureCollection",
                "features": [VALID_ITEM],
                "links": [{"rel": "next", "body": {"token": "next-page-token"}}],
            },
        )

    result = asyncio.run(
        Sentinel2Catalog(transport=httpx.MockTransport(handler)).search(make_request())
    )

    assert result.source_catalog == "Copernicus Data Space Ecosystem STAC"
    assert result.collection == "sentinel-2-l2a"
    assert result.max_cloud_cover_pct == 20
    assert result.next_cursor == "next-page-token"
    assert result.omitted_item_count == 0
    assert len(result.items) == 1
    scene = result.items[0]
    assert scene.product_id == VALID_ITEM["id"]
    assert scene.acquired_at.isoformat() == "2026-11-02T07:20:41.025000+00:00"
    assert scene.cloud_cover_pct == 10.81
    assert scene.platform == "sentinel-2c"
    assert scene.processing_version == "05.13"
    assert scene.bbox_wsen == (50.99, 35.14, 52.22, 36.14)
    assert [(asset.key, asset.gsd_m) for asset in scene.assets] == [
        ("B04_10m", 10),
        ("SCL_20m", 20),
    ]
    assert scene.asset_access == "provider_authentication_required"
    serialized = result.model_dump_json()
    assert "s3://private-provider-product" not in serialized
    assert "example.invalid/image" not in serialized
    assert result.cloud_cover_note.startswith("Cloud cover is the provider's scene-level estimate")


def test_search_rejects_naive_or_reversed_datetimes() -> None:
    with pytest.raises(ValidationError, match="must include a timezone"):
        make_request(datetime_start="2026-10-01T00:00:00")

    with pytest.raises(ValidationError, match="earlier than"):
        make_request(
            datetime_start="2026-11-04T00:00:00Z",
            datetime_end="2026-10-01T00:00:00Z",
        )


def test_search_rejects_out_of_range_limits_and_extra_fields() -> None:
    with pytest.raises(ValidationError):
        make_request(limit=51)

    with pytest.raises(ValidationError):
        make_request(allow_external_url="https://example.invalid")


def test_provider_timeout_is_distinguished() -> None:
    async def handler(_request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("provider timeout")

    with pytest.raises(CatalogTimeoutError):
        asyncio.run(
            Sentinel2Catalog(transport=httpx.MockTransport(handler)).search(make_request())
        )


def test_provider_http_error_is_sanitized() -> None:
    async def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, text="provider internal details")

    with pytest.raises(CatalogProviderError):
        asyncio.run(
            Sentinel2Catalog(transport=httpx.MockTransport(handler)).search(make_request())
        )


def test_cursor_is_forwarded_as_stac_token() -> None:
    async def handler(upstream_request: httpx.Request) -> httpx.Response:
        assert json.loads(upstream_request.content)["token"] == "next-page-token"
        return httpx.Response(200, json={"features": [], "links": []})

    result = asyncio.run(
        Sentinel2Catalog(transport=httpx.MockTransport(handler)).search(
            make_request(cursor="next-page-token")
        )
    )
    assert result.items == []
    assert result.next_cursor is None


def test_invalid_provider_items_are_counted_not_returned() -> None:
    async def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "features": [VALID_ITEM, {"id": "broken", "properties": {}, "bbox": []}],
                "links": [],
            },
        )

    result = asyncio.run(
        Sentinel2Catalog(transport=httpx.MockTransport(handler)).search(make_request())
    )
    assert len(result.items) == 1
    assert result.omitted_item_count == 1


def test_catalog_api_reports_capability_and_returns_real_schema() -> None:
    async def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"features": [VALID_ITEM], "links": []},
        )

    app.dependency_overrides[get_sentinel2_catalog] = lambda: Sentinel2Catalog(
        transport=httpx.MockTransport(handler)
    )
    try:
        with TestClient(app) as client:
            capabilities = client.get("/v1/capabilities")
            capability = next(
                item
                for item in capabilities.json()["capabilities"]
                if item["id"] == "sentinel_catalogue"
            )
            assert capability["status"] == "available"
            assert capabilities.json()["analysis_enabled"] is False

            response = client.post(
                "/v1/catalog/sentinel-2/search",
                json={
                    "geometry": GEOMETRY,
                    "datetime_start": "2026-10-01T00:00:00Z",
                    "datetime_end": "2026-11-04T00:00:00Z",
                    "max_cloud_cover_pct": 20,
                    "limit": 3,
                },
            )
        assert response.status_code == 200
        assert response.json()["items"][0]["product_id"] == VALID_ITEM["id"]
        assert response.json()["items"][0]["assets"][0]["key"] == "B04_10m"
        assert "do not confirm buried objects" in response.json()["scientific_notice"]
    finally:
        app.dependency_overrides.clear()


def test_catalog_api_maps_provider_failure_to_gateway_error() -> None:
    async def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, text="provider internal details")

    app.dependency_overrides[get_sentinel2_catalog] = lambda: Sentinel2Catalog(
        transport=httpx.MockTransport(handler)
    )
    try:
        with TestClient(app) as client:
            response = client.post(
                "/v1/catalog/sentinel-2/search",
                json={
                    "geometry": GEOMETRY,
                    "datetime_start": "2026-10-01T00:00:00Z",
                    "datetime_end": "2026-11-04T00:00:00Z",
                },
            )
        assert response.status_code == 502
        assert "provider internal details" not in response.text
    finally:
        app.dependency_overrides.clear()


def test_catalog_api_maps_timeout_to_gateway_timeout() -> None:
    async def handler(_request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("provider details")

    app.dependency_overrides[get_sentinel2_catalog] = lambda: Sentinel2Catalog(
        transport=httpx.MockTransport(handler)
    )
    try:
        with TestClient(app) as client:
            response = client.post(
                "/v1/catalog/sentinel-2/search",
                json={
                    "geometry": GEOMETRY,
                    "datetime_start": "2026-10-01T00:00:00Z",
                    "datetime_end": "2026-11-04T00:00:00Z",
                },
            )
        assert response.status_code == 504
    finally:
        app.dependency_overrides.clear()
