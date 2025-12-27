package com.example.cupiddating

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

    private lateinit var ivProfilePhoto: ShapeableImageView
    private lateinit var btnChangePhoto: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var edtName: TextInputEditText
    private lateinit var edtMobile: TextInputEditText
    private lateinit var edtLocation: TextInputEditText
    private lateinit var edtBirthday: TextInputEditText
    private lateinit var edtGender: TextInputEditText
    private lateinit var edtBio: TextInputEditText
    private lateinit var edtInterestedIn: TextInputEditText
    private lateinit var edtAgeRange: TextInputEditText
    private lateinit var edtDistance: TextInputEditText
    private lateinit var btnConfirm: MaterialButton

    private var isPhotoSelected = false
    private var selectedImageUri: Uri? = null

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

        edtMobile.filters = arrayOf(InputFilter.LengthFilter(12))

        btnChangePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        setupBirthdayFormatting()
        setupPopupPicker(edtInterestedIn, arrayOf("Male", "Female", "Non-binary"))
        setupPopupPicker(edtGender, arrayOf("Male", "Female", "Non-binary", "Prefer not to say"))

        btnConfirm.setOnClickListener {
            if (validateInputs()) uploadImageToStorage()
            else Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBirthdayFormatting() {
        var isUpdating = false
        edtBirthday.addTextChangedListener(object : android.text.TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) { isUpdating = false; return }
                if (s.toString() != current) {
                    val clean = s.toString().replace("[^\\d]".toRegex(), "")
                    val formatted = StringBuilder()
                    for (i in clean.indices) {
                        formatted.append(clean[i])
                        if ((i == 1 || i == 3) && i != clean.length - 1) formatted.append("/")
                    }
                    current = formatted.toString()
                    isUpdating = true
                    edtBirthday.setText(current)
                    edtBirthday.setSelection(current.length)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupPopupPicker(view: EditText, options: Array<String>) {
        view.setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            options.forEach { popup.menu.add(it) }
            popup.setOnMenuItemClickListener { item ->
                view.setText(item.title)
                true
            }
            popup.show()
        }
    }

    private fun validateInputs(): Boolean {
        var valid = true
        if (!isPhotoSelected || selectedImageUri == null) { Toast.makeText(this, "Select photo", Toast.LENGTH_SHORT).show(); valid = false }
        if (edtName.text.isNullOrEmpty()) { edtName.error = "Required"; valid = false }
        if (edtMobile.text.isNullOrEmpty()) { edtMobile.error = "Required"; valid = false }
        if (edtLocation.text.isNullOrEmpty()) { edtLocation.error = "Required"; valid = false }
        if (!validateBirthday()) valid = false
        if (edtGender.text.isNullOrEmpty()) { Toast.makeText(this, "Select gender", Toast.LENGTH_SHORT).show(); valid = false }
        if (edtBio.text.isNullOrEmpty()) { edtBio.error = "Required"; valid = false }
        if (edtInterestedIn.text.isNullOrEmpty()) { edtInterestedIn.error = "Required"; valid = false }
        return valid
    }

    private fun validateBirthday(): Boolean {
        val birthday = edtBirthday.text.toString().trim()
        val datePattern = "^(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])/\\d{4}$".toRegex()
        if (!birthday.matches(datePattern)) { edtBirthday.error = "Use MM/DD/YYYY"; return false }
        return try {
            val sdf = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US)
            val birthDate = sdf.parse(birthday) ?: return false
            val today = Calendar.getInstance()
            val birthCal = Calendar.getInstance().apply { time = birthDate }
            var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) age--
            if (age < 18) { edtBirthday.error = "Must be 18+"; false } else true
        } catch (e: Exception) { edtBirthday.error = "Invalid Date"; false }
    }

    private fun uploadImageToStorage() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val imageUri = selectedImageUri ?: return
        progressBar.visibility = View.VISIBLE
        btnConfirm.apply { isEnabled = false; text = "Uploading..." }

        val ref = FirebaseStorage.getInstance().getReference("profile_pictures/$userId.jpg")
        ref.putFile(imageUri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { uri -> saveProfileToFirestore(uri.toString()) }
        }.addOnFailureListener {
            progressBar.visibility = View.GONE
            btnConfirm.apply { isEnabled = true; text = "Confirm" }
        }
    }

    private fun saveProfileToFirestore(imageUrl: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val profileUpdates = mapOf(
            "name" to edtName.text.toString(),
            "mobile" to edtMobile.text.toString(),
            "location" to edtLocation.text.toString(),
            "birthday" to edtBirthday.text.toString(),
            "gender" to listOf(edtGender.text.toString()),
            "interested_in" to listOf(edtInterestedIn.text.toString()),
            "bio" to edtBio.text.toString(),
            "preferences" to mapOf("age_range" to edtAgeRange.text.toString(), "distance" to (edtDistance.text.toString().toIntOrNull() ?: 0)),
            "profile_picture" to imageUrl,
            "profile_completed" to false
        )

        FirebaseFirestore.getInstance().collection("tbl_users").document(userId).set(profileUpdates, SetOptions.merge())
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                startActivity(Intent(this, Passions::class.java))
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                btnConfirm.apply { isEnabled = true; text = "Confirm" }
            }
    }
}