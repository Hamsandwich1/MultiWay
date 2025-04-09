package com.example.multiway.ui.suggested

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.multiway.R
import com.example.multiway.databinding.FragmentNearbyEventsBinding
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.launch

class SuggestedFragment : Fragment() {

    private var _binding: FragmentNearbyEventsBinding? = null
    private val binding get() = _binding!!

    private lateinit var circleAnnotationManager: CircleAnnotationManager
    private var userLocation: Point? = null
    private var selectedRadius = 5.0 // in km

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNearbyEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val mapView = binding.mapView

        lifecycleScope.launch {
            mapView.mapboxMap.loadStyle(
                style(Style.MAPBOX_STREETS) {}  // 🔧 Provide an empty DSL block
            ) {
                // This is the onStyleLoaded callback
                circleAnnotationManager = mapView.annotations.createCircleAnnotationManager()
                enableUserLocation()
            }


            // Radius options from strings.xml -> arrays.xml
            val adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.radius_options,
                android.R.layout.simple_spinner_dropdown_item
            )
            binding.radiusSelector.adapter = adapter

            binding.radiusSelector.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?, position: Int, id: Long
                    ) {
                        selectedRadius = when (position) {
                            0 -> 5.0
                            1 -> 10.0
                            2 -> 20.0
                            else -> 5.0
                        }
                        updateRadiusCircle()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
        }
    }

    private fun enableUserLocation() {
        val locationPlugin = binding.mapView.location

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100
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
                    userLocation = Point.fromLngLat(location.longitude, location.latitude)
                    binding.mapView.mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(userLocation)
                            .zoom(12.0)
                            .build()
                    )
                    updateRadiusCircle()
                } else {
                    Toast.makeText(requireContext(), "Unable to retrieve location", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateRadiusCircle() {
        if (userLocation == null) return

        circleAnnotationManager.deleteAll()

        val radiusInMeters = selectedRadius * 1000

        // 🔍 Convert meters to pixel approximation (tune this value if needed)
        val estimatedPixelRadius = when (selectedRadius) {
            5.0 -> 200.0
            10.0 -> 350.0
            20.0 -> 500.0
            else -> 200.0
        }

        val circleOptions = CircleAnnotationOptions()
            .withPoint(userLocation!!)
            .withCircleRadius(estimatedPixelRadius) // use pixel estimate
            .withCircleColor("#44AAFF")
            .withCircleOpacity(0.4)

        circleAnnotationManager.create(circleOptions)

        binding.mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(userLocation)
                .zoom(13.0) // Adjust based on preference
                .build()
        )

    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
