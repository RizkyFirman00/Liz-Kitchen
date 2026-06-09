package com.dissy.lizkitchen.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.FragmentCakeDetailUserBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.ProductCategory
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.availableCategories
import com.dissy.lizkitchen.utility.cakeFromMap
import com.dissy.lizkitchen.utility.cartDocumentId
import com.dissy.lizkitchen.utility.displayUnit
import com.dissy.lizkitchen.utility.productPriceToLong
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class CakeDetailUserFragment : Fragment() {
    private var _binding: FragmentCakeDetailUserBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val userCollection = db.collection("users")
    private var jumlahPesanan = 1
    private var hargaPerSatuan = 0L
    private var stok = 0
    private lateinit var imageUrlDb: String
    private var cakeIdDb: String = ""
    private var namaKueDb: String = ""
    private var selectedCategory = ProductCategory()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCakeDetailUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userId = Preferences.getUserId(requireContext())
        val cakeId = arguments?.getString("cakeId")

        if (cakeId != null) {
            binding.progressBar2.visibility = View.VISIBLE
            db.collection("cakes")
                .document(cakeId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("Firestore", "Listen failed.", error)
                        return@addSnapshotListener
                    }
                    if (_binding != null && snapshot != null && snapshot.exists()) {
                        binding.progressBar2.visibility = View.GONE
                        val cake = cakeFromMap(snapshot.id, snapshot.data ?: emptyMap<String, Any>())
                        cakeIdDb = cake.documentId
                        namaKueDb = cake.namaKue
                        imageUrlDb = cake.imageUrl
                        binding.apply {
                            tvCakeName.text = cake.namaKue
                            tvJumlahPesanan.text = jumlahPesanan.toString()
                            Glide.with(this@CakeDetailUserFragment)
                                .load(cake.imageUrl)
                                .into(ivImageBanner)
                        }
                        setupVariantPicker(cake)
                    }
                }
        } else {
            findNavController().navigateUp()
        }

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnPlus.setOnClickListener {
            increaseQuantity()
        }

        binding.btnMinus.setOnClickListener {
            decreaseQuantity()
        }

        binding.btnAddCart.setOnClickListener {
            if (userId == null) return@setOnClickListener
            binding.progressBar2.visibility = View.VISIBLE
            binding.btnAddCart.isEnabled = false

            if (jumlahPesanan <= 0) {
                binding.progressBar2.visibility = View.GONE
                binding.btnAddCart.isEnabled = true
                Toast.makeText(requireContext(), "Jumlah pesanan belum dipilih", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (jumlahPesanan > stok) {
                binding.progressBar2.visibility = View.GONE
                binding.btnAddCart.isEnabled = true
                Toast.makeText(requireContext(), "Stok tidak mencukupi, Stok = $stok", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cartItemId = cartDocumentId(cakeId.toString(), selectedCategory.namaKategori)
            val cartRef = userCollection.document(userId).collection("cart").document(cartItemId)
            cartRef.get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val existingQuantity = documentSnapshot.getLong("jumlahPesanan") ?: 0
                    val newQuantity = existingQuantity + jumlahPesanan
                    if (newQuantity > stok) {
                        binding.progressBar2.visibility = View.GONE
                        binding.btnAddCart.isEnabled = true
                        Toast.makeText(requireContext(), "Stok tidak mencukupi, Stok = $stok", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    cartRef.update("jumlahPesanan", newQuantity)
                        .addOnSuccessListener {
                            onSuccessAddCart()
                        }
                } else {
                    cartRef.set(
                        hashMapOf(
                            "cakeId" to cakeId,
                            "cake" to Cake(
                                documentId = cakeId ?: "",
                                harga = selectedCategory.harga,
                                imageUrl = imageUrlDb,
                                namaKue = namaKueDb,
                                stok = selectedCategory.stok,
                                satuan = selectedCategory.satuan,
                                kategori = selectedCategory.namaKategori
                            ),
                            "jumlahPesanan" to jumlahPesanan,
                        )
                    ).addOnSuccessListener {
                        onSuccessAddCart()
                    }
                }
            }
        }
    }

    private fun setupVariantPicker(cake: Cake) {
        val categories = cake.availableCategories()
        val shouldShowVariants = categories.size > 1 ||
            categories.firstOrNull()?.namaKategori?.equals("Default", ignoreCase = true) == false ||
            categories.firstOrNull()?.satuan != "pcs"

        binding.categoryContainer.visibility = if (shouldShowVariants) View.VISIBLE else View.GONE
        binding.chipGroupVariant.removeAllViews()

        categories.forEachIndexed { index, category ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category.namaKategori
                isCheckable = true
                isClickable = true
                isCheckedIconVisible = false
                chipStrokeWidth = 1f
                chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.variant_chip_background_selector)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.variant_chip_text_selector))
                chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.variant_chip_text_selector)
                setOnClickListener {
                    jumlahPesanan = if (category.stok > 0) 1 else 0
                    selectedCategory = category
                    updateSelectedCategory()
                }
            }
            binding.chipGroupVariant.addView(chip)
            if (index == 0) {
                binding.chipGroupVariant.check(chip.id)
            }
        }

        selectedCategory = categories.first()
        updateSelectedCategory()
    }

    private fun updateSelectedCategory() {
        hargaPerSatuan = productPriceToLong(selectedCategory.harga)
        stok = selectedCategory.stok.toInt()
        val displayUnit = selectedCategory.displayUnit()
        binding.tvPriceCake.text = selectedCategory.harga
        binding.tvUnitCake.text = "/$displayUnit"
        binding.tvUnitTotal.text = " /$displayUnit"
        binding.tvVariantInfo.text = "Stok $stok | Rp. ${selectedCategory.harga} /$displayUnit"
        if (stok <= 0) {
            jumlahPesanan = 0
        } else if (jumlahPesanan > stok) {
            jumlahPesanan = stok
        } else if (jumlahPesanan == 0) {
            jumlahPesanan = 1
        }
        updateQuantityAndPrice()
    }

    private fun onSuccessAddCart() {
        if (_binding == null) return
        binding.progressBar2.visibility = View.GONE
        binding.btnAddCart.isEnabled = true
        Toast.makeText(requireContext(), "Berhasil menambahkan ke keranjang", Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    private fun increaseQuantity() {
        if (jumlahPesanan >= stok) {
            Toast.makeText(requireContext(), "Stok tidak mencukupi, Stok = $stok", Toast.LENGTH_SHORT).show()
            return
        }
        jumlahPesanan++
        updateQuantityAndPrice()
    }

    private fun decreaseQuantity() {
        if (jumlahPesanan > 1) {
            jumlahPesanan--
            updateQuantityAndPrice()
        }
    }

    private fun updateQuantityAndPrice() {
        binding.tvJumlahPesanan.text = jumlahPesanan.toString()
        val totalHarga = hargaPerSatuan * jumlahPesanan
        binding.tvPriceSum.text = formatAndDisplayCurrency(totalHarga.toString())
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
