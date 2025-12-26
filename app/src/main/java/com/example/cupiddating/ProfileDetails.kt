// ProfileDetails.kt

package com.example.cupiddating

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.storage.FirebaseStorage
import java.util.Calendar

class ProfileDetails : AppCompatActivity() {

    // 1. Declare UI variables
    private lateinit var ivProfilePhoto: ShapeableImageView
    private lateinit var btnChangePhoto: ImageButton
    private lateinit var progressBar: ProgressBar

    // Form Fields
    private lateinit var edtName: TextInputEditText
    private lateinit var edtMobile: TextInputEditText
    private lateinit var edtLocation: TextInputEditText
    private lateinit var edtBirthday: TextInputEditText
    private lateinit var edtGender: TextInputEditText
    private lateinit var edtBio: TextInputEditText
    private lateinit var edtInterestedIn: TextInputEditText
    private lateinit var edtAgeRange: TextInputEditText
    private lateinit var edtDistance: TextInputEditText

    // Buttons
    private lateinit var btnConfirm: MaterialButton

    // Flag to track if user picked a photo
    private var isPhotoSelected = false
    // Variable to hold the actual image URI
    private var selectedImageUri: Uri? = null

    // Image Picker Launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            ivProfilePhoto.setImageURI(uri)
            selectedImageUri = uri
            isPhotoSelected = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile_details)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Initialize All Views
        ivProfilePhoto = findViewById(R.id.iv_profile_photo)
        btnChangePhoto = findViewById(R.id.btn_change_photo)
        progressBar = findViewById(R.id.progressBar_pfp)

        edtName = findViewById(R.id.edt_pfpName)
        edtMobile = findViewById(R.id.edt_pfpMobileNo)
        edtLocation = findViewById(R.id.edt_pfpLocation)
        edtBirthday = findViewById(R.id.edt_pfpBirthday)
        edtGender = findViewById(R.id.edt_pfpGender)
        edtBio = findViewById(R.id.edt_pfpBio)
        edtInterestedIn = findViewById(R.id.edt_pfpInterestedIn)
        edtAgeRange = findViewById(R.id.edt_pfpAgeRange)
        edtDistance = findViewById(R.id.edt_pfpDistance)
        btnConfirm = findViewById(R.id.btnConfirm_pfpDetails)

        // Limit Mobile No. to 12 characters
        edtMobile.filters = arrayOf(InputFilter.LengthFilter(12))

        // 3. Setup Action Listeners
        setupImagePicker()
        setupBirthdayPicker()
        setupGenderPicker()
        setupInterestedPicker()
        setupNavigation()
    }

    private fun setupImagePicker() {
        btnChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun setupBirthdayPicker() {
        // Add this flag to track when the code (not the user) is changing the text
        var isUpdating = false

        edtBirthday.addTextChangedListener(object : android.text.TextWatcher {
            private var current = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 1. If the update is coming from our code, skip it
                if (isUpdating) {
                    isUpdating = false
                    return
                }

                if (s.toString() != current) {
                    val clean = s.toString().replace("[^\\d]".toRegex(), "")
                    val formatted = StringBuilder()

                    for (i in clean.indices) {
                        formatted.append(clean[i])
                        // Add slashes after the 2nd (MM) and 4th (DD) digits
                        if ((i == 1 || i == 3) && i != clean.length - 1) {
                            formatted.append("/")
                        }
                    }

                    current = formatted.toString()

                    // 2. Set the flag to true before calling setText
                    isUpdating = true
                    edtBirthday.setText(current)

                    // 3. Keep the cursor at the end of the text
                    edtBirthday.setSelection(current.length)
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun validateBirthday(): Boolean {
        val birthday = edtBirthday.text.toString().trim()

        // 1. Basic empty check
        if (birthday.isEmpty()) {
            edtBirthday.error = "Birthday is required"
            return false
        }
        // 2. Format check (MM/DD/YYYY)
        val datePattern = "^(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])/\\d{4}$".toRegex()
        if (!birthday.matches(datePattern)) {
            edtBirthday.error = "Enter a valid date (MM/DD/YYYY)"
            return false
        }
        // 3. Age check (Must be 18+)
        return try {
            val sdf = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US)
            val birthDate = sdf.parse(birthday) ?: return false
            val today = Calendar.getInstance()
            val birthCalendar = Calendar.getInstance().apply { time = birthDate }

            var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            if (age < 18) {
                edtBirthday.error = "You must be at least 18 years old"
                false
            } else {
                true
            }
        } catch (e: Exception) {
            edtBirthday.error = "Invalid Date"
            false
        }
    }

    private fun setupInterestedPicker() {
        edtInterestedIn.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("Male")
            popup.menu.add("Female")
            popup.menu.add("Non-binary")

            popup.setOnMenuItemClickListener { item ->
                edtInterestedIn.setText(item.title)
                true
            }
            popup.show()
        }
    }

    private fun setupGenderPicker() {
        edtGender.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("Male")
            popup.menu.add("Female")
            popup.menu.add("Non-binary")
            popup.menu.add("Prefer not to say")

            popup.setOnMenuItemClickListener { item ->
                edtGender.setText(item.title)
                true
            }
            popup.show()
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (!isPhotoSelected || selectedImageUri == null) {
            Toast.makeText(this, "Please select a profile photo", Toast.LENGTH_SHORT).show()
            isValid = false
        }
        if (edtName.text.isNullOrEmpty()) { edtName.error = "Name is required"; isValid = false }
        if (edtMobile.text.isNullOrEmpty()) { edtMobile.error = "Mobile No. is required"; isValid = false }
        if (edtLocation.text.isNullOrEmpty()) { edtLocation.error = "Location is required"; isValid = false }
        if (!validateBirthday()) {
            isValid = false
            return isValid
        }
        if (edtGender.text.isNullOrEmpty()) { Toast.makeText(this, "Please select your gender", Toast.LENGTH_SHORT).show() ; isValid = false }
        if (edtBio.text.isNullOrEmpty()) { edtBio.error = "Bio is required"; isValid = false }
        if (edtInterestedIn.text.isNullOrEmpty()) { edtInterestedIn.error = "Required"; isValid = false }
        if (edtAgeRange.text.isNullOrEmpty()) { edtAgeRange.error = "Required"; isValid = false }
        if (edtDistance.text.isNullOrEmpty()) { edtDistance.error = "Required"; isValid = false }

        return isValid
    }

    // --- Upload Image to Firebase Storage ---
    private fun uploadImageToStorage() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val imageUri = selectedImageUri ?: return

        progressBar.visibility = View.VISIBLE
        btnConfirm.isEnabled = false
        btnConfirm.text = "Uploading..."

        val fileName = "profile_pictures/$userId.jpg"
        val storageRef = FirebaseStorage.getInstance().getReference(fileName)

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val downloadUrl = uri.toString()
                    saveProfileToFirestore(downloadUrl)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnConfirm.isEnabled = true
                btnConfirm.text = "Confirm"
                Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- Save Profile Data (Gender & InterestedIn as Arrays) ---
    private fun saveProfileToFirestore(imageUrl: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val distanceVal = edtDistance.text.toString().toIntOrNull() ?: 0

        // Get strings from EditText
        val genderString = edtGender.text.toString()
        val interestedInString = edtInterestedIn.text.toString()

        val preferencesMap = mapOf(
            "age_range" to edtAgeRange.text.toString(),
            "distance" to distanceVal
        )

        val profileUpdates = mapOf(
            "name" to edtName.text.toString(),
            "mobile" to edtMobile.text.toString(),
            "location" to edtLocation.text.toString(),
            "birthday" to edtBirthday.text.toString(),

            // --- CHANGED: Wrapped in listOf() to save as Array ---
            "gender" to listOf(genderString),
            "interested_in" to listOf(interestedInString),

            "bio" to edtBio.text.toString(),
            "preferences" to preferencesMap,
            "match_percent" to 0,
            "photos" to emptyList<String>(),
            "profile_picture" to imageUrl,
            "profile_completed" to false
        )

        db.collection("tbl_users").document(userId).set(profileUpdates, SetOptions.merge())
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                btnConfirm.isEnabled = true

                val intent = Intent(this, Passions::class.java)
                startActivity(intent)
                // Activity remains open for "Undo" functionality
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnConfirm.isEnabled = true
                btnConfirm.text = "Confirm"
                Toast.makeText(this, "Error saving profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupNavigation() {
        btnConfirm.setOnClickListener {
            if (validateInputs()) {
                uploadImageToStorage()
            } else {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
}