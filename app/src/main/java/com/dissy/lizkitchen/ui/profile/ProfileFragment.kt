package com.dissy.lizkitchen.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dissy.lizkitchen.databinding.FragmentProfileBinding
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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

        binding.root.setOnClickListener { hideKeyboardAndClearFocus() }
        binding.formContainer.setOnClickListener { hideKeyboardAndClearFocus() }
        binding.etAlamat.setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE
            val isEnterUp = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_UP

            if (isDoneAction || isEnterUp) {
                hideKeyboardAndClearFocus()
                true
            } else {
                false
            }
        }

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
            hideKeyboardAndClearFocus()

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
        setRequestLoading(true)
        usersCollection.document(userId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (_binding == null) return@addOnSuccessListener
                setRequestLoading(false)
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
                        tvProfileName.text = username.orEmpty().ifBlank { "User Liz Kitchen" }
                        tvProfileEmail.text = email.orEmpty().ifBlank { "Email belum diisi" }
                        tvProfileInitial.text = username.orEmpty()
                            .trim()
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "L"
                    }
                }
            }
            .addOnFailureListener { exception ->
                if (_binding != null) setRequestLoading(false)
                Log.e("UserData", "Error getting document", exception)
            }
    }

    private fun hideKeyboardAndClearFocus() {
        val inputMethodManager = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focusedView = requireActivity().currentFocus ?: binding.root

        inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
        focusedView.clearFocus()
        binding.root.requestFocus()
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
        setRequestLoading(true)
        usersCollection.document(userId)
            .update(updatedUserData as Map<String, Any>)
            .addOnSuccessListener {
                setRequestLoading(false)
                Toast.makeText(requireContext(), "Data pengguna berhasil diperbarui", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { exception ->
                setRequestLoading(false)
                Toast.makeText(requireContext(), "Gagal memperbarui data pengguna", Toast.LENGTH_SHORT).show()
                Log.e("ProfileFragment", "Error updating user data", exception)
            }
    }

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.root.setFirebaseRequestLoading(isLoading)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
