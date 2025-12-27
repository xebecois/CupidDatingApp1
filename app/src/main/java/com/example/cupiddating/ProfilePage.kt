package com.example.cupiddating

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.view.*

class ProfilePage : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient
    private lateinit var ivProfile: ImageView
    private lateinit var photosContainer: GridLayout
    private var isDeleteMode = false
    private val PICK_GALLERY_IMAGE_REQUEST = 2
    private var selectedPhotoIndex: Int = -1
    private var currentPhotosList = mutableListOf<String>() // To track the 4 photos
    private val PICK_IMAGE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        photosContainer = findViewById(R.id.photosContainer)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        ivProfile = findViewById(R.id.profileImage)
        ivProfile.setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
        }

        val btnDeleteToggle: ImageButton = findViewById(R.id.imageButton) // The trash icon
        btnDeleteToggle.setOnClickListener {
            isDeleteMode = !isDeleteMode // Toggle the state

            // Visual feedback: change button color when active
            if (isDeleteMode) {
                btnDeleteToggle.setColorFilter(android.graphics.Color.RED)
                Toast.makeText(this, "Select a photo to delete", Toast.LENGTH_SHORT).show()
            } else {
                btnDeleteToggle.clearColorFilter()
            }

            displayPhotosGrid() // Redraw the grid to show/hide delete icons
        }

        setupWindowInsets()
        loadUserProfile()

        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)

        val btnEdit = findViewById<ImageButton>(R.id.edtProfileBtn)
        btnEdit.setOnClickListener {
            val editSheet = layoutEditProfile()
            editSheet.show(supportFragmentManager, "layoutEditProfile")
        }

        // --- BOTTOM NAVBAR ACTION BUTTONS ---
        val btnHome: ImageButton = findViewById(R.id.btnHome)
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnMatches: ImageButton = findViewById(R.id.btnMatches)
        btnMatches.setOnClickListener {
            val intent = Intent(this, MatchesPage::class.java)
            // This flag brings the existing MatchesPage to front if it exists, preserving its state
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        val btnMessage: ImageButton = findViewById(R.id.btnMessages)
        btnMessage.setOnClickListener {
            val intent = Intent(this, MessagingPage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        // Inside onCreate, after other button setups:
        val btnLogout = findViewById<Button>(R.id.logout)
        btnLogout.setOnClickListener {
            auth.signOut()
            // This ensures the Google account picker appears again on next login
            googleSignInClient.signOut().addOnCompleteListener {
                val intent = Intent(this, LoginPage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

    }
    fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("tbl_users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // 1. Get Name and Birthday
                    val name = document.getString("name") ?: "User"
                    val birthday = document.getString("birthday") ?: ""
                    // Calculate Age
                    val age = calculateAge(birthday)
                    findViewById<TextView>(R.id.profileNameAge).text = "$name, $age"

                    // 2. Location
                    findViewById<TextView>(R.id.profileLocation).text = document.getString("location")

                    // 3. Birthday
                    findViewById<TextView>(R.id.profileBirthday).text = birthday

                    // 4. Mobile
                    findViewById<TextView>(R.id.profileMobileNo).text = document.getString("mobile")

                    // 5. Bio
                    findViewById<TextView>(R.id.profileBio).text = document.getString("bio")

                    // 6. Gender (Stored as Array/List in your DB)
                    val genderList = document.get("gender") as? List<*>
                    findViewById<TextView>(R.id.profileGender).text = genderList?.joinToString(", ") ?: "Not specified"

                    // 7. Interested In (Stored as Array/List)
                    val interestedInList = document.get("interested_in") as? List<*>
                    findViewById<TextView>(R.id.profileInterestedIn).text = interestedInList?.joinToString(", ") ?: "Not specified"

                    // 8. Passions (Stored as Array/List)
                    val passionsList = document.get("passions") as? List<*>
                    findViewById<TextView>(R.id.profilePassions).text = passionsList?.joinToString(" • ") ?: "None"

                    // 9. Profile Picture
                    val imageUrl = document.getString("profile_picture")
                    if (!imageUrl.isNullOrEmpty()) {
                        displayImage(imageUrl)
                    }
                    val photos = document.get("photos") as? List<String> ?: emptyList()
                    currentPhotosList = photos.toMutableList()
                    displayPhotosGrid()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayPhotosGrid() {
        photosContainer.removeAllViews()

        // 1. Add existing photos (they will be 1/3 width)
        for (i in currentPhotosList.indices) {
            addPhotoSlot(i, currentPhotosList[i])
        }

        // 2. Add the "Plus" button immediately after (it will also be 1/3 width)
        if (currentPhotosList.size < 25) {
            addPlusButton(currentPhotosList.size)
        }
    }

    private fun addPhotoSlot(index: Int, url: String) {
        val frame = createBaseFrame()
        val imageView = ImageView(this)
        imageView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        loadPhotoIntoImageView(url, imageView)
        frame.addView(imageView)

        // --- DELETE OVERLAY LOGIC ---
        if (isDeleteMode) {
            val deleteIcon = ImageView(this)
            val iconSize = (24 * resources.displayMetrics.density).toInt()
            val lp = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.TOP or Gravity.END)
            deleteIcon.layoutParams = lp
            deleteIcon.setImageResource(android.R.drawable.ic_menu_delete)
            deleteIcon.setColorFilter(android.graphics.Color.WHITE)
            deleteIcon.setBackgroundColor(android.graphics.Color.parseColor("#80000000")) // Semi-transparent black
            frame.addView(deleteIcon)

            frame.setOnClickListener {
                deletePhotoAtIndex(index)
            }
        } else {
            // Normal Mode: Click to change photo
            frame.setOnClickListener {
                selectedPhotoIndex = index
                val intent = Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
                startActivityForResult(intent, PICK_GALLERY_IMAGE_REQUEST)
            }
        }

        photosContainer.addView(frame)
    }

    private fun deletePhotoAtIndex(index: Int) {
        val userId = auth.currentUser?.uid ?: return
        currentPhotosList.removeAt(index)

        db.collection("tbl_users").document(userId)
            .update("photos", currentPhotosList) // Must match "photos"
            .addOnSuccessListener {
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                displayPhotosGrid()
            }
    }

    private fun addPlusButton(index: Int) {
        val frame = createBaseFrame() // Uses the new math
        frame.setBackgroundResource(R.drawable.bg_add_photo_slot)

        val imageView = ImageView(this)
        imageView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        imageView.setImageResource(R.drawable.addphoto)
        imageView.setColorFilter(android.graphics.Color.parseColor("#E94057"))
        imageView.setPadding(60, 60, 60, 60)

        frame.setOnClickListener {
            selectedPhotoIndex = index
            openImagePicker()
        }
        frame.addView(imageView)
        photosContainer.addView(frame)
    }

    private fun createBaseFrame(): FrameLayout {
        val frame = FrameLayout(this)

        // Use Spec with weight 1f to force equal distribution across the 3 columns
        val params = GridLayout.LayoutParams()

        // Column spec: undefined start index, span 1, weight 1.0
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)

        // Row spec: undefined
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED)

        // Set width to 0 so the weight (1f) takes over for horizontal sizing
        params.width = 0

        // For the height, we calculate a fixed size based on screen width to keep it square
        val screenWidth = resources.displayMetrics.widthPixels
        val horizontalMarginInPx = (64 * resources.displayMetrics.density).toInt() // 32dp left + 32dp right
        val availableWidth = screenWidth - horizontalMarginInPx
        val itemSize = availableWidth / 3

        params.height = itemSize

        // Set consistent margins
        params.setMargins(8, 8, 8, 8)
        frame.layoutParams = params

        return frame
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(intent, "Select Gallery Photo"), PICK_GALLERY_IMAGE_REQUEST)
    }

    private fun displayImage(url: String) {
        val ivProfile = findViewById<ImageView>(R.id.profileImage)
        try {
            val storageRef = storage.getReferenceFromUrl(url)
            val MAX_SIZE: Long = 5 * 1024 * 1024
            storageRef.getBytes(MAX_SIZE).addOnSuccessListener { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ivProfile.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPhotoIntoImageView(url: String, targetImageView: ImageView) {
        try {
            val storageRef = storage.getReferenceFromUrl(url)
            val MAX_SIZE: Long = 5 * 1024 * 1024
            storageRef.getBytes(MAX_SIZE).addOnSuccessListener { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                targetImageView.setImageBitmap(bitmap)
            }.addOnFailureListener {
                targetImageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        } catch (e: Exception) {
            targetImageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun calculateAge(dateString: String): Int {
        if (dateString.isEmpty()) return 0

        val format = java.text.SimpleDateFormat("M/d/yyyy", java.util.Locale.US)
        return try {
            val date = format.parse(dateString) ?: return 0
            val dob = java.util.Calendar.getInstance()
            dob.time = date

            val today = java.util.Calendar.getInstance()
            var age = today.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR)

            // If birthday hasn't happened yet this year, subtract 1
            if (today.get(java.util.Calendar.DAY_OF_YEAR) < dob.get(java.util.Calendar.DAY_OF_YEAR)) {
                age--
            }
            age
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            val imageUri = data.data
            ivProfile.setImageURI(imageUri) // Preview the image
            uploadImageToFirebase(imageUri) // Upload to Firebase
        }
        if (requestCode == PICK_GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data?.data != null) {
            uploadGalleryImage(data.data!!, selectedPhotoIndex)
        }
    }
    private fun uploadGalleryImage(uri: android.net.Uri, index: Int) {
        val userId = auth.currentUser?.uid ?: return
        val fileName = "gallery_${System.currentTimeMillis()}.jpg"
        val ref = storage.reference.child("users/$userId/gallery/$fileName")

        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUri ->
                val url = downloadUri.toString()

                if (index < currentPhotosList.size) {
                    currentPhotosList[index] = url // Replace existing
                } else {
                    currentPhotosList.add(url) // Add new if clicked the "plus"
                }

                // Enforce the 25 limit
                val finalPhotos = currentPhotosList.take(25)

                db.collection("tbl_users").document(userId)
                    .update("photos", finalPhotos)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Gallery updated!", Toast.LENGTH_SHORT).show()
                        displayPhotosGrid() // Refresh UI to show the next "plus" button
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun uploadImageToFirebase(imageUri: android.net.Uri?) {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_images/$userId.jpg")

        imageUri?.let { uri ->
            storageRef.putFile(uri).addOnSuccessListener {
                // Get the Download URL from Storage
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val newImageUrl = downloadUri.toString()

                    // Save this URL to Firestore immediately
                    db.collection("tbl_users").document(userId)
                        .update("profile_picture", newImageUrl)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Image saved to database!", Toast.LENGTH_SHORT).show()
                            loadUserProfile() // Refresh the main profile view
                        }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
