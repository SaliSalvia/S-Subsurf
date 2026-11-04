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
│   └─ region validation (GeoJSON geometry / area / bbox)  │
└─────────────────────────────────────────────────────────┘

Future, behind explicit provider interfaces:
Catalog search → validated product download → quality masks →
source-specific measurements → provenance-aware analysis →
GeoJSON surface observations → map layers / export.
```

## Design principles

- **Modular monolith first:** avoid microservices until throughput or team boundaries justify them.
- **Contract-first:** GeoJSON and Pydantic schemas define API boundaries; Android domain models remain independent of MapLibre types.
- **No hidden scientific assumptions:** sensor-specific preprocessing and quality metadata stay attached to every observation.
- **No fabricated output:** the initial build only draws and validates user geometry.
- **Offline-tolerant UI:** region sketch state is local; map tiles require a network connection in this first build.
- **Secrets stay out of source control:** provider tokens belong in platform secure storage/server environment, never in map styles or Git.

## Android layers

- `feature/map`: map screen, drawing state, user interaction, and API validation state.
- `data/remote`: bounded-timeout HTTP client for the region-validation contract.
- `core/model`: app-owned coordinate model.
- `core/geometry`: independent geodesic area estimate.
- `ui/theme`: color scheme and typography entry point.

MapLibre is isolated inside `MapLibreMapView`; domain logic does not depend on SDK classes. The first build pins the MapLibre Android 13.7.1 OpenGL ES artifact for broad device compatibility; consider Vulkan only after profiling representative devices. Keep MapView lifecycle callbacks paired with the Android lifecycle. Map styles are local assets. OSM attribution is visible in the UI. The public OSM tile endpoint is not a guaranteed unlimited production tile service; choose a provider and comply with its policy before launch.

## Backend layers

- `schemas.py`: strict request/response contracts.
- `geometry.py`: deterministic geometry calculations and validation.
- `main.py`: HTTP routes and application configuration.

Do not add heavyweight raster libraries until the first real product ingestion path is selected and tested against representative data. This keeps the first API deployable on small instances.

## API evolution

The initial API is intentionally small:

- `GET /health`
- `GET /v1/capabilities`
- `POST /v1/regions/validate` (called by the Android MVP)

Add authentication, persistence, jobs, progress streaming, and provider integrations only after defining ownership, retention, deletion, quota, and secret-handling policies.
