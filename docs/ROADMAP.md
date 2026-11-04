# Implementation roadmap

## Milestone 0 — Foundation (this bootstrap)

- Android project, theme, MapLibre map, region sketching.
- FastAPI health/capability/region-validation endpoints.
- Android-to-API region validation with configurable API base URL, timeouts, and error states.
- Geometry and API tests, CI, documented scientific guardrails.

**Exit gate:** Android debug build and Python tests pass; polygon is drawn and the API returns matching server-validated geometry metrics.

## Milestone 1 — Region persistence and export

- Persist region drafts locally with Room.
- Add GeoJSON export and import.
- Add a map-camera fit-to-region action and an editable vertex workflow.
- Add request IDs and structured client/server request logs without telemetry.

## Milestone 2 — One real data product

- Select one source based on access, licence, coverage, and API stability.
- Implement catalogue search and a small-area download path.
- Preserve source IDs, time, CRS, pixel spacing, processing baseline, and quality masks.
- Build reproducible fixtures from openly licensed sample scenes.

## Milestone 3 — Surface-context processing

- Implement source-specific calculations (for example, cloud-masked optical indices).
- Validate each result against a known scene and an independent GIS workflow.
- Report measurement, uncertainty, and quality flags separately.

## Milestone 4 — Multi-source context

- Add another sensor only when the first pipeline is reproducible.
- Normalize only semantically comparable measurements.
- Learn weights from ground-truth calibration data; never select a scientific model merely by counting sources.

## Milestone 5 — Field validation and release engineering

- Define controlled field protocol and ground-truth schema.
- Compare satellite observations with appropriate EMI, magnetometry, or GPR measurements.
- Add security review, privacy review, provider terms review, offline strategy, app-size/performance profiling, and signed release process.

## Deferred by design

User accounts, cloud sync, paid hosting assumptions, TFLite models, automated depth estimation, object-type labels, and 3D anomaly extrusions are not part of the first milestone.
