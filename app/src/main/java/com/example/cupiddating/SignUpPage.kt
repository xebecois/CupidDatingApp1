// SignUpPage.kt

package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class SignUpPage : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up_page)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- WIDGET INITIALIZATION ---
        val edt_signUpEmail: EditText = findViewById(R.id.edt_signUpEmail)
        val edt_signUpPass: EditText = findViewById(R.id.edt_signUpPass)
        val btn_signUp: Button = findViewById(R.id.btn_signUp)
        val btn_isLogin: Button = findViewById(R.id.btn_isLogin)
        val btn_googleSignUp: Button = findViewById(R.id.btn_googleSignUp)

        // --- FIREBASE SETUP ---
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // --- GOOGLE SIGN-IN SETUP ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleClient = GoogleSignIn.getClient(this, gso)

        // --- GOOGLE RESULT LAUNCHER ---
        val googleLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        // Check if this is a NEW user before overwriting data
                        val userId = auth.currentUser!!.uid
                        val docRef = db.collection("tbl_users").document(userId)

                        docRef.get().addOnSuccessListener { document ->
                            if (!document.exists()) {
                                // Only generate ID and save if user is NEW
                                val name = auth.currentUser?.displayName ?: "No Name"
                                val email = auth.currentUser?.email ?: ""
                                saveUserWithSequentialId(userId, email, name)
                            } else {
                                // User exists, just login (and update login time)
                                docRef.update("last_login", FieldValue.serverTimestamp())
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign Up Failed", Toast.LENGTH_SHORT).show()
            }
        }

        // --- BUTTON LISTENERS ---

        btn_googleSignUp.setOnClickListener {
            googleLauncher.launch(googleClient.signInIntent)
        }

        btn_signUp.setOnClickListener {
            val email = edt_signUpEmail.text.toString().trim()
            val password = edt_signUpPass.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val userId = auth.currentUser!!.uid
                    // Call the helper function to generate ID and Save
                    saveUserWithSequentialId(userId, email, null)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Sign Up Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btn_isLogin.setOnClickListener {
            val intent = Intent(this, LoginPage::class.java)
            startActivity(intent)
            finish()
        }
    }

    // --- HELPER FUNCTION: GENERATE SEQUENTIAL ID & SAVE ---
    private fun saveUserWithSequentialId(userId: String, email: String, name: String?) {
        // 1. Get the current count of users to determine the next number
        db.collection("tbl_users")
            .get()
            .addOnSuccessListener { documents ->

                // Get current count (e.g., 30) and add 1 (e.g., 31)
                val currentCount = documents.size()
                val nextCount = currentCount + 1

                // Format the ID: "user_" + 3 digits (e.g., "user_031")
                val formattedUserId = String.format("user_%03d", nextCount)

                // 2. Prepare the data
                val userData = hashMapOf(
                    "user_id" to formattedUserId,   // The custom sequential ID
                    "email" to email,
                    "profile_completed" to false,
                    "role" to "user",
                    "created_at" to FieldValue.serverTimestamp(), // NEW: Creation timestamp
                    "last_login" to FieldValue.serverTimestamp()  // NEW: Initial login timestamp
                )
                // Add name only if it exists (for Google Sign Up)
                if (name != null) {
                    userData["name"] = name
                }

                // 3. Save to Firestore using the AUTH UID as the document key
                // (This ensures your Login page works correctly)
                db.collection("tbl_users").document(userId).set(userData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Account Created:", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, ProfileDetails::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Database Error", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to get user count", Toast.LENGTH_SHORT).show()
            }
    }
}