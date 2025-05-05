package com.example.multiway.ui.suggested

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multiway.R
import com.example.multiway.databinding.FragmentSuggestedroutesBinding
import com.google.android.gms.location.*
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.toCameraOptions
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import kotlinx.coroutines.*
import kotlin.math.*

class SuggestedFragment : Fragment() {

    private var _binding: FragmentSuggestedroutesBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placeAutocomplete: PlaceAutocomplete
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var polylineAnnotationManager: PolylineAnnotationManager


    private val suggestions = mutableListOf<SuggestedPlaceItem>()
    private lateinit var adapter: SuggestedPlaceAdapter
    private var userLocation: Point? = null

    private val routeTypeCategories = mapOf(
        "Romantic Walk" to listOf("scenic", "cafe", "park"),
        "Family Day Out" to listOf("zoo", "museum", "park"),
        "Solo Exploring" to listOf("landmark", "restaurant"),
        "Pub Crawl" to listOf("pub", "bar")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSuggestedroutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        placeAutocomplete = PlaceAutocomplete.create()

        binding.mapView.mapboxMap.loadStyle(Style.DARK) { style ->
            pointAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.route)
            style.addImage("route", bitmap)
            enableUserLocation()
        }

        adapter = SuggestedPlaceAdapter(suggestions) { item ->
            item.coordinate?.let {
                Toast.makeText(requireContext(), "Clicked: ${item.name}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
        }

        polylineAnnotationManager = binding.mapView.annotations.createPolylineAnnotationManager()

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnFetchSuggestions.setOnClickListener {
            val routeType = binding.routeType.selectedItem.toString()
            val categories = if (routeType != "None") {
                routeTypeCategories[routeType] ?: listOf()
            } else {
                buildQueryFromCheckboxes()
            }

            if (categories.isNotEmpty()) {
                suggestPlaces(categories)
            } else {
                Toast.makeText(requireContext(), "Select categories or route type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildQueryFromCheckboxes(): List<String> {
        val selected = mutableListOf<String>()
        if (binding.checkRestaurants.isChecked) selected.add("restaurant")
        if (binding.checkPubs.isChecked) selected.add("pub")
        if (binding.checkParks.isChecked) selected.add("park")
        return selected
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                userLocation = Point.fromLngLat(location.longitude, location.latitude)
                binding.mapView.mapboxMap.setCamera(CameraOptions.Builder().center(userLocation).zoom(13.0).build())
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun suggestPlaces(categories: List<String>) {
        val query = categories.joinToString(" OR ")
        val location = userLocation ?: return

        suggestions.clear()
        pointAnnotationManager.deleteAll()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val results = placeAutocomplete.suggestions(query = query, proximity = location).value ?: emptyList()

                val resolved = results.mapNotNull {
                    try {
                        val coord = placeAutocomplete.select(it).value?.coordinate
                        if (coord != null) {
                            val dist = calculateDistance(location, coord)
                            SuggestedPlaceItem(it.name, coord, dist)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }

                val sorted = resolved.sortedBy { it.distanceKm }.take(6)
                suggestions.addAll(sorted)
                adapter.notifyDataSetChanged()

                sorted.forEach { place ->
                    place.coordinate?.let { coord ->
                        pointAnnotationManager.create(
                            PointAnnotationOptions()
                                .withPoint(coord)
                                .withIconImage("route")
                                .withIconSize(0.9)
                        )
                    }
                }
                drawLineBetweenSuggestions()

                val allPoints = mutableListOf<Point>()
                userLocation?.let { allPoints.add(it) }
                allPoints.addAll(sorted.mapNotNull { it.coordinate })
                zoomToIncludePoints(allPoints)

            } catch (e: Exception) {
                Log.e("SuggestedDebug", "Error fetching: ${e.message}")
            }
        }
    }

    private fun drawLineBetweenSuggestions() {
        val points = suggestions.mapNotNull { it.coordinate }
        if (points.size < 2) return

        val line = PolylineAnnotationOptions()
            .withPoints(points)
            .withLineColor("#d900ff")
            .withLineWidth(4.0)

        polylineAnnotationManager.deleteAll()
        polylineAnnotationManager.create(line)
    }


    private fun calculateDistance(p1: Point, p2: Point): Double {
        val dLat = Math.toRadians(p2.latitude() - p1.latitude())
        val dLon = Math.toRadians(p2.longitude() - p1.longitude())
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(p1.latitude())) * cos(Math.toRadians(p2.latitude())) * sin(dLon / 2).pow(2.0)
        return 6371 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun zoomToIncludePoints(points: List<Point>) {
        if (points.isEmpty()) return

        val currentCamera = binding.mapView.mapboxMap.cameraState.toCameraOptions()

        val cameraOptions = binding.mapView.mapboxMap.cameraForCoordinates(
            coordinates = points,
            camera = currentCamera,
            coordinatesPadding = com.mapbox.maps.EdgeInsets(100.0, 100.0, 100.0, 100.0),
            maxZoom = null,
            offset = null
        )

        binding.mapView.mapboxMap.setCamera(cameraOptions)
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
