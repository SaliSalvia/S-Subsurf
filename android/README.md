# Android application

## Toolchain

- Android Studio compatible with AGP 9.3.1
- JDK 17
- Android SDK Platform 37.2 and Build Tools 36.0.0
- Gradle 9.5.0

The repository contains `gradle/wrapper/gradle-wrapper.properties`; generated wrapper scripts/JAR are not included in this bootstrap. If Gradle is installed locally, run `gradle wrapper --gradle-version 9.5.0` from this directory, then commit the generated `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`. CI installs Gradle 9.5.0 directly.

## Map renderer and tiles

The app pins MapLibre Native Android 13.7.1 using the OpenGL ES artifact to prioritize compatibility across Android devices. The bundled style uses the public OpenStreetMap raster tile endpoint and displays attribution. The public OSM tile service is not an unlimited production tile CDN. Before a public or high-volume release, select a tile provider whose terms, capacity, privacy posture, and access from target regions have been confirmed.

## Current app behaviour

- Map starts centred on Tehran as a neutral project default; users can pan/zoom anywhere.
- Enable **Draw region**, tap 3–10 points, finish drawing, undo, or clear. The draft survives rotation and process death (kept in the `MapViewModel` via `SavedStateHandle`).
- Area is an approximate spherical calculation; antimeridian-crossing polygons are not supported by this MVP.
- **Review area** sends the drawn GeoJSON polygon to `POST /v1/regions/validate` and displays the server-validated vertex count, approximate area, and bounding box.
- **Sentinel-2 catalogue search** queries live Copernicus Data Space STAC metadata for the selected polygon, date interval, and scene-level cloud-cover limit.
- The catalogue response contains product metadata only. It does not download imagery or infer buried targets, target classes, confidence, or depth.


## Backend connection

The debug app targets `http://10.0.2.2:8000/` (the Android emulator's host bridge). For a phone on the same network, pass `-PAPI_BASE_URL=http://<computer-LAN-IP>:8000/` to Gradle. The debug manifest enables local cleartext HTTP; release builds do not. Release builds require an HTTPS URL configured through the same Gradle property.
