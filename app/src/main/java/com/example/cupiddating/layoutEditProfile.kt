package com.example.cupiddating

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class layoutEditProfile : BottomSheetDialogFragment() {

    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    private val allInterests = arrayOf(
        "Photography", "Shopping", "Karaoke", "Yoga", "Cooking",
        "Tennis", "Run", "Swimming", "Art", "Traveling",
        "Extreme sports", "Music", "Drink", "Video games"
    )
    private var selectedItems = BooleanArray(allInterests.size)
    private val userSelectedInterests = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_layout_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editBio = view.findViewById<EditText>(R.id.editBio)
        val editLocation = view.findViewById<EditText>(R.id.editLocation)
        val editMobile = view.findViewById<EditText>(R.id.editMobile)
        val toggleGender = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGender)
        val toggleInterestedIn = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleInterestedIn)
        val containerInterests = view.findViewById<LinearLayout>(R.id.containerInterests)
        val tvSelectedInterests = view.findViewById<TextView>(R.id.tvSelectedInterests)
        val btnSave = view.findViewById<Button>(R.id.btnSaveProfile)

        // --- CRITICAL ADDITION: LOAD EXISTING DATA ---
        userId?.let { id ->
            db.collection("tbl_users").document(id).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    editBio.setText(doc.getString("bio"))
                    editLocation.setText(doc.getString("location"))
                    editMobile.setText(doc.getString("mobile"))

                    // Load Passions and sync the BooleanArray
                    val passions = doc.get("passions") as? List<String> ?: emptyList()
                    userSelectedInterests.clear()
                    userSelectedInterests.addAll(passions)

                    allInterests.forEachIndexed { index, interest ->
                        selectedItems[index] = userSelectedInterests.contains(interest)
                    }
                    updateInterestsUI(tvSelectedInterests)

                    // Pre-check Gender Toggles
                    val genderList = doc.get("gender") as? List<*>
                    val gender = genderList?.firstOrNull()?.toString()
                    when(gender) {
                        "Male" -> toggleGender.check(R.id.btnMale)
                        "Female" -> toggleGender.check(R.id.btnFemale)
                        "Non-Binary" -> toggleGender.check(R.id.btnNonBi)
                    }

                    // Pre-check Interested In Toggles
                    val intList = doc.get("interested_in") as? List<*>
                    val interestedIn = intList?.firstOrNull()?.toString()
                    when(interestedIn) {
                        "Male" -> toggleInterestedIn.check(R.id.btnIntMale)
                        "Female" -> toggleInterestedIn.check(R.id.btnIntFemale)
                        "Non-Binary" -> toggleInterestedIn.check(R.id.btnIntNonBi)
                    }
                }
            }
        }

        // Passions Dialog Listener
        containerInterests.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Passions")
                .setMultiChoiceItems(allInterests, selectedItems) { _, which, isChecked ->
                    selectedItems[which] = isChecked
                    val passion = allInterests[which]
                    if (isChecked) {
                        if (!userSelectedInterests.contains(passion)) userSelectedInterests.add(passion)
                    } else {
                        userSelectedInterests.remove(passion)
                    }
                }
                .setPositiveButton("OK") { _, _ ->
                    updateInterestsUI(tvSelectedInterests)
                }
                .show()
        }

        // Save Changes Listener
        btnSave.setOnClickListener {
            val genderSelection = when (toggleGender.checkedButtonId) {
                R.id.btnMale -> "Male"
                R.id.btnFemale -> "Female"
                R.id.btnNonBi -> "Non-Binary"
                else -> "Male"
            }

            val interestedInSelection = when (toggleInterestedIn.checkedButtonId) {
                R.id.btnIntMale -> "Male"
                R.id.btnIntFemale -> "Female"
                R.id.btnIntNonBi -> "Non-Binary"
                else -> "Female"
            }

            val updates = hashMapOf<String, Any>(
                "bio" to editBio.text.toString(),
                "location" to editLocation.text.toString(),
                "mobile" to editMobile.text.toString(),
                "gender" to listOf(genderSelection),
                "interested_in" to listOf(interestedInSelection),
                "passions" to userSelectedInterests
            )

            userId?.let { id ->
                db.collection("tbl_users").document(id)
                    // CHANGE THIS LINE: Use .update() instead of .set()
                    .update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                        (activity as? ProfilePage)?.loadUserProfile()
                        dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun updateInterestsUI(tv: TextView) {
        tv.text = if (userSelectedInterests.isEmpty()) "Select your passions..."
        else userSelectedInterests.joinToString(", ")
    }
}