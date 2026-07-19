package com.dissy.lizkitchen.ui.konfirmasi

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.FragmentConfirmBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PAYMENT_VERIFICATION
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.validateOrderExpiryOnRead
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class ConfirmFragment : Fragment() {
    private var _binding: FragmentConfirmBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private var orderId: String? = null
    private var currentOrder: Order? = null
    private var selectedProofUri: Uri? = null

    private val saveQrisPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                saveQrisToGallery()
            } else {
                Toast.makeText(requireContext(), "Izin penyimpanan diperlukan untuk menyimpan QRIS", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderId = arguments?.getString("orderId")
        val userId = Preferences.getUserId(requireContext())

        if (userId != null && orderId != null) {
            setRequestLoading(true)
            db.collection("users").document(userId).collection("orders").document(orderId!!)
                .get()
                .addOnSuccessListener {
                    if (_binding == null) return@addOnSuccessListener
                    setRequestLoading(false)
                    if (!it.exists()) {
                        Toast.makeText(requireContext(), "Pesanan tidak ditemukan", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val order = validateOrderExpiryOnRead(db, orderFromDocument(it))
                    currentOrder = order
                    val formattedText = formatAndDisplayCurrency(order.totalPrice.toString())

                    binding.apply {
                        tvOrderName.text = order.user.name.orEmpty().ifBlank { "Pelanggan" }
                        tvOrderTotal.text = formattedText
                        tvOrderId.text = orderId
                        if (order.paymentProofUrl.isNotBlank()) {
                            Glide.with(this@ConfirmFragment)
                                .load(order.paymentProofUrl)
                                .into(ivBuktiBayar)
                        }
                        if (order.status == ORDER_STATUS_EXPIRED) {
                            btnKonfirmasi.isEnabled = false
                            btnKonfirmasi.text = "Pesanan Expired"
                            Toast.makeText(
                                requireContext(),
                                "Batas pembayaran 1x24 jam sudah lewat",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    updateConfirmationButton()
                }
                .addOnFailureListener { exception ->
                    if (_binding != null) {
                        setRequestLoading(false)
                        Toast.makeText(requireContext(), "Gagal memuat pesanan: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        updateConfirmationButton()

        binding.ivBuktiBayar.setOnClickListener {
            startGallery()
        }

        binding.btnSaveQris.setOnClickListener {
            requestSaveQris()
        }

        binding.btnKonfirmasi.setOnClickListener {
            confirmPayment()
        }
    }

    private fun requestSaveQris() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            saveQrisPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        saveQrisToGallery()
    }

    private fun saveQrisToGallery() {
        val resolver = requireContext().contentResolver
        val fileName = "liz_kitchen_qris_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Liz Kitchen")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val imageUri = resolver.insert(collection, values)
        if (imageUri == null) {
            Toast.makeText(requireContext(), "Gagal menyiapkan galeri", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                resources.openRawResource(R.drawable.qris).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("Output stream tidak tersedia")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, values, null, null)
            }

            Toast.makeText(requireContext(), "QRIS berhasil disimpan ke galeri", Toast.LENGTH_SHORT).show()
        } catch (exception: Exception) {
            resolver.delete(imageUri, null, null)
            Toast.makeText(requireContext(), "Gagal menyimpan QRIS: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startGallery() {
        launcherIntentGallery.launch("image/*")
    }

    private val launcherIntentGallery = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { selectedImg ->
        if (selectedImg == null || _binding == null) return@registerForActivityResult
        selectedProofUri = selectedImg
        Glide.with(this).load(selectedImg).into(binding.ivBuktiBayar)
        updateConfirmationButton()
    }

    private fun confirmPayment() {
        val order = currentOrder
        if (order == null) {
            Toast.makeText(requireContext(), "Detail pesanan belum selesai dimuat", Toast.LENGTH_SHORT).show()
            return
        }

        if (order.status == ORDER_STATUS_EXPIRED) {
            Toast.makeText(requireContext(), "Pesanan sudah expired", Toast.LENGTH_SHORT).show()
            return
        }

        val proofUri = selectedProofUri
        if (proofUri == null) {
            if (order.paymentProofUrl.isNotBlank()) {
                startGallery()
            } else {
                Toast.makeText(requireContext(), "Upload bukti pembayaran terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val currentOrderId = order.orderId.ifBlank { orderId.orEmpty() }
        val userId = Preferences.getUserId(requireContext())
        if (currentOrderId.isBlank() || userId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Data pesanan tidak lengkap", Toast.LENGTH_SHORT).show()
            return
        }

        setRequestLoading(true)
        val proofRef = storage.reference.child(
            "payment_proofs/${currentOrderId}_${System.currentTimeMillis()}.jpg"
        )
        proofRef.putFile(proofUri)
            .addOnSuccessListener {
                proofRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        val uploadedAt = System.currentTimeMillis()
                        val updates = mapOf(
                            "paymentProofUrl" to downloadUri.toString(),
                            "paymentProofUploadedAtMillis" to uploadedAt,
                            "status" to ORDER_STATUS_PAYMENT_VERIFICATION
                        )
                        val globalOrderRef = db.collection("orders").document(currentOrderId)
                        val userOrderRef = db.collection("users").document(userId).collection("orders").document(currentOrderId)
                        db.runBatch { batch ->
                            batch.set(globalOrderRef, updates, SetOptions.merge())
                            batch.set(userOrderRef, updates, SetOptions.merge())
                        }.addOnSuccessListener {
                            currentOrder = order.copy(
                                status = ORDER_STATUS_PAYMENT_VERIFICATION,
                                paymentProofUrl = downloadUri.toString(),
                                paymentProofUploadedAtMillis = uploadedAt
                            )
                            selectedProofUri = null
                            setRequestLoading(false)
                            updateConfirmationButton()
                            Toast.makeText(
                                requireContext(),
                                "Bukti pembayaran berhasil dikirim. Menunggu verifikasi admin.",
                                Toast.LENGTH_LONG
                            ).show()
                            val navOptions = NavOptions.Builder()
                                .setPopUpTo(R.id.navigation_confirm, true)
                                .setLaunchSingleTop(true)
                                .build()
                            findNavController().navigate(
                                R.id.navigation_history,
                                null,
                                navOptions
                            )
                        }.addOnFailureListener { exception ->
                            handlePaymentUploadFailure(exception)
                        }
                    }
                    .addOnFailureListener { exception ->
                        handlePaymentUploadFailure(exception)
                    }
            }
            .addOnFailureListener { exception ->
                handlePaymentUploadFailure(exception)
            }
    }

    private fun handlePaymentUploadFailure(exception: Exception) {
        if (_binding == null) return
        setRequestLoading(false)
        Toast.makeText(
            requireContext(),
            "Gagal mengunggah bukti pembayaran: ${exception.message}",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateConfirmationButton() {
        if (_binding == null) return
        val order = currentOrder
        binding.tvUploadHint.text = when {
            order?.status == ORDER_STATUS_EXPIRED -> "Pesanan sudah expired."
            selectedProofUri != null -> "Bukti siap dikirim ke sistem."
            order?.paymentProofUrl?.isNotBlank() == true -> "Bukti sudah dikirim. Pilih gambar baru jika ingin menggantinya."
            else -> "Setelah membayar QRIS, pilih bukti pembayaran dari galeri."
        }

        when {
            order?.status == ORDER_STATUS_EXPIRED -> {
                binding.btnKonfirmasi.isEnabled = false
                binding.btnKonfirmasi.text = "Pesanan Expired"
            }
            selectedProofUri != null -> {
                binding.btnKonfirmasi.isEnabled = true
                binding.btnKonfirmasi.text = "Kirim Bukti Pembayaran"
            }
            order?.paymentProofUrl?.isNotBlank() == true -> {
                binding.btnKonfirmasi.isEnabled = true
                binding.btnKonfirmasi.text = "Ganti Bukti Pembayaran"
            }
            else -> {
                binding.btnKonfirmasi.isEnabled = false
                binding.btnKonfirmasi.text = "Pilih Bukti Pembayaran"
            }
        }
    }

    private fun formatAndDisplayCurrency(value: String): String {
        val isNegative = value.startsWith("-")
        val cleanValue = if (isNegative) value.substring(1) else value
        val stringBuilder = StringBuilder(cleanValue)
        var i = stringBuilder.length - 3
        while (i > 0) {
            stringBuilder.insert(i, ".")
            i -= 3
        }
        return if (isNegative) "-$stringBuilder" else stringBuilder.toString()
    }

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.tvLoadingState.text = if (currentOrder == null) {
            "Memuat pesanan..."
        } else {
            "Mengunggah bukti pembayaran..."
        }
        binding.root.setFirebaseRequestLoading(isLoading, binding.loadingOverlay)
        binding.ivBuktiBayar.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
