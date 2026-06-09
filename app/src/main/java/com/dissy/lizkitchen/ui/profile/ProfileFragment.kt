package com.dissy.lizkitchen.ui.profile

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dissy.lizkitchen.databinding.FragmentProfileBinding
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.content.Intent
import com.dissy.lizkitchen.R

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = Preferences.getUserId(requireContext())

        if (userId != null) {
            getUserData(userId)
        }

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.topAppBar.menu.findItem(R.id.btn_toProfile)?.isVisible = false

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.btn_toLogout -> {
                    Preferences.logout(requireContext())
                    Intent(requireContext(), LoginActivity::class.java).also {
                        Toast.makeText(requireContext(), "Berhasil Logout", Toast.LENGTH_SHORT).show()
                        startActivity(it)
                        requireActivity().finish()
                    }
                    true
                }
                else -> false
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
        }
    }

    private fun getUserData(userId: String) {
        usersCollection.document(userId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
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
                Toast.makeText(requireContext(), "Data pengguna berhasil diperbarui", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Gagal memperbarui data pengguna", Toast.LENGTH_SHORT).show()
                Log.e("ProfileFragment", "Error updating user data", exception)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}