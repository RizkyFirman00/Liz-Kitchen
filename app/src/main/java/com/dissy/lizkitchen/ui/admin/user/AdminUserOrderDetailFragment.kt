package com.dissy.lizkitchen.ui.admin.user

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.admin.CartDetailUserAdapter
import com.dissy.lizkitchen.databinding.FragmentAdminUserOrderDetailBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.utility.cakeFromMap
import com.dissy.lizkitchen.utility.productCategoriesFromAny
import com.dissy.lizkitchen.utility.toFirestoreMap
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminUserOrderDetailFragment : Fragment() {
    private var _binding: FragmentAdminUserOrderDetailBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private lateinit var orderId: String
    private lateinit var userId: String
    private lateinit var cartDetailUserAdapter: CartDetailUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUserOrderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SimpleDateFormat")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderId = arguments?.getString("orderId") ?: ""
        
        cartDetailUserAdapter = CartDetailUserAdapter()
        binding.rvDetailOrderItem.adapter = cartDetailUserAdapter
        binding.rvDetailOrderItem.layoutManager = LinearLayoutManager(requireContext())
        
        fetchOrderDetails()

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnConfirm.setOnClickListener {
            confirmOrder()
        }

        binding.btnProcess.setOnClickListener {
            updateOrderStatus("Sedang Diproses")
        }

        binding.btnShipping.setOnClickListener {
            updateOrderStatus("Sedang Dikirim")
        }

        binding.btnReady.setOnClickListener {
            updateOrderStatus("Sedang Dikirim")
        }

        binding.btnCancel.setOnClickListener {
            showCancelDialog()
        }
    }

    private fun fetchOrderDetails() {
        db.collection("orders").document(orderId).get()
            .addOnSuccessListener { orderDocument ->
                if (_binding == null) return@addOnSuccessListener
                val cartItemsArray = orderDocument.get("cart") as? ArrayList<HashMap<String, Any>>
                val cartItems = cartItemsArray?.map { map ->
                    val cakeMap = map["cake"] as? HashMap<*, *>
                    Cart(
                        cakeId = map["cakeId"] as? String ?: "",
                        cake = cakeFromMap(cakeMap?.get("documentId")?.toString().orEmpty(), cakeMap ?: emptyMap<String, Any>()),
                        jumlahPesanan = map["jumlahPesanan"] as? Long ?: 0
                    )
                } ?: listOf()
                val userInfo = orderDocument.get("user") as? HashMap<String, Any>
                val order = Order(
                    cart = cartItems,
                    orderId = orderDocument.getString("orderId") ?: "",
                    status = orderDocument.getString("status") ?: "",
                    totalPrice = orderDocument.getLong("totalPrice") ?: 0,
                    tanggalOrder = orderDocument.getString("tanggalOrder") ?: "Menunggu pembayaran",
                    jamOrder = orderDocument.getString("jamOrder") ?: "Menunggu pembayaran",
                    metodePengambilan = orderDocument.getString("metodePengambilan") ?: "",
                    user = userInfo?.let {
                        User(
                            userId = it["userId"] as? String ?: "",
                            username = it["username"] as? String ?: "",
                            email = it["email"] as? String ?: "",
                            phoneNumber = it["phoneNumber"] as? String ?: "",
                            alamat = it["alamat"] as? String ?: ""
                        )
                    } ?: User()
                )
                userId = order.user.userId ?: ""

                updateUI(order)
                cartDetailUserAdapter.submitList(cartItems)
            }
    }

    private fun updateUI(order: Order) {
        binding.apply {
            tvOrderId.text = order.orderId
            tvStatus.text = order.status
            tvOrderDate.text = order.tanggalOrder
            tvMetodePengambilan.text = order.metodePengambilan
            tvJamOrder.text = order.jamOrder
            tvAlamat.text = order.user.alamat
            tvPriceSum.text = formatCurrency(order.totalPrice.toString())

            // Reset visibility
            btnCancel.visibility = GONE
            btnConfirm.visibility = GONE
            btnProcess.visibility = GONE
            btnShipping.visibility = GONE
            btnReady.visibility = GONE

            when (order.status) {
                "Menunggu Pembayaran" -> {
                    btnCancel.visibility = VISIBLE
                    btnConfirm.visibility = VISIBLE
                }
                "Sudah Dikonfirmasi" -> {
                    btnProcess.visibility = VISIBLE
                }
                "Sedang Diproses" -> {
                    if (order.metodePengambilan == "Ambil Sendiri") {
                        btnReady.visibility = VISIBLE
                    } else {
                        btnShipping.visibility = VISIBLE
                    }
                }
                "Sedang Dikirim" -> {
                    // Maybe show "Delivered" button if admin marks it done
                }
            }
        }
    }

    private fun confirmOrder() {
        val formattedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val updates = mapOf(
            "jamOrder" to currentTime,
            "tanggalOrder" to formattedDate,
            "status" to "Sudah Dikonfirmasi"
        )

        db.collection("orders").document(orderId).update(updates)
            .addOnSuccessListener {
                cutStock(orderId)
                db.collection("users").document(userId).collection("orders").document(orderId).update(updates)
                Toast.makeText(requireContext(), "Berhasil mengkonfirmasi pesanan", Toast.LENGTH_SHORT).show()
                fetchOrderDetails()
            }
    }

    private fun updateOrderStatus(status: String) {
        db.collection("orders").document(orderId).update("status", status)
            .addOnSuccessListener {
                db.collection("users").document(userId).collection("orders").document(orderId).update("status", status)
                Toast.makeText(requireContext(), "Status diperbarui ke $status", Toast.LENGTH_SHORT).show()
                fetchOrderDetails()
            }
    }

    private fun showCancelDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Pembatalan")
            .setMessage("Apakah Anda yakin ingin membatalkan pesanan ini?")
            .setPositiveButton("Ya") { _, _ -> updateOrderStatus("Dibatalkan") }
            .setNegativeButton("Tidak", null)
            .show()
    }

    private fun cutStock(orderId: String) {
        db.collection("orders").document(orderId).get().addOnSuccessListener { snapshot ->
            val cartItemsArray = snapshot.get("cart") as? ArrayList<HashMap<String, Any>>
            val cartItems = cartItemsArray?.map { map ->
                val cakeMap = map["cake"] as? HashMap<*, *>
                Cart(
                    cakeId = map["cakeId"] as? String ?: "",
                    cake = cakeFromMap(cakeMap?.get("documentId")?.toString().orEmpty(), cakeMap ?: emptyMap<String, Any>()),
                    jumlahPesanan = map["jumlahPesanan"] as? Long ?: 0
                )
            } ?: listOf()

            for (item in cartItems) {
                val cakeRef = db.collection("cakes").document(item.cake.documentId.ifBlank { item.cakeId })
                cakeRef.get().addOnSuccessListener { cakeDoc ->
                    val categories = productCategoriesFromAny(cakeDoc.get("kategoriProduk")).toMutableList()
                    if (categories.isEmpty()) {
                        val currentStock = cakeDoc.getLong("stok") ?: 0
                        if (currentStock >= item.jumlahPesanan) {
                            cakeRef.update("stok", currentStock - item.jumlahPesanan)
                        }
                        return@addOnSuccessListener
                    }

                    val categoryIndex = categories.indexOfFirst { it.namaKategori == item.cake.kategori }
                        .takeIf { it >= 0 } ?: 0
                    val category = categories[categoryIndex]
                    if (category.stok < item.jumlahPesanan) return@addOnSuccessListener

                    categories[categoryIndex] = category.copy(stok = category.stok - item.jumlahPesanan)
                    val updatedCategory = categories[categoryIndex]
                    val updates = mutableMapOf<String, Any>(
                        "kategoriProduk" to categories.map { it.toFirestoreMap() }
                    )
                    if (categoryIndex == 0) {
                        updates["harga"] = updatedCategory.harga
                        updates["stok"] = updatedCategory.stok
                        updates["satuan"] = updatedCategory.satuan
                        updates["kategori"] = updatedCategory.namaKategori
                    }
                    cakeRef.update(updates)
                }
            }
        }
    }

    private fun formatCurrency(value: String): String {
        val sb = StringBuilder(value)
        var i = sb.length - 3
        while (i > 0) {
            sb.insert(i, ".")
            i -= 3
        }
        return "Rp. $sb"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
