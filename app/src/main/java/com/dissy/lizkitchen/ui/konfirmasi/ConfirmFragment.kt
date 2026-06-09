package com.dissy.lizkitchen.ui.konfirmasi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.uriToFile
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.io.File

class ConfirmFragment : Fragment() {
    private var _binding: FragmentConfirmBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private var file: File? = null
    private var alamatUser: String = ""
    private var orderId: String? = null

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
            db.collection("users").document(userId).collection("orders").document(orderId!!)
                .get()
                .addOnSuccessListener {
                    val totalHarga = it.getLong("totalPrice") ?: 0
                    val formattedText = formatAndDisplayCurrency(totalHarga.toString())

                    val userInfo = it.get("user") as? HashMap<String, Any>
                    val username = userInfo?.get("username") as? String ?: ""
                    alamatUser = userInfo?.get("alamat") as? String ?: ""

                    binding.apply {
                        tvOrderUsername.text = username
                        tvOrderTotal.text = formattedText
                        tvOrderId.text = orderId
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
        val orderUsername = binding.tvOrderUsername.text.toString()
        val orderTotal = binding.tvOrderTotal.text.toString()

        if (orderUsername.isNotEmpty() && orderTotal.isNotEmpty() && file != null) {
            val message = "Halo kak, saya mau konfirmasi pembayaran atas pesanan :\n" +
                    "Nama : $orderUsername\n" +
                    "Alamat : $alamatUser\n" +
                    "Order ID : $orderId\n" +
                    "Total : $orderTotal\n" +
                    "Terima kasih kak"

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra("jid", "6287887003907@s.whatsapp.net")
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(requireContext(), "com.dissy.lizkitchen", file!!))
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(sendIntent)
        } else {
            Toast.makeText(requireContext(), "Lengkapi data dan bukti pembayaran", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}