package com.example.multiway

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.multiway.ui.InfoViewModel




class SignupFragment : Fragment() {
    // TODO: Rename and change types of parameters



    private lateinit var infoViewModel: InfoViewModel


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        val view = inflater.inflate(R.layout.fragment_signup, container, false)
        infoViewModel = ViewModelProvider(requireActivity())[InfoViewModel::class.java]

        // UI elements
        val usernameField = view.findViewById<EditText>(R.id.signup_username)
        val passwordField = view.findViewById<EditText>(R.id.signup_password)
        val signupButton = view.findViewById<Button>(R.id.signup_button)



        // Save user data on signup button click
        signupButton.setOnClickListener {
            val username = usernameField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            findNavController().navigate(R.id.action_signupFragment_to_loginFragment)



            if (username.isNotEmpty() && password.isNotEmpty()) {
                // Couldnt get Firebase to work yet so going to store the values locally.
                //val database = FirebaseDatabase.getInstance()
                //val reference = database.getReference("users")
                infoViewModel.username = username
                infoViewModel.password = password

            } else {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT)
                    .show()
            }
        }

         return view
    }
}

                /* Check if the username already exists
                reference.child(username).get().addOnCompleteListener { task ->
                    //if (task.isSuccessful) {
                        //if (task.result.exists()) {
                            Toast.makeText(requireContext(), "Username already exists", Toast.LENGTH_SHORT).show()
                        } else {
                            // Save the new user
                            reference.child(username).setValue(password).addOnCompleteListener { saveTask ->
                                if (saveTask.isSuccessful) {
                                    Toast.makeText(requireContext(), "Signup successful!", Toast.LENGTH_SHORT).show()
                                    findNavController().navigate(R.id.action_signupFragment_to_loginFragment)
                                } else {
                                    Toast.makeText(requireContext(), "Failed to register. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                     }
                    } else {
                        Toast.makeText(requireContext(), "Database error. Try again later.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }



            }
    }
    */
