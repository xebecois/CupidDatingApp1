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
import com.google.firebase.firestore.FirebaseFirestore

class SignUpPage : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up_page)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- 1. WIDGET INITIALIZATION ---
        val edt_signUpEmail: EditText = findViewById(R.id.edt_signUpEmail)
        val edt_signUpPass: EditText = findViewById(R.id.edt_signUpPass)
        val btn_signUp: Button = findViewById(R.id.btn_signUp)
        val btn_isLogin: Button = findViewById(R.id.btn_isLogin)

        // Note: I am assuming you have a Google button in your Sign Up XML.
        // If it is named differently in your XML, change "btn_googleSignUp" below.
        val btn_googleSignUp: Button = findViewById(R.id.btn_googleSignUp)

        // --- 2. FIREBASE SETUP ---
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        // --- 3. GOOGLE SIGN-IN SETUP ---
        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleClient = GoogleSignIn.getClient(this, gso)

        // --- 4. GOOGLE RESULT LAUNCHER ---
        val googleLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                // Get Google Account
                val account = task.getResult(ApiException::class.java)

                // Auth with Firebase
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        // GOOGLE SIGN UP SUCCESS
                        val user = auth.currentUser
                        val userId = user!!.uid

                        // Prepare data for Firestore
                        val userData = mapOf(
                            "name" to (user.displayName ?: "No Name"),
                            "email" to (user.email ?: ""),
                            "profileCompleted" to false // Important flag for navigation logic
                        )

                        // Save to Firestore
                        db.collection("tbl_users").document(userId).set(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Account Created", Toast.LENGTH_SHORT).show()
                                // Go to Profile Setup
                                val intent = Intent(this, ProfileDetails::class.java)
                                startActivity(intent)
                                finish()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show()
                    }

            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign Up Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // --- 5. BUTTON LISTENERS ---

        // A. Google Sign Up Button
        btn_googleSignUp.setOnClickListener {
            googleLauncher.launch(googleClient.signInIntent)
        }

        // B. Email/Password Sign Up Button
        btn_signUp.setOnClickListener {
            val email = edt_signUpEmail.text.toString().trim()
            val password = edt_signUpPass.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create Account
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val userId = auth.currentUser!!.uid

                    // Create basic user map
                    val userData = mapOf(
                        "email" to email,
                        "profileCompleted" to false, // Set false so they are forced to setup profile
                        "role" to "user"
                    )

                    // Save to Firestore
                    db.collection("tbl_users").document(userId).set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Sign Up Successful", Toast.LENGTH_SHORT).show()
                            // Navigate to Profile Details
                            val intent = Intent(this, ProfileDetails::class.java)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Database Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Sign Up Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // C. "Already have an account?" Button
        btn_isLogin.setOnClickListener {
            val intent = Intent(this, LoginPage::class.java)
            startActivity(intent)
            finish() // Close sign up page so user can't go back to it easily
        }
    }
}