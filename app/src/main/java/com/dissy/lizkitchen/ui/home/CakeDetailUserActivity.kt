package com.dissy.lizkitchen.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.databinding.ActivityCakeDetailUserBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.ui.admin.user.AdminUserOrderActivity
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.NumberFormat
import java.util.Locale
import kotlin.properties.Delegates

class CakeDetailUserActivity : BaseActivity() {
    private val binding by lazy { ActivityCakeDetailUserBinding.inflate(layoutInflater) }
    private val db = Firebase.firestore
    private val userCollection = db.collection("users")
    private var jumlahPesanan = 0
    private var hargaPerPcs = 0
    private var stok = 0
    private lateinit var imageUrlDb: String
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val userId = Preferences.getUserId(this)
        val cakeId = intent.getStringExtra("cakeId")
        if (cakeId != null) {
            binding.apply {
                progressBar2.visibility = View.VISIBLE
            }
            db.collection("cakes")
                .document(cakeId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("Firestore", "Listen failed.", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        binding.apply {
                            progressBar2.visibility = View.GONE
                        }
                        val namaKue = snapshot.getString("namaKue")
                        val hargaKue = snapshot.getString("harga")
                        hargaPerPcs = hargaKue?.replace(".", "").toString().toInt()
                        val stokKue = snapshot.getLong("stok")
                        stok = stokKue?.toInt()!!
                        val imageUrl = snapshot.getString("imageUrl")
                        imageUrlDb = imageUrl.toString()
                        binding.apply {
                            tvCakeName.text = namaKue
                            tvPriceCake.text = hargaKue.toString()
                            tvPriceSum.setText("0")
                            if (!isDestroyed && !isFinishing) {
                                Glide.with(this@CakeDetailUserActivity)
                                    .load(imageUrl)
                                    .into(ivImageBanner)
                            }
                        }
                    }
                }
        } else {
            intent = Intent(this, AdminUserOrderActivity::class.java)
            startActivity(intent)
            finish()
        }

        updateTotalPrice()

        binding.btnToHome.setOnClickListener {
            finish()
        }

        binding.btnPlus.setOnClickListener {
            binding.tvJumlahPesanan.text = jumlahPesanan.toString()
            increaseQuantity()
        }

        binding.btnMinus.setOnClickListener {
            binding.tvJumlahPesanan.text = jumlahPesanan.toString()
            decreaseQuantity()
        }

        binding.btnAddCart.setOnClickListener {
            binding.apply {
                progressBar2.visibility = View.VISIBLE
                btnAddCart.isEnabled = false
            }
            if (jumlahPesanan > stok) {
                binding.apply {
                    progressBar2.visibility = View.GONE
                    btnAddCart.isEnabled = true
                }
                Toast.makeText(this, "Stok tidak mencukupi, Stok = $stok", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val cartRef = userCollection.document(userId.toString()).collection("cart")
                .document(cakeId.toString())
            cartRef.get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    // Produk sudah ada di keranjang, update jumlah pesanan
                    val existingQuantity = documentSnapshot.getLong("jumlahPesanan") ?: 0
                    val newQuantity = existingQuantity + jumlahPesanan
                    cartRef.update("jumlahPesanan", newQuantity)
                        .addOnSuccessListener {
                            binding.apply {
                                progressBar2.visibility = View.GONE
                                btnAddCart.isEnabled = true
                            }
                            Toast.makeText(
                                this,
                                "Berhasil menambahkan ke keranjang",
                                Toast.LENGTH_SHORT
                            ).show()
                            intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this,
                                "Gagal menambahkan ke keranjang",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    // Produk belum ada di keranjang, tambahkan produk baru
                    cartRef.set(
                        hashMapOf(
                            "cakeId" to cakeId,
                            "cake" to Cake(
                                cakeId ?: "",
                                binding.tvPriceCake.text.toString(),
                                imageUrlDb,
                                binding.tvCakeName.text.toString(),
                                stok.toLong(),
                            ),
                            "jumlahPesanan" to jumlahPesanan,
                        )
                    )
                        .addOnSuccessListener {
                            binding.apply {
                                progressBar2.visibility = View.GONE
                                btnAddCart.isEnabled = true
                            }
                            Toast.makeText(
                                this,
                                "Berhasil menambahkan ke keranjang",
                                Toast.LENGTH_SHORT
                            ).show()
                            intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this,
                                "Gagal menambahkan ke keranjang",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
        }

    }

    private fun increaseQuantity() {
        jumlahPesanan++
        updateQuantityAndPrice()
    }

    // Fungsi untuk mengurangi jumlah pesanan dan memperbarui tampilan
    private fun decreaseQuantity() {
        if (jumlahPesanan > 1) {
            jumlahPesanan--
            updateTotalPrice()

        }
        updateQuantityAndPrice()
    }

    // Fungsi untuk memperbarui tampilan jumlah pesanan dan total harga
    private fun updateQuantityAndPrice() {
        binding.tvJumlahPesanan.text = jumlahPesanan.toString()
        updateTotalPrice()
    }

    // Fungsi untuk memperbarui total harga berdasarkan harga per pcs dan jumlah pesanan
    private fun updateTotalPrice() {
        val totalHarga = hargaPerPcs * jumlahPesanan
        val numberFormat = formatAndDisplayCurrency(totalHarga.toString())
        val formattedHarga = numberFormat.format(totalHarga)
        binding.tvPriceSum.text = formattedHarga
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