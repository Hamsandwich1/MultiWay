//Joey Teahan - 20520316
// Gallery class, used to display the gallery features of the app

package com.example.multiway.ui.gallery //Package that links the project together

import android.os.Bundle // Imports the Bundle class
import android.view.LayoutInflater // Need to inflate the layout to fit the fragment
import android.view.View // Has the user interface to function properly
import android.view.ViewGroup // Where the fragment will be
import androidx.fragment.app.Fragment // Import the Fragment class
import com.example.multiway.databinding.FragmentGalleryBinding// Binds the info
import com.mapbox.maps.Style //Needed to get the Mapbox styling options
import com.mapbox.maps.MapView // Displays the Mapbox map



class GalleryFragment : Fragment() { //The name of this class, and it extends the fragment class

    private var _binding: FragmentGalleryBinding? = null // The binding variable
    private val binding get() = _binding!! // The getter
    private lateinit var mapView: MapView //Setting up the mapview as a variable

    override fun onCreateView( //Create view to set up inflater, container and savedInstanceState
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        mapView = binding.mapView //Setting up the mapview as a variable

        mapView.getMapboxMap().loadStyle(Style.MAPBOX_STREETS) { //
        }
    }

   //Used when
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
