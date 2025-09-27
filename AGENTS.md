# DelivMap Agents Overview

This document describes the main **functional agents** in the DelivMapAGENTS Android app.  
Each agent is a logical module or responsibility block, designed for clarity and easy extension.

---

## 1. OCR Agent (`MainActivity + ML Kit`)
**Goal:** Capture a camera frame and extract text.

- **Inputs:**  
  - Camera frame (via CameraX `PreviewView`).
- **Logic:**  
  - Runs ML Kit Text Recognition v2 (on-device OCR).  
  - Passes full extracted text to the Address Parsing Agent.
- **Outputs:**  
  - A list of raw text lines from the paper/label.

---

## 2. Address Parsing Agent (`AddressHeuristics.kt`)
**Goal:** Convert raw OCR text → list of likely postal addresses.

- **Inputs:**  
  - OCR text lines.
- **Logic:**  
  - Regex & heuristic rules to detect street names, postal codes, city names.  
  - Combines lines if needed (e.g., street + postal code on next line).  
  - Deduplicates candidates.
- **Outputs:**  
  - A ranked list of address strings for geocoding.

---

## 3. Geocoding Agent (`MapActivity + Geocoder + Places API`)
**Goal:** Convert address strings → geographic coordinates.

- **Inputs:**  
  - Address candidates from the parsing agent.
- **Logic:**  
  1. Try Android’s built-in `Geocoder.getFromLocationName()`.  
  2. If no result, fallback to **Google Places Autocomplete + FetchPlace** for better accuracy.  
- **Outputs:**  
  - A list of `Stop(label, LatLng)` objects.

---

## 4. Route Optimization Agent (`Routing.kt`)
**Goal:** Given start point + stops, find a near-optimal visit order.

- **Inputs:**  
  - Origin point (manual long-press or GPS).  
  - List of stops (LatLng).  
- **Logic:**  
  - **Nearest Neighbor**: quick initial path.  
  - **2-Opt** refinement: improves route order to shorten total distance.  
- **Outputs:**  
  - Ordered list of stops for driving route.

---

## 5. Map Rendering Agent (`MapActivity + Google Maps SDK`)
**Goal:** Visualize the route on an embedded map.

- **Inputs:**  
  - Start point + ordered stops.  
- **Logic:**  
  - Add markers for each stop.  
  - Draw polyline connecting them in order.  
  - Show approximate distance & ETA.  
- **Outputs:**  
  - Interactive map with preview route.

---

## 6. Navigation Agent (`Google Maps Intent`)
**Goal:** Start turn-by-turn navigation in Google Maps app.

- **Inputs:**  
  - Start + ordered stops.  
- **Logic:**  
  - Builds `https://www.google.com/maps/dir` URL with origin, destination, and waypoints.  
  - Launches Google Maps app via `Intent`.
- **Outputs:**  
  - External navigation session in Google Maps.

---

## 7. UI / Interaction Agent (`MainActivity`, `MapActivity`)
**Goal:** Provide simple user flow.

- **Actions:**  
  - **Scan & Extract Address** → triggers OCR Agent.  
  - **Add Stop** → adds to route.  
  - **Use My Location** → sets origin.  
  - **Optimize & Route** → calls Routing + Map agents.  
  - **Navigate** → triggers Navigation Agent.

---

## Future Agents
- **Server Route Agent:** Use Google Routes API with `optimizeWaypointOrder` for traffic-aware ETAs & polylines.  
- **Offline Maps Agent:** Support offline caching of map tiles & geocoding fallback.  
- **Analytics Agent:** Track route times, distances, and driver stats.

---
