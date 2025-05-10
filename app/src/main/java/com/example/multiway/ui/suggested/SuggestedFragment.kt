package com.example.multiway.ui.suggested

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multiway.R
import com.example.multiway.databinding.FragmentSuggestedroutesBinding
import com.google.android.gms.common.api.Response
import com.google.android.gms.location.*
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.core.constants.Constants.PRECISION_6
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.search.autocomplete.PlaceAutocomplete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*
import com.mapbox.maps.plugin.locationcomponent.location
import javax.security.auth.callback.Callback
import retrofit2.Call





class SuggestedFragment : Fragment() {

    private var _binding: FragmentSuggestedroutesBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var adapter: SuggestedPlaceAdapter
    private lateinit var placeAutocomplete: PlaceAutocomplete

    private var userLocation: Point? = null
    private var selectedRadiusKm = 5.0
    private val suggestions = mutableListOf<SuggestedPlaceItem>()
    private var currentKeywords: List<String> = emptyList()

    private val routeTypeCategories = mapOf(
        "Romantic Walk" to listOf("romantic cafe", "scenic viewpoint", "quiet park"),
        "Family Day Out" to listOf("children museum", "zoo", "family park"),
        "Solo Exploring" to listOf("museum", "park", "nature reserve", "hidden gem", "historical site"),
        "Pub Crawl" to listOf("traditional pub", "craft beer bar", "rooftop bar", "pub", "bar","cocktail bar", "wine bar", "beer bar" )
    )

    private val moodCategoryMap = mapOf(
        "Chill Afternoon" to listOf("coffee shop", "scenic park", "bookstore"),
        "Party Night" to listOf("nightclub", "bar", "live music"),
        "Solo Exploring" to listOf("museum", "library", "landmark"),
        "Romantic Evening" to listOf("romantic restaurant", "rooftop bar", "sunset viewpoint")
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

            style.addImage("poi_notpicked", BitmapFactory.decodeResource(resources, R.drawable.route))
            style.addImage("poi_picked", BitmapFactory.decodeResource(resources, R.drawable.routepicked))

            val chipGroup = binding.routeChipGroup
            val toggleChipsButton = binding.toggleChipsButton

            toggleChipsButton.setOnClickListener {
                chipGroup.visibility = if (chipGroup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }



            binding.mapView.location.updateSettings {
                enabled = true
                pulsingEnabled = true
            }

            adapter = SuggestedPlaceAdapter(suggestions) { place ->
                place.isSelected = !place.isSelected
                adapter.notifyDataSetChanged()

                // Update map icon
                pointAnnotationManager.annotations.find { it.point == place.coordinate }?.let { annotation ->
                    annotation.iconImage = if (place.isSelected) "poi_picked" else "poi_notpicked"
                    pointAnnotationManager.update(annotation)
                }

                drawRouteToSelectedPOIs()
            }

            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerView.adapter = adapter

            pointAnnotationManager.addClickListener { annotation ->
                val clickedPoint = annotation.point
                val matched = suggestions.find { it.coordinate == clickedPoint }
                matched?.let {
                    it.isSelected = !it.isSelected
                    adapter.notifyDataSetChanged()

                    // Change icon dynamically
                    annotation.iconImage = if (it.isSelected) "poi_picked" else "poi_notpicked"
                    pointAnnotationManager.update(annotation)

                    drawRouteToSelectedPOIs()
                }
                true
            }


            enableUserLocation()
        }




        val moodOptions = resources.getStringArray(R.array.moods)
        binding.moodButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Mood")
                .setItems(moodOptions) { _, which ->
                    val selectedMood = moodOptions[which]
                    binding.moodButton.text = selectedMood
                    val keywords = moodCategoryMap[selectedMood] ?: emptyList()
                    currentKeywords = keywords
                    suggestPlaces(keywords)
                }
                .show()
        }

        binding.toggleChipsButton.setOnClickListener {
            val isVisible = binding.routeChipGroup.visibility == View.VISIBLE
            binding.routeChipGroup.visibility = if (isVisible) View.GONE else View.VISIBLE
        }



        binding.routeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val selected = when (checkedIds.firstOrNull()) {
                R.id.chipPubCrawl -> "Pub Crawl"
                R.id.chipRomantic -> "Romantic Walk"
                R.id.chipFamily -> "Family Day Out"
                R.id.chipSolo -> "Solo Exploring"
                else -> null
            }
            selected?.let {

                if (it == "Pub Crawl") {
                    Toast.makeText(requireContext(), "Please drink and travel responsibly!", Toast.LENGTH_LONG).show()
                    binding.pubCrawlHeader.visibility = View.VISIBLE
                } else {
                    binding.pubCrawlHeader.visibility = View.GONE
                }


                currentKeywords = routeTypeCategories[it] ?: emptyList()
                suggestPlaces(currentKeywords)
            }
        }
        setupRadiusSelector()
    }

    private fun suggestPlaces(keywords: List<String>) {
        val location = userLocation ?: return
        suggestions.clear()
        pointAnnotationManager.deleteAll()

        lifecycleScope.launch {
            val seen = mutableSetOf<String>()
            val found = mutableListOf<SuggestedPlaceItem>()

            for (keyword in keywords) {
                val results = withContext(Dispatchers.Main) {
                    placeAutocomplete.suggestions(query = keyword, proximity = location).value ?: emptyList()
                }

                withContext(Dispatchers.Main) {
                    for (suggestion in results) {
                        try {
                            val result = placeAutocomplete.select(suggestion).value ?: continue
                            val coord = result.coordinate ?: continue
                            val key = "${coord.latitude()},${coord.longitude()}"
                            val dist = calculateDistance(location, coord)

                            if (dist <= selectedRadiusKm && seen.add(key)) {
                                found.add(SuggestedPlaceItem(result.name, coord, dist))
                            }
                        } catch (e: Exception) {
                            // skip this result
                        }
                    }
                }
            }

            val sorted = found.sortedBy { it.distanceKm }
            suggestions.addAll(sorted)
            adapter.notifyDataSetChanged()

            sorted.forEach {
                pointAnnotationManager.create(
                    PointAnnotationOptions()
                        .withPoint(it.coordinate!!)
                        .withIconImage(if (it.isSelected) "poi_picked" else "poi_notpicked")
                        .withIconSize(0.9)
                )

            }




            if (sorted.isEmpty()) {
                Toast.makeText(requireContext(), "No results found in this radius.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRadiusSelector() {
        val options = arrayOf("1 km", "2 km", "5 km", "10 km", "20 km", "Country Wide")
        binding.radiusButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Radius")
                .setItems(options) { _, which ->
                    selectedRadiusKm = when (which) {
                        0 -> 1.0
                        1 -> 2.0
                        2 -> 5.0
                        3 -> 10.0
                        4 -> 20.0
                        else -> -1.0
                    }
                    suggestPlaces(currentKeywords)
                }
                .show()
        }
    }

    private fun enableUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                userLocation = Point.fromLngLat(loc.longitude, loc.latitude)
                userLocation?.let {
                    binding.mapView.mapboxMap.setCamera(
                        CameraOptions.Builder().center(it).zoom(13.0).build()
                    )
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }



    private fun calculateDistance(p1: Point, p2: Point): Double {
        val R = 6371.0
        val dLat = Math.toRadians(p2.latitude() - p1.latitude())
        val dLon = Math.toRadians(p2.longitude() - p1.longitude())
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(p1.latitude())) * cos(Math.toRadians(p2.latitude())) * sin(dLon / 2).pow(2.0)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun drawRouteToSelectedPOIs() {
        val origin = userLocation ?: return
        val selected = suggestions.filter { it.isSelected }.mapNotNull { it.coordinate }
        if (selected.isEmpty()) return

        if (selected.isEmpty()) {
            binding.mapView.mapboxMap.style?.apply {
                removeStyleLayer("route-layer")
                removeStyleSource("route-source")
            }
            return
        }


        val destination = selected.last()
        val waypoints = selected.dropLast(1)

        val client = MapboxDirections.builder()
            .origin(origin)
            .destination(destination)
            .apply {
                waypoints.forEach { addWaypoint(it) }
            }
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(DirectionsCriteria.PROFILE_WALKING)
            .accessToken(getString(R.string.mapbox_access_token))
            .build()

        client.enqueueCall(object : retrofit2.Callback<DirectionsResponse> {
            override fun onResponse(call: Call<DirectionsResponse>, response: retrofit2.Response<DirectionsResponse>) {
                val geometry = response.body()?.routes()?.firstOrNull()?.geometry() ?: return
                val lineString = LineString.fromPolyline(geometry, PRECISION_6)

                binding.mapView.mapboxMap.style?.apply {
                    removeStyleLayer("route-layer")
                    removeStyleSource("route-source")

                    addSource(geoJsonSource("route-source") {
                        geometry(lineString)
                    })

                    addLayer(lineLayer("route-layer", "route-source") {
                        lineColor("#d900ff")
                        lineWidth(5.0)
                        lineJoin(LineJoin.ROUND)
                    })
                }
            }

            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                Log.e("RouteError", "Failed to draw selected route: ${t.message}")
            }
        })
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
