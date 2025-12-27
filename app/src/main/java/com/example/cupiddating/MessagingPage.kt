package com.example.cupiddating

import android.content.Intent
import android.graphics.Color
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

        // --- BOTTOM NAVBAR ACTION BUTTONS ---
        val btnHome: ImageButton = findViewById(R.id.btnHome)
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnMatches: ImageButton = findViewById(R.id.btnMatches)
        btnMatches.setOnClickListener {
            val intent = Intent(this, MatchesPage::class.java)
            // This flag brings the existing MatchesPage to front if it exists, preserving its state
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
                db.collection("tbl_matches").whereEqualTo("liker_user_id", myRealIdField),
                db.collection("tbl_matches").whereEqualTo("liked_user_id", myRealIdField)
            )

            queries.forEach { query ->
                query.get().addOnSuccessListener { documents ->
                    for (doc in documents) {
                        val u1 = doc.getString("liker_user_id") ?: ""
                        val u2 = doc.getString("liked_user_id") ?: ""

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

        db.collection("tbl_users")
            .whereEqualTo("user_id", otherUserId)
            .get()
            .addOnSuccessListener { userDocs ->
                if (userDocs.isEmpty) return@addOnSuccessListener

                val userDoc = userDocs.documents[0]
                val name = userDoc.getString("name") ?: "User"
                val imageUrl = userDoc.getString("profile_picture") ?: ""

                val view = layoutInflater.inflate(R.layout.item_message_row, container, false)
                val tvLastMsg = view.findViewById<TextView>(R.id.tv_match_last_msg)
                val tvTime = view.findViewById<TextView>(R.id.tv_match_time)
                val tvBadge = view.findViewById<TextView>(R.id.tv_match_badge)

                // Set initial name and image
                view.findViewById<TextView>(R.id.tv_match_name).text = name
                val profileImage = view.findViewById<ImageView>(R.id.iv_match_profile)
                if (imageUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(this).load(imageUrl).into(profileImage)
                }

                // Real-time listener for the last message and unread count
                db.collection("tbl_messages")
                    .addSnapshotListener { snapshots, e ->
                        if (e != null || snapshots == null) return@addSnapshotListener

                        // 1. Filter messages belonging to THIS specific conversation
                        val conversationMessages = snapshots.documents.filter { doc ->
                            val sId = doc.getString("sender_id") ?: ""
                            val rId = doc.getString("receiver_id") ?: ""
                            (sId == myId && rId == otherUserId) || (sId == otherUserId && rId == myId)
                        }.sortedByDescending { it.getTimestamp("timestamp")?.toDate() ?: Date() }

                        // 2. Get the Most Recent Message for the Preview
                        val lastMessageDoc = conversationMessages.firstOrNull()
                        if (lastMessageDoc != null) {
                            val lastMsgText = lastMessageDoc.getString("message") ?: ""
                            val senderId = lastMessageDoc.getString("sender_id") ?: ""

                            // Show "You: message" if you sent it, otherwise just the message
                            tvLastMsg.text = if (senderId == myId) "You: $lastMsgText" else lastMsgText

                            val timestamp = lastMessageDoc.getTimestamp("timestamp")?.toDate() ?: Date()
                            tvTime.text = formatShortTime(timestamp)
                        } else {
                            tvLastMsg.text = "Start a conversation"
                            tvTime.text = ""
                        }

                        // 3. Badge Logic: Count messages sent by the OTHER user that are NOT seen
                        val unreadCount = conversationMessages.count {
                            it.getString("sender_id") == otherUserId && it.getBoolean("seen") == false
                        }

                        if (unreadCount > 0) {
                            tvBadge.visibility = View.VISIBLE
                            tvBadge.text = unreadCount.toString()
                            // Optional: Make the name or message bold if unread
                            tvLastMsg.setTypeface(null, android.graphics.Typeface.BOLD)
                            tvLastMsg.setTextColor(Color.BLACK)
                        } else {
                            tvBadge.visibility = View.GONE
                            tvLastMsg.setTypeface(null, android.graphics.Typeface.NORMAL)
                            tvLastMsg.setTextColor(Color.GRAY)
                        }
                    }

                view.setOnClickListener {
                    val chatDialog = LayoutChatDialog(otherUserId, name, myId)
                    chatDialog.show(supportFragmentManager, "LayoutChatDialog")
                }
                container.addView(view)
            }
    }

    private fun formatShortTime(date: Date): String {
        val sdp = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdp.format(date)
    }


}