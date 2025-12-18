package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // widget variables
        val edt_loginEmail : EditText = findViewById(R.id.edt_loginEmail)
        val edt_loginPass : EditText = findViewById(R.id.edt_loginPass)
        val btn_emailLogin : Button = findViewById(R.id.btn_emailLogin)
        val btn_googleLogin : Button = findViewById(R.id.btn_googleLogin)
        val btn_isSignUp : Button = findViewById(R.id.btn_isSignUp)

        //start connection with auth
        val auth = FirebaseAuth.getInstance()
        val con = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))  // Web client ID
            .requestEmail()
            .build()

        // Creates Google Sign-In client using the above settings
        val googleClient = GoogleSignIn.getClient(this, gso)

        // --------------------------------------------------------------
        // STEP 2 — Activity Result Launcher (handles result of Google Sign In)
        // This replaces deprecated onActivityResult()
        // --------------------------------------------------------------
        val googleLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            // Google returns an intent → convert to task
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)

            try {
                // STEP 3 — Get the Google Account (may throw ApiException)
                val account = task.getResult(ApiException::class.java)

                // STEP 4 — Convert Google account to Firebase credential
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnSuccessListener{
                        val userid = auth.currentUser!!.uid
                        val email = auth.currentUser!!.email
                        val name = auth.currentUser!!.displayName

                        val values = mapOf(
                            "name" to name,
                            "email" to email
                        )

                        con.collection("reg_tbl_users").document(userid).set(values)
                            .addOnSuccessListener{
                                Toast.makeText(this, "Login Successful",Toast.LENGTH_SHORT).show()

                                val intent = Intent(this, MainActivity::class.java)
                                startActivity(intent)
                            }
                    }

            } catch (e: Exception) {
                Toast.makeText(this, "Google Login Failed", Toast.LENGTH_SHORT).show()
            }
        }
        // --------------------------------------------------------------
        // STEP 8 — When Google button is clicked → launch Google Sign-In
        // --------------------------------------------------------------
        btn_googleLogin.setOnClickListener {
            googleLauncher.launch(googleClient.signInIntent)
        }

        // Email and Password Login
        btn_emailLogin.setOnClickListener {
            //user input
            val email = edt_loginEmail.text.toString()
            val password = edt_loginPass.text.toString()

            //connected to database
            auth.signInWithEmailAndPassword(email, password)

                .addOnSuccessListener {
                    Toast.makeText(this, "Log in successful", Toast.LENGTH_SHORT).show()

                    //transfer to other page
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                }

                .addOnFailureListener {
                        e ->
                    Toast.makeText(this, "Log in failed" + e.message, Toast.LENGTH_SHORT).show()
                }
        }

        btn_isSignUp.setOnClickListener {
            val intent = Intent(this, SignUpPage::class.java)
            startActivity(intent)
        }

    }
}