//Joey Teahan - 20520316
//Gallery Fragment - this file displays custom POIs in a

//Links this file with the rest of the project
package com.example.multiway.ui.gallery

import android.app.AlertDialog //Pop up alerts
import android.content.Context
import android.content.pm.PackageManager //Permission
import android.graphics.Bitmap //Bitmap images
import android.graphics.BitmapFactory //More bitmap images
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log //Used to log data back to the console
import android.view.* //Menus and views
import android.view.inputmethod.InputMethodManager //Used for the keyboard
import android.widget.* //Used for the text views
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat //Permission
import androidx.core.content.ContextCompat// Also for permissions
import androidx.core.view.isVisible //Lets me hide items from my XML
import androidx.core.widget.doAfterTextChanged //Lets me edit items
import androidx.fragment.app.Fragment //Fragment class
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager //Puts recycler views in a list
import com.example.multiway.R //Lets me use resources
import com.example.multiway.databinding.FragmentGalleryBinding //Lets me use the binding class
import com.example.multiway.ui.history.HistoryItem
import com.google.android.gms.location.* //Using location is vital for this file
//Start of my Mapbox imports
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
import com.mapbox.geojson.Polygon //Lets me create a circle
import com.mapbox.geojson.Point //Lets me use point classes
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion //Imports the autocomplete suggetions
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.fillLayer//layer
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions //Annotation options
import com.mapbox.maps.plugin.gestures.gestures //Lets me long press on the map
//End of my Mapbox imports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch //Launch coroutines
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.* //Lets me use math

import com.google.gson.JsonParser //JSON parser
import com.google.android.material.bottomsheet.BottomSheetBehavior//Bottomsheet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.JsonObject
import org.json.JSONObject //JSON object parsing

//The search results characteristics
data class SearchResultItem(
    val name: String,
    val distanceKm: Double,
    val point: Point,
    val suggestion: PlaceAutocompleteSuggestion
)


//Gallery Fragment class
class GalleryFragment : Fragment() {

    //My variables
    private var _binding: FragmentGalleryBinding? = null //Binding class
    private val binding get() = _binding!! //Binding class getter
    private lateinit var mapView: com.mapbox.maps.MapView //Mapbox map
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placeAutocomplete: PlaceAutocomplete //Sets up the autocomplete
    private lateinit var placeAutocompleteUiAdapter: PlaceAutocompleteUiAdapter
    private lateinit var searchAnnotationManager: PointAnnotationManager //Annotation manager
    private lateinit var searchResultsAdapter: SearchResultsAdapter //Adapter for the search results
    private var selectedRadiusKm = 5.0 //Sets default seclected radius to 5km
    private var userLocation: Point? = null //The users location
    private val radiusKm = 5.0 //Sets default radius to 5km
    private var selectedCategory = "restaurant" //Sets default category to restaurant
    private lateinit var pointAnnotationManager: PointAnnotationManager //Annotation manager
    private var currentRouteDestination: Point? = null //The current route destination
    private var selectedTravelMode: String = DirectionsCriteria.PROFILE_WALKING //Sets default travel mode to walking


    //The onCreateView function that is called when the fragment is created
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    //The onViewCreated function that is called when the fragment is created
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //My mapbox binding class
        mapView = binding.mapView
        //Annotation manager
        pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
        //Location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        placeAutocomplete = PlaceAutocomplete.create()
        placeAutocompleteUiAdapter = PlaceAutocompleteUiAdapter(
            binding.searchResultsView,
            placeAutocomplete
        )
        //Place autocomplete
        placeAutocompleteUiAdapter = PlaceAutocompleteUiAdapter(
            binding.searchResultsView,
            placeAutocomplete
        )
        //Sets up the recycler view
        setupRecycler()
        //sets up the search box
        setupSearchBox()
        //The search button
        binding.btnSearch.setOnClickListener {
            searchNearbyPlaces()
        }

        //Sets up the category spinner
        setupCategorySpinner()
        //The radius selector
        setupRadiusSelector()
        //The directions
        binding.directionsRecycler.layoutManager = LinearLayoutManager(requireContext())

        //Sets up the button that toggles if the user can see the results
        var isResultsVisible = true
        binding.btnToggleResults.setOnClickListener {
            isResultsVisible = !isResultsVisible
            binding.customResultsList.isVisible = isResultsVisible
            binding.btnToggleResults.text = if (isResultsVisible) "Hide Results" else "Show Results"
        }

        //Sets up the map
        mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            //Loads the icons on the map
            loadIconsIntoStyle(style)
            searchAnnotationManager = mapView.annotations.createPointAnnotationManager()
            //Gets the user's location
            enableUserLocation()
            //Zooms to the user's location
            zoomToUserLocation {
                drawRadiusCircle()
                searchNearbyPlaces()
            }
        }
        //Sets up the button that shows the walking directions
        binding.btnWalking.setOnClickListener {
            selectedTravelMode = DirectionsCriteria.PROFILE_WALKING
            currentRouteDestination?.let { fetchAndDrawRoute(it) }
        }
        //Sets up the button that shows the driving directions
        binding.btnDriving.setOnClickListener {
            selectedTravelMode = DirectionsCriteria.PROFILE_DRIVING
            currentRouteDestination?.let { fetchAndDrawRoute(it) }
        }

        //Sets up the behaviour of the bottom sheet
        val bottomSheet = binding.placeInfoBottomSheet
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = 300
        behavior.isHideable = false
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        //Sets up the button that toggles if the user can see the bottom sheet
        binding.btnToggleBottomSheetVisibility.setOnClickListener {
            behavior.state = if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }

        //Sets up the point annotation manager
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

        //Makes sure that the menu is visable on default
        var isMenuVisible = true
        //Sets up the button that toggles the visibility of the menu
        binding.toggleMenuButton.setOnClickListener {
            isMenuVisible = !isMenuVisible
            binding.searchContainer.isVisible = isMenuVisible
            binding.toggleMenuButton.setImageResource(
                //Different icons for closing and opening the menu
                if (isMenuVisible) R.drawable.menuopen else R.drawable.menuclose
            )
        }

        //The button that shows the directions
        binding.btnGetDirections.setOnClickListener {
            val destination = currentRouteDestination ?: return@setOnClickListener
            fetchAndDrawRoute(destination)
        }

        //Lets the user long press on the map to create a circle
        mapView.gestures.addOnMapLongClickListener { geoPoint ->
            draw5kCircle(geoPoint)
            fetchAndShowPOIs(geoPoint)
            true

            pointAnnotationManager.addClickListener { clickedAnnotation ->
                val data = clickedAnnotation.getData()
                val json = data?.takeIf { it.isJsonObject }?.asJsonObject

                val name = json?.get("name")?.asString ?: "Unknown Place"
                val category = json?.get("category")?.asString ?: "Unknown Category"
                val destination = clickedAnnotation.point

                currentRouteDestination = destination

                binding.placeName.text = name
                binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
                binding.btnGetDirections.isVisible = true

                saveToHistory(name, "Viewed a $category place")

                true
            }



        }
    }

    //Recycler view class
    private fun setupRecycler() {
        searchResultsAdapter = SearchResultsAdapter { item ->
            // Hides the menu
            binding.customResultsList.isVisible = false
            binding.searchContainer.isVisible = false

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.queryEditText.windowToken, 0)

            //Draws the marker for the result
            drawSearchResultMarker(item.point, item.name)

            // Draws the route
            fetchAndDrawRoute(item.point)

            // Shows the details on the map
            val category = detectCategory(item.name)
            binding.placeName.text = item.name
            binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
            binding.btnGetDirections.isVisible = true

            saveToHistory(item.name, "Selected from search within ${String.format("%.1f", item.distanceKm)} km")

        }
        //Sets up the results list
        binding.customResultsList.layoutManager = LinearLayoutManager(requireContext())
        binding.customResultsList.adapter = searchResultsAdapter
    }

    //Lets the user to search for specific items
    private fun setupSearchBox() {
        binding.queryEditText.doAfterTextChanged { text ->
            val query = text.toString().trim()
            if (query.isNotEmpty()) {
                performSuggestionSearch(query)
            }
        }


        placeAutocompleteUiAdapter.addSearchListener(object : PlaceAutocompleteUiAdapter.SearchListener {
            //What is shown
            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
            }

            //When the user selects an item
            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
                lifecycleScope.launch {
                    //Details of the selected item
                    val result = placeAutocomplete.select(suggestion).value ?: return@launch
                    val coord = result.coordinate ?: return@launch
                    val name = result.name
                    val category = detectCategory(name)

                    currentRouteDestination = coord

                    // Deletes any other markers
                    searchAnnotationManager.deleteAll()

                    // Creates a new marker
                    val jsonData = JSONObject().apply {
                        put("name", name)
                        put("category", category)
                    }

                    binding.customResultsList.isVisible = false
                    binding.searchContainer.isVisible = false
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.queryEditText.windowToken, 0)

                    //Creates the icon
                    searchAnnotationManager.create(
                        PointAnnotationOptions()
                            .withPoint(coord)
                            .withIconImage("tour-icon")
                            .withIconSize(0.3)
                            .withData(JsonParser.parseString(jsonData.toString()))
                    )

                    // Updates the route
                    fetchAndDrawRoute(coord)
                    binding.placeName.text = name
                    binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
                    binding.btnGetDirections.isVisible = true
                }
            }

            override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
                // Updates the query text
                binding.queryEditText.setText(suggestion.name)
            }

            //Sends a message back to teh console if there are any errors
            override fun onError(error: Exception) {
                Log.e("PlaceAutocomplete", "Search error: ${error.localizedMessage}")
            }
        })

    }

    //Sets up the category spinner
    private fun setupCategorySpinner() {
        //The categories that will appear in the spinner
        val categories = listOf("Restaurants", "Pubs", "Parks", "Hotels", "Shops", "Tourist Attractions")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        binding.categorySpinner.adapter = adapter

        //Setting what happens when an item is selected
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

    //Function that shows what POIs are nearby
    private fun searchNearbyPlaces() {
        lifecycleScope.launch {
            //Using the user location as a reference
            val location: Point = userLocation ?: mapView.mapboxMap.cameraState.center
            val query = binding.queryEditText.text.toString().trim()
            val results = mutableListOf<SearchResultItem>()


            // I set keywords to let the categories show up
            val keywords = if (query.isNotBlank()) {
                listOf(query)
            } else {
                when (selectedCategory) {
                    //These POIs will trigger a category
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
            // Deletes any other markers
            searchAnnotationManager.deleteAll()
            //Sets up the proximity points
            val proximityPoints = listOf(location) + predefinedProximityPoints
            for (keyword in keywords) {
                for (proximity in proximityPoints) {
                    val suggestions = withContext(Dispatchers.Main) {
                        placeAutocomplete.suggestions(query = keyword, proximity = proximity).value ?: emptyList()
                    }

                    withContext(Dispatchers.Main) {
                        //Sets up the suggestions
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
                                //Sets up the different category icons
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

                            //Creates the marker
                            searchAnnotationManager.create(
                                PointAnnotationOptions()
                                    .withPoint(coord)
                                    .withIconImage(icon)
                                    .withIconSize(0.3)
                                    .withData(jsonData)
                            )

                            //Adds the results to the list
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
            //Updates the search results
            searchResultsAdapter.update(results)
            binding.customResultsList.isVisible = results.isNotEmpty()

            if (results.isEmpty()) {
                Toast.makeText(requireContext(), "No results found for '$query'", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("SearchResults", "Showing ${results.size} results.")
            }
        }
    }

    //Function that displays the POIs
    private fun fetchAndShowPOIs(center: Point) {
        lifecycleScope.launch {
            //The categories
            val keywords = listOf("restaurant", "pub", "bar", "hotel", "tourist", "shop", "park")
            //The search results
            val results = mutableListOf<SearchResultItem>()
            val seenCoords = mutableSetOf<String>()
            searchAnnotationManager.deleteAll()

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
                            //Sets up the different category icons
                            val icon = when (category) {
                                "restaurant" -> "restaurant-icon"
                                "pub" -> "pub-icon"
                                "park" -> "park-icon"
                                "hotel" -> "hotel-icon"
                                "shop" -> "shop-icon"
                                "tourist_attraction" -> "tour-icon"
                                else -> "restaurant-icon"
                            }
                            //The data for the marker
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

            //The search annotation
            searchAnnotationManager.addClickListener { clickedAnnotation ->
                val json = clickedAnnotation.getData()?.asJsonObject
                val name = json?.get("name")?.asString ?: "Unknown"
                val category = json?.get("category")?.asString ?: "Unknown"
                currentRouteDestination = clickedAnnotation.point
                binding.placeName.text = name
                binding.placeAddress.text = "Category: ${category.replaceFirstChar { it.uppercaseChar() }}"
                binding.btnGetDirections.isVisible = true
                saveToHistory(name, "Selected from long-press POIs in category: ${category.replaceFirstChar { it.uppercaseChar() }}")
                true
            }
            //Updates the search results
            searchResultsAdapter.update(results)
            binding.customResultsList.isVisible = results.isNotEmpty()

            //If there are no results available it will show a message
            if (results.isEmpty()) {
                Toast.makeText(requireContext(), "No places found in this area", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //Proximity points so POIs show up
    private val predefinedProximityPoints = listOf(
        Point.fromLngLat(-6.18497, 53.3498),
        Point.fromLngLat(-6.22264, 53.38874),
        Point.fromLngLat(-6.29796, 53.38874),
        Point.fromLngLat(-6.33563, 53.3498),
        Point.fromLngLat(-6.29796, 53.31086),
        Point.fromLngLat(-6.22264, 53.31086)
    )

    //I needed this function to calculate the distance between the user and the POIs
    private fun calculateDistance(p1: Point, p2: Point): Double {
        // Earth's radius in kilometers
        val R = 6371.0
        val lat1 = Math.toRadians(p1.latitude())
        val lat2 = Math.toRadians(p2.latitude())
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(p2.longitude() - p1.longitude())
        val a = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    //Gives the user the ability to choose the radius of the circle
    private fun setupRadiusSelector() {
        //The options of the radius
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

    //Function that draws the default circle
    private fun drawRadiusCircle() {
        val center = userLocation ?: return
        //If the whole country section is selected, i didnt want a circle to be drawn
        if (selectedRadiusKm <= 0) return
        val radiusMeters = selectedRadiusKm * 1000
        val steps = 64
        val earthRadius = 6371000.0
        //Circle points
        val circlePoints = mutableListOf<Point>()
        if (selectedRadiusKm <= 0) return
        for (i in 0..steps) {
            val theta = 2 * Math.PI * i / steps
            val dx = radiusMeters * cos(theta)
            val dy = radiusMeters * sin(theta)
            val lat = center.latitude() + (dy / earthRadius) * (180 / Math.PI)
            val lon = center.longitude() + (dx / (earthRadius * cos(Math.toRadians(center.latitude())))) * (180 / Math.PI)
            circlePoints.add(Point.fromLngLat(lon, lat))
        }

        //Styles the circle
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

    //Function that draws the specific 5k circle
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

        //Fills the circle
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

    //Gets the user location
    private fun enableUserLocation() {
        mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
        mapView.location.addOnIndicatorPositionChangedListener {
            userLocation = Point.fromLngLat(it.longitude(), it.latitude())
        }
    }

    //This function is used to zoom the user to their location when they open the app
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

    //I wanted to represent the different categories with different icons to give the app a unique look
    private fun loadIconsIntoStyle(style: Style) {
        val iconMap = mapOf(
            //These icons are all saved in my drawable section
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

    //Function that detects the category of the POI
    private fun detectCategory(name: String): String {
        val lowerName = name.lowercase(Locale.getDefault())

        return when {
            // Restaurant Category
            listOf("restaurant", "grill", "diner", "eatery", "café", "bistro", "food", "pizza", "steakhouse", "bbq", "sushi", "noodle", "kitchen")
                .any { it in lowerName } -> "restaurant"

            // Pubs
            listOf("pub", "bar", "tavern", "brewery", "taproom", "ale", "lounge", "saloon", "club")
                .any { it in lowerName } -> "pub"

            // Parks
            listOf("park", "garden", "green", "nature", "forest", "woods", "trail", "reserve")
                .any { it in lowerName } -> "park"

            // Hotels
            listOf("hotel", "inn", "resort", "hostel", "motel", "bnb", "guesthouse", "lodging", "accommodation")
                .any { it in lowerName } -> "hotel"

            // Shops
            listOf("shop", "store", "supermarket", "market", "convenience", "grocery", "boutique", "retail", "mall", "minimart", "chemist", "corner shop")
                .any { it in lowerName } -> "shop"

            // Tourism
            listOf("tourist", "museum", "landmark", "attraction", "gallery", "castle", "monument", "church", "cathedral", "historic", "heritage", "ruins", "site")
                .any { it in lowerName } -> "tourist_attraction"

            else -> "default"
        }
    }

    //Icon that is used to represent the searched item
    private fun drawSearchResultMarker(point: Point, name: String) {
        val iconBitmap = bitmap(R.drawable.search)
        if (iconBitmap == null) {
            Log.e("MapIcon", "Icon bitmap was null")
            return
        }

        val jsonData = JsonObject().apply {
            addProperty("name", name)
        }
            //Creates the marker
        searchAnnotationManager.deleteAll()
        searchAnnotationManager.create(
            PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(iconBitmap)
                .withIconSize(1.3)
                .withData(jsonData)
        )
    }

    //Used to draw the route to the POI
    private fun fetchAndDrawRoute(destination: Point) {
        val origin = userLocation ?: return
        //The Mapbox client
        val client = MapboxDirections.builder()
            .origin(origin)
            .destination(destination)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(selectedTravelMode)
            .steps(true)
            //My mapbox access token
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
                                //Draws a purple route
                                lineColor("#d900ff")
                                //The width of the line
                                lineWidth(5.0)
                            }
                        )
                    }
                }
            }

            //Logs a message back tp the console if there are any errors
            override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
                Log.e("RouteDraw", "Directions API call failed: ${t.localizedMessage}")
            }
        })
    }

    //Function that lets me search for places
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

    // Helper function to get a Bitmap from a drawable resource
    private fun bitmap(@DrawableRes resourceId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(requireContext(), resourceId) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        //Sets up the canvas
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }


    private fun saveToHistory(title: String, subtitle: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val historyRef = FirebaseDatabase.getInstance().getReference("History").child(userId)

        val item = HistoryItem(title = title, subtitle = subtitle)
        historyRef.push().setValue(item)
    }



    //Ends the fragment
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}