package com.dissy.lizkitchen.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.dissy.lizkitchen.databinding.ActivityLoginBinding
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.admin.AdminHomeActivity
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.home.MainActivity
import com.dissy.lizkitchen.ui.register.RegisterActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : BaseActivity() {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        Preferences.getUserInfo(this)?.let {
            Log.d("User Info Login", "$it")
        }

        val usernameCheck = Preferences.getUsername(this)
        if (Preferences.checkUsername(this) && usernameCheck == "admin") {
            Intent(this, AdminHomeActivity::class.java).also {
                startActivity(it)
                finish()
            }
        } else if (Preferences.checkUsername(this)) {
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
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()

            if (username == "admin" && password == "admin") {
                Preferences.saveUsername(username, this)
                Intent(this, AdminHomeActivity::class.java).also {
                    startActivity(it)
                    finish()
                }
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    val loginResult = loginUser(username, password)
                    handleLoginResult(loginResult)
                }
            }
        }
    }

    private fun handleLoginResult(result: Pair<Boolean, String?>) {
        if (result.first) {
            val username = Preferences.getUsername(this)
            Toast.makeText(this, "Selamat Datang $username", Toast.LENGTH_SHORT).show()
            Intent(this, MainActivity::class.java).also {
                startActivity(it)
                finish()
            }
        } else {
            Toast.makeText(this, "Login gagal", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loginUser(username: String, password: String): Pair<Boolean, String?> {
        return try {
            loadingProgress()
            val userQuery = usersCollection.whereEqualTo("username", username).get().await()

            if (!userQuery.isEmpty) {
                val userDoc = userQuery.documents[0]

                val storedPassword = userDoc.getString("password")
                if (storedPassword == password) {
                    val userId = userDoc.getString("userId")
                    val username = userDoc.getString("username")
                    val email = userDoc.getString("email")
                    val phoneNumber = userDoc.getString("phoneNumber")
                    val alamat = userDoc.getString("alamat")
                    Preferences.saveUserInfo(
                        User(
                            userId = userId ?: "",
                            username = username ?: "",
                            email = email ?: "",
                            password = password,
                            phoneNumber = phoneNumber ?: "",
                            alamat = alamat ?: "Belum diisi"
                        ), this
                    )
                    if (userId != null) {
                        unLoadingProgress()
                        Log.d("USER ID LOGIN", userId)
                        if (username != null) {
                            Preferences.saveUsername(username, this)
                        }
                        Preferences.saveUserId(userId, this)
                    }
                    Pair(true, userId)
                } else {
                    unLoadingProgress()
                    Pair(false, null)
                }
            } else {
                unLoadingProgress()
                Pair(false, null)
            }
        } catch (e: Exception) {
            unLoadingProgress()
            Pair(false, null)
        }
    }

    private fun loadingProgress() {
        binding.apply {
            progressBar2.visibility = android.view.View.VISIBLE
            etUsername.isEnabled = false
            etPassword.isEnabled = false
            btnLogin.isEnabled = false
            btnToregister.isEnabled = false
        }
    }

    private fun unLoadingProgress() {
        binding.apply {
            progressBar2.visibility = android.view.View.GONE
            etUsername.isEnabled = true
            etPassword.isEnabled = true
            btnLogin.isEnabled = true
            btnToregister.isEnabled = true
        }
    }

}