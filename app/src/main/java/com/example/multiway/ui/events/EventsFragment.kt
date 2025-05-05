package com.example.multiway.ui.events

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.multiway.R
import com.example.multiway.databinding.EventsBinding
import com.example.multiway.ui.history.HistoryItem
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.mapbox.geojson.*
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*




class EventsFragment : Fragment() {

    private var _binding: EventsBinding? = null
    private val binding get() = _binding!!
    private var userLocation: Point? = null
    private lateinit var circleAnnotationManager: PolygonAnnotationManager
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private var selectedRadiusKm = 5.0
    private val ticketmasterApiKey = "7JYebFrDh8iArCM2MCBkAGl2Clxc0J9k"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = EventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mapView.mapboxMap.loadStyle(
            style(Style.DARK) { } // ✅ This empty block satisfies the required parameter
        ) {
            // ✅ This block runs after the style is fully loaded
            circleAnnotationManager = binding.mapView.annotations.createPolygonAnnotationManager()
            pointAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
            setupRadiusSelector()
            enableUserLocation()

            val dialogView = layoutInflater.inflate(R.layout.eventdetails, null)
            val dialog = BottomSheetDialog(requireContext())
            dialog.setContentView(dialogView)




            binding.mapView.mapboxMap.getStyle { style ->
                val eventPin = ContextCompat.getDrawable(requireContext(), R.drawable.eventpin)
                val artIcon = ContextCompat.getDrawable(requireContext(), R.drawable.art)
                val sportIcon = ContextCompat.getDrawable(requireContext(), R.drawable.sport)

                eventPin?.let { drawable ->
                    style.addImage("eventpin", drawableToBitmap(drawable))
                }
                artIcon?.let { drawable ->
                    style.addImage("art", drawableToBitmap(drawable))
                }
                sportIcon?.let { drawable ->
                    style.addImage("sport", drawableToBitmap(drawable))
                }
            }

        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }



    private fun setupRadiusSelector() {
        val options = arrayOf("5 km", "10 km", "20 km", "50 km", "Country Wide")

        binding.radiusButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Radius")
                .setItems(options) { _, which ->
                    selectedRadiusKm = when (which) {
                        0 -> 5.0
                        1 -> 10.0
                        2 -> 20.0
                        3 -> 50.0
                        4 -> -1.0
                        else -> 5.0
                    }
                    userLocation?.let { updateRadiusCircle(it) }
                }
                .show()
        }
    }


    private fun enableUserLocation() {
        val locationPlugin = binding.mapView.location

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

        locationPlugin.updateSettings {
            enabled = true
            pulsingEnabled = true
        }

        LocationServices.getFusedLocationProviderClient(requireActivity())
            .lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    userLocation = point
                    binding.mapView.mapboxMap.setCamera(
                        CameraOptions.Builder().center(point).zoom(12.0).build()
                    )
                    updateRadiusCircle(point)
                }
            }
    }

    private fun updateRadiusCircle(center: Point) {
        circleAnnotationManager.deleteAll()
        pointAnnotationManager.deleteAll()

        // Create a polygon circle (geographically accurate)
        val coordinates = generateCircleCoordinates(center, selectedRadiusKm)
        val polygon = Polygon.fromLngLats(listOf(coordinates))
        circleAnnotationManager.create(
            PolygonAnnotationOptions()
                .withGeometry(polygon)
                .withFillColor("#228BE6")
                .withFillOpacity(0.3)
        )

        fetchEvents(center, selectedRadiusKm)

    }

    private fun generateCircleCoordinates(center: Point, radiusKm: Double, steps: Int = 64): List<Point> {
        val coordinates = mutableListOf<Point>()
        val earthRadius = 6371.0
        val lat = Math.toRadians(center.latitude())
        val lon = Math.toRadians(center.longitude())

        for (i in 0 until steps) {
            val angle = 2 * Math.PI * i / steps
            val dx = radiusKm / earthRadius * cos(angle)
            val dy = radiusKm / earthRadius * sin(angle)

            val latPoint = lat + dy
            val lonPoint = lon + dx / cos(lat)

            coordinates.add(Point.fromLngLat(Math.toDegrees(lonPoint), Math.toDegrees(latPoint)))
        }
        coordinates.add(coordinates[0]) // close the polygon
        return coordinates
    }

    data class EventData(
        val name: String,
        val date: String,
        val venue: String,
        val url: String,
        val location: Point,
        val category: String

    )

    private fun fetchEvents(center: Point, radiusKm: Double) {
        lifecycleScope.launch {
            val ticketmasterEvents = fetchTicketmasterEvents(center, radiusKm)
            val predicthqEvents = fetchPredictHQEvents(center, radiusKm)
            val allEvents = ticketmasterEvents + predicthqEvents


            displayEventsOnMap(allEvents)
        }
    }



    private suspend fun fetchTicketmasterEvents(center: Point, radiusKm: Double): List<EventData> {
        val eventList = mutableListOf<EventData>()

        try {
            val response = withContext(Dispatchers.IO) {
                val (lat, lon, radius) = if (radiusKm > 0) {
                    Triple(center.latitude(), center.longitude(), radiusKm.toInt())
                } else {
                    Triple(53.4129, -8.2439, 200) // Country-wide fallback
                }

                val url = "https://app.ticketmaster.com/discovery/v2/events.json" +
                        "?apikey=$ticketmasterApiKey&latlong=$lat,$lon&radius=$radius&unit=km"
                URL(url).readText()
            }

            val eventsJson = JSONObject(response)
            val embedded = eventsJson.optJSONObject("_embedded")
            val eventsArray = embedded?.optJSONArray("events") ?: return emptyList()

            for (i in 0 until eventsArray.length()) {
                val event = eventsArray.getJSONObject(i)
                val name = event.optString("name")
                val date = event.optJSONObject("dates")
                    ?.optJSONObject("start")
                    ?.optString("localDate") ?: "Unknown date"

                val venue = event.optJSONObject("_embedded")
                    ?.optJSONArray("venues")
                    ?.optJSONObject(0)

                val venueName = venue?.optString("name") ?: "Unknown venue"
                val loc = venue?.optJSONObject("location")
                val lat = loc?.optString("latitude")?.toDoubleOrNull()
                val lon = loc?.optString("longitude")?.toDoubleOrNull()

                val category = event.optJSONArray("classifications")
                    ?.optJSONObject(0)
                    ?.optJSONObject("segment")
                    ?.optString("name") ?: "Other"

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
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ticketmaster error: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        return eventList
    }



    private suspend fun fetchPredictHQEvents(center: Point, radiusKm: Double): List<EventData> {
        val events = mutableListOf<EventData>()
        val token = "ExEog2MPnfvoorzFZO8rha75S3-p8c2QyUAvtO1p"
        val lat = center.latitude()
        val lon = center.longitude()

        return try {
            val response = withContext(Dispatchers.IO) {
                val url = if (radiusKm > 0) {
                    URL("https://api.predicthq.com/v1/events/?within=${radiusKm}km@${lat},${lon}&category=concerts,sports,festivals,performing-arts,community")
                } else {
                    // Approximate center of Ireland and large bounding radius (~200km)
                    URL("https://api.predicthq.com/v1/events/?within=200km@53.4129,-8.2439&category=concerts,sports,festivals,performing-arts,community")
                }

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

            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return emptyList()

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val title = item.optString("title", "Unnamed Event")
                val start = item.optString("start", "Unknown date")
                val category = item.optString("category", "Other")
                val locationArray = item.optJSONArray("location")
                val latV = locationArray?.optDouble(1)
                val lonV = locationArray?.optDouble(0)

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

            events
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "PredictHQ error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            emptyList()
        }
    }


    private fun showEventDetails(event: EventData) {
        val dialogView = layoutInflater.inflate(R.layout.eventdetails, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)

        dialogView.findViewById<TextView>(R.id.eventTitle).text = "🎤 ${event.name}"
        dialogView.findViewById<TextView>(R.id.eventDate).text = "📅 ${event.date}"
        dialogView.findViewById<TextView>(R.id.eventVenue).text = "📍 ${event.venue}"
        dialogView.findViewById<Button>(R.id.viewMoreButton).setOnClickListener {
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
            startActivity(urlIntent)
        }

        saveToHistory(event.name, "Viewed event at ${event.venue}")


        dialog.show()
    }

    private fun displayEventsOnMap(events: List<EventData>) {
        pointAnnotationManager.deleteAll()

        val grouped = events.groupBy { "${it.location.latitude()},${it.location.longitude()}" }

        for ((_, group) in grouped) {
            val location = group.first().location
            val firstEvent = group.first()

            val iconName = when {
                firstEvent.category.contains("sports", ignoreCase = true) -> "sport"
                firstEvent.category.contains("art", ignoreCase = true) ||
                        firstEvent.category.contains("performing", ignoreCase = true) -> "art"
                else -> "eventpin"
            }

            val marker = pointAnnotationManager.create(
                PointAnnotationOptions()
                    .withPoint(location)
                    .withIconImage(iconName)
                    .withIconSize(1.0)
            )

            pointAnnotationManager.addClickListener {
                if (it == marker) {
                    val eventNames = group.map { e -> e.name }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle(group.first().venue)
                        .setItems(eventNames) { _, index ->
                            showEventDetails(group[index])
                        }
                        .setNegativeButton("Close", null)
                        .show()
                    true
                } else false
            }
        }
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
