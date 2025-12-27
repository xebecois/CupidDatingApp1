package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.ImageButton
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val gridLayout: GridLayout = findViewById(R.id.GL_passionChoices)

        findViewById<ImageButton>(R.id.btnUndo_passions).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnContinuePassions).setOnClickListener {
            val selectedPassions = getSelectedPassions(gridLayout)
            if (selectedPassions.isNotEmpty()) {
                finalizeProfile(selectedPassions)
            } else {
                Toast.makeText(this, "Select at least one hobby/interest.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getSelectedPassions(gridLayout: GridLayout): List<String> {
        val selectionList = mutableListOf<String>()
        for (i in 0 until gridLayout.childCount) {
            val view = gridLayout.getChildAt(i)
            if (view is CheckBox && view.isChecked) {
                selectionList.add(view.text.toString())
            }
        }
        return selectionList
    }

    private fun finalizeProfile(passions: List<String>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val data = mapOf("passions" to passions, "profile_completed" to true)

        FirebaseFirestore.getInstance().collection("tbl_users").document(userId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}