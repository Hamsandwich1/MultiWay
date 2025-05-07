package com.example.multiway.ui.gallery

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multiway.R
import com.example.multiway.databinding.FragmentGalleryBinding
import com.google.android.gms.location.*
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.*
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.*
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.ui.adapter.autocomplete.PlaceAutocompleteUiAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.*
import com.mapbox.geojson.Polygon
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.layers.generated.FillLayer
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion

import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions

import com.mapbox.maps.plugin.gestures.gestures

import com.google.gson.JsonParser
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonObject


import org.json.JSONObject


data class SearchResultItem(
    val name: String,
    val distanceKm: Double,
    val point: Point,
    val suggestion: PlaceAutocompleteSuggestion

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
        placeAutocomplete = PlaceAutocomplete.create()

        placeAutocompleteUiAdapter = PlaceAutocompleteUiAdapter(
            binding.searchResultsView,
            placeAutocomplete
        )




        placeAutocompleteUiAdapter = PlaceAutocompleteUiAdapter(
            binding.searchResultsView,
            placeAutocomplete
        )


        setupRecycler()
        setupSearchBox()
        binding.btnSearch.setOnClickListener {
            searchNearbyPlaces()
        }

        setupCategorySpinner()
        setupRadiusSelector()
        binding.directionsRecycler.layoutManager = LinearLayoutManager(requireContext())


        var isResultsVisible = true
        binding.btnToggleResults.setOnClickListener {
            isResultsVisible = !isResultsVisible
            binding.customResultsList.isVisible = isResultsVisible
            binding.btnToggleResults.text = if (isResultsVisible) "Hide Results" else "Show Results"
        }


        mapView.mapboxMap.loadStyle(Style.DARK) { style ->
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
            // Hide UI
            binding.customResultsList.isVisible = false
            binding.searchContainer.isVisible = false

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.queryEditText.windowToken, 0)

            // Zoom + show marker
            drawSearchResultMarker(item.point, item.name)

            // Draw route
            fetchAndDrawRoute(item.point)

            // Update place info UI
            val category = detectCategory(item.name)
            binding.placeName.text = item.name
            binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
            binding.btnGetDirections.isVisible = true
        }

        binding.customResultsList.layoutManager = LinearLayoutManager(requireContext())
        binding.customResultsList.adapter = searchResultsAdapter
    }



    private fun setupSearchBox() {
        binding.queryEditText.doAfterTextChanged { text ->
            val query = text.toString().trim()
            if (query.isNotEmpty()) {
                performSuggestionSearch(query)
            }
        }




        placeAutocompleteUiAdapter.addSearchListener(object : PlaceAutocompleteUiAdapter.SearchListener {
            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
            }

            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
                lifecycleScope.launch {
                    val result = placeAutocomplete.select(suggestion).value ?: return@launch
                    val coord = result.coordinate ?: return@launch

                    val name = result.name
                    val category = detectCategory(name)

                    currentRouteDestination = coord

                    // Clear previous markers
                    searchAnnotationManager.deleteAll()

                    // Add marker
                    val jsonData = JSONObject().apply {
                        put("name", name)
                        put("category", category)
                    }

                    binding.customResultsList.isVisible = false
                    binding.searchContainer.isVisible = false

                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.queryEditText.windowToken, 0)


                    searchAnnotationManager.create(
                        PointAnnotationOptions()
                            .withPoint(coord)
                            .withIconImage("tour-icon")
                            .withIconSize(0.3)
                            .withData(JsonParser.parseString(jsonData.toString()))
                    )

                    // Draw route and update UI
                    fetchAndDrawRoute(coord)
                    binding.placeName.text = name
                    binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
                    binding.btnGetDirections.isVisible = true
                }
            }



            override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
                // Optional: fill the search box with suggestion text if needed
                binding.queryEditText.setText(suggestion.name)
            }

            override fun onError(error: Exception) {
                Log.e("PlaceAutocomplete", "Search error: ${error.localizedMessage}")
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

                if (::searchAnnotationManager.isInitialized) {
                    searchNearbyPlaces()
                } else {
                    Log.w("CategorySpinner", "searchAnnotationManager not ready yet")
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }


    private fun searchNearbyPlaces() {
        lifecycleScope.launch {
            // ✅ Use userLocation or fallback to map center
            val location: Point = userLocation ?: mapView.mapboxMap.cameraState.center
            val query = binding.queryEditText.text.toString().trim()
            val results = mutableListOf<SearchResultItem>()


            // ✅ Build keyword list
            val keywords = if (query.isNotBlank()) {
                listOf(query)
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

            val seenCoords = mutableSetOf<String>()
            searchAnnotationManager.deleteAll()

            val proximityPoints = listOf(location) + predefinedProximityPoints

            for (keyword in keywords) {
                for (proximity in proximityPoints) {

                    // ✅ Main thread — required by Mapbox SearchEngine
                    val suggestions = withContext(Dispatchers.Main) {
                        placeAutocomplete.suggestions(query = keyword, proximity = proximity).value ?: emptyList()
                    }


                    withContext(Dispatchers.Main) {
                        for (suggestion in suggestions) {

                                val result = placeAutocomplete.select(suggestion).value ?: continue

                                val coord = result.coordinate ?: continue
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

                            val jsonData = JsonObject().apply {
                                addProperty("name", result.name)
                                addProperty("category", detectCategory(result.name))
                            }

                            searchAnnotationManager.create(
                                PointAnnotationOptions()
                                    .withPoint(coord)
                                    .withIconImage(icon)
                                    .withIconSize(0.3)
                                    .withData(jsonData)
                            )

                            results.add(
                                    SearchResultItem(
                                        result.name,
                                        distance,
                                        coord,
                                        suggestion
                                    )
                                )

                            }
                    }
                }
            }

            searchResultsAdapter.update(results)
            binding.customResultsList.isVisible = results.isNotEmpty()

            if (results.isEmpty()) {
                Toast.makeText(requireContext(), "No results found for '$query'", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("SearchResults", "Showing ${results.size} results.")
            }
        }
    }




    private fun fetchAndShowPOIs(center: Point) {
        lifecycleScope.launch {
            val keywords = listOf("restaurant", "pub", "bar", "hotel", "tourist", "shop", "park")

            val results = mutableListOf<SearchResultItem>()
            val seenCoords = mutableSetOf<String>()
            searchAnnotationManager.deleteAll() // ✅ use consistent manager

            for (keyword in keywords) {
                val suggestions = withContext(Dispatchers.IO) {
                    placeAutocomplete.suggestions(
                        query = keyword,
                        proximity = center
                    ).value ?: emptyList()
                }

                withContext(Dispatchers.Main) {
                    for (suggestion in suggestions) {
                        try {
                            val result = placeAutocomplete.select(suggestion).value ?: continue
                            val coord = result.coordinate ?: continue
                            val lat = coord.latitude()
                            val lon = coord.longitude()
                            val key = "$lat,$lon"

                            if (seenCoords.contains(key)) continue
                            val distance = calculateDistance(center, coord)
                            if (distance > selectedRadiusKm) continue // ✅ use selected radius

                            seenCoords.add(key)

                            val category = detectCategory(result.name)
                            val icon = when (category) {
                                "restaurant" -> "restaurant-icon"
                                "pub" -> "pub-icon"
                                "park" -> "park-icon"
                                "hotel" -> "hotel-icon"
                                "shop" -> "shop-icon"
                                "tourist_attraction" -> "tour-icon"
                                else -> "restaurant-icon"
                            }

                            val jsonData = JsonObject().apply {
                                addProperty("name", result.name)
                                addProperty("category", category)
                            }

                            searchAnnotationManager.create(
                                PointAnnotationOptions()
                                    .withPoint(coord)
                                    .withIconImage(icon)
                                    .withIconSize(0.3)
                                    .withData(jsonData)
                            )

                            results.add(SearchResultItem(result.name, distance, coord, suggestion))

                        } catch (e: Exception) {
                            Log.e("POIMarker", "Error resolving suggestion: ${e.message}")
                        }
                    }
                }
            }

            searchAnnotationManager.addClickListener { clickedAnnotation ->
                val json = clickedAnnotation.getData()?.asJsonObject
                val name = json?.get("name")?.asString ?: "Unknown"
                val category = json?.get("category")?.asString ?: "Unknown"
                currentRouteDestination = clickedAnnotation.point

                binding.placeName.text = name
                binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
                binding.btnGetDirections.isVisible = true

                true
            }


            searchResultsAdapter.update(results)
            binding.customResultsList.isVisible = results.isNotEmpty()

            if (results.isEmpty()) {
                Toast.makeText(requireContext(), "No places found in this area", Toast.LENGTH_SHORT).show()
            }
        }
    }



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




    private fun drawSearchResultMarker(point: Point, name: String) {
        val iconBitmap = bitmapFromDrawableRes(R.drawable.search)
        if (iconBitmap == null) {
            Log.e("MapIcon", "Icon bitmap was null")
            return
        }

        val jsonData = JsonObject().apply {
            addProperty("name", name)
        }

        searchAnnotationManager.deleteAll()
        searchAnnotationManager.create(
            PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(iconBitmap)
                .withIconSize(1.3)
                .withData(jsonData)
        )
    }



    private fun fetchAndDrawRoute(destination: Point) {
        val origin = userLocation ?: return  // 🔄 use a clearly named variable

        val client = MapboxDirections.builder()
            .origin(origin)  // 🔄 changed to originPoint
            .destination(destination)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(selectedTravelMode)
            .steps(true)
            .accessToken(getString(R.string.mapbox_access_token))
            .build()

        client.enqueueCall(object : retrofit2.Callback<DirectionsResponse> {
            override fun onResponse(
                call: retrofit2.Call<DirectionsResponse>,
                response: retrofit2.Response<DirectionsResponse>
            ) {
                val route = response.body()?.routes()?.firstOrNull() ?: return
                val lineString = LineString.fromPolyline(route.geometry()!!, 6)

                // Update UI with steps
                val steps = route.legs()
                    ?.flatMap { it.steps() ?: emptyList() }
                    ?.mapIndexed { index, step -> "${index + 1}. ${step.maneuver().instruction()}" }
                    ?: listOf("No steps available.")

                requireActivity().runOnUiThread {
                    binding.directionSummary.text = "Estimated time: ${(route.duration() / 60).roundToInt()} min"
                    binding.directionSummary.visibility = View.VISIBLE
                    binding.directionsRecycler.adapter = DirectionsAdapter(steps)
                    binding.directionsRecycler.isVisible = true
                }

                // Draw route on map
                mapView.mapboxMap.getStyle { style ->
                    val source = style.getSourceAs<GeoJsonSource>("route-source")
                    if (source == null) {
                        style.addSource(geoJsonSource("route-source") {
                            geometry(lineString)
                        })
                    } else {
                        source.geometry(lineString)
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

    private fun retrieveSearchResult(suggestion: PlaceAutocompleteSuggestion) {
        lifecycleScope.launch {
            try {
                val result = placeAutocomplete.select(suggestion).value ?: return@launch
                val coord = result.coordinate ?: return@launch
                val name = result.name
                val category = detectCategory(name)
                val point = Point.fromLngLat(coord.longitude(), coord.latitude())



                currentRouteDestination = coord

                binding.searchContainer.isVisible = false  // 👈 Hide the top search bar
                val bottomSheet = BottomSheetBehavior.from(binding.placeInfoBottomSheet)
                bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED

                // Clear existing markers
                searchAnnotationManager.deleteAll()


                val iconId = "search-icon"
                val bitmap = BitmapFactory.decodeResource(resources, R.drawable.search)
                mapView.mapboxMap.getStyle { style ->
                    try {
                        style.addImage(iconId, bitmap)
                    } catch (_: RuntimeException) {
                        // Ignore duplicate
                    }

                    // Add marker for searched POI
                    val jsonData = JSONObject().apply {
                        put("name", name)
                        put("category", category)
                    }

                    searchAnnotationManager.create(
                        PointAnnotationOptions()
                            .withPoint(coord)
                            .withIconImage(iconId)
                            .withIconSize(0.3)
                            .withData(JsonParser.parseString(jsonData.toString()))
                    )
                }

                // Update UI
                fetchAndDrawRoute(coord)
                binding.placeName.text = name
                binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
                binding.btnGetDirections.isVisible = true

                // 🧭 Zoom out to fit both user location and result
                val origin = userLocation ?: return@launch
                val bounds = listOf(origin, coord)

                val southwest = Point.fromLngLat(
                    bounds.minOf { it.longitude() },
                    bounds.minOf { it.latitude() }
                )
                val northeast = Point.fromLngLat(
                    bounds.maxOf { it.longitude() },
                    bounds.maxOf { it.latitude() }
                )

                val cameraBounds = CameraOptions.Builder()
                    .center(Point.fromLngLat(
                        (southwest.longitude() + northeast.longitude()) / 2,
                        (southwest.latitude() + northeast.latitude()) / 2
                    ))
                    .zoom(10.5) // or calculate appropriate zoom based on distance
                    .build()

                mapView.mapboxMap.setCamera(cameraBounds)

                // Optionally: hide results list
                binding.customResultsList.isVisible = false
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.queryEditText.windowToken, 0)

                binding.searchContainer.isVisible = false
                binding.customResultsList.isVisible = false

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error selecting: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performSuggestionSearch(query: String) {
        val location = userLocation ?: mapView.mapboxMap.cameraState.center

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val suggestions = placeAutocomplete.suggestions(query = query, proximity = location).value ?: return@launch

                val items = suggestions.mapNotNull { suggestion ->
                    val result = placeAutocomplete.select(suggestion).value ?: return@mapNotNull null
                    val coord = result.coordinate ?: return@mapNotNull null
                    val distance = calculateDistance(location, coord)
                    SearchResultItem(result.name, distance, coord, suggestion)
                }

                searchResultsAdapter.update(items)
                binding.customResultsList.isVisible = items.isNotEmpty()

            } catch (e: Exception) {
            }
        }
    }

    private fun bitmapFromDrawableRes(@DrawableRes resourceId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(requireContext(), resourceId) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}