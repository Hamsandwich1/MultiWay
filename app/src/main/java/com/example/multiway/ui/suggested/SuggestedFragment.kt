package com.example.multiway.ui.suggested

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multiway.R
import com.example.multiway.databinding.FragmentSuggestedroutesBinding
import com.example.multiway.databinding.SuggestedrouteBinding
import com.google.android.gms.location.*
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.search.autocomplete.PlaceAutocomplete
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


class SuggestedFragment : Fragment() {


    data class SuggestedPlaceItem(
        val name: String,
        val coordinate: Point?
    )

    private var _binding: FragmentSuggestedroutesBinding? = null
    private val binding get() = _binding!!

    private lateinit var placeAutocomplete: PlaceAutocomplete
    private lateinit var adapter: SuggestedPlaceAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var userAnnotationManager: PointAnnotationManager
    private lateinit var placeAnnotationManager: PointAnnotationManager
    private lateinit var polylineAnnotationManager: PolylineAnnotationManager

    private var userLocation: Point? = null
    private val suggestions = mutableListOf<SuggestedPlaceItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSuggestedroutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        binding.mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) {
            userAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
            placeAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
            polylineAnnotationManager = binding.mapView.annotations.createPolylineAnnotationManager()

            enableUserLocation()
        }

        adapter = SuggestedPlaceAdapter(suggestions) { item ->
            item.coordinate?.let { showRouteToPlace(it) }
        }

        binding.recyclerSuggestions.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerSuggestions.adapter = adapter

        binding.btnSuggest.setOnClickListener {
            val selectedCategories = getSelectedCategories()
            if (selectedCategories.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one interest", Toast.LENGTH_SHORT).show()
            } else {
                suggestPlaces(selectedCategories)
            }
        }
    }

    private fun getSelectedCategories(): List<String> {
        val list = mutableListOf<String>()
        if (binding.checkRestaurants.isChecked) list.add("restaurant")
        if (binding.checkPubs.isChecked) list.add("pub")
        if (binding.checkParks.isChecked) list.add("park")
        return list
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100
            )
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                userLocation = Point.fromLngLat(loc.longitude, loc.latitude)
                moveCamera(userLocation!!)
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun suggestPlaces(categories: List<String>) {
        if (userLocation == null) {
            Toast.makeText(requireContext(), "Getting location, try again shortly.", Toast.LENGTH_SHORT).show()
            return
        }

        placeAutocomplete = PlaceAutocomplete.create()
        val query = categories.joinToString(" OR ")

        CoroutineScope(Dispatchers.Main).launch {
            val result = placeAutocomplete.suggestions(query, proximity = userLocation!!)
            val results = result.value ?: emptyList()

            val sorted = results
                .filter { it.coordinate != null }
                .sortedBy { calculateDistance(userLocation!!, it.coordinate!!) }
                .take(6)

            suggestions.clear()
            suggestions.addAll(sorted.map { SuggestedPlaceItem(it.name, it.coordinate) })
            adapter.notifyDataSetChanged()
        }
    }

    private fun showRouteToPlace(destination: Point) {
        if (userLocation == null) return

        placeAnnotationManager.deleteAll()
        polylineAnnotationManager.deleteAll()

        placeAnnotationManager.create(
            PointAnnotationOptions()
                .withPoint(destination)
                .withIconImage("custom-marker")
                .withIconSize(0.6)
        )

        val client = MapboxDirections.builder()
            .origin(userLocation!!)
            .destination(destination)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .accessToken(getString(R.string.mapbox_access_token))
            .build()

        client.enqueueCall(object : Callback<DirectionsResponse> {
            override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                val route = response.body()?.routes()?.firstOrNull() ?: return
                val line = LineString.fromPolyline(route.geometry()!!, 6)

                polylineAnnotationManager.create(
                    PolylineAnnotationOptions()
                        .withPoints(line.coordinates())
                        .withLineColor("#D900FF")
                        .withLineWidth(6.0)
                )

                moveCamera(destination)
            }

            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                Toast.makeText(requireContext(), "Route failed: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun calculateDistance(point1: Point, point2: Point): Double {
        val lat1 = point1.latitude()
        val lon1 = point1.longitude()
        val lat2 = point2.latitude()
        val lon2 = point2.longitude()

        val earthRadius = 6371.0 // in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }


    private fun moveCamera(point: Point) {
        binding.mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(point)
                .zoom(13.5)
                .build()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
