package com.dissy.lizkitchen.ui.admin.cake

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.setMargins
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.DialogPhotoSourceBinding
import com.dissy.lizkitchen.databinding.FragmentAdminAddBinding
import com.dissy.lizkitchen.model.ProductCategory
import com.dissy.lizkitchen.utility.clearFocusWhenTouchOutsideInput
import com.dissy.lizkitchen.utility.formatProductPrice
import com.dissy.lizkitchen.utility.formatProductionDate
import com.dissy.lizkitchen.utility.limitNumericInput
import com.dissy.lizkitchen.utility.isUsableCameraImage
import com.dissy.lizkitchen.utility.PRODUCT_UNIT
import com.dissy.lizkitchen.utility.PRODUCT_VARIANT_NAMES
import com.dissy.lizkitchen.utility.prepareCameraImage
import com.dissy.lizkitchen.utility.productPriceToLong
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.toFirestoreMap
import com.dissy.lizkitchen.utility.uriToFile
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.Calendar

class AdminAddFragment : Fragment() {
    private var _binding: FragmentAdminAddBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private var cameraFile: File? = null
    private val storage = Firebase.storage
    private var file: File? = null
    private var productionAtMillis: Long = 0L
    private val variants = mutableListOf<ProductCategory>()
    private var editingVariantIndex: Int? = null

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) openCamera()
            else Toast.makeText(requireContext(), getString(R.string.permission_camera_denied), Toast.LENGTH_SHORT).show()
        }

    private val launcherIntentCamera = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@registerForActivityResult
        val photoFile = cameraFile?.takeIf(::isUsableCameraImage)
            ?: return@registerForActivityResult
        file = photoFile
        if (_binding != null) Glide.with(this).load(photoFile).into(binding.ivBanner)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraFile = savedInstanceState?.getString(STATE_CAMERA_FILE)?.let(::File)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        cameraFile?.absolutePath?.let { outState.putString(STATE_CAMERA_FILE, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.clearFocusWhenTouchOutsideInput()
        binding.etNamaVarian.setSimpleItems(PRODUCT_VARIANT_NAMES.toTypedArray())
        binding.etStokVarian.limitNumericInput(3)
        binding.etHargaVarian.limitNumericInput(9, formatThousands = true)
        productionAtMillis = startOfTodayMillis()
        binding.etTanggalProduksi.setText(formatProductionDate(productionAtMillis))
        binding.etTanggalProduksi.setOnClickListener { showProductionDatePicker() }
        binding.tilTanggalProduksi.setEndIconOnClickListener { showProductionDatePicker() }
        binding.ivBanner.setOnClickListener { showImagePickerDialog() }
        binding.btnToHome.setOnClickListener { findNavController().navigateUp() }
        binding.btnAddVarian.setOnClickListener { saveVariantFromInput() }
        binding.btnUpdateData.setOnClickListener {
            val namaKue = binding.etNamaKue.text.toString().trim()
            val shelfLifeDays = binding.etMasaSimpan.text.toString().toLongOrNull()
            val gambar = file
            if (gambar == null || namaKue.isEmpty() || variants.isEmpty() ||
                productionAtMillis <= 0L || shelfLifeDays == null || shelfLifeDays <= 0L
            ) {
                Toast.makeText(requireContext(), "Lengkapi foto, data produk, tanggal produksi, masa simpan, dan minimal 1 varian", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val categoryMaps = variants.map { it.copy(satuan = PRODUCT_UNIT).toFirestoreMap() }
            uploadImageAndGetUrl(namaKue, categoryMaps, gambar, shelfLifeDays)
        }
    }

    private fun saveVariantFromInput() {
        val name = binding.etNamaVarian.text.toString().trim()
        val stock = binding.etStokVarian.text.toString().toLongOrNull()
        val price = formatProductPrice(binding.etHargaVarian.text.toString())
        if (name !in PRODUCT_VARIANT_NAMES || stock == null || price.isEmpty() || productPriceToLong(price) <= 0L) {
            Toast.makeText(requireContext(), "Pilih nama varian, lalu isi stok dan harga", Toast.LENGTH_SHORT).show()
            return
        }
        val variant = ProductCategory(name, price, stock, PRODUCT_UNIT)
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
        binding.etNamaVarian.setText(
            PRODUCT_VARIANT_NAMES.firstOrNull { it.equals(variant.namaKategori, ignoreCase = true) }
                ?: variant.namaKategori,
            false
        )
        binding.etStokVarian.setText(variant.stok.toString())
        binding.etHargaVarian.setText(variant.harga)
        binding.btnAddVarian.text = "Simpan Perubahan Varian"
    }

    private fun clearVariantInput() {
        binding.etNamaVarian.text?.clear()
        binding.etStokVarian.text?.clear()
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

        val stats = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }
        listOf(
            "Stok" to variant.stok.toString(),
            "Satuan" to PRODUCT_UNIT,
            "Harga/satuan" to "Rp. ${variant.harga}"
        ).forEach { (label, value) -> stats.addView(createInfoPill(label, value, dp)) }

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
            layoutParams = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f).apply {
                setMargins((4 * dp).toInt())
            }
        }
    }

    private fun showImagePickerDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogPhotoSourceBinding.inflate(layoutInflater)
        dialogBinding.tvPhotoSourceTitle.text = "Tambah Foto Kue"
        dialogBinding.tvPhotoSourceDescription.text = "Pilih sumber foto produk yang ingin digunakan."
        dialogBinding.btnPhotoGallery.setOnClickListener {
            dialog.dismiss()
            startGalleryWithPermissionCheck()
        }
        dialogBinding.btnPhotoCamera.setOnClickListener {
            dialog.dismiss()
            startCameraWithPermissionCheck()
        }
        dialogBinding.btnPhotoCancel.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(dialogBinding.root)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.from(it).skipCollapsed = true
            }
        }
        dialog.show()
    }

    private fun uploadImageAndGetUrl(
        namaKue: String,
        kategoriProduk: List<Map<String, Any>>,
        gambar: File,
        shelfLifeDays: Long
    ) {
        setRequestLoading(true)
        val imageRef = storage.reference.child("images/$namaKue")
        imageRef.putFile(Uri.fromFile(gambar)).addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener { uri ->
                val data = hashMapOf(
                    "namaKue" to namaKue,
                    "satuan" to PRODUCT_UNIT,
                    "kategoriProduk" to kategoriProduk,
                    "imageUrl" to uri.toString(),
                    "productionAtMillis" to productionAtMillis,
                    "shelfLifeDays" to shelfLifeDays
                )
                db.collection("cakes").add(data).addOnSuccessListener { documentReference ->
                    db.collection("cakes").document(documentReference.id).update("documentId", documentReference.id).addOnSuccessListener {
                        if (_binding != null) setRequestLoading(false)
                        Toast.makeText(requireContext(), "Data berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }.addOnFailureListener { exception ->
                        handleUploadFailure(exception)
                    }
                }.addOnFailureListener { exception ->
                    handleUploadFailure(exception)
                }
            }.addOnFailureListener { exception ->
                handleUploadFailure(exception)
            }
        }.addOnFailureListener { exception ->
            handleUploadFailure(exception)
        }
    }

    private fun handleUploadFailure(exception: Exception) {
        Log.e("AdminAddFragment", "Error uploading cake data", exception)
        if (_binding != null) setRequestLoading(false)
        Toast.makeText(requireContext(), "Gagal menyimpan data kue", Toast.LENGTH_SHORT).show()
    }

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.root.setFirebaseRequestLoading(isLoading, binding.progressBar2)
    }

    private fun showProductionDatePicker() {
        val calendar = Calendar.getInstance().apply {
            if (productionAtMillis > 0L) timeInMillis = productionAtMillis
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                productionAtMillis = calendar.timeInMillis
                binding.etTanggalProduksi.setText(formatProductionDate(productionAtMillis))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun startOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startCameraWithPermissionCheck() { if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera() else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
    private fun startGalleryWithPermissionCheck() = openGallery()

    private fun openCamera() {
        runCatching {
            val (photoFile, photoUri) = requireContext().prepareCameraImage()
            cameraFile = photoFile
            launcherIntentCamera.launch(photoUri)
        }.onFailure {
            cameraFile = null
            Toast.makeText(requireContext(), "Kamera tidak dapat dibuka", Toast.LENGTH_SHORT).show()
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
                Log.e("AdminAddFragment", "Error selecting gallery image", exception)
                Toast.makeText(requireContext(), "Gagal memuat foto dari galeri", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private companion object {
        const val STATE_CAMERA_FILE = "admin_add_camera_file"
    }
}
