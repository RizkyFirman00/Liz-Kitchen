package com.dissy.lizkitchen.ui.admin.user

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.admin.CartDetailUserAdapter
import com.dissy.lizkitchen.databinding.FragmentAdminUserOrderDetailBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.METODE_AMBIL_SENDIRI
import com.dissy.lizkitchen.utility.ORDER_STATUS_CANCELED
import com.dissy.lizkitchen.utility.ORDER_STATUS_CONFIRMED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DONE
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.ORDER_STATUS_PROCESSING
import com.dissy.lizkitchen.utility.ORDER_STATUS_READY_PICKUP
import com.dissy.lizkitchen.utility.ORDER_STATUS_SHIPPING
import com.dissy.lizkitchen.utility.cartItemsFromAny
import com.dissy.lizkitchen.utility.metodePengambilanDisplayForOrder
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.pickupBranchAddressForOrder
import com.dissy.lizkitchen.utility.pickupBranchNameForOrder
import com.dissy.lizkitchen.utility.printOrderInvoice
import com.dissy.lizkitchen.utility.productCategoriesFromAny
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.toFirestoreMap
import com.dissy.lizkitchen.utility.validateOrderExpiryOnRead
import com.google.firebase.firestore.SetOptions
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
    private var userId: String = ""
    private var currentOrder: Order? = null
    private var invoiceWebView: WebView? = null
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

        binding.btnToPrint.setOnClickListener {
            printCurrentInvoice()
        }

        binding.btnConfirm.setOnClickListener {
            confirmOrder()
        }

        binding.btnProcess.setOnClickListener {
            updateOrderStatus(ORDER_STATUS_PROCESSING)
        }

        binding.btnShipping.setOnClickListener {
            updateOrderStatus(ORDER_STATUS_SHIPPING)
        }

        binding.btnReady.setOnClickListener {
            updateOrderStatus(ORDER_STATUS_READY_PICKUP)
        }

        binding.btnCancel.setOnClickListener {
            showCancelDialog()
        }
    }

    private fun printCurrentInvoice() {
        val order = currentOrder
        if (order == null) {
            Toast.makeText(requireContext(), "Detail pesanan belum selesai dimuat", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnToPrint.isEnabled = false
        invoiceWebView?.destroy()
        invoiceWebView = printOrderInvoice(
            context = requireContext(),
            order = order,
            onPrintDialogOpened = {
                if (_binding != null) {
                    binding.btnToPrint.isEnabled = true
                }
            },
            onError = { throwable ->
                if (_binding != null) {
                    binding.btnToPrint.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Gagal membuka invoice: ${throwable.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun fetchOrderDetails() {
        setRequestLoading(true)
        db.collection("orders").document(orderId).get()
            .addOnSuccessListener { orderDocument ->
                if (_binding == null) return@addOnSuccessListener
                if (!orderDocument.exists()) {
                    setRequestLoading(false)
                    Toast.makeText(requireContext(), "Pesanan tidak ditemukan", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                    return@addOnSuccessListener
                }

                val order = validateOrderExpiryOnRead(db, orderFromDocument(orderDocument))
                currentOrder = order
                userId = order.user.userId.orEmpty()

                updateUI(order)
                cartDetailUserAdapter.submitList(order.cart)
                setRequestLoading(false)
            }
            .addOnFailureListener { exception ->
                if (_binding != null) setRequestLoading(false)
                Toast.makeText(requireContext(), "Gagal memuat pesanan: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI(order: Order) {
        binding.apply {
            val statusText = order.status.ifBlank { "Status belum tersedia" }
            tvOrderId.text = order.orderId.ifBlank { "-" }
            tvStatus.text = statusText
            applyStatusStyle(tvStatus, statusText)
            tvStatusDescription.text = buildStatusDescription(statusText)
            tvItemCount.text = buildItemSummary(order)
            tvCustomerName.text = order.user.name.orEmpty().ifBlank { "Pelanggan" }
            tvCustomerPhone.text = order.user.phoneNumber.orEmpty().ifBlank { "Nomor HP belum tersedia" }
            tvCustomerEmail.text = order.user.email.orEmpty().ifBlank { "Email belum tersedia" }
            tvOrderDate.text = order.tanggalOrder.ifBlank { "-" }
            tvMetodePengambilan.text = metodePengambilanDisplayForOrder(order).ifBlank { "-" }
            tvJamOrder.text = order.jamOrder.ifBlank { "-" }
            tvAddressTitle.text = if (order.metodePengambilan.contains("ambil", ignoreCase = true)) {
                "Alamat Cabang"
            } else {
                "Alamat Penerima"
            }
            tvAlamat.text = buildAddressText(order)
            tvPriceSum.text = formatCurrency(order.totalPrice.toString())

            // Reset visibility
            actionContainer.visibility = GONE
            btnCancel.visibility = GONE
            btnConfirm.visibility = GONE
            btnProcess.visibility = GONE
            btnShipping.visibility = GONE
            btnReady.visibility = GONE

            when (order.status) {
                ORDER_STATUS_PENDING_PAYMENT -> {
                    actionContainer.visibility = VISIBLE
                    tvActionTitle.text = "Validasi pembayaran pelanggan untuk melanjutkan pesanan."
                    btnCancel.visibility = VISIBLE
                    btnConfirm.visibility = VISIBLE
                    btnConfirm.text = "Konfirmasi"
                    btnCancel.text = "Batalkan"
                }
                ORDER_STATUS_CONFIRMED -> {
                    actionContainer.visibility = VISIBLE
                    tvActionTitle.text = "Pesanan sudah dikonfirmasi. Lanjutkan ke proses produksi."
                    btnProcess.visibility = VISIBLE
                    btnProcess.text = "Proses"
                }
                ORDER_STATUS_PROCESSING -> {
                    actionContainer.visibility = VISIBLE
                    if (order.metodePengambilan == METODE_AMBIL_SENDIRI) {
                        tvActionTitle.text = "Tandai pesanan siap saat sudah bisa diambil pelanggan."
                        btnReady.visibility = VISIBLE
                        btnReady.text = "Siap Ambil"
                    } else {
                        tvActionTitle.text = "Tandai pesanan dikirim saat kurir mulai mengantar."
                        btnShipping.visibility = VISIBLE
                        btnShipping.text = "Kirim"
                    }
                }
            }
        }
    }

    private fun applyStatusStyle(textView: TextView, status: String) {
        val (textColor, backgroundColor) = when (status) {
            ORDER_STATUS_DONE -> "#128A35" to "#E8F7EC"
            ORDER_STATUS_CANCELED, ORDER_STATUS_EXPIRED -> "#C62828" to "#FDECEC"
            ORDER_STATUS_PENDING_PAYMENT -> "#C46A16" to "#FFF0DE"
            ORDER_STATUS_CONFIRMED, ORDER_STATUS_SHIPPING, ORDER_STATUS_READY_PICKUP -> "#128A35" to "#E8F7EC"
            ORDER_STATUS_PROCESSING -> "#9C6843" to "#F7E6DA"
            else -> "#9C6843" to "#F7E6DA"
        }

        textView.setTextColor(Color.parseColor(textColor))
        textView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = textView.resources.displayMetrics.density * 20
            setColor(Color.parseColor(backgroundColor))
        }
    }

    private fun buildStatusDescription(status: String): String {
        return when (status) {
            ORDER_STATUS_PENDING_PAYMENT -> "Menunggu pembayaran. Admin bisa konfirmasi jika pembayaran sudah valid."
            ORDER_STATUS_CONFIRMED -> "Pembayaran sudah dikonfirmasi. Pesanan siap masuk proses produksi."
            ORDER_STATUS_PROCESSING -> "Pesanan sedang dibuat oleh tim Liz Kitchen."
            ORDER_STATUS_SHIPPING -> "Pesanan sedang dalam pengiriman ke pelanggan."
            ORDER_STATUS_READY_PICKUP -> "Pesanan sudah siap diambil di cabang."
            ORDER_STATUS_DONE -> "Pesanan sudah selesai."
            ORDER_STATUS_CANCELED -> "Pesanan ini sudah dibatalkan."
            ORDER_STATUS_EXPIRED -> "Batas pembayaran 1x24 jam sudah lewat. Pesanan tidak bisa diproses."
            else -> "Pantau dan kelola status pesanan pelanggan di halaman ini."
        }
    }

    private fun buildItemSummary(order: Order): String {
        val itemTypeCount = order.cart.size
        val quantityCount = order.cart.sumOf { it.jumlahPesanan }
        return "$itemTypeCount jenis produk | $quantityCount item"
    }

    private fun buildAddressText(order: Order): String {
        return if (order.metodePengambilan.contains("ambil", ignoreCase = true)) {
            "${pickupBranchNameForOrder(order)}\n${pickupBranchAddressForOrder(order)}"
        } else {
            order.user.alamat?.ifBlank { "Belum ada alamat" } ?: "Belum ada alamat"
        }
    }

    private fun confirmOrder() {
        val formattedDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val updates = mutableMapOf<String, Any>(
            "status" to ORDER_STATUS_CONFIRMED
        )
        if (currentOrder?.tanggalOrder.isNullOrBlank()) {
            updates["tanggalOrder"] = formattedDate
        }
        if (currentOrder?.jamOrder.isNullOrBlank()) {
            updates["jamOrder"] = currentTime
        }

        updateOrderDocuments(updates) {
            cutStock(orderId)
            Toast.makeText(requireContext(), "Berhasil mengkonfirmasi pesanan", Toast.LENGTH_SHORT).show()
            fetchOrderDetails()
        }
    }

    private fun updateOrderStatus(status: String) {
        updateOrderDocuments(mapOf("status" to status)) {
            Toast.makeText(requireContext(), "Status diperbarui ke $status", Toast.LENGTH_SHORT).show()
            fetchOrderDetails()
        }
    }

    private fun updateOrderDocuments(updates: Map<String, Any>, onSuccess: () -> Unit) {
        val globalOrderRef = db.collection("orders").document(orderId)
        setRequestLoading(true)

        if (userId.isBlank()) {
            globalOrderRef.set(updates, SetOptions.merge())
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { exception ->
                    setRequestLoading(false)
                    Toast.makeText(requireContext(), "Gagal memperbarui status: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            return
        }

        val userOrderRef = db.collection("users").document(userId).collection("orders").document(orderId)
        db.runBatch { batch ->
            batch.set(globalOrderRef, updates, SetOptions.merge())
            batch.set(userOrderRef, updates, SetOptions.merge())
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { exception ->
            setRequestLoading(false)
            Toast.makeText(requireContext(), "Gagal memperbarui status: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCancelDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Pembatalan")
            .setMessage("Apakah Anda yakin ingin membatalkan pesanan ini?")
            .setPositiveButton("Ya") { _, _ -> updateOrderStatus(ORDER_STATUS_CANCELED) }
            .setNegativeButton("Tidak", null)
            .show()
    }

    private fun cutStock(orderId: String) {
        db.collection("orders").document(orderId).get().addOnSuccessListener { snapshot ->
            val cartItems = cartItemsFromAny(snapshot.get("cart"))

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
        return "Rp $sb"
    }

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.root.setFirebaseRequestLoading(isLoading)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        invoiceWebView?.destroy()
        invoiceWebView = null
        _binding = null
    }
}
