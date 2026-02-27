package com.dissy.lizkitchen.ui.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.dissy.lizkitchen.databinding.ActivityProfileBinding
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.home.MainActivity
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ProfileActivity : BaseActivity() {
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private val binding by lazy { ActivityProfileBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val userId = Preferences.getUserId(this)

        Log.d("USER ID PROFILE", "$userId")
        if (userId != null) {
            getUserData(userId)
        }

        binding.btnToHome.setOnClickListener {
            finish()
        }

        binding.btnToLogout.setOnClickListener {
            Preferences.logout(this)
            Intent(this, LoginActivity::class.java).also {
                Toast.makeText(this, "Berhasil Logout", Toast.LENGTH_SHORT).show()
                startActivity(it)
                finish()
            }
        }

        binding.btnUpdateData.setOnClickListener {
            val updatedEmail = binding.etEmail.text.toString()
            val updatedPhoneNumber = binding.etNotelp.text.toString()
            val updatedUsername = binding.etUsername.text.toString()
            val updatedAlamat = binding.etAlamat.text.toString()

            if (userId != null) {
                updateUserData(
                    userId,
                    updatedEmail,
                    updatedPhoneNumber,
                    updatedUsername,
                    updatedAlamat
                )
            }
            Intent(this, MainActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }
    }

    private fun getUserData(userId: String) {
        usersCollection.document(userId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val allData: Map<String, Any> = documentSnapshot.data!!

                    for ((key, value) in allData) {
                        Log.d("UserData", "$key: $value")
                    }
                    val email = documentSnapshot.getString("email")
                    val phoneNumber = documentSnapshot.getString("phoneNumber")
                    val username = documentSnapshot.getString("username")
                    val alamat = documentSnapshot.getString("alamat")
                    binding.apply {
                        etEmail.setText(email)
                        etNotelp.setText(phoneNumber)
                        etUsername.setText(username)
                        etAlamat.setText(alamat)
                    }
                } else {
                    Log.e("UserData", "Document not found")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("UserData", "Error getting document", exception)
            }
    }

    private fun updateUserData(
        userId: String,
        email: String,
        phoneNumber: String,
        newUsername: String,
        alamat: String
    ) {
        val updatedUserData = hashMapOf(
            "email" to email,
            "phoneNumber" to phoneNumber,
            "username" to newUsername,
            "alamat" to alamat
        )
        usersCollection.document(userId)
            .update(updatedUserData as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(this, "Data pengguna berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Gagal memperbarui data pengguna", Toast.LENGTH_SHORT).show()
                Log.e("ProfileActivity", "Error updating user data", exception)
            }
    }
}