# Changelog

## Unreleased — Android stability and quality pass

- Fixed region drafts being discarded on rotation/process death; draft state now lives in `MapViewModel` backed by `SavedStateHandle`.
- Removed `require()`-crash paths from catalogue search; invalid input now surfaces as an error state.
- Fixed the 4 backend `ruff check` errors that kept Backend CI red on main.
- Extracted shared `HttpJsonClient` (timeouts, GeoJSON ring encoding, FastAPI error parsing) used by both API clients; unconfigured release endpoints (`.invalid` host) now report an actionable message.
- `MapViewModel` is constructor-injectable with a production `Factory`; added `MapViewModelTest` fakes covering drafts, validation, search, and pagination.
- Local polygon validation now enforces the same 3–10 vertex contract as the API clients.
- Map style/init failures now surface a dismissible in-app banner instead of crashing.
- Added a light color scheme (theme follows the system); fixed the launch-window theme flashing white on dark devices.
- Hardened manifests: cleartext is explicitly disabled in main, debug-only otherwise.
- Fixed cloud-cover slider truncation (`toInt()` → `roundToInt()`).
- Migrated CI from deprecated `actions/checkout@v4` / `actions/setup-java@v4` to `@v5`.
- Added a Persian deep-analysis report (`docs/ANDROID_ANALYSIS_FA.md`).

## 0.1.0 — Bootstrap

- Initial Android Compose + MapLibre map workspace.
- Region sketching, local area approximation, undo/clear.
- FastAPI health, capabilities, and GeoJSON polygon validation.
- Backend tests and CI workflows.
- Scientific scope, architecture, and staged roadmap.
