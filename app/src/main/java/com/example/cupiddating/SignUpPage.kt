package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import android.widget.*
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
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val edtEmail: EditText = findViewById(R.id.edt_signUpEmail)
        val edtPass: EditText = findViewById(R.id.edt_signUpPass)
        val btnSignUp: Button = findViewById(R.id.btn_signUp)
        val btnIsLogin: Button = findViewById(R.id.btn_isLogin)
        val btnGoogleSignUp: Button = findViewById(R.id.btn_googleSignUp)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleClient = GoogleSignIn.getClient(this, gso)

        val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnSuccessListener {
                    val uid = auth.currentUser!!.uid
                    db.collection("tbl_users").document(uid).get().addOnSuccessListener { doc ->
                        if (!doc.exists()) {
                            saveUserWithSequentialId(uid, auth.currentUser?.email ?: "", auth.currentUser?.displayName)
                        } else {
                            db.collection("tbl_users").document(uid).update("last_login", FieldValue.serverTimestamp())
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign Up Failed", Toast.LENGTH_SHORT).show()
            }
        }

        btnGoogleSignUp.setOnClickListener { googleLauncher.launch(googleClient.signInIntent) }

        btnSignUp.setOnClickListener {
            val email = edtEmail.text.toString().trim()
            val pass = edtPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, pass).addOnSuccessListener {
                saveUserWithSequentialId(auth.currentUser!!.uid, email, null)
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Sign Up Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnIsLogin.setOnClickListener {
            startActivity(Intent(this, LoginPage::class.java))
            finish()
        }
    }

    private fun saveUserWithSequentialId(uid: String, email: String, name: String?) {
        db.collection("tbl_users").get().addOnSuccessListener { docs ->
            val formattedUserId = String.format("user_%03d", docs.size() + 1)
            val userData = hashMapOf(
                "user_id" to formattedUserId,
                "email" to email,
                "profile_completed" to false,
                "role" to "user",
                "created_at" to FieldValue.serverTimestamp(),
                "last_login" to FieldValue.serverTimestamp()
            )
            name?.let { userData["name"] = it }

            db.collection("tbl_users").document(uid).set(userData).addOnSuccessListener {
                Toast.makeText(this, "Account Created", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, ProfileDetails::class.java))
                finish()
            }
        }
    }
}