package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.animation.AccelerateInterpolator
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.yuyakaido.android.cardstackview.*

class MainActivity : AppCompatActivity(), CardStackListener {

    private lateinit var cardStackView: CardStackView
    private lateinit var manager: CardStackLayoutManager
    private lateinit var adapter: CardStackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 1. Handle Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Setup Navigation Buttons
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

        // 3. INITIALIZE CARD STACK VIEW
        cardStackView = findViewById(R.id.cardStackView)

        // This manager controls the swipe physics
        manager = CardStackLayoutManager(this, this)
        manager.setStackFrom(StackFrom.None)
        manager.setVisibleCount(3) // Show 3 cards behind the top one
        manager.setTranslationInterval(8.0f)
        manager.setScaleInterval(0.95f)
        manager.setSwipeThreshold(0.3f)
        manager.setMaxDegree(20.0f)
        manager.setDirections(Direction.HORIZONTAL) // Allow Left/Right only
        manager.setCanScrollHorizontal(true)
        manager.setCanScrollVertical(false)

        cardStackView.layoutManager = manager
        adapter = CardStackAdapter()
        cardStackView.adapter = adapter

        // 4. SETUP ACTION BUTTONS (Pass / Like)
        setupButtons()

        // 5. FETCH DATA
        fetchUsersFromFirebase()
    }

    private fun setupButtons() {
        val btnPass = findViewById<ImageButton>(R.id.btn_pass)
        val btnLike = findViewById<ImageButton>(R.id.btn_like)

        btnPass.setOnClickListener {
            // Setup animation for Left Swipe (Pass)
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Left)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(AccelerateInterpolator())
                .build()
            manager.setSwipeAnimationSetting(setting)
            cardStackView.swipe() // Triggers the swipe programmatically
        }

        btnLike.setOnClickListener {
            // Setup animation for Right Swipe (Like)
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Right)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(AccelerateInterpolator())
                .build()
            manager.setSwipeAnimationSetting(setting)
            cardStackView.swipe() // Triggers the swipe programmatically
        }
    }

    private fun fetchUsersFromFirebase() {
        val db = FirebaseFirestore.getInstance()
        db.collection("tbl_users").get()
            .addOnSuccessListener { documents ->
                val usersList = ArrayList<DatingUser>()

                for (document in documents) {
                    val name = document.getString("name") ?: "Unknown"
                    val age = document.getLong("age") ?: 18
                    val job = document.getString("job") ?: "No Job Listed"
                    val matchScore = document.getLong("match_percent") ?: 50
                    val imagesList = document.get("images") as? List<String> ?: emptyList()

                    // Add to list
                    usersList.add(DatingUser(name, age, job, matchScore, imagesList))
                }

                // Update the adapter
                adapter.setUsers(usersList)
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
            }
    }

    // --- CardStackListener Required Methods ---
    override fun onCardSwiped(direction: Direction?) {
        // This calculates the index of the card that was JUST swiped
        // (topPosition points to the NEXT card, so we subtract 1)
        val position = manager.topPosition - 1

        // Safety check to prevent crashing if the list is empty
        if (position >= 0 && position < adapter.itemCount) {
            // If you want to show the specific name (Optional)
            // You would need to make your 'users' list in the adapter public or accessible
            // For now, we will just use a generic message as requested.
        }

        if (direction == Direction.Left) {
            android.widget.Toast.makeText(this, "Pass", android.widget.Toast.LENGTH_SHORT).show()
            Log.d("CardStack", "User Passed (Swiped Left)")

        } else if (direction == Direction.Right) {
            android.widget.Toast.makeText(this, "Liked!", android.widget.Toast.LENGTH_SHORT).show()
            Log.d("CardStack", "User Liked (Swiped Right)")
        }
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {}
    override fun onCardRewound() {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: android.view.View?, position: Int) {}
    override fun onCardDisappeared(view: android.view.View?, position: Int) {}
}