// Joey Teahan - 20520316
// Home class, used to display the user's history

package com.example.multiway.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.multiway.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val currentUser = FirebaseAuth.getInstance().currentUser

        val user = FirebaseAuth.getInstance().currentUser
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
