//Joey Teahan - 20520316
// EventFragment class, this class is used to display and interact with events

//Linking this file with the rest of my project
package com.example.multiway.ui.events

import android.Manifest // Import the Manifest package
import android.app.AlertDialog //To display alerts
import android.content.Intent //Activities
import android.content.pm.PackageManager //check for permissions
import android.graphics.Bitmap //Bitmap images
import android.graphics.Canvas //Lets me draw on the map
import android.graphics.drawable.Drawable //Lets me use the resources in my drawable section
import android.net.Uri //Handles URIs
import android.os.Bundle //Handles the bundle
import android.view.* //Handles the views
import android.widget.* //Handles the widgets (Textviews etc)
import androidx.core.app.ActivityCompat //Handles the permissions
import androidx.core.content.ContextCompat //Context features
import androidx.fragment.app.Fragment// Fragment class
import androidx.lifecycle.lifecycleScope //Lifecycle scope
import com.example.multiway.R // Lets me use rescources
import com.example.multiway.databinding.EventsBinding //Lets me use the binding class
import com.example.multiway.ui.history.HistoryItem //Lets me use the history item class
import com.google.android.gms.location.LocationServices //Needed to get the location of the user
import com.google.android.material.bottomsheet.BottomSheetDialog //Allows me to use the bottom sheet dialog
import com.google.firebase.auth.FirebaseAuth //Firebase authentication
import com.google.firebase.database.FirebaseDatabase //Firebase database
import com.mapbox.geojson.*
import com.mapbox.maps.* //The Mapbox map
import com.mapbox.maps.extension.style.style //Styles the map
import com.mapbox.maps.plugin.annotation.annotations //Annotations plugin
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.locationcomponent.location //Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch //Launch coroutines
import kotlinx.coroutines.withContext
import org.json.JSONObject //JSON object
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.* //Math functions, needed for the radius of the users circle




class EventsFragment : Fragment() {

    //The getter and setter for the binding class
    private var _binding: EventsBinding? = null
    private val binding get() = _binding!!
    // The variable that stores the User's location
    private var userLocation: Point? = null
    // The variables that store the annotations
    private lateinit var circleAnnotationManager: PolygonAnnotationManager
    private lateinit var pointAnnotationManager: PointAnnotationManager
    //Sets the default radius of user circle
    private var selectedRadiusKm = 5.0
    //My unique key that lets me fetch the Ticketmaster events
    private val ticketmasterApiKey = "7JYebFrDh8iArCM2MCBkAGl2Clxc0J9k"

    //The onCreateView function that is called when the fragment is created
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = EventsBinding.inflate(inflater, container, false)
        return binding.root
    }


//The onViewCreated function that is called when the fragment is created
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //Loads up the style of the map
        binding.mapView.mapboxMap.loadStyle(
            //Load the Mapbox style
            style(Style.MAPBOX_STREETS) { } //
        ) {
            //Sets up the annotations for the map
            circleAnnotationManager = binding.mapView.annotations.createPolygonAnnotationManager()
            pointAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()

            //Sets up the radius to filter the events
            theRadiusSelector()
            //Gets the user location on the map
            userLocation()

            //Sets up the bottom sheet dialog
            val dialogView = layoutInflater.inflate(R.layout.eventdetails, null)
            val dialog = BottomSheetDialog(requireContext())
            dialog.setContentView(dialogView)

            //Sets up the icons that I will use on the map
            binding.mapView.mapboxMap.getStyle { style ->
                val eventPin = ContextCompat.getDrawable(requireContext(), R.drawable.eventpin)
                val artIcon = ContextCompat.getDrawable(requireContext(), R.drawable.art)
                val sportIcon = ContextCompat.getDrawable(requireContext(), R.drawable.sport)

                //Displays the icons on the map
                eventPin?.let { drawable ->
                    style.addImage("eventpin", Bitmap(drawable))
                }
                artIcon?.let { drawable ->
                    style.addImage("art", Bitmap(drawable))
                }
                sportIcon?.let { drawable ->
                    style.addImage("sport", Bitmap(drawable))
                }
            }

        }
    }

    //Lets me use drawable resources in this file
    private fun Bitmap(drawable: Drawable): Bitmap {
        //Width and height
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        //Creates the bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        //Creates the canvas
        val canvas = Canvas(bitmap)
        //Sets the drawable to the canvas
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        //Returns the bitmap
        return bitmap
    }


//Sets up the circle
    private fun theRadiusSelector() {
        //The options for the size of the circle
        val options = arrayOf("5 km", "10 km", "20 km", "50 km", "Country Wide")

    //Sets up the button to change the radius
        binding.radiusButton.setOnClickListener {
            //Creates the alert dialog that has the options
            AlertDialog.Builder(requireContext())
                //What the dialog will say
                .setTitle("Select Radius")
                //Sets up the distances from 5k to 50k
                .setItems(options) { _, which ->
                    selectedRadiusKm = when (which) {
                        0 -> 5.0
                        1 -> 10.0
                        2 -> 20.0
                        3 -> 50.0
                        //I used -1.0 to act as the whole country
                        4 -> -1.0
                        else -> 5.0
                    }
                    //Updates the radius
                    userLocation?.let { updateRadiusCircle(it) }
                }
                    //Displays the dialog text
                .show()
        }
    }

    //The function to manage the user's location
    private fun userLocation() {
        //The location plugin
        val locationPlugin = binding.mapView.location

        //Checks for permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            //Request permission if not granted
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }

        //Enables the location plugin
        locationPlugin.updateSettings {
            //Enables tracking
            enabled = true
            //Uses the blue pulsing effect around the user.
             pulsingEnabled = true
        }

        //Gets the user's location
        LocationServices.getFusedLocationProviderClient(requireActivity())
            .lastLocation.addOnSuccessListener { location ->
                //If the users location isnt empty then it will be used
                if (location != null) {
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    userLocation = point
                    //Zooms the camera to the users location
                    binding.mapView.mapboxMap.setCamera(
                        CameraOptions.Builder().center(point).zoom(12.0).build()
                    )
                    //Updates
                    updateRadiusCircle(point)
                }
            }
    }

//Updates the users circle size
    private fun updateRadiusCircle(center: Point) {
        //Deletes any existing circles
        circleAnnotationManager.deleteAll()
        //Deletes any existing markers
        pointAnnotationManager.deleteAll()

        // Creates an object on the map
        val coordinates = generateCircle(center, selectedRadiusKm)
        val polygon = Polygon.fromLngLats(listOf(coordinates))
        circleAnnotationManager.create(
            PolygonAnnotationOptions()
                .withGeometry(polygon)
                //The color of the circle
                .withFillColor("#228BE6")
                .withFillOpacity(0.3)
        )

        //Gets events that are in the circle
        fetchEvents(center, selectedRadiusKm)

    }

    //Generates the circle
    private fun generateCircle(center: Point, radiusKm: Double, steps: Int = 64): List<Point> {
        val coordinates = mutableListOf<Point>()
        //Had to use the earth's radius to create circles
        val earthRadius = 6371.0
        //Converts latitude and longitude to radians
        val lat = Math.toRadians(center.latitude())
        val lon = Math.toRadians(center.longitude())

        //This generates points in the circle
        for (i in 0 until steps) {
            val angle = 2 * Math.PI * i / steps
            val dx = radiusKm / earthRadius * cos(angle)
            val dy = radiusKm / earthRadius * sin(angle)

            val latPoint = lat + dy
            val lonPoint = lon + dx / cos(lat)

            //Adds the points to the list
            coordinates.add(Point.fromLngLat(Math.toDegrees(lonPoint), Math.toDegrees(latPoint)))
        }
        //Returns the coordinates
        coordinates.add(coordinates[0])
        return coordinates
    }

//Data class for the events
    data class EventData(
        val name: String,
        val date: String,
        val venue: String,
        val url: String,
        val location: Point,
        val category: String

    )

//Function to get the events
    private fun fetchEvents(center: Point, radiusKm: Double) {
        lifecycleScope.launch {
            //Getting the Ticketmaster events
            val ticketmasterEvents = fetchTicketmasterEvents(center, radiusKm)
            //Getting the PredictHQ events
            val predicthqEvents = fetchPredictHQEvents(center, radiusKm)
            //Creating a variable that has both types of events
            val allEvents = ticketmasterEvents + predicthqEvents
            //Displays all of the events
            displayEvents(allEvents)
        }
    }


//Function that gets the Ticketmaster events
    private suspend fun fetchTicketmasterEvents(center: Point, radiusKm: Double): List<EventData> {
        //List of the events
        val eventList = mutableListOf<EventData>()

            //Runs the network call
            val response = withContext(Dispatchers.IO) {
                //Uses the available radius
                val (lat, lon, radius) = if (radiusKm > 0) {
                    Triple(center.latitude(), center.longitude(), radiusKm.toInt())
                } else {
                    //Uses the country wide radius
                    Triple(53.4129, -8.2439, 200)
                }

                //Url for the API
                val url = "https://app.ticketmaster.com/discovery/v2/events.json" +
                        "?apikey=$ticketmasterApiKey&latlong=$lat,$lon&radius=$radius&unit=km"
                URL(url).readText()
            }

            //Parses the response
            val eventsJson = JSONObject(response)
            val embedded = eventsJson.optJSONObject("_embedded")
            val eventsArray = embedded?.optJSONArray("events") ?: return emptyList()

            //Loops through the events
            for (i in 0 until eventsArray.length()) {
                val event = eventsArray.getJSONObject(i)
                val name = event.optString("name")
                val date = event.optJSONObject("dates")
                    ?.optJSONObject("start")
                    ?.optString("localDate") ?: "Unknown date"

                //Gets the venue
                val venue = event.optJSONObject("_embedded")
                    ?.optJSONArray("venues")
                    ?.optJSONObject(0)

                //Gets the details of the venue
                val venueName = venue?.optString("name") ?: "Unknown venue"
                val loc = venue?.optJSONObject("location")
                val lat = loc?.optString("latitude")?.toDoubleOrNull()
                val lon = loc?.optString("longitude")?.toDoubleOrNull()

                //Gets the category
                val category = event.optJSONArray("classifications")
                    ?.optJSONObject(0)
                    ?.optJSONObject("segment")
                    ?.optString("name") ?: "Other"

                //If the location is null then it will not be added
                if (lat != null && lon != null) {
                    val point = Point.fromLngLat(lon, lat)
                    eventList.add(
                        EventData(
                            name = name,
                            date = date,
                            venue = venueName,
                            url = event.optString("url") ?: "https://ticketmaster.com",
                            location = point,
                            category = category
                        )
                    )
                }
            }

        //Returns the list of events
        return eventList
    }


    //Function that gets the PredictHQ events
    private suspend fun fetchPredictHQEvents(center: Point, radiusKm: Double): List<EventData> {
        //Stores the events in a list
        val events = mutableListOf<EventData>()
        //My unique key that lets me get the PredictHQ events
        val token = "ExEog2MPnfvoorzFZO8rha75S3-p8c2QyUAvtO1p"
        //Converts the center point to lat and lon
        val lat = center.latitude()
        val lon = center.longitude()

            //Try catch block to handle errors
        return try {
            //Same as my ticketmaster section, i need to use a network request
            val response = withContext(Dispatchers.IO) {
                val url = if (radiusKm > 0) {
                    URL("https://api.predicthq.com/v1/events/?within=${radiusKm}km@${lat},${lon}&category=concerts,sports,festivals,performing-arts,community")
                } else {
                    // Approximate center of Ireland and large bounding radius (~200km)
                    URL("https://api.predicthq.com/v1/events/?within=200km@53.4129,-8.2439&category=concerts,sports,festivals,performing-arts,community")
                }

                //Sets up the connection
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                }
            }

            //Sets up the response
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return emptyList()

            //Loops through the events
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val title = item.optString("title", "Unnamed Event")
                val start = item.optString("start", "Unknown date")
                val category = item.optString("category", "Other")
                val locationArray = item.optJSONArray("location")
                val latV = locationArray?.optDouble(1)
                val lonV = locationArray?.optDouble(0)

                //If the location is null then it will not be added
                if (latV != null && lonV != null) {
                    val point = Point.fromLngLat(lonV, latV)
                    events.add(
                        EventData(
                            name = title,
                            date = start,
                            venue = "PredictHQ Event",
                            url = "https://www.predicthq.com",
                            location = point,
                            category = category
                        )
                    )
                }
            }

            //Returns the list of events
            events
            //Catch block to handle errors
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                //Displays the error
                Toast.makeText(requireContext(), "PredictHQ error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            //Returns an empty list
            emptyList()
        }
    }

    //Function to show the event details
    private fun showEventDetails(event: EventData) {
        //Sets up the bottom sheet dialog
        val dialogView = layoutInflater.inflate(R.layout.eventdetails, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)
        //Sets up the text
        dialogView.findViewById<TextView>(R.id.eventTitle).text = "🎤 ${event.name}"
        dialogView.findViewById<TextView>(R.id.eventDate).text = "📅 ${event.date}"
        dialogView.findViewById<TextView>(R.id.eventVenue).text = "📍 ${event.venue}"
        dialogView.findViewById<Button>(R.id.viewMoreButton).setOnClickListener {
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
            startActivity(urlIntent)
        }

        //Saves the event to the history
        saveToHistory(event.name, "Viewed event at ${event.venue}")
        dialog.show()
    }

    //Function to display the event pins
    private fun displayEvents(events: List<EventData>) {
        pointAnnotationManager.deleteAll()

        //Groups the events by location
        val grouped = events.groupBy { "${it.location.latitude()},${it.location.longitude()}" }

        for ((_, group) in grouped) {
            val location = group.first().location
            val firstEvent = group.first()

            //Sets up the icons for the events
            val iconName = when {
                firstEvent.category.contains("sports", ignoreCase = true) -> "sport"
                firstEvent.category.contains("art", ignoreCase = true) ||
                        firstEvent.category.contains("performing", ignoreCase = true) -> "art"
                else -> "eventpin"
            }

            //Creates the marker
            val marker = pointAnnotationManager.create(
                PointAnnotationOptions()
                    .withPoint(location)
                    .withIconImage(iconName)
                    .withIconSize(1.0)
            )

            //Sets up the click listener
            pointAnnotationManager.addClickListener {
                if (it == marker) {
                    val eventNames = group.map { e -> e.name }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle(group.first().venue)
                        .setItems(eventNames) { _, index ->
                            showEventDetails(group[index])
                        }
                            //Sets up the close button
                        .setNegativeButton("Close", null)
                        .show()
                    //Returns true to indicate the click was handled
                    true
                    //Returns false to indicate the click was not handled
                } else false
            }
        }
    }

    //Function to save the event to the history page
    private fun saveToHistory(title: String, subtitle: String) {
        //Saves the users ID and history reference
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val historyRef = FirebaseDatabase.getInstance().getReference("History").child(userId)
        //Creates the item
        val item = HistoryItem(title = title, subtitle = subtitle)
        historyRef.push().setValue(item)
    }


//Function to close the fragment
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
