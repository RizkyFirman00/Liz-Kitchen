package com.dissy.lizkitchen.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.MotionEvent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.dissy.lizkitchen.databinding.ActivityLoginBinding
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.admin.AdminActivity
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.home.MainActivity
import com.dissy.lizkitchen.ui.register.RegisterActivity
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.hideKeyboardWhenTouchOutsideInput
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.setupPasswordVisibilityToggle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : BaseActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.etPassword.setupPasswordVisibilityToggle()

        Preferences.getUserInfo(this)?.let {
            Log.d("User Info Login", "$it")
        }

        if (Preferences.isAdminSession(this)) {
            Intent(this, AdminActivity::class.java).also {
                startActivity(it)
                finish()
            }
        } else if (Preferences.isUserSession(this)) {
            Intent(this, MainActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        binding.btnToregister.setOnClickListener {
            Intent(this, RegisterActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim().lowercase()
            val password = binding.etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Email dan password wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Format email tidak valid"
                return@setOnClickListener
            }

            if (email == Preferences.ADMIN_EMAIL && password == "admin") {
                Preferences.saveAdminSession(this)
                Intent(this, AdminActivity::class.java).also {
                    startActivity(it)
                    finish()
                }
            } else {
                lifecycleScope.launch {
                    val loginResult = loginUser(email, password)
                    handleLoginResult(loginResult)
                }
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        hideKeyboardWhenTouchOutsideInput(event)
        return super.dispatchTouchEvent(event)
    }

    private fun handleLoginResult(result: LoginResult) {
        when (result) {
            is LoginResult.Success -> {
                Toast.makeText(this, "Selamat datang, ${result.name}", Toast.LENGTH_SHORT).show()
                Intent(this, MainActivity::class.java).also {
                    startActivity(it)
                    finish()
                }
            }
            LoginResult.Unverified -> showEmailVerificationDialog()
            is LoginResult.Failure -> {
                auth.signOut()
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loginUser(email: String, password: String): LoginResult {
        return try {
            loadingProgress()
            val authUser = try {
                auth.signInWithEmailAndPassword(email, password).await().user
            } catch (exception: Exception) {
                if (exception is FirebaseNetworkException) throw exception
                val migrated = migrateLegacyAccount(email, password)
                if (migrated) return LoginResult.Unverified
                throw exception
            }

            authUser ?: return LoginResult.Failure("Login gagal. Silakan coba lagi")
            authUser.reload().await()
            val refreshedUser = auth.currentUser
                ?: return LoginResult.Failure("Sesi login tidak tersedia")

            if (!refreshedUser.isEmailVerified) {
                return LoginResult.Unverified
            }

            val user = loadUserProfile(refreshedUser.uid, refreshedUser.email.orEmpty())
            Preferences.saveUserInfo(user, this)
            Log.d("USER ID LOGIN", user.userId.orEmpty())
            LoginResult.Success(user.name.orEmpty().ifBlank { "Pelanggan" })
        } catch (e: Exception) {
            Log.e("LoginActivity", "Firebase Auth login gagal", e)
            LoginResult.Failure(
                if (e is FirebaseNetworkException) {
                    "Koneksi bermasalah. Coba lagi"
                } else {
                    "Email atau password salah"
                }
            )
        } finally {
            unLoadingProgress()
        }
    }

    private suspend fun migrateLegacyAccount(email: String, password: String): Boolean {
        val legacyDocument = usersCollection.get().await().documents.firstOrNull { document ->
            document.getString("email").orEmpty().trim().equals(email, ignoreCase = true) &&
                document.getString("password") == password
        } ?: return false

        val firebaseUser = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: return false
        val legacyName = legacyDocument.getString("name").orEmpty()
            .ifBlank { legacyDocument.getString("username").orEmpty() }
        legacyDocument.reference.update(
            mapOf(
                "authUid" to firebaseUser.uid,
                "email" to email,
                "name" to legacyName,
                "password" to FieldValue.delete(),
                "username" to FieldValue.delete()
            )
        ).await()
        runCatching { firebaseUser.sendEmailVerification().await() }
            .onFailure { Log.e("LoginActivity", "Gagal mengirim verifikasi akun lama", it) }
        return true
    }

    private suspend fun loadUserProfile(authUid: String, email: String): User {
        val directDocument = usersCollection.document(authUid).get().await()
        val userDocument = if (directDocument.exists()) {
            directDocument
        } else {
            usersCollection.get().await().documents.firstOrNull { document ->
                document.getString("email").orEmpty().trim().equals(email, ignoreCase = true)
            }
        }

        if (userDocument == null) {
            val fallbackName = email.substringBefore('@').ifBlank { "Pelanggan" }
            val newUser = User(
                userId = authUid,
                email = email,
                name = fallbackName,
                phoneNumber = "",
                alamat = "Belum diisi"
            )
            usersCollection.document(authUid).set(
                mapOf(
                    "userId" to authUid,
                    "email" to email,
                    "name" to fallbackName,
                    "phoneNumber" to "",
                    "alamat" to "Belum diisi"
                )
            ).await()
            return newUser
        }

        val userId = userDocument.getString("userId").orEmpty().ifBlank { userDocument.id }
        val name = userDocument.getString("name").orEmpty()
            .ifBlank { userDocument.getString("username").orEmpty() }
        userDocument.reference.update(
            mapOf(
                "authUid" to authUid,
                "email" to email,
                "name" to name,
                "password" to FieldValue.delete(),
                "username" to FieldValue.delete()
            )
        ).await()
        return User(
            userId = userId,
            email = email,
            name = name,
            phoneNumber = userDocument.getString("phoneNumber").orEmpty(),
            alamat = userDocument.getString("alamat") ?: "Belum diisi"
        )
    }

    private fun showEmailVerificationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Verifikasi email dulu")
            .setMessage("Buka link verifikasi yang dikirim ke emailmu. Setelah terverifikasi, silakan masuk kembali.")
            .setNegativeButton("Nanti") { _, _ -> auth.signOut() }
            .setPositiveButton("Kirim Ulang") { _, _ -> resendVerificationEmail() }
            .setOnCancelListener { auth.signOut() }
            .show()
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Silakan masuk kembali", Toast.LENGTH_SHORT).show()
            return
        }
        loadingProgress()
        user.sendEmailVerification().addOnCompleteListener { task ->
            unLoadingProgress()
            auth.signOut()
            val message = if (task.isSuccessful) {
                "Email verifikasi berhasil dikirim ulang"
            } else {
                "Gagal mengirim ulang verifikasi. Coba beberapa saat lagi"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private sealed interface LoginResult {
        data class Success(val name: String) : LoginResult
        data object Unverified : LoginResult
        data class Failure(val message: String) : LoginResult
    }

    private fun loadingProgress() {
        binding.apply {
            root.setFirebaseRequestLoading(true, progressBar2)
            etEmail.isEnabled = false
            etPassword.isEnabled = false
        }
    }

    private fun unLoadingProgress() {
        binding.apply {
            root.setFirebaseRequestLoading(false, progressBar2)
            etEmail.isEnabled = true
            etPassword.isEnabled = true
        }
    }

}
