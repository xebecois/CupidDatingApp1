package com.example.cupiddating // Make sure this matches your package

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class LandingPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_landing_page)

        // 1. Handle the Edge-to-Edge padding (Your existing code)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. THE NEW PART: Wait 3 seconds, then move to Login
        Handler(Looper.getMainLooper()).postDelayed({
            // Create the intent to move to the LoginPage
            // (Ensure your login class is named 'LoginPage')
            val intent = Intent(this, LoginPage::class.java)
            startActivity(intent)

            // "finish()" removes this Landing Page from the back stack
            // so the user can't go back to it by pressing the back button.
            finish()
        }, 5000) // 5000 milliseconds = 5 seconds
    }
}