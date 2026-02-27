package com.dissy.lizkitchen.ui.konfirmasi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.ActivityConfirmBinding
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.home.MainActivity
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.uriToFile
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.io.File

class ConfirmActivity : BaseActivity() {
    private val db = Firebase.firestore
    private lateinit var photoPath: String
    val storage = Firebase.storage
    private var file: File? = null
    private lateinit var alamatUser: String
    private val binding by lazy { ActivityConfirmBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnToHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        val userId = Preferences.getUserId(this)
        val orderId = intent.getStringExtra("orderId") ?: ""
        Log.d("TAG", "onCreate orderID: $orderId")
        db.collection("users").document(userId ?: "").collection("orders").document(orderId)
            .get()
            .addOnSuccessListener {
                val totalHarga = it.get("totalPrice") as? Long ?: 0
                val formattedText = formatAndDisplayCurrency(totalHarga.toString())

                val userInfo = it.get("user") as? HashMap<String, Any>
                userInfo.let {
                    val username = it?.get("username") as? String ?: ""
                    val alamat = it?.get("alamat") as? String ?: ""
                    alamatUser = alamat
                    binding.tvOrderUsername.text = username
                }
                binding.apply {
                    tvOrderTotal.text = formattedText
                    tvOrderId.text = orderId
                }
            }
            .addOnFailureListener { exception ->
                Log.d("TAG", "Error getting documents: ", exception)
            }

        binding.ivBuktiBayar.setOnClickListener {
            startGallery()
        }

        binding.btnKonfirmasi.setOnClickListener {
            val orderUsername = binding.tvOrderUsername.text.toString()
            val orderTotal = binding.tvOrderTotal.text.toString()
            if (orderUsername.isNotEmpty() || orderTotal.isNotEmpty() || file != null) {
                val message = "Halo kak, saya mau konfirmasi pembayaran atas pesanan :\n" +
                        "Nama : $orderUsername\n" +
                        "Alamat : $alamatUser\n" +
                        "Order ID : $orderId\n" +
                        "Total : $orderTotal\n" +
                        "Terima kasih kak"

                val sendIntent = Intent("android.intent.action.MAIN")
                sendIntent.putExtra("jid", "6287887003907" + "@s.whatsapp.net") // Ganti dengan nomor WhatsApp yang dituju
                sendIntent.putExtra(Intent.EXTRA_STREAM,
                    file?.let { it1 -> FileProvider.getUriForFile(this, "com.dissy.lizkitchen", it1) })
                sendIntent.putExtra(Intent.EXTRA_TEXT, message)
                sendIntent.action = Intent.ACTION_SEND
                sendIntent.setPackage("com.whatsapp")
                sendIntent.type = "image/*"
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(sendIntent)
            }
        }
    }

    // Permission Function
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 123
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this, getString(R.string.permission_not_granted), Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    //Gallery Function
    private fun startGallery() {
        val intent = Intent()
        intent.action = Intent.ACTION_GET_CONTENT
        intent.type = "image/*"
        val chooser = Intent.createChooser(intent, "Choose a Picture")
        launcherIntentGallery.launch(chooser)
    }

    private val launcherIntentGallery = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val selectedImg: Uri = it.data?.data as Uri
            val myFile = uriToFile(selectedImg, this)
            file = myFile
            Glide.with(this)
                .load(selectedImg)
                .into(binding.ivBuktiBayar)
        }
    }

    private fun formatAndDisplayCurrency(value: String): String {
        // Tandai apakah angka negatif
        val isNegative = value.startsWith("-")
        val cleanValue = if (isNegative) value.substring(1) else value

        // Format ulang angka dengan menambahkan titik setiap 3 angka
        val stringBuilder = StringBuilder(cleanValue)
        val length = stringBuilder.length
        var i = length - 3
        while (i > 0) {
            stringBuilder.insert(i, ".")
            i -= 3
        }

        // Tambahkan tanda minus kembali jika angka negatif
        val formattedText = if (isNegative) {
            stringBuilder.insert(0, "-").toString()
        } else {
            stringBuilder.toString()
        }

        return formattedText
    }
}