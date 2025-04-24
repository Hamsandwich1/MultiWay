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
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.toCameraOptions
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.multiway.ui.gallery.SimilarPlacesAdapter
import com.example.multiway.ui.history.HistoryItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase


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
    private lateinit var selectedPlaceAnnotationManager: PointAnnotationManager
    private lateinit var searchResultAnnotationManager: PointAnnotationManager
    private var activeSearchListener: PlaceAutocompleteUiAdapter.SearchListener? = null
    private lateinit var similarPlacesRecycler: RecyclerView
    private lateinit var similarPlaceAdapter: SimilarPlacesAdapter
    private var lastKnownLocation: Point? = null




    data class SimilarPlaceItem(
        val name: String,
        val distanceKm: Double
    )

    data class SimilarPlace(
        val name: String,
        val distanceKm: Double
    )


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


        similarPlaceAdapter = SimilarPlacesAdapter(emptyList()) // ✅ Safe init
        similarPlacesRecycler = binding.similarPlacesRecycler
        similarPlacesRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        similarPlacesRecycler.adapter = similarPlaceAdapter


        binding.similarPlacesRecycler.visibility = View.VISIBLE
        binding.similarPlacesRecycler.adapter = similarPlaceAdapter
        binding.similarPlacesRecycler.layoutManager = LinearLayoutManager(requireContext())

        binding.similarPlacesRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)


        binding.placeInfoContainer.visibility = View.VISIBLE
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



        binding.navigationInfoContainer.visibility = View.VISIBLE

        annotationManager = mapView.annotations.createPointAnnotationManager()
        searchResultAnnotationManager = mapView.annotations.createPointAnnotationManager()
        selectedPlaceAnnotationManager = mapView.annotations.createPointAnnotationManager()




        val bottomSheet = view.findViewById<View>(R.id.placeInfoBottomSheet)
        val bottomSheetBehavior = from(bottomSheet)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        binding.btnCloseInfo.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }


        userLocation?.let { location ->
            val userMarker = PointAnnotationOptions()
                .withPoint(location)
                .withIconImage("custom-marker") // your custom icon name
                .withIconSize(0.5)

            userAnnotationManager.create(userMarker)
        }


        val annotationPlugin = mapView.annotations
        selectedPlaceAnnotationManager = annotationPlugin.createPointAnnotationManager()


        placeAutocomplete = PlaceAutocomplete.create(
            locationProvider = null // or you can provide a LocationProvider if needed
        )

        polylineAnnotationManager = mapView.annotations.createPolylineAnnotationManager()

        zoomToUserLocation {
            // Only trigger search once location is fully set
            if (userLocation != null) {
                performSearch(queryEditText.text.toString())
            } else {
                Toast.makeText(requireContext(), "Waiting for accurate location...", Toast.LENGTH_SHORT).show()
            }
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

            val categoriesWithIcons = mapOf(
                "restaurant" to R.drawable.food,
                "pub" to R.drawable.pub,
                "park" to R.drawable.park,
                "tourist_attraction" to R.drawable.tour,
                "hotel" to R.drawable.hotel,
                "shop" to R.drawable.shop,
            )

            categoriesWithIcons.forEach { (key, resId) ->
                val bitmap = BitmapFactory.decodeResource(resources, resId)
                style.addImage(key, bitmap)
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
                    zoomToUserLocation {
                        lifecycleScope.launch {
                            placeAutocompleteUiAdapter.search(queryEditText.text.toString())
                        }
                    }
                    return
                }

                // Filter out suggestions without coordinates or names
                val validSuggestions = suggestions.filter {
                    it.coordinate != null && it.name.isNotBlank()
                }

                // Sort suggestions by distance to user
                val sortedSuggestions = validSuggestions.sortedBy { suggestion ->
                    calculateDistance(userLocation!!, suggestion.coordinate!!)
                }






                // Map to your custom display list
                val displayList = sortedSuggestions.map { suggestion ->
                    SearchResultItem(
                        name = suggestion.name,
                        distanceKm = calculateDistance(userLocation!!, suggestion.coordinate!!)
                    )
                }

                // Update UI
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

        // Detach previous listener
        activeSearchListener?.let { placeAutocompleteUiAdapter.removeSearchListener(it) }

        val newListener = object : PlaceAutocompleteUiAdapter.SearchListener {
            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
                if (userLocation == null) return

                val sorted = suggestions
                    .filter { it.coordinate != null && detectCategory(it) == category }
                    .sortedBy { suggestion -> calculateDistance(userLocation!!, suggestion.coordinate!!) }


                // 🔥 Remove all previous search results
                searchResultAnnotationManager.deleteAll()
                selectedPlaceAnnotationManager.deleteAll()

                sorted.forEach { suggestion ->
                    val coord = suggestion.coordinate!!
                    val categoryIcon = detectCategory(suggestion)
                    val marker = PointAnnotationOptions()
                        .withPoint(coord)
                        .withTextField(suggestion.name)
                        .withIconImage(categoryIcon)
                        .withIconSize(0.5)
                        .withTextOffset(listOf(0.0, 1.5))

                    // 🔥 Add a new search result marker
                    searchResultAnnotationManager.create(marker)
                }

                // Optional: update UI
                val displayList = sorted.map {
                    SearchResultItem(
                        name = it.name,
                        distanceKm = calculateDistance(userLocation!!, it.coordinate!!)
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
                Toast.makeText(requireContext(), "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        placeAutocompleteUiAdapter.addSearchListener(newListener)
        activeSearchListener = newListener

        // ✅ Actually trigger the search
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

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).apply {
            setMaxUpdateAgeMillis(0) // ⚡ Get the freshest data
            setMinUpdateIntervalMillis(1000)
        }.build()

        val tempLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    userLocation = point
                    lastKnownLocation = point
                    moveCameraToLocation(point)
                    onLocated?.invoke()
                } else {
                    Toast.makeText(requireContext(), "Unable to get updated location", Toast.LENGTH_SHORT).show()
                }

                // ✅ Remove callback after first result to save battery
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            tempLocationCallback,
            Looper.getMainLooper()
        )
    }




    private fun performCategorySearch(categoryLabel: String) {
        val categoryMap = mapOf(
            "Pubs" to "bar OR bar",
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

                    if (userLocation == null) {
                        Log.w("Search", "User location not ready — skipping sort.")
                        return
                    }

                    val sortedSuggestions = suggestions
                        .filter { it.coordinate != null && it.name.isNotBlank() }
                        .sortedBy {
                            calculateDistance(userLocation!!, it.coordinate!!)
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
        lifecycleScope.launch(Dispatchers.Main) {
            val result = withContext(Dispatchers.IO) {
                placeAutocomplete.select(suggestion)
            }

            result.onValue { searchResult ->
                val location = searchResult.coordinate
                val placeName = searchResult.name
                val category = detectCategory(suggestion)

                moveCameraToLocation(location)
                showPlaceInfo(suggestion)
                fetchRouteDetailsAndStartUpdates(location)

                // Marker for selected place
                addSearchMarker(location, placeName, category)

                saveToHistory(placeName, "Searched location")


                lifecycleScope.launch {
                    val similarSuggestions = suggestionsNearby(location, "restaurant")

                    if (similarSuggestions.isNotEmpty()) {
                        val convertedSuggestions = similarSuggestions.map {
                            SimilarPlace(it.name, it.distanceKm)
                        }

                        if (similarSuggestions.isNotEmpty()) {
                            similarPlaceAdapter = SimilarPlacesAdapter(convertedSuggestions)
                            binding.similarPlacesRecycler.adapter = similarPlaceAdapter
                            binding.similarPlacesRecycler.visibility = View.VISIBLE
                        } else {
                            binding.similarPlacesRecycler.visibility = View.GONE
                        }




                        Log.d("SimilarPlaces", "Found ${similarSuggestions.size} similar places")


                        binding.btnCloseInfo.setOnClickListener {
                            binding.placeInfoContainer.visibility = View.GONE
                        }

                        lastSelectedDestination = location
                        binding.searchResultsView.visibility = View.GONE

                    }
                }
            }
        }
    }

    private suspend fun suggestionsNearby(center: Point, category: String): List<SimilarPlaceItem> {
        return withContext(Dispatchers.IO) {
            val queryToUse = if (category.isBlank() || category == "default") {
                "restaurant" // fallback
            } else {
                category
            }

            Log.d("SimilarPlaces", "Using query: $queryToUse near ${center.latitude()}, ${center.longitude()}")

            try {
                val result = placeAutocomplete.suggestions(
                    query = queryToUse,
                    proximity = center
                )

                val suggestions = result.value ?: emptyList()

                Log.d("SimilarPlaces", "Suggestions returned: ${suggestions.size}")

                suggestions
                    .filter { it.coordinate != null && it.name.isNotBlank() }
                    .take(5)
                    .map {
                        Log.d("SimilarPlaces", "→ ${it.name} at ${it.coordinate}")
                        SimilarPlaceItem(
                            name = it.name,
                            distanceKm = calculateDistance(center, it.coordinate!!)
                        )
                    }

            } catch (e: Exception) {
                Log.e("SimilarPlaces", "Error fetching suggestions: ${e.localizedMessage}")
                emptyList()
            }
        }
    }




    class SimilarPlacesAdapter(
        private var places: List<SimilarPlace>
    ) : RecyclerView.Adapter<SimilarPlacesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val placeName: TextView = view.findViewById(R.id.similarPlaceName)
            val distance: TextView = view.findViewById(R.id.similarPlaceDistance)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_similar_place, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = places.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val place = places[position]
            holder.placeName.text = place.name
            holder.distance.text = "${"%.1f".format(place.distanceKm)} km"
        }


    }




    private fun detectCategory(suggestion: PlaceAutocompleteSuggestion): String {
        val name = suggestion.name.lowercase(Locale.ROOT)

        return when {
            name.contains("restaurant") || name.contains("grill") || name.contains("bistro") ||
                    name.contains("steakhouse") || name.contains("diner") -> "restaurant"

            name.contains("pub") || name.contains("bar") || name.contains("tavern") ||
                    name.contains("lounge") || name.contains("saloon") -> "pub"

            name.contains("park") || name.contains("garden") || name.contains("greens") -> "park"

            name.contains("hotel") || name.contains("inn") || name.contains("resort") -> "hotel"

            name.contains("shop") || name.contains("store") || name.contains("boutique") -> "shop"

            name.contains("museum") || name.contains("landmark") || name.contains("monument") || name.contains("attraction") -> "tourist_attraction"

            else -> "default"
        }
    }




    private fun addSearchMarker(location: Point, title: String?, category: String?) {
        selectedPlaceAnnotationManager.deleteAll() // ✅ clear first
        searchResultAnnotationManager.deleteAll() // 🔥 remove other search results too

        val iconKey = when (category?.trim()?.lowercase(Locale.ROOT)) {
            "restaurant" -> "restaurant"
            "pub" -> "pub"
            "park" -> "park"
            "tourist_attraction", "landmark", "monument" -> "tourist_attraction"
            "hotel" -> "hotel"
            "shop", "shopping" -> "shop"
            else -> "default"
        }

        val markerOptions = PointAnnotationOptions()
            .withPoint(location)
            .withTextField(title ?: "Location")
            .withIconImage(iconKey)
            .withIconSize(0.4)
            .withTextOffset(listOf(0.0, 2.0))
            .withTextColor("#000000")
            .withTextHaloColor("#FFFFFF")
            .withTextHaloWidth(2.0)

        selectedPlaceAnnotationManager.create(markerOptions)

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
    }


    private fun startLocationUpdates(destination: Point) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10000L
        ).apply {
            setMinUpdateIntervalMillis(5000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                    if (!isAdded) return  // <- Prevent crash

                    val now = System.currentTimeMillis()
                    if (now - lastRouteUpdateTime < 15000) return
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

    private fun saveToHistory(title: String, subtitle: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val historyRef = FirebaseDatabase.getInstance().getReference("History").child(userId)

        val item = HistoryItem(title = title, subtitle = subtitle)
        historyRef.push().setValue(item)
    }

}

