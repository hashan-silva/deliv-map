# DelivMap

DelivMap is a Kotlin-based Android app that helps delivery drivers capture paper labels, extract addresses, geocode them, and build an optimized multi-stop route directly on a Google Map.

## Prerequisites

1. [Create a Google Maps Platform project](https://developers.google.com/maps/documentation/android-sdk/start) and enable the **Maps SDK for Android** and **Places API**.
2. Generate an Android API key that is restricted to the Maps SDK for Android.
3. Update `app/src/main/res/values/strings.xml` with your key:

```xml
<string name="google_maps_key">YOUR_API_KEY</string>
```

## Building and Running

1. Open the project in Android Studio (Giraffe or newer).
2. Sync Gradle when prompted.
3. Connect an Android device (API 24+) or start an emulator with Google Play services.
4. Run the `app` configuration.
5. Grant camera and location permissions when requested.

## Using DelivMap

1. **Scan the label**: On launch, the `MainActivity` shows a CameraX preview. Tap **Scan & Extract Address** to run ML Kit OCR on a single frame.
2. **Review detected candidates**: DelivMap automatically forwards likely address strings to the map screen.
3. **Add stops**: Tap **Add Stop** to geocode the next candidate (Geocoder first, then Places fallback). Repeat until all desired stops are added.
4. **Set your start**: Long-press the map or use **Use My Location as Start** (requires location permission and GPS).
5. **Optimize & visualize**: Press **Optimize & Route** to run a nearest-neighbor + 2-Opt heuristic. The ordered route appears as markers and a polyline, along with total distance and ETA (~40 km/h).
6. **Navigate**: Hit **Navigate** to launch Google Maps with the optimized origin, destination, and intermediate waypoints for turn-by-turn directions.

## Privacy Notes

* OCR happens on-device via ML Kit; captured frames are not uploaded.
* Geocoding and Places requests send the candidate address strings to Google services.

## Future Improvements

* Add optional OpenCV preprocessing (deskew/denoise) to improve OCR accuracy on difficult labels.
* Integrate the Google Routes API via a lightweight server to retrieve polylines and leverage cloud-side `optimizeWaypointOrder` for more precise routing.
