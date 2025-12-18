package com.example.cupiddating

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MatchesPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_matches_page)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- Navigation Logic ---
        val btnHome: ImageButton = findViewById(R.id.btnHome)
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // --- Grid Population Logic ---
        val cardContainer: LinearLayout = findViewById(R.id.LL_matchPageInflater)
        val users = listOf("Alice, 24", "Jessica, 22", "Sophia, 27", "Emma, 25")
        val inflater = LayoutInflater.from(this)

        // Loop through users two at a time
        for (i in users.indices step 2) {

            // 1. Create a horizontal row to hold two cards
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }

            // 2. Inflate and configure the LEFT card
            val cardLeft = inflater.inflate(R.layout.activity_item_matches_card, rowLayout, false)
            cardLeft.findViewById<TextView>(R.id.txtNameAge).text = users[i]
            cardLeft.findViewById<ImageView>(R.id.imgProfile).setBackgroundColor(Color.BLACK)
            rowLayout.addView(cardLeft)

            // 3. Inflate and configure the RIGHT card (if it exists)
            if (i + 1 < users.size) {
                val cardRight = inflater.inflate(R.layout.activity_item_matches_card, rowLayout, false)
                cardRight.findViewById<TextView>(R.id.txtNameAge).text = users[i + 1]
                cardRight.findViewById<ImageView>(R.id.imgProfile).setBackgroundColor(Color.BLACK)
                rowLayout.addView(cardRight)
            } else {
                // If there is an odd number, add an empty space to keep the first card at 50% width
                val spacer = android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                }
                rowLayout.addView(spacer)
            }

            // 4. Add the completed row to the ScrollView's vertical container
            cardContainer.addView(rowLayout)
        }
    }
}