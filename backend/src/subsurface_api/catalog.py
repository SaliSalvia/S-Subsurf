"""Bounded, provenance-preserving access to the CDSE Sentinel-2 L2A STAC catalogue."""

from __future__ import annotations

import re
from datetime import UTC, datetime
from math import isfinite
from typing import Any
from urllib.parse import quote

import httpx

from .schemas import (
    Sentinel2AssetSummary,
    Sentinel2CatalogSearchRequest,
    Sentinel2CatalogSearchResponse,
    Sentinel2Scene,
)

STAC_SEARCH_URL = "https://stac.dataspace.copernicus.eu/v1/search"
STAC_ITEM_URL = "https://stac.dataspace.copernicus.eu/v1/collections/sentinel-2-l2a/items/"
COLLECTION_ID = "sentinel-2-l2a"
CLOUD_COVER_NOTE = (
    "Cloud cover is the provider's scene-level estimate; it is not recalculated for the "
    "selected polygon and must not be interpreted as area-specific clear-sky coverage."
)
ASSET_ACCESS_NOTE = (
    "This endpoint returns catalogue metadata only. It does not download or process image "
    "assets; CDSE asset access may require an authenticated provider account."
)
SCIENTIFIC_NOTICE = (
    "Sentinel-2 records describe optical surface observations. They do not confirm buried "
    "objects, tunnels, cavities, or burial depth."
)


class CatalogTimeoutError(Exception):
    """The catalogue did not respond within the configured timeout."""


class CatalogProviderError(Exception):
    """The catalogue returned an error or an invalid response."""


def _utc_isoformat(value: datetime) -> str:
    return value.astimezone(UTC).isoformat(timespec="seconds").replace("+00:00", "Z")


def _parse_datetime(value: Any) -> datetime:
    if not isinstance(value, str):
        raise ValueError("Catalogue item is missing an acquisition timestamp.")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("Catalogue acquisition timestamp is not timezone-aware.")
    return parsed


def _parse_bbox(value: Any) -> tuple[float, float, float, float]:
    if not isinstance(value, list) or len(value) < 4:
        raise ValueError("Catalogue item is missing a valid bounding box.")
    west, south, east, north = (float(part) for part in value[:4])
    if not all(isfinite(part) for part in (west, south, east, north)):
        raise ValueError("Catalogue bounding box contains a non-finite value.")
    if west > east or south > north:
        raise ValueError("Catalogue bounding box has invalid coordinate order.")
    return west, south, east, north


def _asset_summaries(raw_assets: Any) -> tuple[list[Sentinel2AssetSummary], bool]:
    if not isinstance(raw_assets, dict):
        return [], False

    summaries: list[Sentinel2AssetSummary] = []
    authentication_declared = False
    for key, raw_asset in sorted(raw_assets.items()):
        if not isinstance(key, str) or not isinstance(raw_asset, dict):
            continue
        refs = raw_asset.get("auth:refs", [])
        if isinstance(refs, list) and any(ref in {"s3", "oidc"} for ref in refs):
            authentication_declared = True

        raw_roles = raw_asset.get("roles", [])
        roles = (
            [role for role in raw_roles if isinstance(role, str) and len(role) <= 40]
            if isinstance(raw_roles, list)
            else []
        )
        if not any(role in {"data", "reflectance", "quality"} for role in roles):
            continue

        raw_gsd = raw_asset.get("gsd")
        gsd: int | None = None
        if isinstance(raw_gsd, (int, float)) and not isinstance(raw_gsd, bool):
            if isfinite(float(raw_gsd)) and float(raw_gsd).is_integer():
                gsd = int(raw_gsd)
        summaries.append(
            Sentinel2AssetSummary(
                key=key[:80],
                gsd_m=gsd,
                roles=roles[:8],
            )
        )
        if len(summaries) == 128:
            break

    return summaries, authentication_declared


def _parse_scene(raw_item: Any) -> Sentinel2Scene:
    if not isinstance(raw_item, dict):
        raise ValueError("Catalogue feature is not an object.")
    product_id = raw_item.get("id")
    properties = raw_item.get("properties")
    if not isinstance(product_id, str) or not isinstance(properties, dict):
        raise ValueError("Catalogue feature is missing an identifier or properties.")
    if re.fullmatch(r"[A-Za-z0-9._-]{1,200}", product_id) is None or product_id in {".", ".."}:
        raise ValueError("Catalogue item identifier contains unsupported characters.")
    if raw_item.get("collection") != COLLECTION_ID:
        raise ValueError("Catalogue feature belongs to an unexpected collection.")

    acquired_at = properties.get("datetime") or properties.get("start_datetime")
    cloud_cover = properties.get("eo:cloud_cover")
    if cloud_cover is not None:
        if isinstance(cloud_cover, bool) or not isinstance(cloud_cover, (int, float)):
            raise ValueError("Catalogue cloud-cover value is not numeric.")
        cloud_cover = float(cloud_cover)
        if not isfinite(cloud_cover) or not 0.0 <= cloud_cover <= 100.0:
            raise ValueError("Catalogue cloud-cover value is outside 0–100%.")

    assets, authentication_declared = _asset_summaries(raw_item.get("assets"))
    auth_schemes = properties.get("auth:schemes")
    if isinstance(auth_schemes, dict) and auth_schemes:
        authentication_declared = True

    return Sentinel2Scene(
        product_id=product_id,
        acquired_at=_parse_datetime(acquired_at),
        cloud_cover_pct=cloud_cover,
        platform=(
            properties.get("platform")
            if isinstance(properties.get("platform"), str)
            else None
        ),
        processing_version=(
            properties.get("processing:version")
            if isinstance(properties.get("processing:version"), str)
            else None
        ),
        bbox_wsen=_parse_bbox(raw_item.get("bbox")),
        assets=assets,
        asset_access=(
            "provider_authentication_required" if authentication_declared else "not_declared"
        ),
        catalog_item_url=f"{STAC_ITEM_URL}{quote(product_id, safe='')}",
    )


def _next_cursor(links: Any) -> str | None:
    if not isinstance(links, list):
        return None
    for link in links:
        if not isinstance(link, dict) or link.get("rel") != "next":
            continue
        body = link.get("body")
        token = body.get("token") if isinstance(body, dict) else None
        if isinstance(token, str) and 0 < len(token) <= 1024:
            return token
    return None


def _search_payload(request: Sentinel2CatalogSearchRequest) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "collections": [COLLECTION_ID],
        "intersects": request.geometry.model_dump(mode="json"),
        "datetime": (
            f"{_utc_isoformat(request.datetime_start)}/{_utc_isoformat(request.datetime_end)}"
        ),
        "limit": request.limit,
        "filter-lang": "cql2-json",
        "filter": {
            "op": "<=",
            "args": [{"property": "eo:cloud_cover"}, request.max_cloud_cover_pct],
        },
    }
    if request.cursor is not None:
        payload["token"] = request.cursor
    return payload


class Sentinel2Catalog:
    """Search public STAC metadata without exposing provider asset URLs or credentials."""

    def __init__(self, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self._transport = transport

    async def search(
        self, request: Sentinel2CatalogSearchRequest
    ) -> Sentinel2CatalogSearchResponse:
        try:
            async with httpx.AsyncClient(
                timeout=httpx.Timeout(12.0, connect=5.0),
                limits=httpx.Limits(max_connections=5, max_keepalive_connections=2),
                follow_redirects=False,
                transport=self._transport,
            ) as client:
                response = await client.post(
                    STAC_SEARCH_URL,
                    json=_search_payload(request),
                    headers={"Accept": "application/geo+json, application/json"},
                )
                response.raise_for_status()
                document = response.json()
        except httpx.TimeoutException as exc:
            raise CatalogTimeoutError from exc
        except (httpx.HTTPError, ValueError) as exc:
            raise CatalogProviderError from exc

        if not isinstance(document, dict) or not isinstance(document.get("features"), list):
            raise CatalogProviderError("Catalogue response did not contain a feature list.")

        features = document["features"]
        items: list[Sentinel2Scene] = []
        omitted_item_count = max(0, len(features) - request.limit)
        for raw_item in features[: request.limit]:
            try:
                items.append(_parse_scene(raw_item))
            except (TypeError, ValueError):
                omitted_item_count += 1

        return Sentinel2CatalogSearchResponse(
            datetime_start=request.datetime_start,
            datetime_end=request.datetime_end,
            max_cloud_cover_pct=request.max_cloud_cover_pct,
            items=items,
            next_cursor=_next_cursor(document.get("links")),
            omitted_item_count=omitted_item_count,
            cloud_cover_note=CLOUD_COVER_NOTE,
            asset_access_note=ASSET_ACCESS_NOTE,
            scientific_notice=SCIENTIFIC_NOTICE,
        )
