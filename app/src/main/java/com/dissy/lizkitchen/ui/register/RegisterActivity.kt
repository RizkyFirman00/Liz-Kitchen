package com.dissy.lizkitchen.ui.register

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.dissy.lizkitchen.databinding.ActivityRegisterBinding
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.hideKeyboardWhenTouchOutsideInput
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.setupPasswordVisibilityToggle
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterActivity : BaseActivity() {
    private val binding by lazy { ActivityRegisterBinding.inflate(layoutInflater) }
    private val auth by lazy { FirebaseAuth.getInstance() }
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
            val email = binding.etEmail.text.toString().trim().lowercase()
            val name = binding.etName.text.toString().trim()
            val phoneNumber = binding.etNotelp.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val alamat = "Belum diisi"

            if (email.isEmpty() || name.isEmpty() || phoneNumber.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Harap isi semua kolom", Toast.LENGTH_SHORT).show()
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Format email tidak valid"
            } else {
                val passwordError = validatePassword(password, email)
                if (passwordError != null) {
                    binding.etPassword.error = passwordError
                    Toast.makeText(this, passwordError, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                registerUser(email, name, phoneNumber, password, alamat)
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        hideKeyboardWhenTouchOutsideInput(event)
        return super.dispatchTouchEvent(event)
    }

    private fun registerUser(
        email: String,
        name: String,
        phoneNumber: String,
        password: String,
        alamat: String
    ) {
        if (email == Preferences.ADMIN_EMAIL) {
            Toast.makeText(this, "Email sudah terdaftar", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            loadingProgress()
            try {
                auth.useAppLanguage()
                val firebaseUser = auth.createUserWithEmailAndPassword(email, password)
                    .await()
                    .user
                    ?: error("Firebase tidak mengembalikan data pengguna")

                val userData = mapOf(
                    "userId" to firebaseUser.uid,
                    "email" to email,
                    "name" to name,
                    "phoneNumber" to phoneNumber,
                    "alamat" to alamat
                )

                try {
                    usersCollection.document(firebaseUser.uid).set(userData).await()
                } catch (exception: Exception) {
                    runCatching { firebaseUser.delete().await() }
                    throw exception
                }

                val verificationSent = runCatching {
                    firebaseUser.sendEmailVerification().await()
                }.onFailure {
                    Log.e("RegisterActivity", "Gagal mengirim email verifikasi", it)
                }.isSuccess

                auth.signOut()
                val message = if (verificationSent) {
                    "Registrasi berhasil. Cek email untuk verifikasi akun"
                } else {
                    "Akun berhasil dibuat. Masuk untuk mengirim ulang verifikasi"
                }
                Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_LONG).show()
                navigateToLogin()
            } catch (exception: Exception) {
                auth.signOut()
                Log.e("RegisterActivity", "Registrasi Firebase Auth gagal", exception)
                Toast.makeText(
                    this@RegisterActivity,
                    registrationErrorMessage(exception),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                unLoadingProgress()
            }
        }
    }

    private fun registrationErrorMessage(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthUserCollisionException -> "Email sudah terdaftar"
            is FirebaseAuthWeakPasswordException -> "Password belum memenuhi ketentuan keamanan"
            is FirebaseAuthInvalidCredentialsException -> "Format email tidak valid"
            is FirebaseNetworkException -> "Koneksi bermasalah. Coba lagi"
            else -> "Registrasi gagal. Silakan coba lagi"
        }
    }

    private fun loadingProgress() {
        binding.apply {
            root.setFirebaseRequestLoading(true, progressBar2)
            etEmail.isEnabled = false
            etName.isEnabled = false
            etNotelp.isEnabled = false
            etPassword.isEnabled = false
        }
    }

    private fun unLoadingProgress() {
        binding.apply {
            root.setFirebaseRequestLoading(false, progressBar2)
            etEmail.isEnabled = true
            etName.isEnabled = true
            etNotelp.isEnabled = true
            etPassword.isEnabled = true
        }
    }

    private fun validatePassword(password: String, email: String): String? {
        val normalizedPassword = password.lowercase()
        val normalizedEmail = email.trim().lowercase()
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
            normalizedEmail.isNotBlank() && normalizedPassword.contains(normalizedEmail) ->
                "Password tidak boleh mengandung Email"
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
