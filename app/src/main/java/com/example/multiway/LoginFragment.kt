// Joey Teahan - 20520316
//LoginFragment - Main login page

package com.example.multiway

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.multiway.ui.InfoViewModel



class LoginFragment : Fragment() {
    private lateinit var infoViewModel: InfoViewModel





    override fun onCreateView(

    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        infoViewModel = ViewModelProvider(requireActivity())[InfoViewModel::class.java]

        val loginButton = view.findViewById<Button>(R.id.login_button)
        val usernameEditText = view.findViewById<EditText>(R.id.login_username)
        val passwordEditText = view.findViewById<EditText>(R.id.login_password)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            // Check if the entered username and password match the stored ones
            if (username == infoViewModel.username && password == infoViewModel.password) {
                findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            } else {
                Toast.makeText(requireContext(), "Invalid credentials", Toast.LENGTH_SHORT).show()
            }
        }

        val signupRedirectText = view.findViewById<TextView>(R.id.signupRedirectText)
        signupRedirectText.setOnClickListener {
            // Navigate to the LoginFragment
            findNavController().navigate(R.id.action_loginFragment_to_signupFragment)
        }
        return view
    }


}