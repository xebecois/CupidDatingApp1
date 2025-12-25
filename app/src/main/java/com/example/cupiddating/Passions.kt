package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class Passions : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_passions)

        // Handle Window Insets (Edge to Edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Initialize Views
        val btnUndo: ImageButton = findViewById(R.id.btnUndo_passions)
        val btnSkip: TextView = findViewById(R.id.btnSkip_passions)
        val btnContinue: MaterialButton = findViewById(R.id.btnContinuePassions)
        val gridLayout: GridLayout = findViewById(R.id.GL_passionChoices)

        // 2. Setup Undo Button
        // finish() closes this activity and automatically reveals the previous one (ProfileDetails)
        // This preserves the state of the previous form.
        btnUndo.setOnClickListener {
            finish()
        }

        // 3. Setup Skip Button
        // Proceed to MainActivity without validation
        btnSkip.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            // Use flags to clear the activity stack so they can't go back to onboarding
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // 4. Setup Continue Button
        // Requires at least one selection
        btnContinue.setOnClickListener {
            if (isAnyPassionSelected(gridLayout)) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Select at least one passion or press skip", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper function to check if any checkbox inside the GridLayout is checked
    private fun isAnyPassionSelected(gridLayout: GridLayout): Boolean {
        // Iterate through all children of the GridLayout
        for (i in 0 until gridLayout.childCount) {
            val view = gridLayout.getChildAt(i)
            // Check if the view is a CheckBox (or AppCompatCheckBox) and is checked
            if (view is CheckBox && view.isChecked) {
                return true
            }
        }
        return false
    }
}