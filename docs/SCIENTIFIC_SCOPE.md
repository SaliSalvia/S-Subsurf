# Scientific scope and interpretation policy

## What satellite data can contribute

Satellite and airborne remote-sensing products measure reflected/emitted radiation, radar backscatter, or surface elevation. Properly processed time series can help describe **surface and near-surface context**, for example:

- vegetation or moisture changes (optical multispectral indices);
- surface temperature patterns (thermal products, where resolution and acquisition timing permit);
- radar backscatter/coherence changes (SAR, with sensitivity to roughness, moisture, vegetation, geometry, and processing choices);
- terrain morphology and elevation changes (DEMs and repeat elevation products).

These observations may be useful for mapping, screening, and hypothesis generation. Their interpretation is site- and sensor-dependent.

## What the app must not claim

The current satellite data stack cannot, by itself, establish that a specific buried metal object, tunnel, cavity, or void exists. It cannot provide a defensible burial-depth estimate. A surface anomaly score must never be relabelled as “probability of metal”, “tunnel confidence”, or “depth”.

In particular:

- Sentinel-2 multispectral imagery is a surface-observation instrument; its bands have different ground sampling distances (10, 20, and 60 m).
- Sentinel-1 SAR backscatter/coherence responds to surface and scene properties; it is not a direct underground metal detector.
- DEM products represent surface topography at their stated resolution; pixel-scale void detection is not supported.
- Thermal anomalies are strongly affected by weather, time of acquisition, emissivity, land cover, and spatial resolution.
- ALOS L-band data must not be described as a magnetic measurement. Radar polarimetry is not a substitute for magnetometry.

## Ground truth and confirmation

Any future candidate area should be described as a **surface-context observation requiring field validation**. Appropriate ground methods depend on expected target size, depth, geology, moisture, cultural noise, and safety. Examples include electromagnetic induction (EMI), magnetometry, and ground-penetrating radar (GPR). These methods also have limitations and require trained interpretation.

## Evidence and confidence policy

Every data record must preserve:

- provider, collection/product identifier, and access URL;
- acquisition time and processing time;
- CRS, pixel spacing / nominal resolution, and footprint;
- processing algorithm and version;
- cloud, layover, shadow, incidence-angle, and other quality masks where relevant;
- licence / terms of use;
- missing-data and uncertainty flags.

Confidence is permitted only for a clearly defined and validated target variable with calibration data. Until then, report raw measurements, quality indicators, and exploratory surface-context scores separately. Do not infer target class or depth from the number of available sources.

## Sources to validate during provider integration

- Copernicus Data Space Ecosystem, Sentinel-2 product documentation: https://documentation.dataspace.copernicus.eu/Data/Sentinel2.html
- MapLibre Native Android SDK: https://maplibre.org/maplibre-native/docs/book/platforms/android/
- NASA Earthdata: https://www.earthdata.nasa.gov/
- USGS EarthExplorer: https://earthexplorer.usgs.gov/

This document is a conservative product policy, not a claim that every geophysical method can identify every target.
