package com.dissy.lizkitchen.ui.order

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.user.HomeOrderUserCakeAdapter
import com.dissy.lizkitchen.databinding.FragmentOrderDetailBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.ORDER_STATUS_CANCELED
import com.dissy.lizkitchen.utility.ORDER_STATUS_CONFIRMED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DONE
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.ORDER_STATUS_PROCESSING
import com.dissy.lizkitchen.utility.ORDER_STATUS_READY_PICKUP
import com.dissy.lizkitchen.utility.ORDER_STATUS_SHIPPING
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.metodePengambilanDisplayForOrder
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.orderToFirestoreMap
import com.dissy.lizkitchen.utility.pickupBranchAddressForOrder
import com.dissy.lizkitchen.utility.pickupBranchNameForOrder
import com.dissy.lizkitchen.utility.printOrderInvoice
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.validateOrderExpiryOnRead
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class OrderDetailFragment : Fragment() {
    private var _binding: FragmentOrderDetailBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private lateinit var orderDetailAdapter: HomeOrderUserCakeAdapter
    private var orderId: String? = null
    private var userId: String? = null
    private var currentOrder: Order? = null
    private var invoiceWebView: WebView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderId = arguments?.getString("orderId")
        userId = Preferences.getUserId(requireContext())

        orderDetailAdapter = HomeOrderUserCakeAdapter()
        binding.rvDetailOrderItem.adapter = orderDetailAdapter
        binding.rvDetailOrderItem.layoutManager = LinearLayoutManager(requireContext())

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnToPrint.setOnClickListener { printCurrentInvoice() }

        fetchOrderDetails()
    }

    private fun fetchOrderDetails() {
        val currentUserId = userId ?: return
        val currentOrderId = orderId ?: return
        setRequestLoading(true)
        val userOrderRef = db.collection("users").document(currentUserId)
            .collection("orders").document(currentOrderId)

        userOrderRef.get()
            .addOnSuccessListener { document ->
                if (_binding == null) return@addOnSuccessListener
                if (document.exists()) {
                    val order = validateOrderExpiryOnRead(db, orderFromDocument(document))
                    setRequestLoading(false)
                    bindOrder(order)
                } else {
                    fetchGlobalOrderFallback(currentUserId, currentOrderId)
                }
            }
            .addOnFailureListener {
                setRequestLoading(false)
                Toast.makeText(requireContext(), "Gagal memuat detail pesanan", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchGlobalOrderFallback(currentUserId: String, currentOrderId: String) {
        db.collection("orders").document(currentOrderId).get()
            .addOnSuccessListener { document ->
                if (_binding == null) return@addOnSuccessListener
                if (!document.exists()) {
                    setRequestLoading(false)
                    Toast.makeText(requireContext(), "Pesanan tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val order = validateOrderExpiryOnRead(db, orderFromDocument(document))
                if (order.user.userId != currentUserId) {
                    setRequestLoading(false)
                    Toast.makeText(requireContext(), "Pesanan tidak sesuai dengan akun ini", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val orderData = orderToFirestoreMap(order)
                db.collection("users").document(currentUserId)
                    .collection("orders").document(currentOrderId)
                    .set(orderData, SetOptions.merge())
                setRequestLoading(false)
                bindOrder(order)
            }
            .addOnFailureListener {
                setRequestLoading(false)
                Toast.makeText(requireContext(), "Gagal memuat detail pesanan", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bindOrder(order: Order) {
        currentOrder = order
        orderDetailAdapter.submitList(order.cart)

        binding.apply {
            val statusText = order.status.ifBlank { "Status belum tersedia" }
            tvOrderId.text = order.orderId
            tvStatus.text = statusText
            applyStatusStyle(tvStatus, statusText)
            tvStatusDescription.text = buildStatusDescription(statusText)
            tvItemCount.text = buildItemSummary(order)
            tvPriceSum.text = formatCurrency(order.totalPrice.toString())
            tvMetodePengambilan.text = metodePengambilanDisplayForOrder(order).ifBlank { "-" }
            tvAlamat.text = buildAddressText(order)
            tvOrderDate.text = order.tanggalOrder.ifBlank { "-" }
            tvJamOrder.text = order.jamOrder.ifBlank { "-" }

            actionContainer.visibility = View.GONE
            btnCancel.visibility = View.GONE
            btnConfirm.visibility = View.GONE
            btnReceive.visibility = View.GONE

            when (order.status) {
                ORDER_STATUS_PENDING_PAYMENT -> {
                    actionContainer.visibility = View.VISIBLE
                    tvActionTitle.text = "Selesaikan pembayaran agar pesanan bisa diproses."
                    btnCancel.visibility = View.VISIBLE
                    btnConfirm.visibility = View.VISIBLE
                    btnConfirm.text = "Bayar Sekarang"
                    btnCancel.setOnClickListener { showCancelDialog() }
                    btnConfirm.setOnClickListener {
                        val bundle = Bundle().apply { putString("orderId", order.orderId) }
                        findNavController().navigate(R.id.navigation_confirm, bundle)
                    }
                }
                ORDER_STATUS_SHIPPING, ORDER_STATUS_READY_PICKUP -> {
                    actionContainer.visibility = View.VISIBLE
                    tvActionTitle.text = if (order.status == ORDER_STATUS_READY_PICKUP) {
                        "Konfirmasi setelah pesanan sudah kamu ambil."
                    } else {
                        "Konfirmasi setelah pesanan sudah kamu terima."
                    }
                    btnReceive.visibility = View.VISIBLE
                    btnReceive.text = if (order.status == ORDER_STATUS_READY_PICKUP) {
                        "Sudah Diambil"
                    } else {
                        "Pesanan Diterima"
                    }
                    btnReceive.setOnClickListener { updateOrderStatus(ORDER_STATUS_DONE) }
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
        val badgeBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = textView.resources.displayMetrics.density * 20
            setColor(Color.parseColor(backgroundColor))
        }

        textView.setTextColor(Color.parseColor(textColor))
        textView.background = badgeBackground
    }

    private fun buildStatusDescription(status: String): String {
        return when (status) {
            ORDER_STATUS_PENDING_PAYMENT -> "Selesaikan pembayaran supaya pesanan bisa diproses."
            ORDER_STATUS_CONFIRMED -> "Pembayaran sudah diterima. Pesanan akan segera masuk proses produksi."
            ORDER_STATUS_PROCESSING -> "Pesanan sedang dibuat oleh tim Liz Kitchen."
            ORDER_STATUS_SHIPPING -> "Pesanan sedang dalam perjalanan menuju alamat penerima."
            ORDER_STATUS_READY_PICKUP -> "Pesanan sudah siap diambil di cabang Liz Kitchen."
            ORDER_STATUS_DONE -> "Pesanan selesai. Terima kasih sudah berbelanja di Liz Kitchen."
            ORDER_STATUS_CANCELED -> "Pesanan ini sudah dibatalkan."
            ORDER_STATUS_EXPIRED -> "Batas pembayaran 1x24 jam sudah lewat. Silahkan buat pesanan baru."
            else -> "Pantau perkembangan pesananmu di halaman ini."
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

    private fun showCancelDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Batalkan Pesanan")
            .setMessage("Apakah Anda yakin ingin membatalkan pesanan ini?")
            .setPositiveButton("Ya") { _, _ -> updateOrderStatus(ORDER_STATUS_CANCELED) }
            .setNegativeButton("Tidak", null)
            .show()
    }

    private fun updateOrderStatus(status: String) {
        val currentUserId = userId ?: return
        val currentOrderId = orderId ?: return
        val updates = mapOf("status" to status)
        val globalOrderRef = db.collection("orders").document(currentOrderId)
        val userOrderRef = db.collection("users").document(currentUserId)
            .collection("orders").document(currentOrderId)

        setRequestLoading(true)
        db.runBatch { batch ->
            batch.set(globalOrderRef, updates, SetOptions.merge())
            batch.set(userOrderRef, updates, SetOptions.merge())
        }.addOnSuccessListener {
            setRequestLoading(false)
            Toast.makeText(requireContext(), "Status pesanan diperbarui", Toast.LENGTH_SHORT).show()
            fetchOrderDetails()
        }.addOnFailureListener { exception ->
            setRequestLoading(false)
            Toast.makeText(requireContext(), "Gagal memperbarui status: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.root.setFirebaseRequestLoading(isLoading)
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

    override fun onDestroyView() {
        super.onDestroyView()
        invoiceWebView?.destroy()
        invoiceWebView = null
        _binding = null
    }
}
