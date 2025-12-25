package com.example.cupiddating

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import com.google.firebase.firestore.SetOptions

class ProfileDetails : AppCompatActivity() {

    // 1. Declare UI variables
    private lateinit var ivProfilePhoto: ShapeableImageView
    private lateinit var btnChangePhoto: ImageButton

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

    // Image Picker Launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            ivProfilePhoto.setImageURI(uri)
            isPhotoSelected = true // Mark photo as selected
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

        // 3. Setup Action Listeners
        setupImagePicker()
        setupBirthdayPicker()
        setupGenderPicker()
        setupNavigation()
    }

    private fun setupImagePicker() {
        btnChangePhoto.setOnClickListener {
            // Launches the gallery to pick an image
            pickImageLauncher.launch("image/*")
        }
    }

    private fun setupBirthdayPicker() {
        edtBirthday.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    // Month is 0-indexed, so we add 1
                    val formattedDate = "${selectedMonth + 1}/$selectedDay/$selectedYear"
                    edtBirthday.setText(formattedDate)
                },
                year, month, day
            )
            datePickerDialog.show()
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

    // 4. Helper function to check if inputs are valid
    private fun validateInputs(): Boolean {
        var isValid = true

        // Check Photo
        if (!isPhotoSelected) {
            Toast.makeText(this, "Please select a profile photo", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // Check Text Fields (Set error if empty)
        if (edtName.text.isNullOrEmpty()) {
            edtName.error = "Name is required"
            isValid = false
        }
        if (edtMobile.text.isNullOrEmpty()) {
            edtMobile.error = "Mobile No. is required"
            isValid = false
        }
        if (edtLocation.text.isNullOrEmpty()) {
            edtLocation.error = "Location is required"
            isValid = false
        }
        if (edtBirthday.text.isNullOrEmpty() || edtBirthday.text.toString() == "mm/dd/yyyy") {
            // Check if still default or empty
            if(edtBirthday.text.toString() == "mm/dd/yyyy") {
                Toast.makeText(this, "Please select your birthday", Toast.LENGTH_SHORT).show()
            } else {
                edtBirthday.error = "Birthday is required"
            }
            isValid = false
        }
        if (edtGender.text.isNullOrEmpty()) {
            // Since focusing is disabled, error icon might not show well, so we use Toast fallback if needed
            Toast.makeText(this, "Please select your gender", Toast.LENGTH_SHORT).show()
            isValid = false
        }
        if (edtBio.text.isNullOrEmpty()) {
            edtBio.error = "Bio is required"
            isValid = false
        }
        if (edtInterestedIn.text.isNullOrEmpty()) {
            edtInterestedIn.error = "Required"
            isValid = false
        }
        if (edtAgeRange.text.isNullOrEmpty()) {
            edtAgeRange.error = "Required"
            isValid = false
        }
        if (edtDistance.text.isNullOrEmpty()) {
            edtDistance.error = "Required"
            isValid = false
        }

        return isValid
    }

    private fun saveProfileToFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val profileUpdates = mapOf(
            "name" to edtName.text.toString(),
            "mobile" to edtMobile.text.toString(),
            "location" to edtLocation.text.toString(),
            "birthday" to edtBirthday.text.toString(),
            "gender" to edtGender.text.toString(),
            "bio" to edtBio.text.toString(),
            "profileCompleted" to true
        )

        // Use set with Merge so we don't overwrite the email/role from SignUp
        db.collection("tbl_users").document(userId).set(profileUpdates, SetOptions.merge())
            .addOnSuccessListener {
                val intent = Intent(this, Passions::class.java)
                startActivity(intent)
                finish()
            }
    }
    private fun setupNavigation() {
        btnConfirm.setOnClickListener {
            if (validateInputs()) {
                // Call the save function instead of just starting an intent
                saveProfileToFirestore()
            } else {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
}