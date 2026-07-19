package com.dissy.lizkitchen.ui.order

import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.user.HomeOrderUserCakeAdapter
import com.dissy.lizkitchen.databinding.FragmentOrderDetailBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.ORDER_STATUS_CANCELED
import com.dissy.lizkitchen.utility.ORDER_STATUS_CONFIRMED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DONE
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.ORDER_STATUS_PAYMENT_VERIFICATION
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
        binding.ivPaymentProofPreview.setOnClickListener {
            currentOrder?.paymentProofUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { showPaymentProofDialog(it) }
        }

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

            if (order.status == ORDER_STATUS_PAYMENT_VERIFICATION && order.paymentProofUrl.isNotBlank()) {
                paymentProofPanel.visibility = View.VISIBLE
                Glide.with(this@OrderDetailFragment)
                    .load(order.paymentProofUrl)
                    .into(ivPaymentProofPreview)
            } else {
                paymentProofPanel.visibility = View.GONE
                Glide.with(this@OrderDetailFragment).clear(ivPaymentProofPreview)
            }

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
                ORDER_STATUS_PAYMENT_VERIFICATION -> {
                    actionContainer.visibility = View.VISIBLE
                    tvActionTitle.text = "Bukti pembayaran sudah dikirim dan sedang menunggu verifikasi admin."
                    btnConfirm.visibility = View.VISIBLE
                    btnConfirm.text = "Ganti Bukti Pembayaran"
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

    private fun showPaymentProofDialog(url: String) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(4))
        }

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360)
            )
            isFillViewport = true
        }

        val proofImage = AppCompatImageView(requireContext()).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Bukti pembayaran"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        Glide.with(this).load(url).into(proofImage)
        scrollView.addView(proofImage)
        container.addView(scrollView)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Bukti Pembayaran")
            .setView(container)
            .setNeutralButton("Download Bukti", null)
            .setPositiveButton("Tutup", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            setTextColor(Color.parseColor("#9C6843"))
            setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.baseline_file_download_24_white,
                0,
                0,
                0
            )
            compoundDrawablePadding = dp(6)
            setOnClickListener { downloadPaymentProof(url) }
        }
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun downloadPaymentProof(url: String) {
        try {
            val safeOrderId = (orderId ?: "pesanan")
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Bukti Pembayaran $safeOrderId")
                .setDescription("Mengunduh bukti pembayaran")
                .setMimeType("image/*")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "liz_kitchen_bukti_$safeOrderId.jpg"
                )
            val manager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(requireContext(), "Bukti pembayaran sedang diunduh", Toast.LENGTH_SHORT).show()
        } catch (exception: Exception) {
            Toast.makeText(
                requireContext(),
                "Gagal mengunduh bukti pembayaran: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun applyStatusStyle(textView: TextView, status: String) {
        val (textColor, backgroundColor) = when (status) {
            ORDER_STATUS_DONE -> "#128A35" to "#E8F7EC"
            ORDER_STATUS_CANCELED, ORDER_STATUS_EXPIRED -> "#C62828" to "#FDECEC"
            ORDER_STATUS_PENDING_PAYMENT, ORDER_STATUS_PAYMENT_VERIFICATION -> "#C46A16" to "#FFF0DE"
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
            ORDER_STATUS_PAYMENT_VERIFICATION -> "Bukti pembayaran sedang diperiksa oleh admin."
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
            buildString {
                append(order.user.alamat?.ifBlank { "Belum ada alamat" } ?: "Belum ada alamat")
                if (order.patokanAlamat.isNotBlank()) {
                    append("\nPatokan: ")
                    append(order.patokanAlamat)
                }
            }
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
        if (_binding != null) {
            Glide.with(this).clear(binding.ivPaymentProofPreview)
        }
        invoiceWebView?.destroy()
        invoiceWebView = null
        _binding = null
    }
}
