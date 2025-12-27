package com.example.cupiddating

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.google.android.gms.auth.api.signin.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class ProfilePage : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var ivProfile: ImageView
    private lateinit var photosContainer: GridLayout
    private var isDeleteMode = false
    private var selectedPhotoIndex: Int = -1
    private var currentPhotosList = mutableListOf<String>()

    private val PICK_IMAGE_REQUEST = 1
    private val PICK_GALLERY_IMAGE_REQUEST = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile_page)
        setupWindowInsets()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        photosContainer = findViewById(R.id.photosContainer)
        ivProfile = findViewById(R.id.profileImage)

        ivProfile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
        }

        findViewById<ImageButton>(R.id.imageButton).setOnClickListener { btn ->
            isDeleteMode = !isDeleteMode
            (btn as ImageButton).setColorFilter(if (isDeleteMode) android.graphics.Color.RED else 0)
            if (isDeleteMode) Toast.makeText(this, "Select a photo to delete", Toast.LENGTH_SHORT).show()
            displayPhotosGrid()
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<ImageButton>(R.id.edtProfileBtn).setOnClickListener {
            layoutEditProfile().show(supportFragmentManager, "layoutEditProfile")
        }

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
        }

        findViewById<ImageButton>(R.id.btnMatches).setOnClickListener {
            startActivity(Intent(this, MatchesPage::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
        }

        findViewById<ImageButton>(R.id.btnMessages).setOnClickListener {
            startActivity(Intent(this, MessagingPage::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
        }

        findViewById<Button>(R.id.logout).setOnClickListener {
            auth.signOut()
            googleSignInClient.signOut().addOnCompleteListener {
                startActivity(Intent(this, LoginPage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }

        loadUserProfile()
    }

    fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("tbl_users").document(userId).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener

            val name = doc.getString("name") ?: "User"
            val birthday = doc.getString("birthday") ?: ""
            findViewById<TextView>(R.id.profileNameAge).text = "$name, ${calculateAge(birthday)}"
            findViewById<TextView>(R.id.profileLocation).text = doc.getString("location")
            findViewById<TextView>(R.id.profileBirthday).text = birthday
            findViewById<TextView>(R.id.profileMobileNo).text = doc.getString("mobile")
            findViewById<TextView>(R.id.profileBio).text = doc.getString("bio")
            findViewById<TextView>(R.id.profileGender).text = (doc.get("gender") as? List<*>)?.joinToString(", ") ?: "Not specified"
            findViewById<TextView>(R.id.profileInterestedIn).text = (doc.get("interested_in") as? List<*>)?.joinToString(", ") ?: "Not specified"
            findViewById<TextView>(R.id.profilePassions).text = (doc.get("passions") as? List<*>)?.joinToString(" • ") ?: "None"

            doc.getString("profile_picture")?.let { if (it.isNotEmpty()) displayImage(it) }
            currentPhotosList = (doc.get("photos") as? List<String> ?: emptyList()).toMutableList()
            displayPhotosGrid()
        }
    }

    private fun displayPhotosGrid() {
        photosContainer.removeAllViews()
        currentPhotosList.forEachIndexed { i, url -> addPhotoSlot(i, url) }
        if (currentPhotosList.size < 25) addPlusButton(currentPhotosList.size)
    }

    private fun addPhotoSlot(index: Int, url: String) {
        val frame = createBaseFrame()
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        loadPhotoIntoImageView(url, imageView)
        frame.addView(imageView)

        if (isDeleteMode) {
            val deleteIcon = ImageView(this).apply {
                val size = (24 * resources.displayMetrics.density).toInt()
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END)
                setImageResource(R.drawable.deletephoto)
                setColorFilter(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            }
            frame.addView(deleteIcon)
            frame.setOnClickListener { deletePhotoAtIndex(index) }
        } else {
            frame.setOnClickListener {
                selectedPhotoIndex = index
                startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, PICK_GALLERY_IMAGE_REQUEST)
            }
        }
        photosContainer.addView(frame)
    }

    private fun addPlusButton(index: Int) {
        val frame = createBaseFrame().apply { setBackgroundResource(R.drawable.bg_add_photo_slot) }
        val iv = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setImageResource(R.drawable.addphoto)
            setColorFilter(android.graphics.Color.parseColor("#E94057"))
            setPadding(60, 60, 60, 60)
        }
        frame.setOnClickListener {
            selectedPhotoIndex = index
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, PICK_GALLERY_IMAGE_REQUEST)
        }
        frame.addView(iv)
        photosContainer.addView(frame)
    }

    private fun createBaseFrame(): FrameLayout {
        val itemSize = (resources.displayMetrics.widthPixels - (64 * resources.displayMetrics.density).toInt()) / 3
        return FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                width = 0
                height = itemSize
                setMargins(8, 8, 8, 8)
            }
        }
    }

    private fun deletePhotoAtIndex(index: Int) {
        val uid = auth.currentUser?.uid ?: return
        currentPhotosList.removeAt(index)
        db.collection("tbl_users").document(uid).update("photos", currentPhotosList).addOnSuccessListener { displayPhotosGrid() }
    }

    private fun displayImage(url: String) {
        storage.getReferenceFromUrl(url).getBytes(5 * 1024 * 1024).addOnSuccessListener { bytes ->
            findViewById<ImageView>(R.id.profileImage).setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
    }

    private fun loadPhotoIntoImageView(url: String, iv: ImageView) {
        storage.getReferenceFromUrl(url).getBytes(5 * 1024 * 1024).addOnSuccessListener { bytes ->
            iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }.addOnFailureListener { iv.setImageResource(android.R.drawable.ic_menu_report_image) }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun calculateAge(dateString: String): Int {
        if (dateString.isEmpty()) return 0
        return try {
            val dob = Calendar.getInstance().apply { time = java.text.SimpleDateFormat("M/d/yyyy", Locale.US).parse(dateString)!! }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
            age
        } catch (e: Exception) { 0 }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        when (requestCode) {
            PICK_IMAGE_REQUEST -> {
                ivProfile.setImageURI(data.data)
                uploadImageToFirebase(data.data)
            }
            PICK_GALLERY_IMAGE_REQUEST -> uploadGalleryImage(data.data!!, selectedPhotoIndex)
        }
    }

    private fun uploadGalleryImage(uri: android.net.Uri, index: Int) {
        val uid = auth.currentUser?.uid ?: return
        val ref = storage.reference.child("users/$uid/gallery/gallery_${System.currentTimeMillis()}.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUri ->
                val url = downloadUri.toString()
                if (index < currentPhotosList.size) currentPhotosList[index] = url else currentPhotosList.add(url)
                db.collection("tbl_users").document(uid).update("photos", currentPhotosList.take(25)).addOnSuccessListener { displayPhotosGrid() }
            }
        }
    }

    private fun uploadImageToFirebase(uri: android.net.Uri?) {
        val uid = auth.currentUser?.uid ?: return
        val ref = storage.reference.child("profile_images/$uid.jpg")
        uri?.let {
            ref.putFile(it).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { dUri ->
                    db.collection("tbl_users").document(uid).update("profile_picture", dUri.toString())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }
}