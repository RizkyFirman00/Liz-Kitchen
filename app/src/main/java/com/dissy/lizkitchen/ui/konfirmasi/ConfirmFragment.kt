package com.dissy.lizkitchen.ui.konfirmasi

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.databinding.FragmentConfirmBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.METODE_AMBIL_SENDIRI
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.pickupBranchAddressForOrder
import com.dissy.lizkitchen.utility.pickupBranchNameForOrder
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.uriToFile
import com.dissy.lizkitchen.utility.validateOrderExpiryOnRead
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.io.File

class ConfirmFragment : Fragment() {
    private var _binding: FragmentConfirmBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private var file: File? = null
    private var orderId: String? = null
    private var currentOrder: Order? = null

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
                        tvOrderUsername.text = order.user.username.orEmpty()
                        tvOrderTotal.text = formattedText
                        tvOrderId.text = orderId
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

        binding.ivBuktiBayar.setOnClickListener {
            startGallery()
        }

        binding.btnKonfirmasi.setOnClickListener {
            confirmPayment()
        }
    }

    private fun startGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        val chooser = Intent.createChooser(intent, "Choose a Picture")
        launcherIntentGallery.launch(chooser)
    }

    private val launcherIntentGallery = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedImg: Uri = result.data?.data ?: return@registerForActivityResult
            file = uriToFile(selectedImg, requireContext())
            Glide.with(this).load(selectedImg).into(binding.ivBuktiBayar)
        }
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

        if (file != null) {
            val message = buildWhatsAppMessage(order)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra("jid", "6287887003907@s.whatsapp.net")
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(requireContext(), "com.dissy.lizkitchen", file!!))
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(sendIntent)
            } catch (exception: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "WhatsApp tidak terpasang di perangkat ini", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Lengkapi data dan bukti pembayaran", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildWhatsAppMessage(order: Order): String {
        val orderTotal = binding.tvOrderTotal.text.toString().ifBlank {
            formatAndDisplayCurrency(order.totalPrice.toString())
        }
        val totalText = if (orderTotal.startsWith("Rp", ignoreCase = true)) {
            orderTotal
        } else {
            "Rp $orderTotal"
        }
        val customerName = order.user.username.orEmpty().ifBlank { "Pelanggan" }
        val customerPhone = order.user.phoneNumber.orEmpty().ifBlank { "-" }
        val displayOrderId = order.orderId.ifBlank { orderId.orEmpty() }
        val isPickup = order.metodePengambilan.contains("ambil", ignoreCase = true)
        val methodText = if (isPickup) {
            METODE_AMBIL_SENDIRI
        } else {
            order.metodePengambilan.ifBlank { "-" }
        }

        return buildString {
            appendLine("Halo kak, saya ingin konfirmasi pembayaran pesanan Liz Kitchen.")
            appendLine()
            appendLine("Nama: $customerName")
            appendLine("No. HP: $customerPhone")
            appendLine("Order ID: $displayOrderId")
            appendLine("Metode: $methodText")
            if (isPickup) {
                appendLine("Cabang Pengambilan: ${pickupBranchNameForOrder(order)}")
                appendLine("Alamat Cabang: ${pickupBranchAddressForOrder(order)}")
            } else {
                appendLine("Alamat Pengiriman: ${order.user.alamat.orEmpty().ifBlank { "-" }}")
            }
            appendLine("Total Pembayaran: $totalText")
            appendLine()
            appendLine("Bukti pembayaran saya lampirkan di pesan ini.")
            append("Terima kasih kak.")
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
        binding.root.setFirebaseRequestLoading(isLoading)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
