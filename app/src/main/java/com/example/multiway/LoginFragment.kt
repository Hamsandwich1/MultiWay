package com.example.multiway

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        auth = FirebaseAuth.getInstance()
        prefs = requireContext().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)

        val emailEditText = view.findViewById<EditText>(R.id.login_username)
        val passwordEditText = view.findViewById<EditText>(R.id.login_password)
        val loginButton = view.findViewById<Button>(R.id.login_button)
        val signupRedirectText = view.findViewById<TextView>(R.id.signupRedirectText)
        val rememberCheckBox = view.findViewById<CheckBox>(R.id.remember_me_checkbox)

        // 🔁 Auto-login if remembered and user is already authenticated
        if (prefs.getBoolean("rememberMe", false) && auth.currentUser != null) {
            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
        }

        // 🔐 Login logic
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // ✅ Save "remember me" choice
                            prefs.edit().putBoolean("rememberMe", rememberCheckBox.isChecked).apply()

                            // ✅ Fetch user data from Realtime DB (optional)
                            val userId = auth.currentUser?.uid
                            if (userId != null) {
                                val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
                                userRef.get().addOnSuccessListener { snapshot ->
                                    val name = snapshot.child("name").value?.toString() ?: "User"
                                    Toast.makeText(context, "Welcome back, $name!", Toast.LENGTH_SHORT).show()
                                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                                }
                            } else {
                                findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                            }
                        } else {
                            Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        signupRedirectText.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_signupFragment)
        }

        return view
    }
}
