# SubsurfaceExplorer API

FastAPI service for health checks, declared capabilities, strict GeoJSON survey-region validation, and live Copernicus Sentinel-2 L2A STAC metadata search. It does not download or process satellite imagery and does not infer buried targets.

## Local run

```bash
python -m venv .venv
# Activate the environment, then:
python -m pip install -e ".[dev]"
uvicorn subsurface_api.main:app --reload
```

## Tests

```bash
pytest --cov=subsurface_api --cov-report=term-missing
ruff check .
```

## Sentinel-2 catalogue search

`POST /v1/catalog/sentinel-2/search` accepts a validated GeoJSON polygon, timezone-aware start/end timestamps, a scene-level cloud-cover limit, and a bounded page size (1–50). It queries the official CDSE STAC API and returns product IDs, acquisition times, scene cloud-cover estimates, product footprints, processing versions, band/quality asset names, ground-sampling distances where provided, and a pagination cursor.

The route deliberately omits asset URLs and credentials. Catalogue search is separate from downloading image assets; product access may require an authenticated Copernicus Data Space account. Cloud cover is a scene-level estimate, not a recomputed statistic for the user's polygon. Sentinel-2 metadata is not evidence of buried objects or burial depth.

Example request:

```json
{
  "geometry": {
    "type": "Polygon",
    "coordinates": [[[51.36, 35.66], [51.43, 35.66], [51.43, 35.73], [51.36, 35.73], [51.36, 35.66]]]
  },
  "datetime_start": "2026-10-01T00:00:00Z",
  "datetime_end": "2026-11-04T00:00:00Z",
  "max_cloud_cover_pct": 30,
  "limit": 20
}
```

Provider interactions are mocked in CI; no credentials or live external service are required to run the test suite.
