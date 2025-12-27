// MainActivity.kt

package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        // --- BOTTOM NAVBAR ACTION BUTTONS ---
        val btnFilter = findViewById<ImageButton>(R.id.btnFilter)
        btnFilter.setOnClickListener {
            val filterSheet = FilterBottomSheet()
            filterSheet.show(supportFragmentManager, "FilterBottomSheet")
        }

        val btnMainMatches: ImageButton = findViewById(R.id.btnMainMatches)
        btnMainMatches.setOnClickListener {
            val intent = Intent(this, MatchesPage::class.java)
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

        // --- CARD STACK SETUP ---
        cardStackView = findViewById(R.id.cardStackView)
        manager = CardStackLayoutManager(this, this)
        manager.setStackFrom(StackFrom.None)
        manager.setVisibleCount(3)
        manager.setTranslationInterval(8.0f)
        manager.setScaleInterval(0.95f)
        manager.setSwipeThreshold(0.3f)
        manager.setMaxDegree(20.0f)
        manager.setDirections(Direction.HORIZONTAL) // Note: Enable VERTICAL if you want Up swipe for Superlike
        manager.setCanScrollHorizontal(true)
        manager.setCanScrollVertical(true) // Enabled for potential Superlike

        cardStackView.layoutManager = manager
        adapter = CardStackAdapter()
        cardStackView.adapter = adapter

        // --- EMPTY STATE ---
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh_profiles)
        btnRefresh.setOnClickListener {
            layoutEmptyState.visibility = View.GONE
            cardStackView.visibility = View.VISIBLE
            fetchUsersFromFirebase(currentGenderFilter, currentMinAge, currentMaxAge)
        }

        setupButtons()

        // Initial Load
        fetchUsersFromFirebase("Both", 18, 80)
    }

    override fun onFilterApplied(gender: String, minAge: Int, maxAge: Int) {
        currentGenderFilter = gender
        currentMinAge = minAge
        currentMaxAge = maxAge
        layoutEmptyState.visibility = View.GONE
        cardStackView.visibility = View.VISIBLE
        fetchUsersFromFirebase(gender, minAge, maxAge)
    }

    private fun fetchUsersFromFirebase(gender: String, minAge: Int, maxAge: Int) {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Get MY custom user_id (e.g. user_005)
        db.collection("tbl_users").document(authUid).get()
            .addOnSuccessListener { myDoc ->
                val myRealIdField = myDoc.getString("user_id") ?: ""
                val myBirthday = myDoc.getString("birthday") ?: ""
                val myAge = calculateAgeFromString(myBirthday)
                val myLocation = myDoc.getString("location") ?: ""
                val myPassions = (myDoc.get("passions") as? List<String>) ?: emptyList()

                // 2. Get list of people I already interacted with using Custom IDs
                db.collection("tbl_matches")
                    .whereEqualTo("liker_user_id", myRealIdField)
                    .get()
                    .addOnSuccessListener { alreadyInteractedDocs ->

                        val excludedIds = mutableSetOf<String>()
                        excludedIds.add(myRealIdField)

                        for (doc in alreadyInteractedDocs) {
                            val targetId = doc.getString("liked_user_id")
                            if (targetId != null) excludedIds.add(targetId)
                        }

                        // 3. Fetch Candidates
                        var query: Query = db.collection("tbl_users")
                        if (gender != "Both") {
                            query = query.whereArrayContains("gender", gender)
                        }

                        query.limit(50).get().addOnSuccessListener { documents ->
                            val usersList = ArrayList<DatingUser>()

                            for (document in documents) {
                                // IMPORTANT: Get the custom "user_id" field (e.g., user_006)
                                val theirCustomId = document.getString("user_id") ?: ""

                                if (excludedIds.contains(theirCustomId) || theirCustomId.isEmpty()) {
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
                                        myPassions, theirPassions, myAge, calculatedAge, myLocation, location
                                    )

                                    // Pass the CUSTOM ID (theirCustomId) to the DatingUser object, NOT document.id
                                    usersList.add(
                                        DatingUser(
                                            theirCustomId,
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

    // --- LOGIC HELPERS ---
    private fun calculateAgeFromString(dateString: String): Int {
        if (dateString.isEmpty()) return 18
        val format = SimpleDateFormat("MM/dd/yyyy", Locale.US)
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

    private fun calculateMatchScore(
        myInterests: List<String>, theirInterests: List<String>,
        myAge: Int, theirAge: Int, myLocation: String, theirLocation: String
    ): Int {
        var score = 0.0
        val commonInterests = myInterests.intersect(theirInterests.toSet()).size
        score += (commonInterests * 15.0).coerceAtMost(60.0)
        val ageGap = kotlin.math.abs(myAge - theirAge)
        if (ageGap <= 2) score += 20.0 else if (ageGap <= 5) score += 10.0
        if (myLocation.equals(theirLocation, ignoreCase = true)) score += 20.0
        return (score + 40).coerceAtMost(100.0).toInt()
    }

    // --- CARD SWIPE LISTENERS ---

    override fun onCardSwiped(direction: Direction?) {
        val position = manager.topPosition - 1
        val swipedUser = adapter.getUser(position)

        if (swipedUser != null) {
            when (direction) {
                Direction.Right -> {
                    // Normal Like
                    saveMatchToFirestore(swipedUser, "like")
                    Toast.makeText(this, "Liked ${swipedUser.name}", Toast.LENGTH_SHORT).show()
                }
                Direction.Top -> {
                    // Super Like
                    saveMatchToFirestore(swipedUser, "superlike")
                    Toast.makeText(this, "Super Liked ${swipedUser.name}!", Toast.LENGTH_SHORT).show()
                }
                Direction.Left -> {
                    // Pass (logic omitted for brevity, usually just ignore or save 'pass' to excluded list)
                }
                else -> {}
            }
        }

        if (manager.topPosition == adapter.itemCount) {
            cardStackView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        }
    }

    private fun saveMatchToFirestore(targetUser: DatingUser, interactionType: String) {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Get MY Custom ID (user_005)
        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { myDoc ->
            val myCustomId = myDoc.getString("user_id") ?: return@addOnSuccessListener

            // The adapter already holds the target's custom ID (user_006)
            val targetCustomId = targetUser.userId

            // 2. Count existing matches to generate "match_010"
            db.collection("tbl_matches").get().addOnSuccessListener { matchSnapshots ->
                val nextCount = matchSnapshots.size() + 1
                val matchId = String.format("match_%03d", nextCount)

                // 3. Prepare Data strictly matching your screenshot
                val matchData = hashMapOf(
                    "match_id" to matchId,
                    "liker_user_id" to myCustomId,      // e.g. "user_005"
                    "liked_user_id" to targetCustomId,  // e.g. "user_006"
                    "type" to listOf(interactionType),  // Array: ["like"] or ["superlike"]
                    "match_date" to FieldValue.serverTimestamp() // Firestore Timestamp
                )

                // Save to tbl_matches
                db.collection("tbl_matches").document(matchId).set(matchData)
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to save match", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {}
    override fun onCardRewound() {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: View?, position: Int) {}
    override fun onCardDisappeared(view: View?, position: Int) {}
}