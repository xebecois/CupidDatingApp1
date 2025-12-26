package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.google.firebase.firestore.*
import com.google.firebase.auth.FirebaseAuth
import com.yuyakaido.android.cardstackview.*
import java.text.SimpleDateFormat
import java.util.*

class MessagingPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_messaging_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val btnMatches: ImageButton = findViewById(R.id.btnMatches)
        btnMatches.setOnClickListener {
            val intent = Intent(this, MatchesPage::class.java)
            // This flag brings the existing MatchesPage to front if it exists, preserving its state
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnHome: ImageButton = findViewById(R.id.btnHome)
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnProfile: ImageButton = findViewById(R.id.btnProfile)
        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfilePage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
        fetchMatches()
    }

    // Inside MessagingPage class
    private fun fetchMatches() {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        // Clear container to avoid duplicates if refreshing
        val container = findViewById<LinearLayout>(R.id.LL_messagePageInflater)
        container.removeAllViews()

        // Query 1: Where I am User 1
        db.collection("tbl_matches").whereEqualTo("user_id_1", currentUserId).get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val otherUserId = doc.getString("user_id_2") ?: continue
                    loadMatchUI(otherUserId, container)
                }
            }
        // Query 2: Where I am User 2
        db.collection("tbl_matches").whereEqualTo("user_id_2", currentUserId).get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val otherUserId = doc.getString("user_id_1") ?: continue
                    loadMatchUI(otherUserId, container)
                }
            }
    }

    private fun loadMatchUI(otherUserId: String, container: LinearLayout) {
        val db = FirebaseFirestore.getInstance()

        // 1. Get User Profile info
        db.collection("tbl_users").document(otherUserId).get().addOnSuccessListener { userDoc ->
            val name = userDoc.getString("name") ?: "User"
            val image = userDoc.getString("profile_picture") ?: ""

            // 2. Get the Latest Message
            db.collection("tbl_messages")
                .whereIn("sender_id", listOf(otherUserId, "myID")) // Logic to find conversation
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { msgDocs ->
                    val lastMsg = if (msgDocs.isEmpty) "Say hi!" else msgDocs.documents[0].getString("message")

                    // 3. Inflate row and set data
                    val view = layoutInflater.inflate(R.layout.item_message_row, container, false)
                    view.findViewById<TextView>(R.id.tv_match_name).text = name
                    view.findViewById<TextView>(R.id.tv_match_last_msg).text = lastMsg

                    // Add click listener to open Chat
                    view.setOnClickListener { /* Open ChatActivity */ }

                    container.addView(view)
                }
        }
    }

}