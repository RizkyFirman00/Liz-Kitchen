package com.dissy.lizkitchen.ui.cart

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.user.CheckoutUserAdapter
import com.dissy.lizkitchen.databinding.ActivityDetailCartBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.base.BaseActivity
import com.dissy.lizkitchen.ui.home.MainActivity
import com.dissy.lizkitchen.ui.home.MainActivity.Companion.EXTRA_FRAGMENT_TO_LOAD
import com.dissy.lizkitchen.ui.home.MainActivity.Companion.FRAGMENT_CART
import com.dissy.lizkitchen.ui.konfirmasi.ConfirmActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class DetailCartActivity : BaseActivity(), MetodeAmbilFragment.MetodePengambilanListener {
    private val db = Firebase.firestore
    private lateinit var checkoutAdapter: CheckoutUserAdapter
    private val binding by lazy { ActivityDetailCartBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        checkoutAdapter = CheckoutUserAdapter()
        binding.rvCheckout.adapter = checkoutAdapter
        binding.rvCheckout.layoutManager = LinearLayoutManager(this)
        fetchDataAndUpdateRecyclerView()

        val userId = Preferences.getUserId(this)
        val orderId = intent.getStringExtra("orderId")
        Log.d("TAG", "onCreate orderID: $orderId")
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener {
                    val user = User(
                        userId = it.get("userId") as? String ?: "",
                        username = it.get("username") as? String ?: "",
                        phoneNumber = it.get("phoneNumber") as? String ?: "",
                        email = it.get("email") as? String ?: "",
                        password = it.get("password") as? String ?: "",
                        alamat = it.get("alamat") as? String ?: "Belum ada alamat",
                    )
                    binding.apply {
                        etAlamat.setText(user.alamat)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("TAG", "Error getting documents: ", exception)
                }
            db.collection("users").document(userId).collection("orders").document(orderId!!)
                .get()
                .addOnSuccessListener {
                    val totalHarga = it.get("totalPrice") as? Long ?: 0
                    binding.apply {
                        val formattedText = formatAndDisplayCurrency(totalHarga.toString())
                        tvPriceSum.text = formattedText
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("TAG", "Error getting documents: ", exception)
                }
        }

        binding.btnToHome.setOnClickListener {
            Intent(this, MainActivity::class.java).also {
                it.putExtra(EXTRA_FRAGMENT_TO_LOAD, FRAGMENT_CART)
                startActivity(it)
                finish()
            }
        }

        binding.btnGantiMetodePengambilan.setOnClickListener {
            val metodeAmbilFragment = MetodeAmbilFragment()
            metodeAmbilFragment.setListener(object : MetodeAmbilFragment.MetodePengambilanListener {
                override fun onMetodePengambilanSelected(metode: String) {
                    binding.tvMetodePengambilan.text = metode
                }
            })
            metodeAmbilFragment.show(supportFragmentManager, metodeAmbilFragment.tag)
        }

        binding.btnCancel.setOnClickListener {
            val userId = Preferences.getUserId(this)
            val orderId = intent.getStringExtra("orderId")
            if (userId != null && orderId != null) {
                db.collection("users").document(userId).collection("orders").document(orderId)
                    .update(
                        mapOf(
                            "status" to "Dibatalkan",
                        )
                    ).addOnSuccessListener {
                        Toast.makeText(this, "Pesanan berhasil dibatalkan", Toast.LENGTH_SHORT)
                            .show()
                        Intent(this, MainActivity::class.java).also {
                            startActivity(it)
                            finish()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Pesanan gagal dibatalkan, $e",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                db.collection("orders").document(orderId).update(
                    mapOf(
                        "status" to "Dibatalkan",
                    )
                ).addOnSuccessListener {
                    Toast.makeText(this, "Pesanan berhasil dibatalkan", Toast.LENGTH_SHORT).show()
                    Intent(this, MainActivity::class.java).also {
                        startActivity(it)
                        finish()
                    }
                }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Pesanan gagal dibatalkan, $e",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }

        binding.btnCheckout.setOnClickListener {
            binding.progressBar2.visibility = android.view.View.VISIBLE
            val metodePengambilan = binding.tvMetodePengambilan.text.toString()
            val alamat = binding.etAlamat.text.toString()
            if (alamat.isEmpty() || alamat.isBlank()) {
                Toast.makeText(this, "Alamat tidak boleh kosong", Toast.LENGTH_SHORT).show()
                binding.progressBar2.visibility = android.view.View.GONE
            } else if (metodePengambilan.isEmpty() || metodePengambilan == "Pilih Metode Pengambilan") {
                Toast.makeText(this, "Silahkan pilih metode pengambilan", Toast.LENGTH_SHORT)
                    .show()
                binding.progressBar2.visibility = android.view.View.GONE
            } else {
                val userId = Preferences.getUserId(this)
                if (userId != null && orderId != null) {
                    db.collection("users").document(userId).update(
                        mapOf(
                            "alamat" to alamat,
                        )
                    )
                    db.collection("users").document(userId).collection("orders").document(orderId)
                        .update(
                            mapOf(
                                "user.alamat" to alamat,
                                "metodePengambilan" to metodePengambilan,
                                "status" to "Menunggu Pembayaran",
                            )
                        )
                        .addOnSuccessListener {

                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Pesanan gagal dibuat, $e",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    db.collection("orders").document(orderId).update(
                        mapOf(
                            "user.alamat" to alamat,
                            "metodePengambilan" to metodePengambilan,
                            "status" to "Menunggu Pembayaran",
                        )
                    )
                        .addOnSuccessListener {
                            binding.progressBar2.visibility = android.view.View.GONE
                            Intent(this, ConfirmActivity::class.java).also {
                                it.putExtra("orderId", orderId)
                                Log.d(
                                    "orderId DetailCartActivity",
                                    "onCreate: $orderId"
                                )
                                startActivity(it)
                                finish()
                            }
                            Toast.makeText(
                                this,
                                "Pesanan berhasil dibuat, Silahkan lakukan pembayaran",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Pesanan gagal dibuat, $e",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
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

    private fun fetchDataAndUpdateRecyclerView() {
        val userId = Preferences.getUserId(this)
        val orderId = intent.getStringExtra("orderId")
        if (userId != null && orderId != null) {
            db.collection("users").document(userId).collection("orders").document(orderId).get()
                .addOnSuccessListener {
                    // masukkan datanya ke dalam recyclerview
                    val cartList = mutableListOf<Cart>()
                    val cartItemsArray = it.get("cart") as? ArrayList<HashMap<String, Any>>
                    val cartItems = cartItemsArray?.map { map ->
                        val cakeMap = map["cake"] as? HashMap<String, Any>
                        Cart(
                            cakeId = map["cakeId"] as? String ?: "",
                            cake = Cake(
                                documentId = cakeMap?.get("cakeId") as? String ?: "",
                                namaKue = cakeMap?.get("namaKue") as? String ?: "",
                                harga = cakeMap?.get("harga") as? String ?: "",
                                stok = cakeMap?.get("stok") as? Long ?: 0,
                                imageUrl = cakeMap?.get("imageUrl") as? String ?: ""
                            ),
                            jumlahPesanan = map["jumlahPesanan"] as? Long ?: 0
                        )
                    } ?: listOf()
                    cartList.addAll(cartItems)
                    checkoutAdapter.submitList(cartItems)
                    Log.d(
                        "TAG",
                        "fetchDataAndUpdateRecyclerView: $cartItems"
                    )
                }.addOnFailureListener { exception ->
                    Log.d("TAG", "Error getting documents: ", exception)
                }
        }
    }

    override fun onMetodePengambilanSelected(metode: String) {
        binding.tvMetodePengambilan.text = metode
    }
}