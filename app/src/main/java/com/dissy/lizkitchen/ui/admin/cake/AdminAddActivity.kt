package com.dissy.lizkitchen.ui.admin.cake

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.ActivityAdminAddBinding
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.utility.createCustomTempFile
import com.dissy.lizkitchen.utility.uriToFile
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File

class AdminAddActivity : BaseActivity() {
    private val db = Firebase.firestore
    private lateinit var photoPath: String
    val storage = Firebase.storage
    private var file: File? = null
    private val binding by lazy { ActivityAdminAddBinding.inflate(layoutInflater) }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(this, getString(R.string.permission_camera_denied), Toast.LENGTH_SHORT).show()
            }
        }

    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openGallery()
            } else {
                Toast.makeText(this, getString(R.string.permission_gallery_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Logika Currency Formatter
        val etHargaKue: EditText = binding.etHarga
        etHargaKue.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                if (isUpdating) {
                    return
                }

                isUpdating = true

                val originalText = s.toString()

                // Hapus semua tanda titik sebelum memformat angka
                val cleanText = originalText.replace(".", "")

                // Format ulang angka dengan menambahkan titik setiap 3 angka
                val formattedText = formatCurrency(cleanText)

                // Set teks yang telah diformat ke EditText
                etHargaKue.setText(formattedText)

                // Posisikan kursor di akhir teks
                etHargaKue.setSelection(formattedText.length)

                isUpdating = false
            }

            private fun formatCurrency(value: String): String {
                // Hapus tanda minus jika ada
                var isNegative = false
                var cleanValue = value
                if (cleanValue.startsWith("-")) {
                    isNegative = true
                    cleanValue = cleanValue.substring(1)
                }

                // Format ulang angka dengan menambahkan titik setiap 3 angka
                val stringBuilder = StringBuilder(cleanValue)
                val length = stringBuilder.length
                var i = length - 3
                while (i > 0) {
                    stringBuilder.insert(i, ".")
                    i -= 3
                }

                // Tambahkan tanda minus kembali jika angka negatif
                if (isNegative) {
                    stringBuilder.insert(0, "-")
                }

                return stringBuilder.toString()
            }
        })

        binding.ivBanner.setOnClickListener {
            showImagePickerDialog()
        }

        binding.btnToHome.setOnClickListener {
            Intent(this, AdminCakeActivity::class.java).also {
                startActivity(it)
                finish()
            }
        }

        binding.btnUpdateData.setOnClickListener {
            val namaKue = binding.etNamaKue.text.toString()
            val harga = binding.etHarga.text.toString()
            val stok = binding.etStok.text.toString()
            val gambar = file
            if (gambar != null && namaKue.isNotEmpty() && harga.isNotEmpty() && stok.isNotEmpty()) {
                uploadImageAndGetUrl(namaKue, harga, stok.toLong(), gambar)
            } else {
                Toast.makeText(this, "Data tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImagePickerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_picker, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.btn_dialog_camera).setOnClickListener {
            startCameraWithPermissionCheck()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_dialog_gallery).setOnClickListener {
            startGalleryWithPermissionCheck()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun uploadImageAndGetUrl(namaKue: String, harga: String, stok: Long, gambar: File) {
        binding.apply {
            progressBar2.visibility = View.VISIBLE
            etNamaKue.isEnabled = false
            etHarga.isEnabled = false
            etStok.isEnabled = false
        }
        val storageRef = storage.reference
        val imageRef = storageRef.child("images/${namaKue}")
        val uploadTask = imageRef.putFile(Uri.fromFile(gambar))

        uploadTask.addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener { uri ->
                val url = uri.toString()
                Log.d("AdminAddActivity", "URL: $url")
                val data = hashMapOf(
                    "namaKue" to namaKue,
                    "harga" to harga,
                    "stok" to stok,
                    "imageUrl" to url
                )
                db.collection("cakes")
                    .add(data)
                    .addOnSuccessListener { documentReference ->
                        val generatedDocumentId = documentReference.id
                        db.collection("cakes").document(generatedDocumentId)
                            .update("documentId", generatedDocumentId)
                            .addOnSuccessListener {
                                binding.apply {
                                    progressBar2.visibility = View.GONE
                                    etNamaKue.isEnabled = true
                                    etHarga.isEnabled = true
                                    etStok.isEnabled = true
                                }
                                Log.d(
                                    "AdminAddActivity",
                                    "DocumentSnapshot added with ID: $generatedDocumentId"
                                )
                                Toast.makeText(
                                    this,
                                    "Data berhasil ditambahkan",
                                    Toast.LENGTH_SHORT
                                ).show()
                                Intent(this, AdminCakeActivity::class.java).also {
                                    startActivity(it)
                                    finish()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w("AdminAddActivity", "Error updating document", e)
                                Toast.makeText(
                                    this,
                                    "Error updating document: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        Toast.makeText(this, "Data berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                        Intent(this, AdminCakeActivity::class.java).also {
                            startActivity(it)
                            finish()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w("AdminAddActivity", "Error adding document", e)
                        Toast.makeText(
                            this,
                            "Error adding document: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }

    //Permission Function
    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            baseContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCameraWithPermissionCheck() {
        if (isPermissionGranted(Manifest.permission.CAMERA)) {
            openCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startGalleryWithPermissionCheck() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (isPermissionGranted(permission)) {
            openGallery()
        } else {
            requestGalleryPermissionLauncher.launch(permission)
        }
    }

    //Camera Function
    @SuppressLint("QueryPermissionsNeeded")
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.resolveActivity(packageManager)
        createCustomTempFile(application).also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this, "com.dissy.lizkitchen", it
            )
            photoPath = it.absolutePath
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            launcherIntentCamera.launch(intent)
        }
    }

    private val launcherIntentCamera = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val myFile = File(photoPath)
            file = myFile
            val result = BitmapFactory.decodeFile(myFile.path)
            Glide.with(this)
                .load(result)
                .into(binding.ivBanner)
        }
    }

    //Gallery Function
    private fun openGallery() {
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
                .into(binding.ivBanner)
        }
    }
}
