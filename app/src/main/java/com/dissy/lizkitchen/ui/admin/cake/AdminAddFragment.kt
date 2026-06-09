package com.dissy.lizkitchen.ui.admin.cake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.FragmentAdminAddBinding
import com.dissy.lizkitchen.utility.normalizeProductUnit
import com.dissy.lizkitchen.utility.parseProductCategoryInput
import com.dissy.lizkitchen.utility.toFirestoreMap
import com.dissy.lizkitchen.utility.createCustomTempFile
import com.dissy.lizkitchen.utility.uriToFile
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File

class AdminAddFragment : Fragment() {
    private var _binding: FragmentAdminAddBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private lateinit var photoPath: String
    private val storage = Firebase.storage
    private var file: File? = null

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(requireContext(), getString(R.string.permission_camera_denied), Toast.LENGTH_SHORT).show()
            }
        }

    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openGallery()
            } else {
                Toast.makeText(requireContext(), getString(R.string.permission_gallery_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etSatuan.setText("pcs")

        // Logika Currency Formatter
        val etHargaKue: EditText = binding.etHarga
        etHargaKue.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (isUpdating) return
                isUpdating = true
                val cleanText = s.toString().replace(".", "")
                val formattedText = formatCurrency(cleanText)
                etHargaKue.setText(formattedText)
                etHargaKue.setSelection(formattedText.length)
                isUpdating = false
            }

            private fun formatCurrency(value: String): String {
                var isNegative = false
                var cleanValue = value
                if (cleanValue.startsWith("-")) {
                    isNegative = true
                    cleanValue = cleanValue.substring(1)
                }
                val stringBuilder = StringBuilder(cleanValue)
                var i = stringBuilder.length - 3
                while (i > 0) {
                    stringBuilder.insert(i, ".")
                    i -= 3
                }
                if (isNegative) stringBuilder.insert(0, "-")
                return stringBuilder.toString()
            }
        })

        binding.ivBanner.setOnClickListener {
            showImagePickerDialog()
        }

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnUpdateData.setOnClickListener {
            val namaKue = binding.etNamaKue.text.toString()
            val harga = binding.etHarga.text.toString()
            val stok = binding.etStok.text.toString()
            val satuan = normalizeProductUnit(binding.etSatuan.text.toString())
            val gambar = file
            if (gambar != null && namaKue.isNotEmpty() && harga.isNotEmpty() && stok.isNotEmpty()) {
                try {
                    val categories = parseProductCategoryInput(
                        binding.etKategoriProduk.text.toString(),
                        harga,
                        stok.toLong(),
                        satuan
                    )
                    uploadImageAndGetUrl(namaKue, categories.first().harga, categories.first().stok, categories.first().satuan, categories.map { it.toFirestoreMap() }, gambar)
                } catch (exception: IllegalArgumentException) {
                    Toast.makeText(requireContext(), exception.message, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Data tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImagePickerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_image_picker, null)
        val dialog = AlertDialog.Builder(requireContext())
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

    private fun uploadImageAndGetUrl(
        namaKue: String,
        harga: String,
        stok: Long,
        satuan: String,
        kategoriProduk: List<Map<String, Any>>,
        gambar: File
    ) {
        binding.apply {
            progressBar2.visibility = View.VISIBLE
            etNamaKue.isEnabled = false
            etHarga.isEnabled = false
            etStok.isEnabled = false
            etSatuan.isEnabled = false
            etKategoriProduk.isEnabled = false
        }
        val storageRef = storage.reference
        val imageRef = storageRef.child("images/${namaKue}")
        val uploadTask = imageRef.putFile(Uri.fromFile(gambar))

        uploadTask.addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener { uri ->
                val url = uri.toString()
                val data = hashMapOf(
                    "namaKue" to namaKue,
                    "harga" to harga,
                    "stok" to stok,
                    "satuan" to satuan,
                    "kategori" to (kategoriProduk.firstOrNull()?.get("namaKategori") ?: "Default"),
                    "kategoriProduk" to kategoriProduk,
                    "imageUrl" to url
                )
                db.collection("cakes")
                    .add(data)
                    .addOnSuccessListener { documentReference ->
                        val generatedDocumentId = documentReference.id
                        db.collection("cakes").document(generatedDocumentId)
                            .update("documentId", generatedDocumentId)
                            .addOnSuccessListener {
                                if (_binding != null) {
                                    binding.apply {
                                        progressBar2.visibility = View.GONE
                                        etNamaKue.isEnabled = true
                                        etHarga.isEnabled = true
                                        etStok.isEnabled = true
                                        etSatuan.isEnabled = true
                                        etKategoriProduk.isEnabled = true
                                    }
                                }
                                Toast.makeText(requireContext(), "Data berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }
                    }
            }
        }
    }

    private fun startCameraWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
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

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            requestGalleryPermissionLauncher.launch(permission)
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        createCustomTempFile(requireActivity().application).also {
            val photoURI: Uri = FileProvider.getUriForFile(requireContext(), "com.dissy.lizkitchen", it)
            photoPath = it.absolutePath
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            launcherIntentCamera.launch(intent)
        }
    }

    private val launcherIntentCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val myFile = File(photoPath)
            file = myFile
            Glide.with(this).load(BitmapFactory.decodeFile(myFile.path)).into(binding.ivBanner)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        val chooser = Intent.createChooser(intent, "Choose a Picture")
        launcherIntentGallery.launch(chooser)
    }

    private val launcherIntentGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val selectedImg: Uri = it.data?.data ?: return@registerForActivityResult
            file = uriToFile(selectedImg, requireContext())
            Glide.with(this).load(selectedImg).into(binding.ivBanner)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
