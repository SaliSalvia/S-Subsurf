# Architecture

## Initial system boundary

```text
┌──────────────────────── Android ─────────────────────────┐
│ Jetpack Compose UI                                      │
│   └─ MapLibre Native MapView                             │
│       ├─ OSM base map + attribution                      │
│       └─ user polygon sketch (GeoJSON line/vertices)      │
│                                                         │
│ domain: GeoPoint / SurveyRegion / polygon metrics        │
└──────────────────────────────┬──────────────────────────┘
                               │ HTTP (debug) / HTTPS (release)
┌──────────────────────────────▼──────────────────────────┐
│ FastAPI backend                                          │
│   ├─ health + capabilities                               │
│   ├─ region validation (GeoJSON geometry / area / bbox)  │
│   └─ bounded Sentinel-2 L2A STAC metadata search         │
└─────────────────────────────────────────────────────────┘
                               │ fixed HTTPS endpoint
┌──────────────────────────────▼──────────────────────────┐
│ Copernicus Data Space Ecosystem STAC                     │
│   └─ product metadata only; no assets downloaded          │
└─────────────────────────────────────────────────────────┘

Future, behind explicit provider interfaces:
authenticated product download → quality masks →
source-specific measurements → provenance-aware analysis →
GeoJSON surface observations → map layers / export.
```

## Design principles

- **Modular monolith first:** avoid microservices until throughput or team boundaries justify them.
- **Contract-first:** GeoJSON and Pydantic schemas define API boundaries; Android domain models remain independent of MapLibre types.
- **No hidden scientific assumptions:** sensor-specific preprocessing and quality metadata stay attached to every observation.
- **No fabricated output:** the app draws/validates geometry and returns real catalog metadata; no analysis score is emitted.
- **Offline-tolerant UI:** region sketch state is local; map tiles require a network connection in this first build.
- **Secrets stay out of source control:** provider tokens belong in platform secure storage/server environment, never in map styles or Git.

## Android layers

- `feature/map`: map screen, user interaction, and API state. The region draft (vertices + drawing flag) lives in `MapViewModel` and is mirrored into `SavedStateHandle`, so rotation and process death never discard it.
- `data/remote`: bounded-timeout HTTP clients for region validation and catalog search, sharing connection setup, GeoJSON ring encoding, and FastAPI error parsing through `HttpJsonClient`.
- `core/model`: app-owned coordinate model.
- `core/geometry`: independent geodesic area estimate.
- `ui/theme`: color scheme and typography entry point.

MapLibre is isolated inside `MapLibreMapView`; domain logic does not depend on SDK classes. The first build pins the MapLibre Android 13.7.1 OpenGL ES artifact for broad device compatibility; consider Vulkan only after profiling representative devices. Keep MapView lifecycle callbacks paired with the Android lifecycle. Map styles are local assets. OSM attribution is visible in the UI. The public OSM tile endpoint is not a guaranteed unlimited production tile service; choose a provider and comply with its policy before launch.

## Backend layers

- `schemas.py`: strict request/response contracts.
- `geometry.py`: deterministic geometry calculations and validation.
- `catalog.py`: fixed-host, bounded CDSE STAC client; sanitizes upstream errors and omits asset URLs.
- `main.py`: HTTP routes and application configuration.

Do not add heavyweight raster libraries until the first real product ingestion path is selected and tested against representative data. This keeps the first API deployable on small instances.

## API evolution

The API currently exposes:

- `GET /health`
- `GET /v1/capabilities`
- `POST /v1/regions/validate` (called by the Android MVP)
- `POST /v1/catalog/sentinel-2/search` (live product metadata; no image download)

Add authenticated downloads, raster processing, authentication, persistence, jobs, and progress streaming only after defining ownership, retention, deletion, quota, and secret-handling policies.
