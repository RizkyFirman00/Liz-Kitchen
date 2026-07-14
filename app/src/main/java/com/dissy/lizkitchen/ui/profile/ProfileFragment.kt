package com.dissy.lizkitchen.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.dissy.lizkitchen.databinding.FragmentProfileBinding
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.ktx.Firebase
import com.dissy.lizkitchen.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private var profileDocumentId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cachedUser = Preferences.getUserInfo(requireContext())
        if (cachedUser != null && cachedUser.userId.orEmpty().isNotBlank()) {
            bindUserData(cachedUser)
        }

        val userId = cachedUser?.userId?.takeIf { it.isNotBlank() }
            ?: FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }

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

            val updatedEmail = binding.etEmail.text.toString().trim().lowercase()
            val updatedPhoneNumber = binding.etNotelp.text.toString().trim()
            val updatedName = binding.etName.text.toString().trim()
            val updatedAlamat = binding.etAlamat.text.toString().trim()

            if (updatedEmail.isBlank() || updatedName.isBlank() || updatedPhoneNumber.isBlank()) {
                Toast.makeText(requireContext(), "Email, nama, dan nomor telepon wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(updatedEmail).matches()) {
                binding.etEmail.error = "Format email tidak valid"
                return@setOnClickListener
            }

            if (userId != null) {
                updateUserData(
                    profileDocumentId ?: userId,
                    updatedEmail,
                    updatedPhoneNumber,
                    updatedName,
                    updatedAlamat
                )
            }
        }
    }

    private fun getUserData(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            setRequestLoading(true)
            try {
                // Auth reload is useful for a fresh email, but it must not block profile data.
                runCatching { auth.currentUser?.reload()?.await() }
                val documentSnapshot = findUserDocument(userId)
                if (documentSnapshot != null) {
                    profileDocumentId = documentSnapshot.id
                    val storedEmail = documentSnapshot.getString("email").orEmpty()
                    val email = auth.currentUser?.email.orEmpty().ifBlank { storedEmail }
                    val phoneNumber = documentSnapshot.getString("phoneNumber").orEmpty()
                    val name = documentSnapshot.getString("name").orEmpty()
                        .ifBlank { documentSnapshot.getString("username").orEmpty() }
                    val alamat = documentSnapshot.getString("alamat").orEmpty()
                    bindUserData(
                        User(
                            userId = documentSnapshot.getString("userId").orEmpty().ifBlank {
                                documentSnapshot.id
                            },
                            email = email,
                            name = name,
                            phoneNumber = phoneNumber,
                            alamat = alamat
                        )
                    )
                    val syncedUserData = mutableMapOf<String, Any>(
                        "email" to email,
                        "name" to name,
                        "password" to FieldValue.delete(),
                        "username" to FieldValue.delete()
                    )
                    if (email != storedEmail) {
                        syncedUserData["pendingEmail"] = FieldValue.delete()
                    }
                    documentSnapshot.reference.update(syncedUserData).await()
                    val currentUser = Preferences.getUserInfo(requireContext())
                    Preferences.saveUserInfo(
                        (currentUser ?: User(userId = userId)).copy(
                            email = email,
                            name = name,
                            phoneNumber = phoneNumber,
                            alamat = alamat.ifBlank { "Belum diisi" }
                        ),
                        requireContext()
                    )
                }
            } catch (exception: Exception) {
                Log.e("UserData", "Error getting document", exception)
                if (Preferences.getUserInfo(requireContext()) == null) {
                    Toast.makeText(requireContext(), "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (_binding != null) setRequestLoading(false)
            }
        }
    }

    private suspend fun findUserDocument(preferredUserId: String): DocumentSnapshot? {
        val preferredDocument = usersCollection.document(preferredUserId).get().await()
        if (preferredDocument.exists()) return preferredDocument

        val authUserId = auth.currentUser?.uid.orEmpty()
        if (authUserId.isNotBlank() && authUserId != preferredUserId) {
            val authDocument = usersCollection.document(authUserId).get().await()
            if (authDocument.exists()) return authDocument
        }

        val email = auth.currentUser?.email.orEmpty().trim().lowercase()
        if (email.isBlank()) return null

        return usersCollection
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
    }

    private fun bindUserData(user: User) {
        if (_binding == null) return
        val name = user.name.orEmpty()
        val email = user.email.orEmpty()
        binding.apply {
            etEmail.setText(email)
            etNotelp.setText(user.phoneNumber.orEmpty())
            etName.setText(name)
            etAlamat.setText(user.alamat.orEmpty())
            tvProfileName.text = name.ifBlank { "Pelanggan Liz Kitchen" }
            tvProfileEmail.text = email.ifBlank { "Email belum diisi" }
            tvProfileInitial.text = name
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "L"
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
        name: String,
        alamat: String
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            setRequestLoading(true)
            try {
                val authUser = auth.currentUser
                    ?: throw IllegalStateException("Sesi Firebase Auth tidak tersedia")
                val currentEmail = authUser.email.orEmpty().trim().lowercase()
                val emailChanged = email != currentEmail

                if (emailChanged) {
                    auth.useAppLanguage()
                    authUser.verifyBeforeUpdateEmail(email).await()
                }

                val updatedUserData = mutableMapOf<String, Any>(
                    "email" to currentEmail,
                    "name" to name,
                    "phoneNumber" to phoneNumber,
                    "alamat" to alamat,
                    "password" to FieldValue.delete(),
                    "username" to FieldValue.delete()
                )
                updatedUserData["pendingEmail"] = if (emailChanged) {
                    email
                } else {
                    FieldValue.delete()
                }
                usersCollection.document(userId).update(updatedUserData).await()

                val currentUser = Preferences.getUserInfo(requireContext())
                Preferences.saveUserInfo(
                    (currentUser ?: User(userId = userId)).copy(
                        email = currentEmail,
                        name = name,
                        phoneNumber = phoneNumber,
                        alamat = alamat
                    ),
                    requireContext()
                )
                val message = if (emailChanged) {
                    "Data disimpan. Cek email baru untuk menyelesaikan perubahan email"
                } else {
                    "Data pengguna berhasil diperbarui"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            } catch (exception: Exception) {
                Log.e("ProfileFragment", "Gagal memperbarui profil", exception)
                Toast.makeText(
                    requireContext(),
                    profileUpdateErrorMessage(exception),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                if (_binding != null) setRequestLoading(false)
            }
        }
    }

    private fun profileUpdateErrorMessage(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthUserCollisionException -> "Email sudah digunakan akun lain"
            is FirebaseAuthInvalidCredentialsException -> "Format email tidak valid"
            is FirebaseAuthRecentLoginRequiredException ->
                "Untuk mengganti email, silakan logout lalu masuk kembali"
            is FirebaseNetworkException -> "Koneksi bermasalah. Coba lagi"
            else -> "Gagal memperbarui data pengguna"
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
