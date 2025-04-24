package com.example.multiway.ui.suggested

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multiway.R
import com.example.multiway.databinding.FragmentSuggestedroutesBinding
import com.google.android.gms.location.*
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSuggestedroutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        placeAutocomplete = PlaceAutocomplete.create()

        binding.mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            pointAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
            polylineAnnotationManager = binding.mapView.annotations.createPolylineAnnotationManager()

            // Add custom icon for POIs
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.tour)
            style.addImage("tour", bitmap)

            enableUserLocation()
        }

        adapter = SuggestedPlaceAdapter(suggestions) { item ->
            item.coordinate?.let {
                Toast.makeText(requireContext(), "Clicked: ${item.name}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnFetchSuggestions.setOnClickListener {
            val query = buildQueryFromCheckboxes()
            if (query.isNotEmpty()) {
                fetchSuggestions(query)
            } else {
                Toast.makeText(requireContext(), "Select at least one category", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildQueryFromCheckboxes(): String {
        val selected = mutableListOf<String>()
        if (binding.checkRestaurants.isChecked) selected.add("restaurant")
        if (binding.checkPubs.isChecked) selected.add("pub")
        if (binding.checkParks.isChecked) selected.add("park")
        return selected.joinToString(" OR ")
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }
        zoomToUserLocation()


        binding.mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
        }


    }

    @SuppressLint("MissingPermission")
    private fun zoomToUserLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                userLocation = Point.fromLngLat(location.longitude, location.latitude)

                binding.mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(userLocation)
                        .zoom(13.5)
                        .build()
                )

                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }


    private fun fetchSuggestions(query: String) {
        val location = userLocation ?: return
        suggestions.clear()
        pointAnnotationManager.deleteAll()
        polylineAnnotationManager.deleteAll()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val results = placeAutocomplete.suggestions(query = query, proximity = location).value ?: emptyList()

                val validPlaces = results.mapNotNull { suggestion ->
                    resolveCoordinates(suggestion)
                }.sortedBy { it.distanceKm }

                suggestions.addAll(validPlaces.take(6))
                adapter.notifyDataSetChanged()

                // Place POI markers
                suggestions.forEach { place ->
                    place.coordinate?.let { coord ->
                        pointAnnotationManager.create(
                            PointAnnotationOptions()
                                .withPoint(coord)
                                .withIconImage("tour")
                                .withIconSize(0.6)
                        )
                    }
                }

                drawRouteBetweenSuggestions()

            } catch (e: Exception) {
                Log.e("SuggestedDebug", "Error fetching suggestions: ${e.localizedMessage}")
                Toast.makeText(requireContext(), "Error fetching suggestions.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun resolveCoordinates(suggestion: PlaceAutocompleteSuggestion): SuggestedPlaceItem? {
        return try {
            val coord = placeAutocomplete.select(suggestion).value?.coordinate
            if (coord == null) {
                Log.d("SuggestedDebug", "Skipping ${suggestion.name} — no coordinate")
                null
            } else {
                val distance = calculateDistance(userLocation!!, coord)
                Log.d("SuggestedDebug", "Including ${suggestion.name} — $distance km")
                SuggestedPlaceItem(suggestion.name, coord, distance)
            }
        } catch (e: Exception) {
            Log.e("SuggestedDebug", "Error resolving suggestion: ${e.localizedMessage}")
            null
        }
    }

    private fun drawRouteBetweenSuggestions() {
        val coords = suggestions.mapNotNull { it.coordinate }
        if (coords.size < 2 || userLocation == null) return

        val directions = MapboxDirections.builder()
            .accessToken(getString(R.string.mapbox_access_token))
            .origin(userLocation!!)
            .destination(coords.last())
            .waypoints(coords.dropLast(1))
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(DirectionsCriteria.PROFILE_WALKING)
            .build()

        directions.enqueueCall(object : Callback<DirectionsResponse> {
            override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                val route = response.body()?.routes()?.firstOrNull() ?: return
                val geometry = route.geometry() ?: return

                val line = LineString.fromPolyline(geometry, 6)
                polylineAnnotationManager.create(
                    PolylineAnnotationOptions()
                        .withPoints(line.coordinates())
                        .withLineColor("#d900ff")
                        .withLineWidth(6.0)
                )
            }

            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                Log.e("SuggestedDebug", "Route error: ${t.localizedMessage}")
            }
        })
    }

    private fun calculateDistance(p1: Point, p2: Point): Double {
        val lat1 = p1.latitude()
        val lon1 = p1.longitude()
        val lat2 = p2.latitude()
        val lon2 = p2.longitude()

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 6371 * 2 * atan2(sqrt(a), sqrt(1 - a)) // km
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
