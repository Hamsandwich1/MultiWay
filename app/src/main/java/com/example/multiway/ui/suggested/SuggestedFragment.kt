package com.example.multiway.ui.suggested

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
import com.example.multiway.databinding.FragmentNearbyEventsBinding
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import java.net.URL
import kotlin.math.*




class SuggestedFragment : Fragment() {

    private var _binding: FragmentNearbyEventsBinding? = null
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
        _binding = FragmentNearbyEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mapView.mapboxMap.loadStyle(
            style(Style.MAPBOX_STREETS) { } // ✅ This empty block satisfies the required parameter
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
                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.eventpin)
                drawable?.let {
                    val bitmap = drawableToBitmap(it)
                    style.addImage("custom-marker", bitmap)
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
        val options = arrayOf("5 km", "10 km", "20 km")

        binding.radiusButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Radius")
                .setItems(options) { _, which ->
                    selectedRadiusKm = when (which) {
                        0 -> 5.0
                        1 -> 10.0
                        2 -> 20.0
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

        fetchTicketmasterEvents(center, selectedRadiusKm)
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
        val url: String
    )

    private fun fetchTicketmasterEvents(center: Point, radiusKm: Double) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val lat = center.latitude()
                    val lon = center.longitude()
                    val radius = radiusKm.toInt()
                    val url = "https://app.ticketmaster.com/discovery/v2/events.json" +
                            "?apikey=$ticketmasterApiKey&latlong=$lat,$lon&radius=$radius&unit=km"
                    URL(url).readText()
                }

                val eventsJson = JSONObject(response)
                val embedded = eventsJson.optJSONObject("_embedded")
                val eventsArray = embedded?.optJSONArray("events") ?: return@launch

                val eventMap = mutableMapOf<String, MutableList<Triple<String, String?, String?>>>()
                // key = "lat,lon", value = List of (eventName, eventDate, venueName)

                for (i in 0 until eventsArray.length()) {
                    val event = eventsArray.getJSONObject(i)
                    val name = event.optString("name")
                    val dates = event.optJSONObject("dates")
                    val startDate = dates?.optJSONObject("start")?.optString("localDate") ?: "Unknown date"
                    val venues = event.optJSONObject("_embedded")?.optJSONArray("venues")
                    val venue = venues?.optJSONObject(0)
                    val venueName = venue?.optString("name") ?: "Unknown venue"
                    val loc = venue?.optJSONObject("location")
                    val lat = loc?.optString("latitude")?.toDoubleOrNull()
                    val lon = loc?.optString("longitude")?.toDoubleOrNull()

                    if (lat != null && lon != null) {
                        val key = "$lat,$lon|$venueName"
                        val list = eventMap.getOrPut(key) { mutableListOf() }
                        list.add(Triple(name, startDate, venueName))
                    }
                }

                for ((key, eventTriples) in eventMap) {
                    val parts = key.split("|")
                    val (lat, lon) = parts[0].split(",").map { it.toDouble() }
                    val venueName = parts.getOrNull(1) ?: "Events at this location"
                    val point = Point.fromLngLat(lon, lat)

                    // Display a pin with optional label or leave empty
                    val annotation = pointAnnotationManager.create(
                        PointAnnotationOptions()
                            .withPoint(point)
                            .withIconImage("custom-marker")
                            .withIconSize(1.3)
                    )

                    pointAnnotationManager.addClickListener {
                        if (it == annotation) {
                            val eventNames = eventTriples.map { triple -> triple.first }
                            AlertDialog.Builder(requireContext())
                                .setTitle(venueName)
                                .setItems(eventNames.toTypedArray()) { _, index ->
                                    val selected = eventTriples[index]
                                    showEventDetails(
                                        EventData(
                                            name = selected.first,
                                            date = selected.second ?: "Unknown date",
                                            venue = selected.third ?: venueName,
                                            url = "https://ticketmaster.com" // ✅ You can replace with actual URL if available
                                        )
                                    )
                                }
                                .setNegativeButton("Cancel", null)
                                .show()

                            true
                        } else false
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to fetch events: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
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
            val urlIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(event.url))
            startActivity(urlIntent)
        }

        dialog.show()
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
