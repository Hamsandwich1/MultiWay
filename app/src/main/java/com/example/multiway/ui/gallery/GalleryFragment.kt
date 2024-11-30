package com.example.multiway.ui.gallery


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.multiway.databinding.FragmentGalleryBinding
import com.google.android.gms.maps.MapView
import com.mapbox.maps.Style


class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapView: MapView

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
        mapView = binding.mapView

        // Load the map asynchronously
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            // You can perform additional setup here (e.g., add markers or layers)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
