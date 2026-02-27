package com.dissy.lizkitchen.ui.register

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.dissy.lizkitchen.databinding.ActivityRegisterBinding
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class RegisterActivity : BaseActivity() {
    private val binding by lazy { ActivityRegisterBinding.inflate(layoutInflater) }
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnTologin.setOnClickListener {
            Intent(this, LoginActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val phoneNumber = binding.etNotelp.text.toString()
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()
            val alamat = "Belum diisi"

            if (email.isEmpty() || phoneNumber.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Harap isi semua kolom", Toast.LENGTH_SHORT).show()
            } else {
                registerUser(email, phoneNumber, username, password, alamat)
            }
        }
    }

    private fun registerUser(
        email: String,
        phoneNumber: String,
        username: String,
        password: String,
        alamat: String
    ) {
        loadingProgress()

        usersCollection.whereEqualTo("username", username).get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    // Username already exists
                    unLoadingProgress()
                    Toast.makeText(this, "Username sudah dipakai", Toast.LENGTH_SHORT).show()
                } else {
                    // Username is available, proceed with registration
                    val newUserId = usersCollection.document().id
                    val user = hashMapOf(
                        "userId" to newUserId,
                        "email" to email,
                        "phoneNumber" to phoneNumber,
                        "username" to username,
                        "password" to password,
                        "alamat" to alamat
                    )

                    usersCollection.document(newUserId).set(user)
                        .addOnSuccessListener {
                            unLoadingProgress()
                            Toast.makeText(this, "Registrasi berhasil", Toast.LENGTH_SHORT).show()
                            Intent(this, LoginActivity::class.java).also {
                                startActivity(it)
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            unLoadingProgress()
                            Toast.makeText(this, "Registrasi gagal: ${e.message}", Toast.LENGTH_LONG).show()
                            Log.e("RegisterActivity", "Error writing new user", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                unLoadingProgress()
                Toast.makeText(this, "Gagal memeriksa username: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("RegisterActivity", "Error checking existing user", e)
            }
    }

    private fun loadingProgress() {
        binding.apply {
            progressBar2.visibility = android.view.View.VISIBLE
            etEmail.isEnabled = false
            etNotelp.isEnabled = false
            etUsername.isEnabled = false
            etPassword.isEnabled = false
            btnRegister.isEnabled = false
            btnTologin.isEnabled = false
        }
    }

    private fun unLoadingProgress() {
        binding.apply {
            progressBar2.visibility = android.view.View.GONE
            etEmail.isEnabled = true
            etNotelp.isEnabled = true
            etUsername.isEnabled = true
            etPassword.isEnabled = true
            btnRegister.isEnabled = true
            btnTologin.isEnabled = true
        }
    }
}