package com.hashan0314.delivmap

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.FetchPlaceRequest
import com.google.android.libraries.places.api.model.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

class MapActivity : AppCompatActivity() {

    private lateinit var map: GoogleMap
    private lateinit var addStopButton: Button
    private lateinit var useLocationButton: Button
    private lateinit var optimizeButton: Button
    private lateinit var navigateButton: Button
    private lateinit var summaryText: TextView

    private val candidateQueue = ArrayDeque<String>()
    private val stops = mutableListOf<Stop>()
    private var optimizedOrder: List<Int>? = null
    private var startLatLng: LatLng? = null

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var placesClient: PlacesClient

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (::map.isInitialized) {
                enableMyLocation()
            }
            setStartFromDeviceLocation()
        } else {
            Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        addStopButton = findViewById(R.id.addStopButton)
        useLocationButton = findViewById(R.id.useLocationButton)
        optimizeButton = findViewById(R.id.optimizeButton)
        navigateButton = findViewById(R.id.navigateButton)
        summaryText = findViewById(R.id.routeSummary)

        val candidates = intent.getStringArrayListExtra(EXTRA_ADDRESS_CANDIDATES) ?: arrayListOf()
        candidateQueue.addAll(candidates)
        if (candidateQueue.isEmpty()) {
            summaryText.text = getString(R.string.no_candidates_message)
        }

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }
        placesClient = Places.createClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            map = googleMap
            map.uiSettings.isZoomControlsEnabled = true
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CAMERA_POSITION, 10f))
            map.setOnMapLongClickListener { latLng ->
                setStartPoint(latLng)
                Toast.makeText(this, "Start set manually.", Toast.LENGTH_SHORT).show()
            }
            enableMyLocation()
            updateMap()
        }

        addStopButton.setOnClickListener { fetchNextResolvedStop() }
        useLocationButton.setOnClickListener { requestLocationPermissionAndSetStart() }
        optimizeButton.setOnClickListener { optimizeRoute() }
        navigateButton.setOnClickListener { openNavigation() }
    }

    private fun fetchNextResolvedStop() {
        lifecycleScope.launch {
            val stop = withContext(Dispatchers.IO) {
                resolveNextCandidate()
            }
            if (stop != null) {
                stops.add(stop)
                optimizedOrder = null
                updateMap()
                Toast.makeText(this@MapActivity, "Added stop: ${stop.label}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MapActivity, "Couldn't resolve address", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun resolveNextCandidate(): Stop? {
        while (candidateQueue.isNotEmpty()) {
            val candidate = candidateQueue.removeFirst()
            val latLng = geocodeCandidate(candidate)
            if (latLng != null) {
                return Stop(candidate, latLng)
            }
        }
        return null
    }

    private suspend fun geocodeCandidate(candidate: String): LatLng? {
        val geocoderResult = geocodeWithAndroid(candidate)
        if (geocoderResult != null) return geocoderResult
        return geocodeWithPlaces(candidate)
    }

    private suspend fun geocodeWithAndroid(candidate: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(this@MapActivity, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(candidate, 5)
            val address = addresses?.firstOrNull()
            address?.let { LatLng(it.latitude, it.longitude) }
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun geocodeWithPlaces(candidate: String): LatLng? {
        return try {
            val predictionsRequest = FindAutocompletePredictionsRequest.builder()
                .setQuery(candidate)
                .build()
            val predictions = placesClient.findAutocompletePredictions(predictionsRequest).await()
            val placeId = predictions.autocompletePredictions.firstOrNull()?.placeId ?: return null
            val placeRequest = FetchPlaceRequest.newInstance(
                placeId,
                listOf(Place.Field.LAT_LNG, Place.Field.NAME, Place.Field.ADDRESS)
            )
            val place = placesClient.fetchPlace(placeRequest).await().place
            place.latLng
        } catch (exception: Exception) {
            null
        }
    }

    private fun requestLocationPermissionAndSetStart() {
        if (hasLocationPermission()) {
            setStartFromDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun setStartFromDeviceLocation() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission required.", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    setStartPoint(latLng)
                    if (::map.isInitialized) {
                        enableMyLocation()
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                    }
                } else {
                    Toast.makeText(this, "Couldn't determine current location.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't access location.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setStartPoint(latLng: LatLng) {
        startLatLng = latLng
        optimizedOrder = null
        updateMap()
    }

    private fun updateMap() {
        if (!::map.isInitialized) return
        map.clear()

        val start = startLatLng
        val orderedStops = optimizedOrder?.map { stops[it] } ?: stops

        if (start != null) {
            map.addMarker(
                MarkerOptions()
                    .position(start)
                    .title(getString(R.string.start_marker))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        }

        orderedStops.forEachIndexed { index, stop ->
            map.addMarker(
                MarkerOptions()
                    .position(stop.latLng)
                    .title(String.format(getString(R.string.stop_marker), index + 1))
            )
        }

        if (start != null && orderedStops.isNotEmpty()) {
            val polylinePoints = mutableListOf<LatLng>()
            polylinePoints.add(start)
            polylinePoints.addAll(orderedStops.map { it.latLng })
            map.addPolyline(
                PolylineOptions()
                    .addAll(polylinePoints)
                    .width(8f)
                    .color(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            )
        }

        when {
            start != null && orderedStops.isNotEmpty() ->
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(orderedStops.last().latLng, 12f))
            start != null ->
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(start, 12f))
            orderedStops.isNotEmpty() ->
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(orderedStops.last().latLng, 12f))
        }

        if (orderedStops.isEmpty() && optimizedOrder == null) {
            summaryText.text = getString(R.string.route_summary_placeholder)
        }
    }

    private fun optimizeRoute() {
        val start = startLatLng
        if (start == null) {
            Toast.makeText(this, "Set a start location first.", Toast.LENGTH_LONG).show()
            return
        }
        if (stops.isEmpty()) {
            Toast.makeText(this, "Add at least one stop before optimizing.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val points = stops.map { it.latLng }
            val initialOrder = Routing.nearestNeighborOrder(start, points)
            val improvedOrder = Routing.twoOpt(initialOrder, points, start)
            optimizedOrder = improvedOrder
            val totalMeters = calculateRouteDistance(start, improvedOrder)
            val totalKm = totalMeters / 1000.0
            val minutes = ((totalKm / 40.0) * 60).roundToInt().coerceAtLeast(1)
            withContext(Dispatchers.Main) {
                summaryText.text = getString(R.string.optimize_results, totalKm, minutes)
                updateMap()
            }
        }
    }

    private fun calculateRouteDistance(start: LatLng, order: List<Int>): Double {
        var total = 0.0
        var previous = start
        for (index in order) {
            val next = stops[index].latLng
            total += Routing.haversine(previous, next)
            previous = next
        }
        return total
    }

    private fun openNavigation() {
        val start = startLatLng
        if (start == null) {
            Toast.makeText(this, "Set a start location first.", Toast.LENGTH_LONG).show()
            return
        }
        val orderedStops = optimizedOrder?.map { stops[it] } ?: stops
        if (orderedStops.isEmpty()) {
            Toast.makeText(this, "Add at least one stop before navigating.", Toast.LENGTH_LONG).show()
            return
        }

        val origin = "${start.latitude},${start.longitude}"
        val destinationStop = orderedStops.last()
        val destination = "${destinationStop.latLng.latitude},${destinationStop.latLng.longitude}"
        val waypointString = orderedStops.dropLast(1)
            .joinToString("|") { "${it.latLng.latitude},${it.latLng.longitude}" }

        val uriBuilder = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("origin", origin)
            .appendQueryParameter("destination", destination)
            .appendQueryParameter("travelmode", "driving")
        if (waypointString.isNotEmpty()) {
            uriBuilder.appendQueryParameter("waypoints", waypointString)
        }
        val uri = uriBuilder.build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, "Google Maps app not installed.", Toast.LENGTH_LONG).show()
        }
    }

    private fun enableMyLocation() {
        if (hasLocationPermission()) {
            try {
                map.isMyLocationEnabled = true
            } catch (_: SecurityException) {
                // Ignore, permission state changed unexpectedly.
            }
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Unknown task failure"))
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }

    companion object {
        const val EXTRA_ADDRESS_CANDIDATES = "address_candidates"
        private val DEFAULT_CAMERA_POSITION = LatLng(59.3293, 18.0686)
    }
}
