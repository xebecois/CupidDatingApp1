// MainActivity.kt

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.yuyakaido.android.cardstackview.*
import java.text.SimpleDateFormat
import java.util.*

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
            // This flag brings the existing MatchesPage to front if it exists, preserving its state
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnMainProfile: ImageButton = findViewById(R.id.btnMainProfile)
        btnMainProfile.setOnClickListener {
            val intent = Intent(this, ProfilePage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnMainMessage: ImageButton = findViewById(R.id.btnMainMessages)
        btnMainMessage.setOnClickListener {
            val intent = Intent(this, MessagingPage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        // CardStackView Setup
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

        // Empty State Setup
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh_profiles)
        btnRefresh.setOnClickListener {
            layoutEmptyState.visibility = View.GONE
            cardStackView.visibility = View.VISIBLE
            fetchUsersFromFirebase(currentGenderFilter, currentMinAge, currentMaxAge)
        }

        setupButtons()

        // Initial Load (Default: Both, 18-80)
        fetchUsersFromFirebase("Both", 18, 80)
    }

    override fun onFilterApplied(gender: String, minAge: Int, maxAge: Int) {
        currentGenderFilter = gender
        currentMinAge = minAge
        currentMaxAge = maxAge

        layoutEmptyState.visibility = View.GONE
        cardStackView.visibility = View.VISIBLE

        Toast.makeText(this, "Filtering: $gender, $minAge-$maxAge", Toast.LENGTH_SHORT).show()
        fetchUsersFromFirebase(gender, minAge, maxAge)
    }

    private fun fetchUsersFromFirebase(gender: String, minAge: Int, maxAge: Int) {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Get MY Profile Details
        db.collection("tbl_users").document(authUid).get()
            .addOnSuccessListener { myDoc ->
                val myRealIdField = myDoc.getString("user_id") ?: ""
                val myBirthday = myDoc.getString("birthday") ?: ""
                val myAge = calculateAgeFromString(myBirthday)
                val myLocation = myDoc.getString("location") ?: ""
                val myPassions = (myDoc.get("passions") as? List<String>) ?: emptyList()

                // 2. Get the list of people I already LIKED/MATCHED
                db.collection("tbl_likes")
                    .whereEqualTo("liker_id", myRealIdField)
                    .get()
                    .addOnSuccessListener { alreadyInteractedDocs ->

                        // Create a set of IDs to exclude
                        val excludedIds = mutableSetOf<String>()
                        excludedIds.add(myRealIdField) // Exclude myself

                        for (doc in alreadyInteractedDocs) {
                            val targetId = doc.getString("target_id")
                            if (targetId != null) excludedIds.add(targetId)
                        }

                        // 3. Now fetch the candidate users
                        var query: Query = db.collection("tbl_users")
                        if (gender != "Both") {
                            query = query.whereArrayContains("gender", gender)
                        }

                        query.limit(50).get().addOnSuccessListener { documents ->
                            val usersList = ArrayList<DatingUser>()

                            for (document in documents) {
                                val theirIdField = document.getString("user_id") ?: ""

                                // CHECK IF THEY ARE IN THE EXCLUDED LIST
                                if (excludedIds.contains(theirIdField) || theirIdField.isEmpty()) {
                                    continue
                                }

                                val name = document.getString("name") ?: "Unknown"
                                val birthdayString = document.getString("birthday") ?: ""
                                val location = document.getString("location") ?: "No Location"
                                val profilePicture = document.getString("profile_picture") ?: ""
                                val theirPassions = (document.get("passions") as? List<String>) ?: emptyList()

                                val calculatedAge = calculateAgeFromString(birthdayString)

                                if (calculatedAge in minAge..maxAge) {
                                    val dynamicMatchPercent = calculateMatchScore(
                                        myInterests = myPassions,
                                        theirInterests = theirPassions,
                                        myAge = myAge,
                                        theirAge = calculatedAge,
                                        myLocation = myLocation,
                                        theirLocation = location
                                    )

                                    usersList.add(
                                        DatingUser(
                                            theirIdField,
                                            name,
                                            calculatedAge,
                                            location,
                                            dynamicMatchPercent,
                                            listOf(profilePicture),
                                            theirPassions
                                        )
                                    )
                                }
                            }

                            usersList.sortByDescending { it.matchPercent }
                            adapter.setUsers(usersList)

                            // Handle Empty State
                            if (usersList.isEmpty()) {
                                cardStackView.visibility = View.GONE
                                layoutEmptyState.visibility = View.VISIBLE
                            } else {
                                cardStackView.visibility = View.VISIBLE
                                layoutEmptyState.visibility = View.GONE
                            }
                        }
                    }
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
        val format = SimpleDateFormat("MM/dd/yyyy", Locale.US)
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

    private fun calculateMatchScore(
        myInterests: List<String>,
        theirInterests: List<String>,
        myAge: Int,
        theirAge: Int,
        myLocation: String,
        theirLocation: String
    ): Int {
        var score = 0.0

        // 1. COMMON PASSIONS
        val commonInterests = myInterests.intersect(theirInterests.toSet()).size
        val interestScore = (commonInterests * 15.0).coerceAtMost(60.0)
        score += interestScore

        // 2. AGE PROXIMITY
        val ageGap = kotlin.math.abs(myAge - theirAge)
        if (ageGap <= 2) {
            score += 20.0
        } else if (ageGap <= 5) {
            score += 10.0
        }

        // 3. LOCATION MATCH
        if (myLocation.equals(theirLocation, ignoreCase = true)) {
            score += 20.0
        }

        val finalScore = (score + 40).coerceAtMost(100.0)
        return finalScore.toInt()
    }

    // --- CardStackListener Implementation ---

    override fun onCardSwiped(direction: Direction?) {
        // The card that was just swiped is at (topPosition - 1)
        // because the manager has already advanced the index internally.
        val position = manager.topPosition - 1
        val swipedUser = adapter.getUser(position)

        if (swipedUser != null) {
            if (direction == Direction.Right) {
                // Swiped Right -> LIKE
                saveMatchToFirestore(swipedUser, "like")
                Toast.makeText(this, "Liked ${swipedUser.name}", Toast.LENGTH_SHORT).show()
            } else if (direction == Direction.Left) {
                // Swiped Left -> PASS
                // Optional: Save pass to DB to avoid showing them again
            }
        }

        // Check for empty state
        if (manager.topPosition == adapter.itemCount) {
            cardStackView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        }
    }

    // In MainActivity.kt, update saveMatchToFirestore:
    private fun saveMatchToFirestore(targetUser: DatingUser, type: String) {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Get YOUR user_id field first to ensure we aren't using the Document ID
        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { myDoc ->
            val myIdField = myDoc.getString("user_id") ?: return@addOnSuccessListener
            val targetIdField = targetUser.userId // This is already the field from your adapter

            val likeData = hashMapOf(
                "liker_id" to myIdField,     // This is now the field "user_id"
                "target_id" to targetIdField, // This is now the field "user_id"
                "timestamp" to FieldValue.serverTimestamp()
            )

            // Predictable Doc ID for likes (using the fields)
            val likeDocId = "${myIdField}_${targetIdField}"

            db.collection("tbl_likes").document(likeDocId).set(likeData)
                .addOnSuccessListener {
                    // Check for reverse match using fields
                    db.collection("tbl_likes")
                        .whereEqualTo("liker_id", targetIdField)
                        .whereEqualTo("target_id", myIdField)
                        .get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                createNewMatchDocument(myIdField, targetIdField)
                                Toast.makeText(this, "Match with ${targetUser.name}!", Toast.LENGTH_LONG).show()
                            }
                        }
                }
        }
    }

    private fun createNewMatchDocument(userId1: String, userId2: String) {
        val db = FirebaseFirestore.getInstance()
        val sortedIds = listOf(userId1, userId2).sorted()
        val matchDocId = "${sortedIds[0]}_${sortedIds[1]}"

        val matchData = hashMapOf(
            "match_id" to matchDocId,
            "user_id_1" to sortedIds[0], // Using the user_id fields
            "user_id_2" to sortedIds[1],
            "match_date" to FieldValue.serverTimestamp()
        )
        db.collection("tbl_matches").document(matchDocId).set(matchData)
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {}
    override fun onCardRewound() {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: View?, position: Int) {}
    override fun onCardDisappeared(view: View?, position: Int) {}
}