package com.example.cupiddating

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MatchesPage : AppCompatActivity() {

    private lateinit var cardContainer: LinearLayout
    private val db = FirebaseFirestore.getInstance()
    private val authUid = FirebaseAuth.getInstance().currentUser?.uid

    // Store user data maps
    private val activeMatches = ArrayList<Map<String, Any>>()
    // Store my own custom ID
    private var myCustomId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_matches_page)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupNavigation()
        cardContainer = findViewById(R.id.LL_matchPageInflater)

        // Start process: Get My ID -> Get Excluded IDs -> Fetch Users
        getMyCustomIdAndLoad()
    }

    private fun setupNavigation() {
        // --- BOTTOM NAVBAR ACTION BUTTONS ---
        val btnHome: ImageButton = findViewById(R.id.btnHome)
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnMessage: ImageButton = findViewById(R.id.btnMessages)
        btnMessage.setOnClickListener {
            val intent = Intent(this, MessagingPage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnProfile: ImageButton = findViewById(R.id.btnProfile)
        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfilePage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }

    private fun getMyCustomIdAndLoad() {
        if (authUid == null) return
        db.collection("tbl_users").document(authUid).get()
            .addOnSuccessListener { doc ->
                myCustomId = doc.getString("user_id") ?: ""
                fetchAndDisplayMatches()
            }
    }

    private fun fetchAndDisplayMatches() {
        if (myCustomId.isEmpty()) return

        // 1. Get IDs I have already liked from tbl_matches
        db.collection("tbl_matches")
            .whereEqualTo("liker_user_id", myCustomId)
            .get()
            .addOnSuccessListener { interactedDocs ->
                val excludedIds = mutableSetOf<String>()
                excludedIds.add(myCustomId)

                for (doc in interactedDocs) {
                    val likedId = doc.getString("liked_user_id")
                    if (likedId != null) excludedIds.add(likedId)
                }

                // 2. Fetch Users
                db.collection("tbl_users")
                    .limit(10) // Limit loading
                    .get()
                    .addOnSuccessListener { documents ->
                        activeMatches.clear()

                        for (document in documents) {
                            // Get Custom ID
                            val userCustomId = document.getString("user_id") ?: ""

                            // Skip if empty, self, or already liked
                            if (userCustomId.isEmpty() || excludedIds.contains(userCustomId)) {
                                continue
                            }

                            val data = document.data
                            val userMap = data.toMutableMap()
                            // Ensure "user_id" is explicitly available in the map
                            userMap["user_id"] = userCustomId
                            activeMatches.add(userMap)
                        }
                        populateGrid()
                    }
            }
    }

    private fun populateGrid() {
        val inflater = LayoutInflater.from(this)
        cardContainer.removeAllViews()
        val marginInPixels = (12 * resources.displayMetrics.density).toInt()

        for (i in activeMatches.indices step 2) {
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }

            // Left Card
            val cardLeft = inflater.inflate(R.layout.activity_item_matches_card, rowLayout, false) as CardView
            bindUserDataToCard(cardLeft, activeMatches[i])
            rowLayout.addView(cardLeft)

            // Right Card or Spacer
            if (i + 1 < activeMatches.size) {
                val cardRight = inflater.inflate(R.layout.activity_item_matches_card, rowLayout, false) as CardView
                bindUserDataToCard(cardRight, activeMatches[i + 1])
                rowLayout.addView(cardRight)
            } else {
                val spacer = View(this)
                val params = LinearLayout.LayoutParams(0, 1, 1f)
                params.setMargins(marginInPixels, marginInPixels, marginInPixels, marginInPixels)
                spacer.layoutParams = params
                rowLayout.addView(spacer)
            }
            cardContainer.addView(rowLayout)
        }
    }

    private fun bindUserDataToCard(cardView: View, userData: Map<String, Any>) {
        val txtNameAge = cardView.findViewById<TextView>(R.id.txtNameAge)
        val imgProfile = cardView.findViewById<ImageView>(R.id.imgProfile)
        val btnPass = cardView.findViewById<ImageButton>(R.id.btnPass)
        val btnLike = cardView.findViewById<ImageButton>(R.id.btnLike)

        val targetCustomId = userData["user_id"] as? String ?: return
        val name = userData["name"] as? String ?: "Unknown"
        val dob = userData["birthday"] as? String ?: ""
        val imageUrl = userData["profile_picture"] as? String ?: ""
        val age = calculateAgeFromString(dob)

        txtNameAge.text = "$name, $age"

        if (imageUrl.isNotEmpty()) {
            Glide.with(this).load(imageUrl).centerCrop().placeholder(android.R.color.darker_gray).into(imgProfile)
        } else {
            imgProfile.setBackgroundColor(Color.GRAY)
        }

        btnPass.setOnClickListener {
            Toast.makeText(this, "Passed $name", Toast.LENGTH_SHORT).show()
            animateCardRemoval(cardView) { removeItemAndRefresh(userData) }
        }

        // Only "like" available here, no superlike
        btnLike.setOnClickListener {
            saveMatchToFirestore(targetCustomId, name)
            animateCardRemoval(cardView) { removeItemAndRefresh(userData) }
        }
    }

    private fun saveMatchToFirestore(likedCustomId: String, likedName: String) {
        if (myCustomId.isEmpty()) return

        // 1. TRANSACTIONAL ID GENERATION
        // We use a counter document to prevent race conditions.
        val counterRef = db.collection("tbl_counters").document("match_counter")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)

            // Initialize if doesn't exist. Start at 9, so next is 10
            val currentCount = if (snapshot.exists()) {
                snapshot.getLong("count") ?: 9
            } else {
                9
            }

            val nextCount = currentCount + 1
            val newMatchId = String.format("match_%03d", nextCount)

            // Write new count back to counter doc
            val newData = hashMapOf("count" to nextCount)
            transaction.set(counterRef, newData)

            // Prepare Data (Only "like" for this page)
            val matchData = hashMapOf(
                "match_id" to newMatchId,
                "liker_user_id" to myCustomId,
                "liked_user_id" to likedCustomId,
                "type" to listOf("like"), // Array format
                "match_date" to FieldValue.serverTimestamp()
            )

            // Write the actual match document
            val matchRef = db.collection("tbl_matches").document(newMatchId)
            transaction.set(matchRef, matchData)

            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Liked $likedName!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to save match (Transaction)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeItemAndRefresh(userToRemove: Map<String, Any>) {
        activeMatches.remove(userToRemove)
        populateGrid()
    }

    private fun animateCardRemoval(view: View, onAnimationEnd: () -> Unit) {
        view.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(300)
            .withEndAction {
                view.visibility = View.INVISIBLE
                view.alpha = 1f
                view.scaleX = 1f; view.scaleY = 1f
                onAnimationEnd()
            }.start()
    }

    private fun calculateAgeFromString(dateString: String): Int {
        if (dateString.isEmpty()) return 18
        val format = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        return try {
            val date = format.parse(dateString) ?: return 18
            val dob = Calendar.getInstance().apply { time = date }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
            if (age < 0) 0 else age
        } catch (e: Exception) { 18 }
    }
}