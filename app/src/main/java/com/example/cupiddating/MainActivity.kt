// MainActivity.kt

package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.yuyakaido.android.cardstackview.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// 1. Add "FilterBottomSheet.FilterListener" to the class definition
class MainActivity : AppCompatActivity(), CardStackListener, FilterBottomSheet.FilterListener {

    private lateinit var cardStackView: CardStackView
    private lateinit var manager: CardStackLayoutManager
    private lateinit var adapter: CardStackAdapter
    private lateinit var layoutEmptyState: LinearLayout

    // Store current filters
    private var currentGenderFilter: String = "Both"
    private var currentMinAge: Int = 18
    private var currentMaxAge: Int = 80

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // ... (Window Insets code remains the same) ...
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ... (Button setup code remains the same) ...
        val btnFilter = findViewById<ImageButton>(R.id.btnFilter)
        btnFilter.setOnClickListener {
            val filterSheet = FilterBottomSheet()
            filterSheet.show(supportFragmentManager, "FilterBottomSheet")
        }

        val btnMainMatches: ImageButton = findViewById(R.id.btnMainMatches)
        btnMainMatches.setOnClickListener {
            val intent = Intent(this, MatchesPage::class.java)
            startActivity(intent)
        }

        // ... (CardStackView setup remains the same) ...
        cardStackView = findViewById(R.id.cardStackView)
        manager = CardStackLayoutManager(this, this)
        // ... (Your existing manager settings) ...
        manager.setVisibleCount(3)
        manager.setTranslationInterval(8.0f)
        manager.setScaleInterval(0.95f)
        manager.setSwipeThreshold(0.3f)
        manager.setMaxDegree(20.0f)
        manager.setDirections(Direction.HORIZONTAL)

        cardStackView.layoutManager = manager
        adapter = CardStackAdapter()
        cardStackView.adapter = adapter

        // Empty State Setup
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh_profiles)
        btnRefresh.setOnClickListener {
            // When refreshing manually, reset filters or keep them?
            // Here we keep current filters and reload
            layoutEmptyState.visibility = View.GONE
            cardStackView.visibility = View.VISIBLE
            fetchUsersFromFirebase(currentGenderFilter, currentMinAge, currentMaxAge)
        }

        setupButtons()

        // Initial Load (Default: Both, 18-80)
        fetchUsersFromFirebase("Both", 18, 80)
    }

    // 2. Implement the interface function from FilterBottomSheet
    override fun onFilterApplied(gender: String, minAge: Int, maxAge: Int) {
        // Save state
        currentGenderFilter = gender
        currentMinAge = minAge
        currentMaxAge = maxAge

        // Reset UI
        layoutEmptyState.visibility = View.GONE
        cardStackView.visibility = View.VISIBLE

        // Fetch with new filters
        Toast.makeText(this, "Filtering: $gender, $minAge-$maxAge", Toast.LENGTH_SHORT).show()
        fetchUsersFromFirebase(gender, minAge, maxAge)
    }

    // 3. Update Fetch Logic
    private fun fetchUsersFromFirebase(gender: String, minAge: Int, maxAge: Int) {
        val db = FirebaseFirestore.getInstance()
        var query: Query = db.collection("tbl_users")

        // FILTER 1: Gender (Server-Side)
        if (gender != "Both") {
            // FIX: Use 'whereArrayContains' because 'gender' is stored as an Array in Firestore
            query = query.whereArrayContains("gender", gender)
        }

        // Note: Fetching 50 to ensure we have enough people after age filtering
        query.limit(50).get()
            .addOnSuccessListener { documents ->
                val tempUsersList = ArrayList<DatingUser>()

                for (document in documents) {
                    val name = document.getString("name") ?: "Unknown"
                    val birthdayString = document.getString("birthday") ?: ""
                    val location = document.getString("location") ?: "No Location"
                    val matchScore = document.getLong("match_percent")?.toInt() ?: 50

                    // Handle profile picture being empty or null
                    val profilePicture = document.getString("profile_picture") ?: ""

                    // Calculate Age
                    val calculatedAge = calculateAgeFromString(birthdayString)

                    // FILTER 2: Age (Client-Side)
                    if (calculatedAge in minAge..maxAge) {
                        tempUsersList.add(
                            DatingUser(name, calculatedAge, location, matchScore, listOf(profilePicture))
                        )
                    }
                }

                // Update Adapter
                adapter.setUsers(tempUsersList)

                // Check Empty State
                if (tempUsersList.isEmpty()) {
                    cardStackView.visibility = View.GONE
                    layoutEmptyState.visibility = View.VISIBLE
                    // Optional: Update empty text to be more specific
                    // findViewById<TextView>(R.id.tv_empty_message).text = "No $gender found in that age range."
                } else {
                    cardStackView.visibility = View.VISIBLE
                    layoutEmptyState.visibility = View.GONE
                }
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                Toast.makeText(this, "Error fetching users: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupButtons() {
        val btnPass = findViewById<ImageButton>(R.id.btn_pass)
        val btnLike = findViewById<ImageButton>(R.id.btn_like)

        btnPass.setOnClickListener {
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Left)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(AccelerateInterpolator())
                .build()
            manager.setSwipeAnimationSetting(setting)
            cardStackView.swipe()
        }

        btnLike.setOnClickListener {
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Right)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(AccelerateInterpolator())
                .build()
            manager.setSwipeAnimationSetting(setting)
            cardStackView.swipe()
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
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            if (age < 0) 0 else age
        } catch (e: Exception) {
            e.printStackTrace()
            18
        }
    }

    // ... (Existing CardStackListener methods: onCardSwiped, etc.) ...
    override fun onCardSwiped(direction: Direction?) {
        if (manager.topPosition == adapter.itemCount) {
            cardStackView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        }
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {}
    override fun onCardRewound() {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: View?, position: Int) {}
    override fun onCardDisappeared(view: View?, position: Int) {}
}