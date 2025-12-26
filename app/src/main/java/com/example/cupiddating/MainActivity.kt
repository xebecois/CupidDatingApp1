package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View // Import View
import android.view.animation.AccelerateInterpolator
import android.widget.Button // Import Button
import android.widget.ImageButton
import android.widget.LinearLayout // Import LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.yuyakaido.android.cardstackview.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), CardStackListener {

    private lateinit var cardStackView: CardStackView
    private lateinit var manager: CardStackLayoutManager
    private lateinit var adapter: CardStackAdapter

    // 1. Declare the Empty State Layout
    private lateinit var layoutEmptyState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

        // --- SETUP CARD STACK ---
        cardStackView = findViewById(R.id.cardStackView)
        manager = CardStackLayoutManager(this, this)
        manager.setStackFrom(StackFrom.None)
        manager.setVisibleCount(3)
        manager.setTranslationInterval(8.0f)
        manager.setScaleInterval(0.95f)
        manager.setSwipeThreshold(0.3f)
        manager.setMaxDegree(20.0f)
        manager.setDirections(Direction.HORIZONTAL)
        manager.setCanScrollHorizontal(true)
        manager.setCanScrollVertical(false)

        cardStackView.layoutManager = manager
        adapter = CardStackAdapter()
        cardStackView.adapter = adapter

        // 2. SETUP EMPTY STATE & REFRESH BUTTON
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh_profiles)

        btnRefresh.setOnClickListener {
            // Hide empty state, show cards (even if empty initially), and fetch
            layoutEmptyState.visibility = View.GONE
            cardStackView.visibility = View.VISIBLE
            fetchUsersFromFirebase()
        }

        setupButtons()
        fetchUsersFromFirebase()
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

    private fun fetchUsersFromFirebase() {
        val db = FirebaseFirestore.getInstance()
        db.collection("tbl_users").limit(30).get()
            .addOnSuccessListener { documents ->
                val usersList = ArrayList<DatingUser>()

                for (document in documents) {
                    val name = document.getString("name") ?: "Unknown"
                    val birthdayString = document.getString("birthday") ?: ""
                    val calculatedAge = calculateAgeFromString(birthdayString)
                    val location = document.getString("location") ?: "No Location"
                    val matchScore = document.getLong("match_percent")?.toInt() ?: 50
                    val profilePicture = document.getString("profile_picture") ?: ""

                    usersList.add(DatingUser(name, calculatedAge, location, matchScore, listOf(profilePicture)))
                }

                adapter.setUsers(usersList)

                // 3. LOGIC: If list is empty immediately (e.g., DB is empty)
                if (usersList.isEmpty()) {
                    cardStackView.visibility = View.GONE
                    layoutEmptyState.visibility = View.VISIBLE
                } else {
                    cardStackView.visibility = View.VISIBLE
                    layoutEmptyState.visibility = View.GONE
                }
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
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

    override fun onCardSwiped(direction: Direction?) {
        // 4. LOGIC: Check if this was the last card
        // manager.topPosition is the index of the *next* card.
        // If topPosition == itemCount, it means we have exhausted the list.
        if (manager.topPosition == adapter.itemCount) {
            cardStackView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        }

        if (direction == Direction.Left) {
            Log.d("CardStack", "User Passed")
        } else if (direction == Direction.Right) {
            Log.d("CardStack", "User Liked")
        }
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {}
    override fun onCardRewound() {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: android.view.View?, position: Int) {}
    override fun onCardDisappeared(view: android.view.View?, position: Int) {}
}