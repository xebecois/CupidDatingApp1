// MainActivity.kt

package com.example.cupiddating

import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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

    // --- STATE TRACKING ---
    // Flag to distinguish between a Normal Like button click and a SuperLike button click
    // because both perform a "Right Swipe" visually.
    private var isSuperLikeBtnClicked: Boolean = false

    // Stack to track if the last swipe resulted in a DB write (Like/SuperLike) or not (Pass)
    // true = DB write occurred (need to delete), false = No DB write (just rewind)
    private val actionHistory = Stack<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- UNDO BUTTON LOGIC ---
        val btnUndoMain = findViewById<ImageButton>(R.id.btnUndo_main)
        btnUndoMain.setOnClickListener {
            undoLastAction()
        }

        // --- BOTTOM NAVBAR ACTION BUTTONS ---
        val btnFilter = findViewById<ImageButton>(R.id.btnFilter)
        btnFilter.setOnClickListener {
            val filterSheet = FilterBottomSheet()
            filterSheet.show(supportFragmentManager, "FilterBottomSheet")
        }

        val btnMatches: ImageButton = findViewById(R.id.btnMainMatches)
        btnMatches.setOnClickListener {
            val intent = Intent(this, MatchesPage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnProfile: ImageButton = findViewById(R.id.btnMainProfile)
        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfilePage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnMessage: ImageButton = findViewById(R.id.btnMainMessages)
        btnMessage.setOnClickListener {
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

        // Setting direction to HORIZONTAL allows Left (Pass) and Right (Like/Superlike)
        manager.setDirections(Direction.HORIZONTAL)
        manager.setCanScrollHorizontal(true)
        manager.setCanScrollVertical(false)

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
        val btnSuperLike = findViewById<ImageButton>(R.id.btn_superLike)

        btnPass.setOnClickListener {
            // Logic: Left Swipe -> Pass
            performSwipe(Direction.Left)
        }

        btnLike.setOnClickListener {
            // Logic: Right Swipe, NO flag -> Normal Like
            isSuperLikeBtnClicked = false
            performSwipe(Direction.Right)
        }

        btnSuperLike.setOnClickListener {
            // Logic: Right Swipe, WITH flag -> SuperLike
            // We set the flag here, so onCardSwiped knows it's special
            isSuperLikeBtnClicked = true
            performSwipe(Direction.Right)
        }
    }

    // Helper to perform swipe with animation settings
    private fun performSwipe(direction: Direction) {
        val setting = SwipeAnimationSetting.Builder()
            .setDirection(direction)
            .setDuration(Duration.Normal.duration)
            .setInterpolator(AccelerateInterpolator())
            .build()
        manager.setSwipeAnimationSetting(setting)
        cardStackView.swipe()
    }

    // --- UNDO IMPLEMENTATION ---
    private fun undoLastAction() {
        // 1. Visual Rewind (Library function)
        cardStackView.rewind()

        // 2. Logic Rewind (Database)
        if (actionHistory.isNotEmpty()) {
            val wasDbWrite = actionHistory.pop()

            // If the last action saved to DB (Like/SuperLike), we must delete it
            if (wasDbWrite) {
                deleteLastMatchFromFirestore()
            } else {
                Toast.makeText(this, "Rewind (Pass undone)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteLastMatchFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { myDoc ->
            val myCustomId = myDoc.getString("user_id") ?: return@addOnSuccessListener

            // Query specifically for the MOST RECENT match created by THIS user
            db.collection("tbl_matches")
                .whereEqualTo("liker_user_id", myCustomId)
                .orderBy("match_date", Query.Direction.DESCENDING) // Get the latest one
                .limit(1)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        // Delete the document found
                        db.collection("tbl_matches").document(document.id).delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Undid Last Match", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to undo match", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
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
        // --- TUTORIAL LOGIC START ---
        val prefs = getSharedPreferences("CupidPrefs", Context.MODE_PRIVATE)
        val hasSeenPassTutorial = prefs.getBoolean("seen_pass_tutorial", false)
        val hasSeenLikeTutorial = prefs.getBoolean("seen_like_tutorial", false)

        // Scenario 1: First time swiping Left (Pass)
        if (direction == Direction.Left && !hasSeenPassTutorial) {
            prefs.edit().putBoolean("seen_pass_tutorial", true).apply()
            cardStackView.rewind()
            showPassTutorialDialog()
            return
        }

        // Scenario 2: First time swiping Right (Like)
        if (direction == Direction.Right && !hasSeenLikeTutorial) {
            prefs.edit().putBoolean("seen_like_tutorial", true).apply()
            cardStackView.rewind()
            showLikeTutorialDialog()
            return
        }
        // --- TUTORIAL LOGIC END ---

        // Proceed with normal logic if tutorials are already seen
        val position = manager.topPosition - 1
        val swipedUser = adapter.getUser(position)

        if (swipedUser != null) {
            when (direction) {
                Direction.Right -> {
                    // It can be a "Like" or a "Superlike" depending on which button was clicked
                    if (isSuperLikeBtnClicked) {
                        saveMatchToFirestore(swipedUser, "superlike")
                        Toast.makeText(this, "Super Liked ${swipedUser.name}!", Toast.LENGTH_SHORT).show()
                        // Reset flag
                        isSuperLikeBtnClicked = false
                    } else {
                        saveMatchToFirestore(swipedUser, "like")
                        Toast.makeText(this, "Liked ${swipedUser.name}", Toast.LENGTH_SHORT).show()
                    }
                    actionHistory.push(true) // Push TRUE because we saved to DB
                }
                Direction.Left -> {
                    // Pass (logic omitted for brevity, usually just ignore or save 'pass' to excluded list)
                    actionHistory.push(false) // Push FALSE because we didn't save to DB
                }
                Direction.Top -> {
                    // Fallback in case manual UP swipe is enabled later
                    saveMatchToFirestore(swipedUser, "superlike")
                    actionHistory.push(true)
                }
                else -> {}
            }
        }

        if (manager.topPosition == adapter.itemCount) {
            cardStackView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        }
    }

    // --- DIALOG DISPLAY FUNCTIONS ---

    private fun showPassTutorialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial_pass, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_dialog_cancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btn_dialog_confirm)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            performSwipe(Direction.Left)
        }
        dialog.show()
    }

    private fun showLikeTutorialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial_like, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_dialog_cancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btn_dialog_confirm)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            performSwipe(Direction.Right)
        }
        dialog.show()
    }

    private fun saveMatchToFirestore(targetUser: DatingUser, interactionType: String) {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Get MY Custom ID
        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { myDoc ->
            val myCustomId = myDoc.getString("user_id") ?: return@addOnSuccessListener
            val targetCustomId = targetUser.userId

            // 2. TRANSACTIONAL ID GENERATION
            // We use a counter document to prevent race conditions.
            val counterRef = db.collection("tbl_counters").document("match_counter")

            db.runTransaction { transaction ->
                val snapshot = transaction.get(counterRef)

                // Initialize if doesn't exist. Start at 9, so next increment is 10
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

                // Prepare match data
                val matchData = hashMapOf(
                    "match_id" to newMatchId,
                    "liker_user_id" to myCustomId,
                    "liked_user_id" to targetCustomId,
                    "type" to listOf(interactionType),
                    "match_date" to FieldValue.serverTimestamp()
                )

                // Write the actual match document
                val matchRef = db.collection("tbl_matches").document(newMatchId)
                transaction.set(matchRef, matchData)

                // Return null or success flag if needed
                null
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to save match (Transaction)", Toast.LENGTH_SHORT).show()
                // You might want to rewind the card here in production if saving fails
            }
        }
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {}
    override fun onCardRewound() {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: View?, position: Int) {}
    override fun onCardDisappeared(view: View?, position: Int) {}
}