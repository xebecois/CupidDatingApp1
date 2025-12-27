// MessagingPage.kt

package com.example.cupiddating

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.google.firebase.firestore.*
import com.google.firebase.auth.FirebaseAuth
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

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
        }

        findViewById<ImageButton>(R.id.btnMatches).setOnClickListener {
            startActivity(Intent(this, MatchesPage::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
        }

        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfilePage::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
        }

        fetchMatches()
    }

    private fun fetchMatches() {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val container = findViewById<LinearLayout>(R.id.LL_messagePageInflater)
        container.removeAllViews()

        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { myDoc ->
            val myId = myDoc.getString("user_id") ?: ""
            if (myId.isEmpty()) return@addOnSuccessListener

            db.collection("tbl_matches")
                .whereEqualTo("liker_user_id", myId)
                .get()
                .addOnSuccessListener { myLikes ->
                    for (likeDoc in myLikes) {
                        val otherUserId = likeDoc.getString("liked_user_id") ?: ""

                        db.collection("tbl_matches")
                            .whereEqualTo("liker_user_id", otherUserId)
                            .whereEqualTo("liked_user_id", myId)
                            .get()
                            .addOnSuccessListener { reciprocalLikes ->
                                if (!reciprocalLikes.isEmpty) {
                                    loadMatchUI(otherUserId, container, myId)
                                }
                            }
                    }
                }
        }
    }

    private fun loadMatchUI(otherUserId: String, container: LinearLayout, myId: String) {
        val db = FirebaseFirestore.getInstance()

        db.collection("tbl_users").whereEqualTo("user_id", otherUserId).get().addOnSuccessListener { userDocs ->
            if (userDocs.isEmpty) return@addOnSuccessListener

            val userDoc = userDocs.documents[0]
            val name = userDoc.getString("name") ?: "User"
            val imageUrl = userDoc.getString("profile_picture") ?: ""

            val view = layoutInflater.inflate(R.layout.item_message_row, container, false)
            val tvLastMsg = view.findViewById<TextView>(R.id.tv_match_last_msg)
            val tvTime = view.findViewById<TextView>(R.id.tv_match_time)
            val tvBadge = view.findViewById<TextView>(R.id.tv_match_badge)
            val profileImage = view.findViewById<ImageView>(R.id.iv_match_profile)

            view.findViewById<TextView>(R.id.tv_match_name).text = name
            if (imageUrl.isNotEmpty()) com.bumptech.glide.Glide.with(this).load(imageUrl).into(profileImage)

            db.collection("tbl_messages").addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener

                val conversationMessages = snapshots.documents.filter { doc ->
                    val sId = doc.getString("sender_id") ?: ""
                    val rId = doc.getString("receiver_id") ?: ""
                    (sId == myId && rId == otherUserId) || (sId == otherUserId && rId == myId)
                }.sortedByDescending { it.getTimestamp("timestamp")?.toDate() ?: Date() }

                val lastMsgDoc = conversationMessages.firstOrNull()
                if (lastMsgDoc != null) {
                    val lastMsgText = lastMsgDoc.getString("message") ?: ""
                    tvLastMsg.text = if (lastMsgDoc.getString("sender_id") == myId) "You: $lastMsgText" else lastMsgText
                    tvTime.text = formatShortTime(lastMsgDoc.getTimestamp("timestamp")?.toDate() ?: Date())
                } else {
                    tvLastMsg.text = "Start a conversation"
                    tvTime.text = ""
                }

                val unreadCount = conversationMessages.count { it.getString("sender_id") == otherUserId && it.getBoolean("seen") == false }
                if (unreadCount > 0) {
                    tvBadge.apply { visibility = View.VISIBLE; text = unreadCount.toString() }
                    tvLastMsg.apply { setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(Color.BLACK) }
                } else {
                    tvBadge.visibility = View.GONE
                    tvLastMsg.apply { setTypeface(null, android.graphics.Typeface.NORMAL); setTextColor(Color.GRAY) }
                }
            }

            view.setOnClickListener { LayoutChatDialog(otherUserId, name, myId).show(supportFragmentManager, "LayoutChatDialog") }
            container.addView(view)
        }
    }

    private fun formatShortTime(date: Date): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}