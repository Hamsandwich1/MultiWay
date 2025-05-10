//Joey Teahan - 20520316
// AccountFragment.kt - this class managers the user's account
package com.example.multiway.ui.account //My projects package

import android.os.Bundle //Used to save data
import android.view.LayoutInflater //Used to inflate the layout
import android.view.View //Get views
import android.view.ViewGroup //Get view groups
import android.widget.Toast //Needed to show toast messages
import androidx.fragment.app.Fragment //Fragment
import androidx.navigation.fragment.findNavController //Navigation controller
import com.example.multiway.R //To connect to other functions in my project
import com.example.multiway.databinding.FragmentAccountBinding //To connect to the layout
import com.google.firebase.auth.FirebaseAuth //needed for Firebase

// My Account Fragment class
class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null //Binding for the layout
    private val binding get() = _binding!! //Get the binding

    //Sets up the Firebase
    private val auth = FirebaseAuth.getInstance()

    //Inflates the layout
    override fun onCreateView(
        //Setting the inflater, container, and savedInstanceState
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = auth.currentUser

        if (user != null) {
            binding.userEmail.text = user.email ?: "N/A"
        } else {
            Toast.makeText(requireContext(), "No one logged in", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.nav_login)
        }

        //setting up what will happen when the user clicks the logout button
        binding.logoutButton.setOnClickListener {
            //Signs the user out
            auth.signOut()
            //Toast message
            Toast.makeText(requireContext(), "Logging out", Toast.LENGTH_SHORT).show()
            //Goes back to log in screen
            findNavController().navigate(R.id.nav_login)
        }
    }

//Used to clear the file after use
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}