package com.dissy.lizkitchen.ui.admin.cake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.setMargins
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.databinding.FragmentCakeDetailBinding
import com.dissy.lizkitchen.model.ProductCategory
import com.dissy.lizkitchen.utility.availableCategories
import com.dissy.lizkitchen.utility.cakeFromMap
import com.dissy.lizkitchen.utility.clearFocusWhenTouchOutsideInput
import com.dissy.lizkitchen.utility.createCustomTempFile
import com.dissy.lizkitchen.utility.formatProductPrice
import com.dissy.lizkitchen.utility.normalizeProductUnit
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.toFirestoreMap
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
    private val variants = mutableListOf<ProductCategory>()
    private var editingVariantIndex: Int? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openCamera() else Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCakeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.clearFocusWhenTouchOutsideInput()
        documentId = arguments?.getString("documentId")
        documentId?.let { fetchCakeData(it) }
        binding.btnToHome.setOnClickListener { findNavController().navigateUp() }
        binding.btnGaleri.setOnClickListener { startGalleryWithPermissionCheck() }
        binding.btnCamera.setOnClickListener { startCameraWithPermissionCheck() }
        binding.btnAddVarian.setOnClickListener { saveVariantFromInput() }
        binding.btnUpdateData.setOnClickListener { updateCakeData() }
        binding.btnDeleteData.setOnClickListener { deleteCakeData() }
    }

    private fun fetchCakeData(id: String) {
        setRequestLoading(true)
        db.collection("cakes").document(id).get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            setRequestLoading(false)
            val cake = cakeFromMap(snapshot.id, snapshot.data ?: emptyMap<String, Any>())
            val imageUrl = snapshot.getString("imageUrl")
            imageUrlDb = imageUrl ?: ""
            variants.clear()
            variants.addAll(cake.availableCategories())
            binding.etNamaKue.setText(cake.namaKue)
            renderVariants()
            Glide.with(this@CakeDetailFragment).load(imageUrl).into(binding.ivBanner)
        }.addOnFailureListener { exception ->
            handleSaveFailure(exception)
        }
    }

    private fun saveVariantFromInput() {
        val name = binding.etNamaVarian.text.toString().trim()
        val stock = binding.etStokVarian.text.toString().toLongOrNull()
        val unit = normalizeProductUnit(binding.etSatuanVarian.text.toString())
        val price = formatProductPrice(binding.etHargaVarian.text.toString())
        if (name.isEmpty() || stock == null || price.isEmpty()) {
            Toast.makeText(requireContext(), "Nama varian, stok, dan harga wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        val variant = ProductCategory(name, price, stock, unit)
        val editIndex = editingVariantIndex
        if (editIndex == null) variants.add(variant) else variants[editIndex] = variant
        editingVariantIndex = null
        binding.btnAddVarian.text = "Tambah Varian"
        clearVariantInput()
        renderVariants()
    }

    private fun editVariant(index: Int) {
        val variant = variants[index]
        editingVariantIndex = index
        binding.etNamaVarian.setText(variant.namaKategori)
        binding.etStokVarian.setText(variant.stok.toString())
        binding.etSatuanVarian.setText(variant.satuan)
        binding.etHargaVarian.setText(variant.harga)
        binding.btnAddVarian.text = "Simpan Perubahan Varian"
    }

    private fun clearVariantInput() {
        binding.etNamaVarian.text?.clear()
        binding.etStokVarian.text?.clear()
        binding.etSatuanVarian.text?.clear()
        binding.etHargaVarian.text?.clear()
    }

    private fun renderVariants() {
        binding.variantListContainer.removeAllViews()
        variants.forEachIndexed { index, variant ->
            binding.variantListContainer.addView(createVariantRow(index, variant))
        }
    }

    private fun createVariantRow(index: Int, variant: ProductCategory): View {
        val dp = resources.displayMetrics.density
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((1 * dp).toInt(), Color.parseColor("#EED8C8"))
                cornerRadius = 8 * dp
            }
            elevation = 2 * dp
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (10 * dp).toInt()) }
        }

        // --- HEADER: badge nomor, nama varian, harga ---
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val badgeSize = (36 * dp).toInt()
        val numberBadge = TextView(requireContext()).apply {
            text = (index + 1).toString()
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#9C6843"))
                shape = GradientDrawable.OVAL
            }
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                setMargins(0, 0, (12 * dp).toInt(), 0)
            }
        }
        val title = TextView(requireContext()).apply {
            text = variant.namaKategori
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#4A2F1D"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(numberBadge)
        header.addView(title)

        // --- STATS PILLS: stok, satuan, harga ---
        val stats = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }
        stats.addView(createInfoPill("Stok", variant.stok.toString(), dp))
        stats.addView(createInfoPill("Satuan", variant.satuan, dp))
        stats.addView(createInfoPill("Harga/satuan", "Rp. ${variant.harga}", dp))

        // --- ACTION BUTTONS: edit & hapus ---
        val actions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        actions.addView(createActionButton("Edit", "#9C6843", dp) { editVariant(index) })
        actions.addView(createActionButton("Hapus", "#D10826", dp) {
            variants.removeAt(index)
            if (editingVariantIndex == index) {
                editingVariantIndex = null
                binding.btnAddVarian.text = "Tambah Varian"
                clearVariantInput()
            }
            renderVariants()
        })

        card.addView(header)
        card.addView(stats)
        card.addView(actions)
        return card
    }

    private fun createInfoPill(label: String, value: String, dp: Float): View {
        val pill = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF6EF"))
                cornerRadius = 8 * dp
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((4 * dp).toInt())
            }
        }
        pill.addView(TextView(requireContext()).apply {
            text = label
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8A7567"))
        })
        pill.addView(TextView(requireContext()).apply {
            text = value
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#5D3A24"))
        })
        return pill
    }

    private fun createActionButton(label: String, color: String, dp: Float, onClick: () -> Unit): AppCompatButton {
        return AppCompatButton(requireContext()).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            minHeight = 0
            minimumHeight = 0
            background = GradientDrawable().apply {
                setColor(Color.parseColor(color))
                cornerRadius = 8 * dp
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                0, (40 * dp).toInt(), 1f
            ).apply { setMargins((4 * dp).toInt()) }
        }
    }

    private fun updateCakeData() {
        val namaKue = binding.etNamaKue.text.toString().trim()
        if (namaKue.isEmpty() || variants.isEmpty()) {
            Toast.makeText(requireContext(), "Nama kue dan minimal 1 varian wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        setRequestLoading(true)
        val categoryMaps = variants.map { it.toFirestoreMap() }
        if (file != null) {
            val imageRef = storage.reference.child("images/$namaKue")
            imageRef.putFile(Uri.fromFile(file)).addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri -> saveToFirestore(namaKue, categoryMaps, uri.toString()) }
                    .addOnFailureListener { handleSaveFailure(it) }
            }.addOnFailureListener { handleSaveFailure(it) }
        } else {
            saveToFirestore(namaKue, categoryMaps, imageUrlDb)
        }
    }

    private fun saveToFirestore(nama: String, kategoriProduk: List<Map<String, Any>>, url: String) {
        val data = mapOf("namaKue" to nama, "kategoriProduk" to kategoriProduk, "imageUrl" to url)
        db.collection("cakes").document(documentId!!).update(data).addOnSuccessListener {
            if (_binding != null) setRequestLoading(false)
            Toast.makeText(requireContext(), "Data berhasil diupdate", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }.addOnFailureListener { handleSaveFailure(it) }
    }

    private fun handleSaveFailure(exception: Exception) {
        Log.e("CakeDetailFragment", "Error saving cake data", exception)
        if (_binding != null) setRequestLoading(false)
        Toast.makeText(requireContext(), "Data gagal disimpan", Toast.LENGTH_SHORT).show()
    }

    private fun deleteCakeData() {
        setRequestLoading(true)
        db.collection("cakes").document(documentId!!).delete().addOnSuccessListener {
            setRequestLoading(false)
            Toast.makeText(requireContext(), "Data berhasil dihapus", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }.addOnFailureListener { exception ->
            handleSaveFailure(exception)
        }
    }

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.root.setFirebaseRequestLoading(isLoading, binding.progressBar2)
    }

    private fun startCameraWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startGalleryWithPermissionCheck() = openGallery()

    @SuppressLint("QueryPermissionsNeeded")
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        createCustomTempFile(requireActivity().application).also {
            val photoURI = FileProvider.getUriForFile(requireContext(), "com.dissy.lizkitchen", it)
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
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launcherIntentGallery.launch(Intent.createChooser(intent, "Choose a Picture"))
    }

    private val launcherIntentGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val selectedImg = it.data?.data ?: return@registerForActivityResult
            runCatching {
                file = uriToFile(selectedImg, requireContext())
                Glide.with(this).load(selectedImg).into(binding.ivBanner)
            }.onFailure { exception ->
                Log.e("CakeDetailFragment", "Error selecting gallery image", exception)
                Toast.makeText(requireContext(), "Gagal memuat foto dari galeri", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
