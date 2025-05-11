// Joey Teahan - 20520316
// HomeFragment class, The opening page of the app

package com.example.multiway.ui.home
//Imports that I have explained in previous classes
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.multiway.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.example.multiway.R

//HomeFragment class
class HomeFragment : Fragment() {
    //Binding variables
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    //onCreateView function
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        //ViewModelProvider function
        ViewModelProvider(this).get(HomeViewModel::class.java)
        //Inflates the layout
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val currentUser = FirebaseAuth.getInstance().currentUser
        val user = FirebaseAuth.getInstance().currentUser
        //Couldnt figure out how to get the username to be displayed on the home page, so I just used the email address
        val name = user?.displayName ?: user?.email?.substringBefore("@") ?: "there"
        binding.welcomeText.text = "Welcome back $name!"

        val displayName = when {
            !currentUser?.displayName.isNullOrEmpty() -> currentUser?.displayName
            !currentUser?.email.isNullOrEmpty() -> currentUser?.email?.substringBefore("@")
            else -> "there"
        }
        binding.welcomeText.text = "Welcome back, $displayName!"

        return root

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.gallerybtn.setOnClickListener {
            //The button that brings the user to the gallery page
            findNavController().navigate(R.id.nav_gallery)
        }

        binding.eventsbtn.setOnClickListener {
            //The button that brings the user to the events page
            findNavController().navigate(R.id.nav_events)
        }

        binding.historybtn.setOnClickListener {
            //The button that brings the user to the history page
            findNavController().navigate(R.id.nav_history)
        }

        binding.routesbtn.setOnClickListener {
            //The button that brings the user to the suggested page
            findNavController().navigate(R.id.nav_suggested)

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
