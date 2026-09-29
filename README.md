# Wildfire Risk Detector (California) - Test Build

A wildfire **ignition-risk** detector scoped to California, built with a React
frontend (Leaflet map + risk zone sidebar) and a zero-dependency Java backend. Rather
than tracking currently-burning fires, it computes the likelihood of a wildfire starting
at a given location derived from meteorological conditions: heat, humidity, wind velocity,
and dry-spell length.

## Weather → Model → API Inference Flow

```
+-----------------------------------------------------------+
| Weather Observation Feeds / User Query Parameters         |
|  - Temperature (°F or °C)                                 |
|  - Relative Humidity (%)                                  |
|  - Wind Velocity (mph or km/h)                            |
|  - Consecutive Dry Spell Days (days without rain)         |
+-----------------------------+-----------------------------+
                              |
                              v
+-----------------------------------------------------------+
| WeatherFeatures (Validation & Feature Engineering)        |
|  - Out-of-range checks (T: [-40, 140]°F, H: [0, 100]%,     |
|    wind: [0, 150] mph, dry days >= 0)                     |
|  - Unit conversions (Celsius to Fahrenheit, km/h to mph)  |
+-----------------------------+-----------------------------+
                              |
                              v
+-----------------------------------------------------------+
| WildfireRiskModel (Fosberg Fire Weather Index Model)      |
|  1. Equilibrium Moisture Content m(H, T) (Simard/Fosberg) |
|  2. Moisture Damping Factor η(m)                          |
|  3. Wind Scaling λ = √(1 + U²)                            |
|  4. Base FFWI = (η * λ) / 0.3002                          |
|  5. Antecedent Drought Multiplier D_f = 1 + 0.35 * min(1, d/30)|
|  6. Continuous Risk % Mapping P ∈ [0, 100]%               |
|  7. Categorical Rating (Low, Moderate, High, Extreme)     |
+-----------------------------+-----------------------------+
                              |
                              v
+-----------------------------------------------------------+
| REST Controller (ApiServer)                               |
|  - GET  /api/risk-zones  -> California risk zones with    |
|                             provenance metadata           |
|  - GET  /api/predict     -> On-demand inference (params)  |
|  - POST /api/predict     -> On-demand inference (JSON)    |
|  - GET  /api/health      -> Health check liveness         |
+-----------------------------+-----------------------------+
                              |
                              v
+-----------------------------------------------------------+
| React + Leaflet Frontend                                  |
|  - Dynamic risk card indicators, map circles, popups      |
|  - Clear differentiation between live & fallback feeds    |
+-----------------------------------------------------------+
```

### Scientific Reference & Formulation

The underlying risk model implements the **Fosberg Fire Weather Index (FFWI)**:
- **Fosberg, M. A. (1978)**. *Weather in wildland fire management: the fire weather index.* Conference on Sierra Nevada Meteorology, American Meteorological Society.
- **Schroeder, M. J. & Buck, C. C. (1970)**. *Fire weather: a guide for application of meteorological information to forest fire control operations.* USDA Forest Service Agricultural Handbook 360.
- **Thomas, B. et al. (2018)**. *Machine Learning for Wildfire Risk Prediction.* [arXiv:1812.11699](https://arxiv.org/abs/1812.11699).
- **Koh, P. W. et al. (2021)**. *Wildfire Hazard Analysis using Meteorological Indices.* [arXiv:2110.09497](https://arxiv.org/abs/2110.09497).

Tiers:
- **Low**: Risk < 25% (FFWI < 20)
- **Moderate**: Risk 25% – 49% (FFWI 20 – 40)
- **High**: Risk 50% – 74% (FFWI 40 – 75)
- **Extreme**: Risk ≥ 75% (FFWI ≥ 75)

## Project layout

```
wildfire-tracker/
  backend/    Java REST API (pure JDK - no Maven/Gradle required)
  frontend/   React + Vite + Leaflet app
```

## Running the backend

The backend uses only the JDK's built-in `HttpServer`
(`com.sun.net.httpserver`) - no Spring Boot, no Maven, no external dependencies
needed to build it. You just need a JDK (11+) installed.

```bash
cd backend
javac -d out $(find src -name "*.java")
java -cp out com.wildfire.tracker.controller.ApiServer
```

To configure a custom port, set the `PORT` environment variable or `server.port` system property:
```bash
java -Dserver.port=8080 -cp out com.wildfire.tracker.controller.ApiServer
```

### Testing the backend

Run the automated test suite covering unit calculations, threshold boundaries, out-of-range validations, and JSON serialization:

```bash
cd backend
javac -d out $(find src -name "*.java")
java -cp out com.wildfire.tracker.TestRunner
```

### Available Endpoints

1. **`GET /api/risk-zones`**:
   Returns the current California risk zones. Each zone includes calculated `riskPct`, `riskLevel`, `tempF`, `humidityPct`, `windMph`, `drySpellDays`, `lastUpdate`, `modelVersion`, `predictionTimestamp`, and `dataSourceStatus` (`"live"`, `"stale"`, or `"fallback"`).

   ```bash
   curl http://localhost:8080/api/risk-zones
   ```

2. **`GET /api/predict?tempF=95&humidityPct=12&windMph=25&drySpellDays=30`**:
   Computes on-demand risk inference for specified weather conditions. Supports metric queries (`tempC` and `windKmh`).

   ```bash
   curl "http://localhost:8080/api/predict?tempF=95&humidityPct=12&windMph=25&drySpellDays=30"
   ```

   Response:
   ```json
   {
     "riskPct": 81,
     "riskLevel": "Extreme",
     "rawIndex": 93.30,
     "fuelMoisturePct": 2.74,
     "modelVersion": "ffwi-v1.0-california",
     "predictionTimestamp": "2026-07-24T08:15:00Z",
     "summary": "Critical fire danger: Fuel moisture 2.7% with 95°F heat, 12% humidity, and 25 mph wind over 30 dry days."
   }
   ```

3. **`POST /api/predict`**:
   Accepts a JSON payload with weather parameters:

   ```bash
   curl -X POST http://localhost:8080/api/predict \
     -H "Content-Type: application/json" \
     -d '{"tempF": 102, "humidityPct": 8, "windMph": 30, "drySpellDays": 40}'
   ```

4. **`GET /api/health`**:
   Returns `{"status":"ok"}`.

## Running the frontend

```bash
cd frontend
npm install
npm run dev
```

Also opens at https://antonwontonn.github.io/wildfire-risk-prediction/

## What's implemented

- Full-height California map (Leaflet + OpenStreetMap tiles), panning
  restricted to a California bounding box.
- Collapsible sidebar listing risk zones sorted by risk %, with a risk-level
  badge (Low / Moderate / High / Extreme), temperature, humidity, wind, and a
  relative-time stamp.
- Map markers sized and color-coded by ignition-risk percentage (extreme =
  red, high = orange, moderate = amber, low = green) - bigger and redder
  means higher risk of a fire starting there.
- Clicking a sidebar card or a map marker selects it, flies the map to it,
  and opens its popup with the risk % and contributing conditions.
- Zero-dependency Java backend implementing the Fosberg Fire Weather Index (FFWI)
  with dry-spell drought adjustment factor and threshold calibration.
- Provenance tracking with `modelVersion`, `predictionTimestamp`, and `dataSourceStatus`
  (`live`, `stale`, `fallback`).
