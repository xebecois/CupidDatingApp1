package com.example.cupiddating

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class registerPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // widget variables

        val edt_signUpEmail : EditText = findViewById(R.id.edt_signUpEmail)
        val edt_signUpPass : EditText = findViewById(R.id.edt_signUpPass)
        val btn_signUp : Button = findViewById(R.id.btn_signUp)
        val signUp_Google : ImageView = findViewById(R.id.signUp_Google)
        val btn_isLogin : Button = findViewById(R.id.btn_isLogin)

        // Start connection for firestore and authentication
        val con = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()


        btn_signUp.setOnClickListener {
            // User inputs
            val email = edt_signUpEmail.text.toString()
            val password = edt_signUpPass.text.toString()

            // Create account in db
            auth.createUserWithEmailAndPassword(email, password)

                .addOnSuccessListener {
                    // User id field
                    val userid = auth.currentUser!!.uid
                    val values = mapOf(
//                        "name" to name,
                        "email" to email,
                        "role" to "",
                        "gender" to ""
                    )
                    // Firebase database connection
                    con.collection("reg_tbl_users").document(userid).set(values)

                    // Transfer to other page
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                }

                .addOnFailureListener {
                        e ->
                    Toast.makeText(this, "Sign Up Failed: " +e.message, Toast.LENGTH_SHORT).show()
                }
        }

        btn_isLogin.setOnClickListener {
            val intent = Intent(this, LoginPage::class.java)
            startActivity(intent)
        }

    }
}