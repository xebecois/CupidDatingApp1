package com.example.cupiddating

import android.content.Intent
import android.os.*
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.google.firebase.auth.FirebaseAuth

class LandingPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_landing_page)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Handler(Looper.getMainLooper()).postDelayed({ checkSessionAndRedirect() }, 5000)
    }

    private fun checkSessionAndRedirect() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user != null) {
            val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
            val sessionTimeout = 12 * 60 * 60 * 1000L

            if (System.currentTimeMillis() - lastSignIn > sessionTimeout) {
                auth.signOut()
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                navigate(LoginPage::class.java)
            } else {
                navigate(MainActivity::class.java)
            }
        } else {
            navigate(LoginPage::class.java)
        }
    }

    private fun navigate(target: Class<*>) {
        startActivity(Intent(this, target))
        finish()
    }
}