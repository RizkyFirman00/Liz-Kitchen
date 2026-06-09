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
import com.dissy.lizkitchen.databinding.FragmentCakeDetailBinding
import com.dissy.lizkitchen.utility.availableCategories
import com.dissy.lizkitchen.utility.cakeFromMap
import com.dissy.lizkitchen.utility.normalizeProductUnit
import com.dissy.lizkitchen.utility.parseProductCategoryInput
import com.dissy.lizkitchen.utility.productCategoriesToInput
import com.dissy.lizkitchen.utility.toFirestoreMap
import com.dissy.lizkitchen.utility.createCustomTempFile
import com.dissy.lizkitchen.utility.uriToFile
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File

class CakeDetailFragment : Fragment() {
    private var _binding: FragmentCakeDetailBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private lateinit var photoPath: String
    private val storage = Firebase.storage
    private var file: File? = null
    private var documentId: String? = null
    private lateinit var imageUrlDb: String

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) openCamera()
            else Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
        }

    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) openGallery()
            else Toast.makeText(requireContext(), "Gallery permission denied", Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCakeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        documentId = arguments?.getString("documentId")
        if (documentId != null) {
            fetchCakeData(documentId!!)
        }

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnGaleri.setOnClickListener {
            startGalleryWithPermissionCheck()
        }

        binding.btnCamera.setOnClickListener {
            startCameraWithPermissionCheck()
        }

        binding.btnUpdateData.setOnClickListener {
            updateCakeData()
        }

        binding.btnDeleteData.setOnClickListener {
            deleteCakeData()
        }

        setupCurrencyFormatter()
    }

    private fun fetchCakeData(id: String) {
        db.collection("cakes").document(id).get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val cake = cakeFromMap(snapshot.id, snapshot.data ?: emptyMap<String, Any>())
            val imageUrl = snapshot.getString("imageUrl")
            imageUrlDb = imageUrl ?: ""

            binding.apply {
                etNamaKue.setText(cake.namaKue)
                etHarga.setText(cake.harga)
                etStok.setText(cake.stok.toString())
                etSatuan.setText(cake.satuan)
                etKategoriProduk.setText(productCategoriesToInput(cake.availableCategories()))
                Glide.with(this@CakeDetailFragment).load(imageUrl).into(ivBanner)
            }
        }
    }

    private fun updateCakeData() {
        val namaKue = binding.etNamaKue.text.toString()
        val harga = binding.etHarga.text.toString()
        val stok = binding.etStok.text.toString()
        val satuan = normalizeProductUnit(binding.etSatuan.text.toString())
        
        if (namaKue.isEmpty() || harga.isEmpty() || stok.isEmpty()) {
            Toast.makeText(requireContext(), "Data tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val categories = try {
            parseProductCategoryInput(binding.etKategoriProduk.text.toString(), harga, stok.toLong(), satuan)
        } catch (exception: IllegalArgumentException) {
            Toast.makeText(requireContext(), exception.message, Toast.LENGTH_SHORT).show()
            return
        }
        val primary = categories.first()
        val categoryMaps = categories.map { it.toFirestoreMap() }

        binding.progressBar2.visibility = View.VISIBLE
        
        if (file != null) {
            val imageRef = storage.reference.child("images/$namaKue")
            imageRef.putFile(Uri.fromFile(file)).addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveToFirestore(namaKue, primary.harga, primary.stok, primary.satuan, categoryMaps, uri.toString())
                }
            }
        } else {
            saveToFirestore(namaKue, primary.harga, primary.stok, primary.satuan, categoryMaps, imageUrlDb)
        }
    }

    private fun saveToFirestore(
        nama: String,
        harga: String,
        stok: Long,
        satuan: String,
        kategoriProduk: List<Map<String, Any>>,
        url: String
    ) {
        val data = mapOf(
            "namaKue" to nama,
            "harga" to harga,
            "stok" to stok,
            "satuan" to satuan,
            "kategori" to (kategoriProduk.firstOrNull()?.get("namaKategori") ?: "Default"),
            "kategoriProduk" to kategoriProduk,
            "imageUrl" to url
        )
        db.collection("cakes").document(documentId!!).update(data).addOnSuccessListener {
            if (_binding != null) binding.progressBar2.visibility = View.GONE
            Toast.makeText(requireContext(), "Data berhasil diupdate", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun deleteCakeData() {
        db.collection("cakes").document(documentId!!).delete().addOnSuccessListener {
            Toast.makeText(requireContext(), "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun setupCurrencyFormatter() {
        binding.etHarga.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (isUpdating) return
                isUpdating = true
                val cleanText = s.toString().replace(".", "")
                val formatted = formatCurrency(cleanText)
                binding.etHarga.setText(formatted)
                binding.etHarga.setSelection(formatted.length)
                isUpdating = false
            }
        })
    }

    private fun formatCurrency(value: String): String {
        val sb = StringBuilder(value)
        var i = sb.length - 3
        while (i > 0) {
            sb.insert(i, ".")
            i -= 3
        }
        return sb.toString()
    }

    private fun startCameraWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startGalleryWithPermissionCheck() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) openGallery()
        else requestGalleryPermissionLauncher.launch(permission)
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
