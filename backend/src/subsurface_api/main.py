"""HTTP entry point for the SubsurfaceExplorer API."""

from __future__ import annotations

import os

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from . import __version__
from .geometry import polygon_metrics
from .schemas import (
    Capability,
    CapabilitiesResponse,
    RegionValidationRequest,
    RegionValidationResponse,
)

app = FastAPI(
    title="SubsurfaceExplorer API",
    summary="Region geometry and evidence-aware geospatial project API",
    description=(
        "The current API validates survey geometry only. It does not detect underground objects "
        "or estimate burial depth."
    ),
    version=__version__,
    docs_url="/docs",
    redoc_url=None,
)

allowed_origins = [
    item.strip()
    for item in os.getenv("CORS_ORIGINS", "").split(",")
    if item.strip()
]
if allowed_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=allowed_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type", "Authorization", "X-Request-ID"],
    )


@app.get("/health", tags=["system"])
def health() -> dict[str, str]:
    """Return a lightweight liveness response."""
    return {"status": "ok", "version": __version__}


@app.get("/v1/capabilities", response_model=CapabilitiesResponse, tags=["system"])
def capabilities() -> CapabilitiesResponse:
    """Report only capabilities that are actually implemented or explicitly planned."""
    return CapabilitiesResponse(
        project_version=__version__,
        analysis_enabled=False,
        capabilities=[
            Capability(
                id="map_region_validation",
                status="available",
                description=(
                    "Validate GeoJSON survey polygons and calculate approximate area/bounds."
                ),
            ),
            Capability(
                id="sentinel_catalogue",
                status="planned",
                description="Provider catalogue search; not connected.",
            ),
            Capability(
                id="raster_processing",
                status="planned",
                description="Quality-aware surface-observation processing; not connected.",
            ),
            Capability(
                id="subsurface_classification",
                status="not_configured",
                description=(
                    "Satellite data alone is insufficient; validated ground-truth methods "
                    "are required."
                ),
            ),
        ],
        scientific_notice=(
            "Satellite and terrain observations can characterize surface context. "
            "They do not directly "
            "confirm buried metal, tunnels, voids, or burial depth."
        ),
    )


@app.post("/v1/regions/validate", response_model=RegionValidationResponse, tags=["regions"])
def validate_region(request: RegionValidationRequest) -> RegionValidationResponse:
    """Validate a GeoJSON Polygon and return approximate area and WGS84 bounds."""
    try:
        area_m2, bbox, vertex_count = polygon_metrics(request.geometry)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    west, south, east, north = bbox
    return RegionValidationResponse(
        area_m2_approx=area_m2,
        area_ha_approx=area_m2 / 10_000.0,
        bbox_wsen=(west, south, east, north),
        vertex_count=vertex_count,
    )
