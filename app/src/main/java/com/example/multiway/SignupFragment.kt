package com.example.multiway

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class SignupFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_signup, container, false)

        val loginRedirectText = view.findViewById<TextView>(R.id.loginRedirectText)
        loginRedirectText.setOnClickListener {
            findNavController().navigate(R.id.action_signupFragment_to_loginFragment)
        }


        // 🔐 Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // 🧾 Get form fields
        val nameField = view.findViewById<EditText>(R.id.signup_name)
        val emailField = view.findViewById<EditText>(R.id.signup_email)
        val passwordField = view.findViewById<EditText>(R.id.signup_password)
        val signupButton = view.findViewById<View>(R.id.signup_button)

        signupButton.setOnClickListener {
            val name = nameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val username = nameField.text.toString().trim()

            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                // 🔐 Create user
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid ?: ""
                            val database = FirebaseDatabase.getInstance()
                            val userRef = database.getReference("users").child(userId)

                            val userData = mapOf(
                                "name" to name,
                                "email" to email
                            )

                            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = FirebaseAuth.getInstance().currentUser
                                        val profileUpdates = UserProfileChangeRequest.Builder()
                                            .setDisplayName(username) // <-- Set the display name
                                            .build()
                                        user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                            // Navigate to home or show success message
                                        }
                                    }
                                }


                            // 💾 Save to database
                            userRef.setValue(userData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Signup successful!", Toast.LENGTH_SHORT).show()
                                    findNavController().navigate(R.id.action_signupFragment_to_loginFragment)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Signup worked, but DB save failed: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                        } else {
                            Toast.makeText(context, "Signup failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}
