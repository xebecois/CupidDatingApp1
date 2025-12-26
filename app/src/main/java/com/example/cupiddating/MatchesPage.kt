// Matches.kt

package com.example.cupiddating

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.*
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.text.SimpleDateFormat
import java.util.*

class MatchesPage : AppCompatActivity() {

    private lateinit var cardContainer: LinearLayout
    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // 1. Maintain a local list of the currently displayed users
    private val activeMatches = ArrayList<Map<String, Any>>()

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
        resetDemoDataAndLoadUsers()
    }

    private fun resetDemoDataAndLoadUsers() {
        if (currentUserId == null) return
        db.collection("tbl_matches")
            .whereEqualTo("likerId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()
                for (doc in documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().addOnCompleteListener {
                    fetchAndDisplayMatches()
                }
            }
            .addOnFailureListener {
                fetchAndDisplayMatches()
            }
    }

    private fun fetchAndDisplayMatches() {
        db.collection("tbl_users")
            .limit(4)
            .get()
            .addOnSuccessListener { documents ->
                activeMatches.clear() // Clear local list

                for (document in documents) {
                    if (document.id == currentUserId) continue

                    val data = document.data
                    val userMap = data.toMutableMap()
                    userMap["id"] = document.id
                    activeMatches.add(userMap)
                }
                // Initial Population
                populateGrid()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load matches", Toast.LENGTH_SHORT).show()
            }
    }

    // 2. Modified to use the class-level 'activeMatches' list
    private fun populateGrid() {
        val inflater = LayoutInflater.from(this)
        cardContainer.removeAllViews()

        // Calculate 12dp in pixels to match the XML margin
        val marginInPixels = (12 * resources.displayMetrics.density).toInt()

        for (i in activeMatches.indices step 2) {

            // 1. Create Row
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }

            // 2. Left Card
            val cardLeft = inflater.inflate(R.layout.activity_item_matches_card, rowLayout, false) as CardView
            bindUserDataToCard(cardLeft, activeMatches[i])
            rowLayout.addView(cardLeft)

            // 3. Right Card OR Spacer
            if (i + 1 < activeMatches.size) {
                val cardRight = inflater.inflate(R.layout.activity_item_matches_card, rowLayout, false) as CardView
                bindUserDataToCard(cardRight, activeMatches[i + 1])
                rowLayout.addView(cardRight)
            } else {
                // [FIX] Configure Spacer with exact same layout params as the Card
                val spacer = View(this)
                val params = LinearLayout.LayoutParams(0, 1, 1f) // width=0, height=dummy, weight=1

                // Apply the same 12dp margins to the spacer
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

        val targetUserId = userData["id"] as? String ?: return
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

        // --- BUTTONS ---
        btnPass.setOnClickListener {
            Toast.makeText(this, "Passed $name", Toast.LENGTH_SHORT).show()
            // 3. Pass a callback to remove item AFTER animation
            animateCardRemoval(cardView) {
                removeItemAndRefresh(userData)
            }
        }

        btnLike.setOnClickListener {
            saveMatchToFirestore(targetUserId, name, age, imageUrl)
            // 3. Pass a callback to remove item AFTER animation
            animateCardRemoval(cardView) {
                removeItemAndRefresh(userData)
            }
        }
    }

    // 4. Helper to remove from list and redraw grid
    private fun removeItemAndRefresh(userToRemove: Map<String, Any>) {
        activeMatches.remove(userToRemove)
        populateGrid() // This causes the remaining cards to shift into the empty spots
    }

    // 5. Updated animation to accept a callback action
    private fun animateCardRemoval(view: View, onAnimationEnd: () -> Unit) {
        view.animate()
            .alpha(0f)
            .scaleX(0.5f) // Added scale for better effect
            .scaleY(0.5f)
            .setDuration(300)
            .withEndAction {
                view.visibility = View.INVISIBLE
                view.alpha = 1f // Reset alpha for recycled views
                view.scaleX = 1f
                view.scaleY = 1f
                onAnimationEnd() // Trigger the refresh
            }
            .start()
    }

    private fun saveMatchToFirestore(likedUserId: String, likedName: String, likedAge: Int, likedImage: String) {
        if (currentUserId == null) return

        val matchData = hashMapOf(
            "likerId" to currentUserId,
            "likedUserId" to likedUserId,
            "likedUserName" to likedName,
            "likedUserAge" to likedAge,
            "likedUserImage" to likedImage,
            "type" to "like",
            "timestamp" to FieldValue.serverTimestamp()
        )
        val docId = "${currentUserId}_${likedUserId}"

        db.collection("tbl_matches").document(docId)
            .set(matchData)
            .addOnSuccessListener {
                Log.d("MATCH", "Match saved: $likedName")
                Toast.makeText(this, "Liked $likedName!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateAgeFromString(dateString: String): Int {
        if (dateString.isEmpty()) return 18
        val format = SimpleDateFormat("M/d/yyyy", Locale.US)
        return try {
            val date = format.parse(dateString) ?: return 18
            val dob = Calendar.getInstance()
            dob.time = date
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
            if (age < 0) 0 else age
        } catch (e: Exception) { 18 }
    }

    private fun setupNavigation() {
        val btnFilter = findViewById<ImageButton>(R.id.btnFilter)
        btnFilter.setOnClickListener {
            val filterSheet = FilterBottomSheet()
            filterSheet.show(supportFragmentManager, "FilterBottomSheet")
        }

        val btnHome: ImageButton = findViewById(R.id.btnHome)
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }
}