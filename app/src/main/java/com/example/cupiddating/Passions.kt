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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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
        val btnContinue: MaterialButton = findViewById(R.id.btnContinuePassions)
        val gridLayout: GridLayout = findViewById(R.id.GL_passionChoices)

        // 2. Setup Undo Button
        // finish() closes this activity and automatically returns to the previous one (ProfileDetails)
        btnUndo.setOnClickListener {
            finish()
        }

        // 3. Setup Continue Button
        btnContinue.setOnClickListener {
            // Collect selected items from the Grid
            val selectedPassions = getSelectedPassions(gridLayout)

            if (selectedPassions.isNotEmpty()) {
                // Save passions and mark profile as complete
                finalizeProfile(selectedPassions)
            } else {
                Toast.makeText(this, "Select at least one hobby/interest.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Helper: Iterate through Grid to get selected text ---
    private fun getSelectedPassions(gridLayout: GridLayout): List<String> {
        val selectionList = mutableListOf<String>()

        // Loop through all children of the GridLayout
        for (i in 0 until gridLayout.childCount) {
            val view = gridLayout.getChildAt(i)
            // Check if the view is a CheckBox (or AppCompatCheckBox) and is checked
            if (view is CheckBox && view.isChecked) {
                selectionList.add(view.text.toString())
            }
        }
        return selectionList
    }

    // --- Helper: Save to Firestore & Finalize ---
    private fun finalizeProfile(passions: List<String>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Prepare the data to update
        // We add the passions array AND set profile_completed to true
        val data = mapOf(
            "passions" to passions,
            "profile_completed" to true
        )

        // Use SetOptions.merge() to update the existing user document without overwriting other fields
        db.collection("tbl_users").document(userId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                if (passions.isNotEmpty()) {
                    Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
                }
                goToMainActivity()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- Helper: Navigation ---
    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        // Clear the back stack so the user cannot press "Back" to return to the Onboarding screens
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}