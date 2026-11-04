# SubsurfaceExplorer

**An evidence-aware geospatial workspace for exploring satellite and terrain observations.**

SubsurfaceExplorer is an Android-first project for selecting survey areas, organizing geospatial observations, and later analysing verified satellite/terrain products. It is **not** a remote subsurface scanner. Satellite imagery does not directly identify buried metal objects, tunnels, cavities, or their depth. The initial release therefore focuses on mapping, region geometry, data provenance, and defensible surface indicators.

## Status

The repository bootstrap contains:

- Native Android app (Kotlin, Jetpack Compose, Material 3, MapLibre Native).
- Region sketching on an interactive map (3–10 vertices, undo, clear, area estimate).
- FastAPI backend with health/capability endpoints and strict polygon validation.
- Android-to-API region-validation request with loading, success, and error states.
- Python unit tests and CI workflows.
- Scientific guardrails and a staged implementation roadmap.

Satellite providers, user accounts, remote analysis, and anomaly interpretation are **not connected yet**. The UI does not fabricate results.

## Repository layout

```text
android/       Android application
backend/       Python geospatial API
docs/          Architecture, science scope, roadmap
.github/       Continuous integration
```

## Run the backend

```bash
cd backend
python -m venv .venv
# Activate the environment, then:
python -m pip install -e ".[dev]"
uvicorn subsurface_api.main:app --reload
```

API documentation: `http://127.0.0.1:8000/docs`.

## Open the Android app

Open `android/` in Android Studio with JDK 17+ and Android SDK Platform 36 plus Build Tools 36.0.0 installed. The Gradle build uses Kotlin DSL, a version catalog, and Maven Central dependencies. This bootstrap contains the Gradle wrapper properties but not the generated wrapper scripts/JAR. Use an installed Gradle 9.5 distribution or run `gradle wrapper --gradle-version 9.5.0` once to generate a complete wrapper locally; the CI workflow installs Gradle directly.

### Connect the Android app to the backend

Start the API on your development computer (`uvicorn subsurface_api.main:app --host 0.0.0.0 --port 8000`). The Android emulator uses `http://10.0.2.2:8000/` by default. For a physical phone, pass the computer's LAN URL when building, for example `gradle -PAPI_BASE_URL=http://192.168.1.20:8000/ :app:assembleDebug` (replace the address with your computer's reachable LAN IP). Debug builds allow cleartext HTTP for local development only. Release builds default to a deliberately invalid HTTPS placeholder; set `-PAPI_BASE_URL=https://your-api-host/` to configure a real HTTPS endpoint. Never ship a development HTTP endpoint in a release.

## Scientific guardrails

1. A surface anomaly is not evidence of a buried target.
2. Optical, thermal, SAR, and elevation datasets have different spatial/temporal resolutions, processing artefacts, and license terms.
3. No depth estimate or object-type classification is emitted without a validated physical model and site-specific ground truth.
4. Every future observation must retain provider, product ID, acquisition time, processing version, spatial resolution, quality flags, and licence/provenance metadata.
5. Field confirmation needs suitable ground-based methods (e.g. EMI, magnetometry, or GPR), chosen for the target and geology.

Read [Scientific scope](docs/SCIENTIFIC_SCOPE.md), [Architecture](docs/ARCHITECTURE.md), and [Roadmap](docs/ROADMAP.md).

## Licensing

Application/backend source: MIT. Documentation: CC BY 4.0. See `LICENSE` and `docs/LICENSE-DOCS.md`.
