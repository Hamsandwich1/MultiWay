package com.example.multiway.ui.gallery

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.telecom.Call
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multiway.R
import com.example.multiway.databinding.FragmentGalleryBinding
import com.google.android.gms.common.api.Response
import com.google.android.gms.location.*
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.*
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.*
import com.mapbox.maps.extension.style.layers.properties.generated.*
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.*
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import com.mapbox.search.ui.adapter.autocomplete.PlaceAutocompleteUiAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.*
import com.mapbox.geojson.Polygon
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.layers.generated.FillLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.properties.generated.FillExtrusionTranslateAnchor
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.search.SearchOptions
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.search.result.SearchResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.gson.JsonParser
import com.google.android.material.bottomsheet.BottomSheetBehavior




import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import org.json.JSONObject


data class SearchResultItem(
    val name: String,
    val distanceKm: Double,
    val point: Point
)

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapView: com.mapbox.maps.MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placeAutocomplete: PlaceAutocomplete
    private lateinit var placeAutocompleteUiAdapter: PlaceAutocompleteUiAdapter
    private lateinit var searchAnnotationManager: PointAnnotationManager
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private var selectedRadiusKm = 5.0
    private var userLocation: Point? = null
    private val radiusKm = 5.0
    private var selectedCategory = "restaurant"
    private lateinit var searchEngine: com.mapbox.search.SearchEngine
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private var currentRouteDestination: Point? = null
    private var selectedTravelMode: String = DirectionsCriteria.PROFILE_WALKING


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mapView = binding.mapView
        pointAnnotationManager = mapView.annotations.createPointAnnotationManager()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        placeAutocomplete = PlaceAutocomplete.create(locationProvider = null)
        placeAutocompleteUiAdapter =
            PlaceAutocompleteUiAdapter(binding.searchResultsView, placeAutocomplete)

        setupRecycler()
        setupSearchBox()
        setupCategorySpinner()
        setupRadiusSelector()
        binding.directionsRecycler.layoutManager = LinearLayoutManager(requireContext())


        var isResultsVisible = true
        binding.btnToggleResults.setOnClickListener {
            isResultsVisible = !isResultsVisible
            binding.customResultsList.isVisible = isResultsVisible
            binding.btnToggleResults.text = if (isResultsVisible) "Hide Results" else "Show Results"
        }


        // 👇 Fix is here
        mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            loadIconsIntoStyle(style)
            searchAnnotationManager = mapView.annotations.createPointAnnotationManager()

            enableUserLocation()

            zoomToUserLocation {
                drawRadiusCircle()
                searchNearbyPlaces() // ✅ Safe to call here now
            }
        }

        binding.btnWalking.setOnClickListener {
            selectedTravelMode = DirectionsCriteria.PROFILE_WALKING
            currentRouteDestination?.let { fetchAndDrawRoute(it) }
        }

        binding.btnDriving.setOnClickListener {
            selectedTravelMode = DirectionsCriteria.PROFILE_DRIVING
            currentRouteDestination?.let { fetchAndDrawRoute(it) }
        }

        val bottomSheet = binding.placeInfoBottomSheet
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = 300
        behavior.isHideable = false
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.btnToggleBottomSheetVisibility.setOnClickListener {
            behavior.state = if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }


        pointAnnotationManager.addClickListener { clickedAnnotation ->
            val data = clickedAnnotation.getData()
            val json = data?.takeIf { it.isJsonObject }?.asJsonObject

            val name = json?.get("name")?.asString ?: "Unknown Place"
            val category = json?.get("category")?.asString ?: "Unknown Category"
            val destination = clickedAnnotation.point

            currentRouteDestination = destination

            binding.placeName.text = name
            binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
            binding.btnGetDirections.isVisible = true // 👈 show the button

            true
        }




        var isMenuVisible = true
        binding.toggleMenuButton.setOnClickListener {
            isMenuVisible = !isMenuVisible
            binding.searchContainer.isVisible = isMenuVisible
            binding.toggleMenuButton.setImageResource(
                if (isMenuVisible) R.drawable.menuopen else R.drawable.menuclose
            )
        }

        binding.btnGetDirections.setOnClickListener {
            val destination = currentRouteDestination ?: return@setOnClickListener
            fetchAndDrawRoute(destination)
        }


        mapView.gestures.addOnMapLongClickListener { geoPoint ->
            draw5kCircle(geoPoint)
            fetchAndShowPOIs(geoPoint)
            true
        }





    }

    private fun setupRecycler() {
        searchResultsAdapter = SearchResultsAdapter { item ->
            fetchAndDrawRoute(item.point)

        }
        binding.customResultsList.layoutManager = LinearLayoutManager(requireContext())
        binding.customResultsList.adapter = searchResultsAdapter
    }

    private fun setupSearchBox() {
        binding.queryEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchNearbyPlaces()
            }
        })
    }

    private fun setupCategorySpinner() {
        val categories = listOf("Restaurants", "Pubs", "Parks", "Hotels", "Shops", "Tourist Attractions")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        binding.categorySpinner.adapter = adapter

        binding.categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, id: Long) {
                selectedCategory = when (categories[position]) {
                    "Restaurants" -> "restaurant"
                    "Pubs" -> "pub"
                    "Parks" -> "park"
                    "Hotels" -> "hotel"
                    "Shops" -> "shop"
                    "Tourist Attractions" -> "tourist_attraction"
                    else -> "restaurant"
                }
                searchNearbyPlaces()
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun showPOIDetails(name: String, location: Point) {
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setMessage("Latitude: ${location.latitude()}\nLongitude: ${location.longitude()}")
            .setPositiveButton("OK", null)
            .show()
    }


    private fun searchNearbyPlaces() {
        lifecycleScope.launch {
            val location = userLocation ?: return@launch

            val keywords = if (binding.queryEditText.text.isNotBlank()) {
                listOf(binding.queryEditText.text.toString())
            } else {
                when (selectedCategory) {
                    "pub" -> listOf("pub", "bar", "brewery", "tavern")
                    "restaurant" -> listOf("restaurant", "food", "diner")
                    "shop" -> listOf("shop", "store", "market", "supermarket")
                    "tourist_attraction" -> listOf("tourist", "museum", "landmark")
                    "park" -> listOf("park", "garden", "green", "forest")
                    "hotel" -> listOf("hotel", "inn", "resort", "hostel")
                    else -> listOf(selectedCategory)
                }
            }

            val results = mutableListOf<SearchResultItem>()
            val seenCoords = mutableSetOf<String>()
            searchAnnotationManager.deleteAll()

            val proximityPoints = listOf(location) + predefinedProximityPoints

            for (keyword in keywords) {
                for (proximity in proximityPoints) {
                    val suggestions = withContext(Dispatchers.IO) {
                        placeAutocomplete.suggestions(
                            query = keyword,
                            proximity = proximity
                        ).value ?: emptyList()
                    }

                    for (suggestion in suggestions) {
                        try {
                            val result = placeAutocomplete.select(suggestion).value
                            val coord = result?.coordinate ?: continue
                            val lat = coord.latitude()
                            val lon = coord.longitude()
                            val key = "$lat,$lon"

                            if (seenCoords.contains(key)) continue

                            val distance = calculateDistance(location, coord)
                            if (distance > radiusKm) continue

                            seenCoords.add(key)

                            val icon = when (detectCategory(result.name)) {
                                "restaurant" -> "restaurant-icon"
                                "pub" -> "pub-icon"
                                "park" -> "park-icon"
                                "hotel" -> "hotel-icon"
                                "shop" -> "shop-icon"
                                "tourist_attraction" -> "tour-icon"
                                else -> "restaurant-icon"
                            }

                            searchAnnotationManager.create(
                                PointAnnotationOptions()
                                    .withPoint(coord)
                                    .withIconImage(icon)
                                    .withIconSize(0.3)
                            )

                            results.add(SearchResultItem(result.name, distance, coord))
                        } catch (e: Exception) {
                            Log.e("POIMarker", "Error resolving suggestion: ${e.message}")
                        }
                    }
                }
            }

            searchResultsAdapter.update(results)
            binding.customResultsList.isVisible = results.isNotEmpty()
        }
    }


    private fun fetchAndShowPOIs(center: Point) {
        lifecycleScope.launch {
            val keywords = listOf("restaurant", "pub", "bar", "hotel", "tourist", "shop", "park")

            val results = mutableListOf<SearchResultItem>()
            val seenCoords = mutableSetOf<String>()
            pointAnnotationManager.deleteAll()

            for (keyword in keywords) {
                val suggestions = withContext(Dispatchers.IO) {
                    placeAutocomplete.suggestions(
                        query = keyword,
                        proximity = center
                    ).value ?: emptyList()
                }

                for (suggestion in suggestions) {
                    try {
                        val result = placeAutocomplete.select(suggestion).value
                        val coord = result?.coordinate ?: continue
                        val lat = coord.latitude()
                        val lon = coord.longitude()
                        val key = "$lat,$lon"
                        if (seenCoords.contains(key)) continue

                        val distance = calculateDistance(center, coord)
                        if (distance > 5.0) continue

                        seenCoords.add(key)

                        val category = detectCategory(result.name)
                        if (category != selectedCategory) continue
                        val icon = when (category) {
                            "restaurant" -> "restaurant-icon"
                            "pub" -> "pub-icon"
                            "park" -> "park-icon"
                            "hotel" -> "hotel-icon"
                            "shop" -> "shop-icon"
                            "tourist_attraction" -> "tour-icon"
                            else -> "restaurant-icon"
                        }

                        val jsonObject = JSONObject().apply {
                            put("name", result.name)
                            put("category", category)
                        }

                        pointAnnotationManager.create(
                            PointAnnotationOptions()
                                .withPoint(coord)
                                .withIconImage(icon)  // ✅ icon is defined above
                                .withIconSize(0.3)
                                .withData(JsonParser.parseString(jsonObject.toString()))
                        )



                        results.add(SearchResultItem(result.name, distance, coord))
                    } catch (e: Exception) {
                        Log.e("POI Fetch", "Error resolving suggestion: ${e.message}")
                    }
                }
            }



        }
    }




    private val proximityPoints = listOf(
        Point.fromLngLat(-6.2603, 53.3498),   // Center (Dublin)
        Point.fromLngLat(-6.18497, 53.3498),
        Point.fromLngLat(-6.22264, 53.38874),
        Point.fromLngLat(-6.29796, 53.38874),
        Point.fromLngLat(-6.33563, 53.3498),
        Point.fromLngLat(-6.29796, 53.31086),
        Point.fromLngLat(-6.22264, 53.31086)
    )

    private val predefinedProximityPoints = listOf(
        Point.fromLngLat(-6.18497, 53.3498),
        Point.fromLngLat(-6.22264, 53.38874),
        Point.fromLngLat(-6.29796, 53.38874),
        Point.fromLngLat(-6.33563, 53.3498),
        Point.fromLngLat(-6.29796, 53.31086),
        Point.fromLngLat(-6.22264, 53.31086)
    )


    private fun calculateDistance(p1: Point, p2: Point): Double {
        val R = 6371.0
        val lat1 = Math.toRadians(p1.latitude())
        val lat2 = Math.toRadians(p2.latitude())
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(p2.longitude() - p1.longitude())
        val a = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun setupRadiusSelector() {
        val options = arrayOf("1 km", "2 km", "5 km", "10 km", "15 km", "20 km", "Country Wide")

        binding.radiusButton1.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Radius")
                .setItems(options) { _, which ->
                    selectedRadiusKm = when (which) {
                        0 -> 1.0
                        1 -> 2.0
                        2 -> 5.0
                        3 -> 10.0
                        4 -> 15.0
                        5 -> 20.0
                        else -> -1.0
                    }
                    drawRadiusCircle()
                    searchNearbyPlaces()
                }
                .show()
        }
    }

    fun isInsideRadius(center: Point, target: Point, radiusKm: Double): Boolean {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(target.latitude() - center.latitude())
        val dLon = Math.toRadians(target.longitude() - center.longitude())

        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(center.latitude())) *
                cos(Math.toRadians(target.latitude())) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = earthRadius * c

        return distance <= radiusKm
    }



    private fun drawRadiusCircle() {
        val center = userLocation ?: return
        if (selectedRadiusKm <= 0) return // skip drawing if "Country Wide"

        val radiusMeters = selectedRadiusKm * 1000
        val steps = 64
        val earthRadius = 6371000.0
        val circlePoints = mutableListOf<Point>()

        if (selectedRadiusKm <= 0) return // skip drawing circle


        for (i in 0..steps) {
            val theta = 2 * Math.PI * i / steps
            val dx = radiusMeters * cos(theta)
            val dy = radiusMeters * sin(theta)
            val lat = center.latitude() + (dy / earthRadius) * (180 / Math.PI)
            val lon = center.longitude() + (dx / (earthRadius * cos(Math.toRadians(center.latitude())))) * (180 / Math.PI)
            circlePoints.add(Point.fromLngLat(lon, lat))
        }

        mapView.mapboxMap.getStyle { style ->
            if (style.getSource("circle-source") == null) {
                style.addSource(geoJsonSource("circle-source") {
                    geometry(Polygon.fromLngLats(listOf(circlePoints)))
                })
                style.addLayer(FillLayer("circle-layer", "circle-source").apply {
                    fillColor("#3399FF")
                    fillOpacity(0.25)
                })
            } else {
                style.getSourceAs<GeoJsonSource>("circle-source")?.geometry(Polygon.fromLngLats(listOf(circlePoints)))
            }
        }
    }

    private fun draw5kCircle(center: Point) {
        val radiusKm = 5.0
        val steps = 64
        val earthRadius = 6371.0

        val circlePoints = mutableListOf<Point>()
        for (i in 0 until steps) {
            val angle = 2 * Math.PI * i / steps
            val dx = radiusKm / earthRadius * cos(angle)
            val dy = radiusKm / earthRadius * sin(angle)

            val lat = center.latitude() + Math.toDegrees(dy)
            val lon =
                center.longitude() + Math.toDegrees(dx / cos(Math.toRadians(center.latitude())))
            circlePoints.add(Point.fromLngLat(lon, lat))
        }
        circlePoints.add(circlePoints[0]) // close the ring

        val polygon = Polygon.fromLngLats(listOf(circlePoints))

        val geoJsonSource = geoJsonSource("circle-source") {
            geometry(polygon)
        }

        val fillLayer = fillLayer("circle-layer", "circle-source") {
            fillColor("#3399FF")
            fillOpacity(0.4)
        }

        mapView.mapboxMap.style?.apply {
            removeStyleLayer("circle-layer")
            removeStyleSource("circle-source")
            addSource(geoJsonSource)
            addLayer(fillLayer)
        }
    }





    private fun enableUserLocation() {
        mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
        mapView.location.addOnIndicatorPositionChangedListener {
            userLocation = Point.fromLngLat(it.longitude(), it.latitude())
        }
    }

    private fun zoomToUserLocation(onLocated: () -> Unit) {
        if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLocation = Point.fromLngLat(location.longitude, location.latitude)
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(userLocation)
                        .zoom(14.0)
                        .build()
                )
                onLocated()
            }
        }
    }

    private fun loadIconsIntoStyle(style: Style) {
        val iconMap = mapOf(
            "restaurant-icon" to R.drawable.food,
            "pub-icon" to R.drawable.pub,
            "park-icon" to R.drawable.park,
            "hotel-icon" to R.drawable.hotel,
            "shop-icon" to R.drawable.shop,
            "tour-icon" to R.drawable.tour,
        )


        iconMap.forEach { (key, resId) ->
            val bitmap = BitmapFactory.decodeResource(resources, resId)
            style.addImage(key, bitmap)
        }
    }

    private fun detectCategory(name: String): String {
        val lowerName = name.lowercase(Locale.getDefault())

        return when {
            // Restaurants
            listOf("restaurant", "grill", "diner", "eatery", "café", "bistro", "food", "pizza", "steakhouse", "bbq", "sushi", "noodle", "kitchen")
                .any { it in lowerName } -> "restaurant"

            // Pubs and Bars
            listOf("pub", "bar", "tavern", "brewery", "taproom", "ale", "lounge", "saloon", "club")
                .any { it in lowerName } -> "pub"

            // Parks and Nature
            listOf("park", "garden", "green", "nature", "forest", "woods", "trail", "reserve")
                .any { it in lowerName } -> "park"

            // Hotels and Lodging
            listOf("hotel", "inn", "resort", "hostel", "motel", "bnb", "guesthouse", "lodging", "accommodation")
                .any { it in lowerName } -> "hotel"

            // Shops and Retail
            listOf("shop", "store", "supermarket", "market", "convenience", "grocery", "boutique", "retail", "mall", "minimart", "chemist", "corner shop")
                .any { it in lowerName } -> "shop"

            // Tourist Attractions
            listOf("tourist", "museum", "landmark", "attraction", "gallery", "castle", "monument", "church", "cathedral", "historic", "heritage", "ruins", "site")
                .any { it in lowerName } -> "tourist_attraction"

            else -> "default"
        }
    }


    private fun fetchAndDrawRoute(destination: Point) {
        val origin = userLocation
        if (origin == null) {
            return
        }

        val client = MapboxDirections.builder()
            .origin(origin)
            .destination(destination)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(selectedTravelMode)
            .steps(true) // ✅ Ensures step-by-step instructions are returned
            .accessToken(getString(R.string.mapbox_access_token))
            .build()

        client.enqueueCall(object : retrofit2.Callback<DirectionsResponse> {
            override fun onResponse(call: retrofit2.Call<DirectionsResponse>, response: retrofit2.Response<DirectionsResponse>) {
                val route = response.body()?.routes()?.firstOrNull()


                route?.legs()?.forEachIndexed { i, leg ->
                }

                if (route == null || route.geometry() == null) {
                    return
                }

                val steps = route.legs()
                    ?.flatMap { it.steps() ?: emptyList() }
                    ?.mapIndexed { index, step -> "${index + 1}. ${step.maneuver().instruction()}" }
                    ?: listOf("No steps available.")

                requireActivity().runOnUiThread {
                    val durationMinutes = (route.duration() / 60).roundToInt()
                    val durationText = "Estimated time: $durationMinutes min"
                    requireActivity().runOnUiThread {
                        binding.directionSummary.text = durationText
                        binding.directionSummary.visibility = View.VISIBLE
                    }
                    binding.directionsRecycler.adapter = DirectionsAdapter(steps)
                    binding.directionsRecycler.isVisible = true


                }

                val lineString = LineString.fromPolyline(route.geometry()!!, 6)

                mapView.mapboxMap.getStyle { style ->
                    val routeSource = style.getSourceAs<GeoJsonSource>("route-source")
                    if (routeSource == null) {
                        style.addSource(
                            geoJsonSource("route-source") {
                                geometry(lineString)
                            }
                        )
                    } else {
                        routeSource.geometry(lineString)
                    }

                    if (!style.styleLayerExists("route-layer")) {
                        style.addLayer(
                            LineLayer("route-layer", "route-source").apply {
                                lineColor("#d900ff")
                                lineWidth(5.0)
                            }
                        )
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
                Log.e("RouteDraw", "Directions API call failed: ${t.localizedMessage}")
            }
        })
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
