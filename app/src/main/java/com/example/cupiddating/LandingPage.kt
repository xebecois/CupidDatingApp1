// LandingPage.kt

package com.example.cupiddating

import android.content.Intent
import android.os.*
import android.widget.Toast
import androidx.core.view.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LandingPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_landing_page)

        // 1. Handle Edge-to-Edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Wait 5 seconds, THEN check session validity
        Handler(Looper.getMainLooper()).postDelayed({

            checkSessionAndRedirect()

        }, 5000)
    }

    private fun checkSessionAndRedirect() {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // --- TIME CHECK LOGIC ---
            val lastSignInTime = currentUser.metadata?.lastSignInTimestamp ?: 0L
            val currentTime = System.currentTimeMillis()

            // 12 hours in milliseconds = 12 * 60 * 60 * 1000 = 43,200,000
            val sessionTimeout = 12 * 60 * 60 * 1000L

            if (currentTime - lastSignInTime > sessionTimeout) {
                // Session Expired: Force logout
                auth.signOut()
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                goToLogin()
            } else {
                // Session Valid: Go to Main
                goToMain()
            }
        } else {
            // No user found: Go to Login
            goToLogin()
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginPage::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}