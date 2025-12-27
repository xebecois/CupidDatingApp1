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
        val toggleIntIn = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleInterestedIn)
        val tvSelectedInterests = view.findViewById<TextView>(R.id.tvSelectedInterests)

        userId?.let { id ->
            db.collection("tbl_users").document(id).get().addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                editBio.setText(doc.getString("bio"))
                editLocation.setText(doc.getString("location"))
                editMobile.setText(doc.getString("mobile"))

                val passions = doc.get("passions") as? List<String> ?: emptyList()
                userSelectedInterests.clear()
                userSelectedInterests.addAll(passions)
                allInterests.forEachIndexed { i, interest -> selectedItems[i] = passions.contains(interest) }
                updateInterestsUI(tvSelectedInterests)

                val gender = (doc.get("gender") as? List<*>)?.firstOrNull()?.toString()
                toggleGender.check(when(gender) {
                    "Male" -> R.id.btnMale
                    "Female" -> R.id.btnFemale
                    "Non-Binary" -> R.id.btnNonBi
                    else -> View.NO_ID
                })

                val interestedIn = (doc.get("interested_in") as? List<*>)?.firstOrNull()?.toString()
                toggleIntIn.check(when(interestedIn) {
                    "Male" -> R.id.btnIntMale
                    "Female" -> R.id.btnIntFemale
                    "Non-Binary" -> R.id.btnIntNonBi
                    else -> View.NO_ID
                })
            }
        }

        view.findViewById<LinearLayout>(R.id.containerInterests).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Passions")
                .setMultiChoiceItems(allInterests, selectedItems) { _, which, isChecked ->
                    selectedItems[which] = isChecked
                    val passion = allInterests[which]
                    if (isChecked) userSelectedInterests.add(passion) else userSelectedInterests.remove(passion)
                }
                .setPositiveButton("OK") { _, _ -> updateInterestsUI(tvSelectedInterests) }
                .show()
        }

        view.findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Save Changes")
                .setMessage("Are you sure you want to save these changes?")
                .setPositiveButton("Confirm") { _, _ ->
                    val updates = hashMapOf(
                        "bio" to editBio.text.toString(),
                        "location" to editLocation.text.toString(),
                        "mobile" to editMobile.text.toString(),
                        "gender" to listOf(when(toggleGender.checkedButtonId) {
                            R.id.btnFemale -> "Female"
                            R.id.btnNonBi -> "Non-Binary"
                            else -> "Male"
                        }),
                        "interested_in" to listOf(when(toggleIntIn.checkedButtonId) {
                            R.id.btnIntMale -> "Male"
                            R.id.btnIntNonBi -> "Non-Binary"
                            else -> "Female"
                        }),
                        "passions" to userSelectedInterests
                    )

                    userId?.let { id ->
                        db.collection("tbl_users").document(id).update(updates).addOnSuccessListener {
                            Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                            (activity as? ProfilePage)?.loadUserProfile()
                            dismiss()
                        }
                    }
                }
                .setNegativeButton("Discard") { _, _ -> dismiss() }
                .setNeutralButton("Cancel", null)
                .show()
        }
    }

    private fun updateInterestsUI(tv: TextView) {
        tv.text = userSelectedInterests.ifEmpty { listOf("Select your passions...") }.joinToString(", ")
    }
}