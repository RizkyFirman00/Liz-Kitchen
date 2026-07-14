package com.dissy.lizkitchen.ui.register

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.dissy.lizkitchen.databinding.ActivityRegisterBinding
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.hideKeyboardWhenTouchOutsideInput
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.setupPasswordVisibilityToggle
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class RegisterActivity : BaseActivity() {
    private val binding by lazy { ActivityRegisterBinding.inflate(layoutInflater) }
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.etPassword.setupPasswordVisibilityToggle()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToLogin()
            }
        })

        binding.btnTologin.setOnClickListener {
            navigateToLogin()
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
                val passwordError = validatePassword(password, username)
                if (passwordError != null) {
                    binding.etPassword.error = passwordError
                    Toast.makeText(this, passwordError, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                registerUser(email, phoneNumber, username, password, alamat)
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        hideKeyboardWhenTouchOutsideInput(event)
        return super.dispatchTouchEvent(event)
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
                            navigateToLogin()
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
            root.setFirebaseRequestLoading(true, progressBar2)
            etEmail.isEnabled = false
            etNotelp.isEnabled = false
            etUsername.isEnabled = false
            etPassword.isEnabled = false
        }
    }

    private fun unLoadingProgress() {
        binding.apply {
            root.setFirebaseRequestLoading(false, progressBar2)
            etEmail.isEnabled = true
            etNotelp.isEnabled = true
            etUsername.isEnabled = true
            etPassword.isEnabled = true
        }
    }

    private fun validatePassword(password: String, username: String): String? {
        val normalizedPassword = password.lowercase()
        val normalizedUsername = username.trim().lowercase()
        val weakPasswords = setOf(
            "password",
            "password123",
            "12345678",
            "qwerty123",
            "admin123",
            "lizkitchen"
        )

        return when {
            password.length < 8 -> "Password minimal 8 karakter"
            password.any { it.isWhitespace() } -> "Password tidak boleh memakai spasi"
            password.none { it.isUpperCase() } -> "Password wajib punya huruf besar"
            password.none { it.isLowerCase() } -> "Password wajib punya huruf kecil"
            password.none { it.isDigit() } -> "Password wajib punya angka"
            password.none { !it.isLetterOrDigit() } -> "Password wajib punya simbol"
            normalizedUsername.isNotBlank() && normalizedPassword.contains(normalizedUsername) ->
                "Password tidak boleh mengandung username"
            normalizedPassword in weakPasswords -> "Password terlalu mudah ditebak"
            else -> null
        }
    }

    private fun navigateToLogin() {
        Intent(this, LoginActivity::class.java).also {
            startActivity(it)
            finish()
        }
    }
}
