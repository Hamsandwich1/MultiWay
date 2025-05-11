// Joey Teahan - 20520316
//SignupFragment - Creates and sets up the sign up section
package com.example.multiway

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
        val policyToggle = view.findViewById<TextView>(R.id.privacy_policy_toggle)
        val policyText = view.findViewById<TextView>(R.id.privacypolicytext)
        policyText.movementMethod = ScrollingMovementMethod()

        policyToggle.setOnClickListener {
            if (policyText.visibility == View.GONE) {
                policyText.visibility = View.VISIBLE
                policyToggle.text = "Hide Privacy Policy"
            } else {
                policyText.visibility = View.GONE
                policyToggle.text = "Read Privacy Policy"
            }
        }


        // Sets up Firebase Authentication
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
            val privacyCheckbox = view.findViewById<CheckBox>(R.id.privacy_checkbox)
            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() && privacyCheckbox.isChecked) {
                // Creates the user
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

                            //Sets up the user in the database
                            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = FirebaseAuth.getInstance().currentUser
                                        val profileUpdates = UserProfileChangeRequest.Builder()
                                            //Sets the username
                                            .setDisplayName(username)
                                            .build()
                                        user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                        }
                                    }
                                }

                            userRef.setValue(userData)
                                .addOnSuccessListener {
                                    //Message that comes up when the user sucesfully signs up
                                    Toast.makeText(context, "Signup successful!", Toast.LENGTH_SHORT).show()
                                    findNavController().navigate(R.id.action_signupFragment_to_loginFragment)
                                }

                        } else {
                            //If the user cant sign up properly this message comes up
                            Toast.makeText(context, "Signup failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else if (!privacyCheckbox.isChecked) {
                //If the user does not agree to the Privacy Policy
                Toast.makeText(context, "Please agree to the Privacy Policy", Toast.LENGTH_SHORT).show()

            } else {
                //If they do not fill in all of the fields
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }

        }

        return view
    }
}
