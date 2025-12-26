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
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val container = findViewById<LinearLayout>(R.id.LL_messagePageInflater)
        container.removeAllViews()

        // 1. Get YOUR real user_id field first
        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { myDoc ->
            val myRealIdField = myDoc.getString("user_id") ?: ""
            if (myRealIdField.isEmpty()) return@addOnSuccessListener

            // 2. Query matches where YOU are either user_1 or user_2
            // We run two queries to cover both possible slots in the match document
            val queries = listOf(
                db.collection("tbl_matches").whereEqualTo("user_id_1", myRealIdField),
                db.collection("tbl_matches").whereEqualTo("user_id_2", myRealIdField)
            )

            queries.forEach { query ->
                query.get().addOnSuccessListener { documents ->
                    for (doc in documents) {
                        val u1 = doc.getString("user_id_1") ?: ""
                        val u2 = doc.getString("user_id_2") ?: ""

                        // The "other" person is the one who ISN'T you
                        val otherUserId = if (u1 == myRealIdField) u2 else u1

                        if (otherUserId.isNotEmpty()) {
                            loadMatchUI(otherUserId, container, myRealIdField)
                        }
                    }
                }
            }
        }
    }

    private fun loadMatchUI(otherUserId: String, container: LinearLayout, myId: String) {
        val db = FirebaseFirestore.getInstance()

        // Query for the user document where the FIELD "user_id" matches
        db.collection("tbl_users")
            .whereEqualTo("user_id", otherUserId)
            .get()
            .addOnSuccessListener { userDocs ->
                if (userDocs.isEmpty) return@addOnSuccessListener

                val userDoc = userDocs.documents[0]
                val name = userDoc.getString("name") ?: "User"
                val imageUrl = userDoc.getString("profile_picture") ?: ""

                // Inflate row
                val view = layoutInflater.inflate(R.layout.item_message_row, container, false)

                // Set Data
                view.findViewById<TextView>(R.id.tv_match_name).text = name
                val profileImage = view.findViewById<ImageView>(R.id.iv_match_profile)

                if (imageUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(this).load(imageUrl).into(profileImage)
                }

                // Optional: Add "Say hi!" as default last message
                view.findViewById<TextView>(R.id.tv_match_last_msg).text = "New Match! Say hi 👋"

                // Click to Chat (Pass the other person's ID)
                view.setOnClickListener {
                    // We pass the IDs we gathered earlier
                    val chatDialog = LayoutChatDialog(otherUserId, name, myId)
                    chatDialog.show(supportFragmentManager, "LayoutChatDialog")
                }

                container.addView(view)
            }
    }

}