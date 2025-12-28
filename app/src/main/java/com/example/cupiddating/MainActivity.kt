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

    private var currentGenderFilter = "Both"
    private var currentMinAge = 18
    private var currentMaxAge = 80
    private var isSuperLikeBtnClicked = false
    private var myPassionsList = arrayListOf<String>()
    private val actionHistory = Stack<Boolean>()

    private var myCustomId: String = ""

    private val detailsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            when (result.data?.getStringExtra("ACTION")) {
                "LIKE" -> performSwipe(Direction.Right)
                "PASS" -> performSwipe(Direction.Left)
                "SUPERLIKE" -> {
                    isSuperLikeBtnClicked = true
                    performSwipe(Direction.Right)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        setupWindowInsets()
        setupNavigation()
        setupCardStack()
        setupButtons()

        fetchUsersFromFirebase("Both", 18, 80)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.btnUndo_main).setOnClickListener { undoLastAction() }
        findViewById<ImageButton>(R.id.btnFilter).setOnClickListener {
            FilterBottomSheet().show(supportFragmentManager, "FilterBottomSheet")
        }
        findViewById<ImageButton>(R.id.btnMainMatches).setOnClickListener { navigateTo(MatchesPage::class.java) }
        findViewById<ImageButton>(R.id.btnMainProfile).setOnClickListener { navigateTo(ProfilePage::class.java) }
        findViewById<ImageButton>(R.id.btnMainMessages).setOnClickListener { navigateTo(MessagingPage::class.java) }
    }

    private fun navigateTo(cls: Class<*>) {
        val intent = Intent(this, cls).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) }
        startActivity(intent)
    }

    private fun setupCardStack() {
        cardStackView = findViewById(R.id.cardStackView)
        layoutEmptyState = findViewById(R.id.layout_empty_state)

        manager = CardStackLayoutManager(this, this).apply {
            setStackFrom(StackFrom.None)
            setVisibleCount(3)
            setTranslationInterval(8.0f)
            setScaleInterval(0.95f)
            setSwipeThreshold(0.3f)
            setMaxDegree(20.0f)
            setDirections(Direction.HORIZONTAL)
            setCanScrollHorizontal(true)
            setCanScrollVertical(false)
        }

        adapter = CardStackAdapter().apply {
            onCardClick = { user ->
                val intent = Intent(this@MainActivity, MainProfileDetailsActivity::class.java).apply {
                    putExtra("userId", user.userId)
                    putExtra("name", user.name)
                    putExtra("age", user.age)
                    putExtra("location", user.location)
                    putExtra("bio", user.bio)
                    putExtra("distance", user.distance)
                    putStringArrayListExtra("passions", ArrayList(user.passions))
                    putStringArrayListExtra("images", ArrayList(user.images))
                    putStringArrayListExtra("myPassions", myPassionsList)
                }
                detailsLauncher.launch(intent)
            }
        }

        cardStackView.layoutManager = manager
        cardStackView.adapter = adapter

        findViewById<Button>(R.id.btn_refresh_profiles).setOnClickListener {
            layoutEmptyState.visibility = View.GONE
            cardStackView.visibility = View.VISIBLE
            fetchUsersFromFirebase(currentGenderFilter, currentMinAge, currentMaxAge)
        }
    }

    private fun setupButtons() {
        val prefs = getSharedPreferences("CupidPrefs", Context.MODE_PRIVATE)

        findViewById<ImageButton>(R.id.btn_pass).setOnClickListener {
            val isFirstTime = prefs.getBoolean("isFirstPass", true)
            if (isFirstTime) {
                showTutorialDialog(R.layout.dialog_tutorial_pass, Direction.Left)
                prefs.edit().putBoolean("isFirstPass", false).apply()
            } else {
                performSwipe(Direction.Left)
            }
        }

        findViewById<ImageButton>(R.id.btn_like).setOnClickListener {
            isSuperLikeBtnClicked = false
            val isFirstTime = prefs.getBoolean("isFirstLike", true)
            if (isFirstTime) {
                showTutorialDialog(R.layout.dialog_tutorial_like, Direction.Right)
                prefs.edit().putBoolean("isFirstLike", false).apply()
            } else {
                performSwipe(Direction.Right)
            }
        }

        findViewById<ImageButton>(R.id.btn_superLike).setOnClickListener {
            isSuperLikeBtnClicked = true
            performSwipe(Direction.Right)
        }
    }

    private fun performSwipe(direction: Direction) {
        val setting = SwipeAnimationSetting.Builder()
            .setDirection(direction)
            .setDuration(Duration.Normal.duration)
            .setInterpolator(AccelerateInterpolator())
            .build()
        manager.setSwipeAnimationSetting(setting)
        cardStackView.swipe()
    }

    override fun onFilterApplied(gender: String, minAge: Int, maxAge: Int) {
        currentGenderFilter = gender
        currentMinAge = minAge
        currentMaxAge = maxAge
        fetchUsersFromFirebase(gender, minAge, maxAge)
    }

    private fun fetchUsersFromFirebase(gender: String, minAge: Int, maxAge: Int) {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { myDoc ->
            val myId = myDoc.getString("user_id") ?: ""
            val myPassions = (myDoc.get("passions") as? List<String>) ?: emptyList()
            myPassionsList = ArrayList(myPassions)

            db.collection("tbl_matches").whereEqualTo("liker_user_id", myId).get().addOnSuccessListener { interactions ->
                val excludedIds = mutableSetOf(myId)
                interactions.forEach { doc -> doc.getString("liked_user_id")?.let { excludedIds.add(it) } }

                var query: Query = db.collection("tbl_users")
                if (gender != "Both") query = query.whereArrayContains("gender", gender)

                query.limit(50).get().addOnSuccessListener { docs ->
                    val usersList = ArrayList<DatingUser>()
                    for (doc in docs) {
                        val theirId = doc.getString("user_id") ?: ""
                        if (excludedIds.contains(theirId) || theirId.isEmpty()) continue

                        val age = calculateAgeFromString(doc.getString("birthday") ?: "")
                        if (age in minAge..maxAge) {
                            val theirPassions = (doc.get("passions") as? List<String>) ?: emptyList()
                            val images = arrayListOf<String>()
                            doc.getString("profile_picture")?.let { images.add(it) }
                            (doc.get("photos") as? List<String>)?.let { images.addAll(it) }

                            usersList.add(DatingUser(
                                theirId, doc.getString("name") ?: "Unknown", age,
                                doc.getString("location") ?: "No Location",
                                calculateMatchScore(myPassions, theirPassions, age, myId, myDoc),
                                images, theirPassions, doc.getString("bio") ?: "",
                                (doc.get("preferences") as? Map<*, *>)?.get("distance") as? Int ?: 0
                            ))
                        }
                    }
                    usersList.sortByDescending { it.matchPercent }
                    adapter.setUsers(usersList)
                    updateEmptyState(usersList.isEmpty())
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        cardStackView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun undoLastAction() {
        cardStackView.rewind()
        if (actionHistory.isNotEmpty() && actionHistory.pop()) {
            deleteLastMatchFromFirestore()
        }
    }

    private fun deleteLastMatchFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { doc ->
            val myId = doc.getString("user_id") ?: return@addOnSuccessListener
            db.collection("tbl_matches")
                .whereEqualTo("liker_user_id", myId)
                .orderBy("match_date", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { results ->
                    for (res in results) db.collection("tbl_matches").document(res.id).delete()
                }
        }
    }

    override fun onCardSwiped(direction: Direction?) {
        val prefs = getSharedPreferences("CupidPrefs", Context.MODE_PRIVATE)

        val user = adapter.getUser(manager.topPosition - 1) ?: return
        when (direction) {
            Direction.Right -> {
                saveLikeToFirestore(user.userId)
                actionHistory.push(true)
                isSuperLikeBtnClicked = false
            }
            Direction.Left -> actionHistory.push(false)
            else -> {}
        }

        if (manager.topPosition == adapter.itemCount) updateEmptyState(true)
    }

    private fun showTutorialDialog(layoutId: Int, direction: Direction) {
        val view = layoutInflater.inflate(layoutId, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<TextView>(R.id.btn_dialog_cancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btn_dialog_confirm).setOnClickListener {
            dialog.dismiss()
            performSwipe(direction)
        }
        dialog.show()
    }

    private fun saveLikeToFirestore(targetCustomId: String) {
        val db = FirebaseFirestore.getInstance()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("tbl_users").document(authUid).get().addOnSuccessListener { myDoc ->
            myCustomId = myDoc.getString("user_id") ?: ""

            val likeData = hashMapOf(
                "liker_user_id" to myCustomId,
                "liked_user_id" to targetCustomId,
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("tbl_likes").add(likeData).addOnSuccessListener {
                checkForMutualMatch(targetCustomId)
            }
        }
    }

    private fun checkForMutualMatch(targetId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("tbl_likes")
            .whereEqualTo("liker_user_id", targetId)
            .whereEqualTo("liked_user_id", myCustomId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {

                    val matchData = hashMapOf(
                        "user_a" to myCustomId,
                        "user_b" to targetId,
                        "created_at" to FieldValue.serverTimestamp()
                    )
                    db.collection("tbl_matches").add(matchData)
                }
            }
    }

    private fun calculateAgeFromString(dateString: String): Int {
        if (dateString.isEmpty()) return 18
        return try {
            val date = SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(dateString) ?: return 18
            val dob = Calendar.getInstance().apply { time = date }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
            age.coerceAtLeast(0)
        } catch (e: Exception) { 18 }
    }

    private fun calculateMatchScore(myInts: List<String>, theirInts: List<String>, theirAge: Int, myId: String, myDoc: DocumentSnapshot): Int {
        var score = 40.0
        score += (myInts.intersect(theirInts.toSet()).size * 15.0).coerceAtMost(60.0)
        val myAge = calculateAgeFromString(myDoc.getString("birthday") ?: "")
        val gap = kotlin.math.abs(myAge - theirAge)
        score += if (gap <= 2) 20.0 else if (gap <= 5) 10.0 else 0.0
        if (myDoc.getString("location") == "No Location") score += 0.0
        return score.coerceAtMost(100.0).toInt()
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {
        val prefs = getSharedPreferences("CupidPrefs", Context.MODE_PRIVATE)

        if (direction == Direction.Right && prefs.getBoolean("isFirstLike", true)) {
            manager.setCanScrollHorizontal(false)
            cardStackView.rewind()
            showTutorialDialog(R.layout.dialog_tutorial_like, Direction.Right)
            prefs.edit().putBoolean("isFirstLike", false).apply()
            manager.setCanScrollHorizontal(true)
        }

        if (direction == Direction.Left && prefs.getBoolean("isFirstPass", true)) {
            manager.setCanScrollHorizontal(false)
            cardStackView.rewind()
            showTutorialDialog(R.layout.dialog_tutorial_pass, Direction.Left)
            prefs.edit().putBoolean("isFirstPass", false).apply()
            manager.setCanScrollHorizontal(true)
        }
    }
    override fun onCardRewound() {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(v: View?, p: Int) {}
    override fun onCardDisappeared(v: View?, p: Int) {}
}