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

class LoginPage : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_page)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val edtEmail: EditText = findViewById(R.id.edt_loginEmail)
        val edtPass: EditText = findViewById(R.id.edt_loginPass)
        val btnEmailLogin: Button = findViewById(R.id.btn_emailLogin)
        val btnGoogleLogin: Button = findViewById(R.id.btn_googleLogin)
        val btnSignUp: Button = findViewById(R.id.btn_isSignUp)

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
                auth.signInWithCredential(credential).addOnSuccessListener { handleUserRedirection() }
            } catch (e: Exception) {
                Toast.makeText(this, "Google Login Failed", Toast.LENGTH_SHORT).show()
            }
        }

        btnGoogleLogin.setOnClickListener { googleLauncher.launch(googleClient.signInIntent) }

        btnEmailLogin.setOnClickListener {
            val email = edtEmail.text.toString().trim()
            val password = edtPass.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { handleUserRedirection() }
                .addOnFailureListener { Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show() }
        }

        btnSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpPage::class.java))
        }
    }

    private fun handleUserRedirection() {
        val user = auth.currentUser ?: return
        val docRef = db.collection("tbl_users").document(user.uid)

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                docRef.update("last_login", FieldValue.serverTimestamp())
                val isComplete = document.getBoolean("profile_completed") ?: false
                navigate(if (isComplete) MainActivity::class.java else ProfileDetails::class.java)
            } else {
                val values = hashMapOf(
                    "name" to user.displayName,
                    "email" to user.email,
                    "profile_completed" to false,
                    "created_at" to FieldValue.serverTimestamp(),
                    "last_login" to FieldValue.serverTimestamp()
                )
                docRef.set(values).addOnSuccessListener { navigate(ProfileDetails::class.java) }
            }
        }
    }

    private fun navigate(target: Class<*>) {
        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, target))
        finish()
    }
}