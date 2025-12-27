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

class ProfilePage : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient
    private lateinit var ivProfile: ImageView
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
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
