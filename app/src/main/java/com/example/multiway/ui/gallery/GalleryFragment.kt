package com.example.multiway.ui.gallery

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.multiway.databinding.FragmentGalleryBinding
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import com.mapbox.search.ui.adapter.autocomplete.PlaceAutocompleteUiAdapter
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.SearchResultsView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.maps.plugin.annotation.annotations
import com.example.multiway.R
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.android.gms.location.LocationRequest
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.from
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.toCameraOptions
import kotlinx.coroutines.withContext


class GalleryFragment : Fragment() {


    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView
    private lateinit var placeAutocomplete: PlaceAutocomplete
    private lateinit var searchResultsView: SearchResultsView
    private lateinit var queryEditText: EditText
    private lateinit var placeAutocompleteUiAdapter: PlaceAutocompleteUiAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var annotationManager: PointAnnotationManager
    private var userLocation: Point? = null
    private lateinit var customResultsList: RecyclerView
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private lateinit var polylineAnnotationManager: PolylineAnnotationManager
    private lateinit var locationCallback: LocationCallback
    private lateinit var drivingInfo: TextView
    private lateinit var walkingInfo: TextView
    private lateinit var navigationStepsList: LinearLayout
    private lateinit var navigationStepsLabel: TextView
    private lateinit var stepsScrollView: ScrollView
    private var lastRouteUpdateTime = 0L
    private var selectedRouteProfile: String = DirectionsCriteria.PROFILE_DRIVING
    private var lastSelectedDestination: Point? = null
    private lateinit var userAnnotationManager: PointAnnotationManager
    private lateinit var searchAnnotationManager: PointAnnotationManager


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Initialize Views
        mapView = binding.mapView
        queryEditText = binding.queryEditText
        searchResultsView = binding.searchResultsView
        val categorySpinner = binding.categorySpinner
        val toggleButton = binding.btnToggleSearch // 🔥 Toggle button for search visibility
        val partyTypeSpinner = binding.partyTypeSpinner

        drivingInfo = binding.drivingInfo
        walkingInfo = binding.walkingInfo
        navigationStepsList = binding.navigationStepsList
        navigationStepsLabel = binding.navigationStepsLabel
        stepsScrollView = binding.stepsScrollView


        binding.placeInfoContainer.visibility = View.VISIBLE
        binding.navigationInfoContainer.visibility = View.VISIBLE
        binding.navigationStepsLabel.visibility = View.GONE
        binding.stepsScrollView.visibility = View.GONE

        binding.radioDriving.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedRouteProfile = DirectionsCriteria.PROFILE_DRIVING
            }
        }

        binding.radioWalking.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedRouteProfile = DirectionsCriteria.PROFILE_WALKING
            }
        }

        binding.btnUpdateDirections.setOnClickListener {
            if (lastSelectedDestination != null) {
                fetchRouteDetailsAndStartUpdates(lastSelectedDestination!!)
            }
        }


        val bottomSheet = view.findViewById<View>(R.id.placeInfoBottomSheet)
        val bottomSheetBehavior = from(bottomSheet)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        binding.btnCloseInfo.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }




        placeAutocomplete = PlaceAutocomplete.create(
            locationProvider = null // or you can provide a LocationProvider if needed
        )

        polylineAnnotationManager = mapView.annotations.createPolylineAnnotationManager()

        zoomToUserLocation {
            // 🔥 Only perform search after location is ready
            performSearch(queryEditText.text.toString())
        }


        mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            // ✅ Load and Resize the Custom Marker
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.marker)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, false)

            if (false) {
                Log.e("MarkerError", "Failed to load resized marker image!")
            } else {
                style.addImage("custom-marker", scaledBitmap) // ✅ Add marker once
            }
            val toggleDirectionsButton = binding.btnToggleDirections
            toggleDirectionsButton.setOnClickListener {
                val isVisible = binding.stepsScrollView.visibility == View.VISIBLE

                binding.stepsScrollView.visibility = if (isVisible) View.GONE else View.VISIBLE
                binding.navigationStepsLabel.visibility = if (isVisible) View.GONE else View.VISIBLE

                toggleDirectionsButton.text =
                    if (isVisible) "Show Directions" else "Hide Directions"
            }


            // ✅ Enable User Location & Zoom to User
            enableUserLocation()
            zoomToUserLocation()

            val partyAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.party_types,
                android.R.layout.simple_spinner_dropdown_item
            )
            partyTypeSpinner.adapter = partyAdapter

            searchResultsAdapter = SearchResultsAdapter { item ->
            }

            partyTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedParty = partyTypes[position]
                    if (selectedParty != "All") {
                        filterPOIsByPartyType(selectedParty)
                        binding.btnClearResults.visibility = View.VISIBLE  // ✅ Show "X" button
                    } else {
                        clearSearchResults() // ✅ Hide search results when "All" is selected
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            customResultsList = binding.customResultsList
            customResultsList.layoutManager = LinearLayoutManager(requireContext())


            binding.btnToggleSearch.setOnClickListener {
                val isVisible = binding.queryEditText.visibility == View.VISIBLE

                // Toggle visibility for Search Bar, Category Spinner, and Party Type Spinner
                binding.queryEditText.visibility = if (isVisible) View.GONE else View.VISIBLE
                binding.categorySpinner.visibility = if (isVisible) View.GONE else View.VISIBLE
                binding.partyTypeSpinner.visibility = if (isVisible) View.GONE else View.VISIBLE
            }

            val bottomSheet = view.findViewById<LinearLayout>(R.id.placeInfoBottomSheet)
            val behavior = from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        }

        searchResultsAdapter = SearchResultsAdapter { item ->
            // Convert SearchResultItem back to name if needed
            // Or better: update SearchResultItem to also store original suggestion
            Log.d("Clicked", "Place: ${item.name}, Distance: ${item.distanceKm}")
            // optionally show place info, zoom to location, etc.
        }

        binding.customResultsList.adapter = searchResultsAdapter
        binding.customResultsList.layoutManager = LinearLayoutManager(requireContext())
        binding.customResultsList.isVisible = true



        binding.btnClearResults.setOnClickListener {
            clearSearchResults()
        }

        userAnnotationManager = mapView.annotations.createPointAnnotationManager()
        searchAnnotationManager = mapView.annotations.createPointAnnotationManager()



        annotationManager = mapView.annotations.createPointAnnotationManager()

        // ✅ Zoom button
        binding.btnZoomToLocation.setOnClickListener {
            zoomToUserLocation()
        }


        // ✅ Toggle Search & Category Spinner Visibility
        toggleButton.setOnClickListener {
            val isVisible = queryEditText.visibility == View.VISIBLE
            queryEditText.visibility = if (isVisible) View.GONE else View.VISIBLE
            categorySpinner.visibility = if (isVisible) View.GONE else View.VISIBLE
        }


        // ✅ Populate Category Spinner
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
        categorySpinner.adapter = adapter

        // ✅ Handle Category Selection
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedCategory = categories[position]
                if (selectedCategory != "All") {
                    performCategorySearch(selectedCategory)
                    binding.btnClearResults.visibility = View.VISIBLE
                } else {
                    clearSearchResults()
                }

            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        searchResultsView = binding.searchResultsView // ✅ Correct way


        // ✅ Initialize UI Adapter for SearchResultsView
        placeAutocompleteUiAdapter = PlaceAutocompleteUiAdapter(
            view = searchResultsView,
            placeAutocomplete = placeAutocomplete
        )

        placeAutocompleteUiAdapter.addSearchListener(object :
            PlaceAutocompleteUiAdapter.SearchListener {

            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
                if (userLocation == null) {
                    Log.w("Search", "User location not available yet.")
                    return
                }

                val validSuggestions = suggestions.filter {
                    it.coordinate != null && it.name.isNotBlank()
                }

                val sorted = validSuggestions.sortedBy { suggestion ->
                    val coord = suggestion.coordinate!!
                    calculateDistance(userLocation!!, coord)
                }

                val displayList = sorted.map { suggestion ->
                    SearchResultItem(
                        name = suggestion.name,
                        distanceKm = calculateDistance(userLocation!!, suggestion.coordinate!!)
                    )
                }

                searchResultsAdapter.update(displayList)
                binding.customResultsList.isVisible = displayList.isNotEmpty()
            }

            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
                CoroutineScope(Dispatchers.Main).launch {
                    handleSearchSelection(suggestion)
                    clearSearchResults()
                }
            }

            override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
                queryEditText.setText(suggestion.name)
            }

            override fun onError(e: Exception) {
                Log.e("SearchError", "Search failed: ${e.message}")
                Toast.makeText(requireContext(), "Search error: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        })

        searchResultsView.initialize(
            SearchResultsView.Configuration(
                commonConfiguration = CommonSearchViewConfiguration()
            )
        )


        // ✅ Handle Search
        queryEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
        })

        placeAutocompleteUiAdapter.addSearchListener(object :
            PlaceAutocompleteUiAdapter.SearchListener {
            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
                Log.d("SearchDebug", "Suggestions count: ${suggestions.size}")
                searchResultsView.isVisible = suggestions.isNotEmpty()

                // 🔥 Keep "X" button visible while search results are showing
                binding.btnClearResults.visibility =
                    if (searchResultsView.isVisible) View.VISIBLE else View.GONE
            }

            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {

                handleSearchSelection(suggestion)
                clearSearchResults() // 🔥 Automatically clear results when a place is selected
            }

            override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
                queryEditText.setText(suggestion.name)
            }

            override fun onError(e: Exception) {
                Log.e("SearchError", "Search failed: ${e.message}")
                Toast.makeText(requireContext(), "Search error: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        })

        binding.btnClearResults.setOnClickListener {
            clearSearchResults()
        }


// ✅ Show "X" Button when Typing
        binding.queryEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearResults.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

    }

    private fun filterPOIsByPartyType(partyType: String) {
        val poiCategoryMap = mapOf(
            "Travellers" to listOf(
                "bus_stops",
                "train_station",
                "monuments",
                "landmarks",
                "hotels"
            ),
            "Couples" to listOf("romantic_restaurant", "scenic_walk", "beach", "cafe"),
            "Friends" to listOf("pub", "nightclub", "concert"),
            "Families" to listOf("park", "museum", "zoo", "cinema"),
            "Solo" to listOf(
                "restaurant",
                "park",
                "museum",
                "pub",
                "zoo",
                "beach"
            ) // Default: Show all
        )

        val selectedCategories = poiCategoryMap[partyType] ?: listOf()

        if (selectedCategories.isNotEmpty()) {
            // 🔥 Perform search only for the selected party type categories
            val searchQuery = selectedCategories.joinToString(" OR ") // E.g., "pub OR nightclub"
            performSearch(query = searchQuery, partyType = partyType)
        } else {
            performSearch("") // Show all if "All" is selected
        }
    }


    private fun performSearch(query: String, category: String? = null, partyType: String? = null) {
        val filters = mutableListOf<String>()

        if (query.isNotBlank()) filters.add(query)
        if (!category.isNullOrBlank() && category != "All") filters.add(category)
        if (!partyType.isNullOrBlank() && partyType != "All") filters.add(partyType)

        val finalQuery = filters.joinToString(" AND ")

        placeAutocompleteUiAdapter.addSearchListener(object :
            PlaceAutocompleteUiAdapter.SearchListener {
            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
                if (userLocation == null) {
                    Log.w("Suggestions", "User location not available yet.")
                    return
                }

                val sorted = suggestions
                    .filter { it.coordinate != null }
                    .sortedBy { suggestion ->
                        calculateDistance(userLocation!!, suggestion.coordinate!!)
                    }

                val displayList = sorted.map { suggestion ->
                    SearchResultItem(
                        name = suggestion.name,
                        distanceKm = calculateDistance(userLocation!!, suggestion.coordinate!!)
                    )
                }

                searchResultsAdapter.update(displayList)
                binding.customResultsList.isVisible = displayList.isNotEmpty()
            }

            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
                handleSearchSelection(suggestion)
                clearSearchResults()
            }

            override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
                queryEditText.setText(suggestion.name)
            }

            override fun onError(e: Exception) {
                Log.e("SearchError", "Search failed: ${e.message}")
                Toast.makeText(requireContext(), "Search error: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        })
        CoroutineScope(Dispatchers.Main).launch {
            placeAutocompleteUiAdapter.search(finalQuery)
        }

    }


    private fun calculateDistance(point1: Point, point2: Point): Double {
        val lat1 = point1.latitude()
        val lon1 = point1.longitude()
        val lat2 = point2.latitude()
        val lon2 = point2.longitude()

        val earthRadius = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c // Distance in km
    }


    private val categories =
        listOf("All", "Restaurants", "Pubs", "Parks", "Tourist Attractions", "Hotels", "Shops")
    private val partyTypes = listOf("Solo", "Couples", "Friends", "Families", "Travellers")


    private fun enableUserLocation() {
        val locationPlugin = mapView.location

        if (ActivityCompat.checkSelfPermission(
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

        locationPlugin.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
    }


    private fun zoomToUserLocation(onLocated: (() -> Unit)? = null) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (ActivityCompat.checkSelfPermission(
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

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLocation = Point.fromLngLat(location.longitude, location.latitude)
                moveCameraToLocation(userLocation!!)
                onLocated?.invoke() // Run this after location is set
            } else {
                Log.e("Location", "Last location is null!")
            }
        }
    }


    private fun performCategorySearch(categoryLabel: String) {
        val categoryMap = mapOf(
            "Pubs" to "pub",
            "Restaurants" to "restaurant",
            "Parks" to "park",
            "Hotels" to "hotel",
            "Shops" to "shopping",
            "Tourist Attractions" to "tourist_attraction OR landmark OR monument"
        )

        val mappedQuery = categoryMap[categoryLabel] ?: categoryLabel
        performSearch(query = mappedQuery, category = mappedQuery)



        CoroutineScope(Dispatchers.Main).launch {
            placeAutocompleteUiAdapter.search(mappedQuery)

            // Listen for results only once
            placeAutocompleteUiAdapter.addSearchListener(object :
                PlaceAutocompleteUiAdapter.SearchListener {

                override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
                    if (userLocation == null) {
                        Log.w("Search", "User location is null, skipping distance sort")
                        Toast.makeText(
                            requireContext(),
                            "Getting location... please wait",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    val validSuggestions =
                        suggestions.filter { it.coordinate != null && it.name.isNotBlank() }

                    // ✅ Sort by distance from user location
                    val sortedSuggestions = validSuggestions.sortedBy { suggestion ->
                        suggestion.coordinate?.let { coord ->
                            userLocation?.let { userLoc -> calculateDistance(userLoc, coord) }
                        } ?: Double.MAX_VALUE
                    }


                    // ✅ Map to display items with accurate distance
                    val displayList = sortedSuggestions.map { suggestion ->
                        SearchResultItem(
                            name = suggestion.name,
                            distanceKm = suggestion.coordinate?.let { coord ->
                                userLocation?.let { userLoc -> calculateDistance(userLoc, coord) }
                            } ?: Double.MAX_VALUE
                        )
                    }


                    // ✅ Update UI
                    searchResultsAdapter.update(displayList)
                    binding.customResultsList.isVisible = displayList.isNotEmpty()
                }

                override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
                    CoroutineScope(Dispatchers.Main).launch {
                        handleSearchSelection(suggestion)
                        clearSearchResults()
                    }
                }

                override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
                    queryEditText.setText(suggestion.name)
                }

                override fun onError(e: Exception) {
                    Log.e("SearchError", "Search failed: ${e.message}")
                    Toast.makeText(
                        requireContext(),
                        "Search error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    private fun clearSearchResults() {
        searchResultsAdapter.update(emptyList()) // clears RecyclerView
        binding.customResultsList.isVisible = false
        binding.queryEditText.setText("")
        binding.queryEditText.visibility = View.GONE
        binding.categorySpinner.visibility = View.GONE
        binding.partyTypeSpinner.visibility = View.GONE
        binding.btnClearResults.visibility = View.GONE
    }


    private fun handleSearchSelection(suggestion: PlaceAutocompleteSuggestion) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = placeAutocomplete.select(suggestion)

            withContext(Dispatchers.Main) {
                result.onValue { searchResult ->
                    val location = searchResult.coordinate
                    val placeName = searchResult.name

                    moveCameraToLocation(location)
                    showPlaceInfo(suggestion)
                    fetchRouteDetailsAndStartUpdates(location)
                    addSearchMarker(location, placeName)

                    binding.btnCloseInfo.setOnClickListener {
                        binding.placeInfoContainer.visibility = View.GONE
                    }

                    lastSelectedDestination = location
                    fetchRouteDetailsAndStartUpdates(location)


                    binding.searchResultsView.visibility = View.GONE
                }.onError {
                    Toast.makeText(
                        requireContext(),
                        "Selection failed: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun addSearchMarker(location: Point, title: String?) {
        // ✅ Clear previous markers before adding a new one
        annotationManager.deleteAll()

        val markerOptions = PointAnnotationOptions()
            .withPoint(location)
            .withTextField(title ?: "Location")
            .withIconImage("custom-marker") // 🔥 Use the custom marker
            .withIconSize(1.0) // ✅ Adjust marker size (1.0 = full size, 0.5 = half size)

        annotationManager.create(markerOptions)

        // ✅ Move camera to marker location after placing it
        moveCameraToLocation(location)
        zoomToSuggestion(location)

    }


    private fun showPlaceInfo(suggestion: PlaceAutocompleteSuggestion) {
        val name = suggestion.name
        val address = suggestion.formattedAddress ?: "No address available"
        val destination = suggestion.coordinate

        binding.placeInfoContainer.visibility = View.VISIBLE
        binding.placeName.text = name
        binding.placeAddress.text = address

        // Calculate and show distance/time
        destination?.let {
            val distanceKm = userLocation?.let { userLoc ->
                calculateDistance(userLoc, it)
            } ?: -1.0

            if (distanceKm > 0) {
                val estimatedTime = (distanceKm / 40.0 * 60).toInt() // Assuming 40km/h average
                binding.placeAddress.append("\nApprox. ${"%.1f".format(distanceKm)} km")
                binding.placeAddress.append("\nEst. time: $estimatedTime mins")
            }
        }
    }

    private fun zoomToRouteBounds(route: DirectionsRoute) {
        val lineString = LineString.fromPolyline(route.geometry() ?: return, 6)
        val coordinates = lineString.coordinates()

        val currentCamera = mapView.mapboxMap.cameraState.toCameraOptions()

        val cameraOptions = mapView.mapboxMap.cameraForCoordinates(
            coordinates = coordinates,
            camera = currentCamera, // ✅ REQUIRED!
            coordinatesPadding = EdgeInsets(200.0, 200.0, 200.0, 200.0),
            maxZoom = null,
            offset = null
        )

        mapView.mapboxMap.easeTo(
            cameraOptions,
            mapAnimationOptions {
                duration(2000L)
            }
        )
    }



    private fun zoomToSuggestion(destination: Point?) {
        destination?.let {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(it)
                    .zoom(14.0)
                    .build()
            )
        }
    }


    private fun drawRouteLine(routeLine: LineString) {
        val routeFeature = Feature.fromGeometry(routeLine)
        val sourceId = "route-source"
        val layerId = "route-layer"

        mapView.mapboxMap.getStyle { style ->
            if (!style.styleSourceExists(sourceId)) {
                style.addSource(geoJsonSource(sourceId) {
                    feature(routeFeature)
                })
                style.addLayer(lineLayer(layerId, sourceId) {
                    lineColor("#d900ff")
                    lineWidth(5.0)
                })
            } else {
                style.getSourceAs<GeoJsonSource>(sourceId)?.feature(routeFeature)
            }
        }
    }

    private fun showUserLocationMarker() {
        userAnnotationManager.deleteAll() // optional, or keep 1 fixed marker
        userLocation?.let { point ->
            val userMarker = PointAnnotationOptions()
                .withPoint(point)
                .withTextField("You")
                .withIconImage("custom-marker") // or a blue icon for user
                .withIconSize(1.0)

            userAnnotationManager.create(userMarker)
        }
    }




    private fun fetchRouteDetailsAndStartUpdates(destination: Point) {
        val origin = userLocation ?: return

        val profiles = listOf(DirectionsCriteria.PROFILE_DRIVING, DirectionsCriteria.PROFILE_WALKING)

        profiles.forEach { profile ->
            val client = MapboxDirections.builder()
                .origin(origin)
                .destination(destination)
                .overview(DirectionsCriteria.OVERVIEW_FULL)
                .steps(true)
                .profile(selectedRouteProfile)
                .accessToken(getString(R.string.mapbox_access_token))
                .build()

            client.enqueueCall(object : Callback<DirectionsResponse> {
                @SuppressLint("SetTextI18n")
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    val route = response.body()?.routes()?.firstOrNull() ?: return
                    zoomToRouteBounds(route)


                    val timeMin = route.duration().div(60).toInt()
                    val distanceKm = route.distance().div(1000)

                    if (profile == DirectionsCriteria.PROFILE_DRIVING) {
                        drivingInfo.text = "Driving: $timeMin min, ${"%.1f".format(distanceKm)} km"
                        drawRouteLine(LineString.fromPolyline(route.geometry()!!, 6))
                        showNavigationSteps(route)
                    } else {
                        walkingInfo.text = "Walking: $timeMin min, ${"%.1f".format(distanceKm)} km"
                    }


                    binding.placeInfoContainer.visibility = View.VISIBLE
                    binding.navigationInfoContainer.visibility = View.VISIBLE
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    Toast.makeText(requireContext(), "Route error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        startLocationUpdates(destination) // 🔥 Live updates while moving
    }

    private fun showNavigationSteps(route: DirectionsRoute) {
        binding.navigationStepsList.removeAllViews()

        val steps = route.legs()?.firstOrNull()?.steps() ?: return

        for (step in steps) {
            val instruction = step.maneuver().instruction()
            val distance = step.distance()?.toInt() ?: 0

            val textView = TextView(requireContext()).apply {
                text = "• $instruction (${distance}m)"
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }

            Log.d("StepsDebug", "Steps: ${steps.size}")


            binding.navigationStepsList.addView(textView)
        }

        // Show containers
        binding.navigationStepsLabel.visibility = View.VISIBLE
        binding.stepsScrollView.visibility = View.VISIBLE
        binding.navigationInfoContainer.visibility = View.VISIBLE
    }


    private fun startLocationUpdates(destination: Point) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10000L
        ).apply {
            setMinUpdateIntervalMillis(5000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val now = System.currentTimeMillis()
                if (now - lastRouteUpdateTime < 15000) return // wait at least 15s
                lastRouteUpdateTime = now

                val newLoc = result.lastLocation ?: return
                userLocation = Point.fromLngLat(newLoc.longitude, newLoc.latitude)
                fetchRouteDetailsAndStartUpdates(destination)
            }
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101
            )
        }
    }



    private fun moveCameraToLocation(location: Point) {
        mapView.mapboxMap.setCamera(CameraOptions.Builder().center(location).zoom(14.0).build())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

