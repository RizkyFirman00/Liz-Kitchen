package com.dissy.lizkitchen.ui.admin.user

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
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
import com.dissy.lizkitchen.utility.ORDER_STATUS_PAYMENT_VERIFICATION
import com.dissy.lizkitchen.utility.ORDER_STATUS_PROCESSING
import com.dissy.lizkitchen.utility.ORDER_STATUS_READY_PICKUP
import com.dissy.lizkitchen.utility.ORDER_STATUS_SHIPPING
import com.dissy.lizkitchen.utility.cartItemsFromAny
import com.dissy.lizkitchen.utility.deliveryDistanceLabel
import com.dissy.lizkitchen.utility.deliveryFeeLabel
import com.dissy.lizkitchen.utility.metodePengambilanDisplayForOrder
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.orderProductSubtotal
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
import com.google.firebase.storage.FirebaseStorage
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
    private val storage = FirebaseStorage.getInstance()
    private var selectedStatusProofUri: Uri? = null
    private var statusProofTarget: String? = null

    private val statusProofPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { selectedUri ->
        if (selectedUri == null || _binding == null) return@registerForActivityResult
        selectedStatusProofUri = selectedUri
        Glide.with(this).load(selectedUri).into(binding.ivStatusProofInput)
        binding.tvStatusProofHint.text = "Foto siap diunggah saat status disimpan."
        binding.btnChooseStatusProof.text = "Ganti Foto Bukti"
        updateStatusProofActionAvailability()
    }

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

        binding.ivPaymentProof.setOnClickListener {
            currentOrder?.paymentProofUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { showPaymentProofDialog(it) }
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
            updateStatusWithProof(ORDER_STATUS_READY_PICKUP)
        }

        binding.btnChooseStatusProof.setOnClickListener {
            statusProofPicker.launch("image/*")
        }

        binding.btnUploadStatusProof.setOnClickListener {
            saveCurrentStatusProof()
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

                setRequestLoading(false)
                updateUI(order)
                cartDetailUserAdapter.submitList(order.cart)
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
            tvOrderSubtotal.text = formatCurrency(orderProductSubtotal(order).toString())
            deliveryFeeRow.visibility = if (order.metodePengambilan.contains("antar", ignoreCase = true)) {
                VISIBLE
            } else {
                GONE
            }
            tvOrderDeliveryDistance.text = "${deliveryDistanceLabel(order.deliveryDistanceMeters)} dari cabang"
            tvOrderDeliveryFee.text = deliveryFeeLabel(order.deliveryFee)

            if (order.paymentProofUrl.isBlank()) {
                tvPaymentProofStatus.text = "Pelanggan belum mengunggah bukti pembayaran."
                ivPaymentProof.visibility = GONE
                Glide.with(this@AdminUserOrderDetailFragment).clear(ivPaymentProof)
            } else {
                tvPaymentProofStatus.text = "Bukti pembayaran sudah diunggah oleh pelanggan."
                ivPaymentProof.visibility = VISIBLE
                Glide.with(this@AdminUserOrderDetailFragment)
                    .load(order.paymentProofUrl)
                    .into(ivPaymentProof)
            }

            // Reset visibility
            actionContainer.visibility = GONE
            btnCancel.visibility = GONE
            btnConfirm.visibility = GONE
            btnProcess.visibility = GONE
            btnShipping.visibility = GONE
            btnReady.visibility = GONE
            btnConfirm.isEnabled = true
            statusProofInputPanel.visibility = GONE
            btnUploadStatusProof.visibility = GONE
            selectedStatusProofUri = null
            statusProofTarget = null

            when (order.status) {
                ORDER_STATUS_PENDING_PAYMENT, ORDER_STATUS_PAYMENT_VERIFICATION -> {
                    actionContainer.visibility = VISIBLE
                    val hasPaymentProof = order.paymentProofUrl.isNotBlank()
                    tvActionTitle.text = if (hasPaymentProof) {
                        "Periksa bukti pembayaran pelanggan sebelum mengkonfirmasi pesanan."
                    } else {
                        "Menunggu pelanggan mengunggah bukti pembayaran."
                    }
                    btnCancel.visibility = VISIBLE
                    btnConfirm.visibility = VISIBLE
                    btnConfirm.isEnabled = hasPaymentProof
                    btnConfirm.text = if (hasPaymentProof) "Verifikasi Pembayaran" else "Menunggu Bukti"
                    btnCancel.text = "Batalkan"
                }
                ORDER_STATUS_CONFIRMED -> {
                    actionContainer.visibility = VISIBLE
                    tvActionTitle.text = "Pesanan sudah dikonfirmasi. Lanjutkan ke proses produksi."
                    btnProcess.visibility = VISIBLE
                    btnProcess.text = "Proses"
                    bindStatusProofInput(order, ORDER_STATUS_PROCESSING)
                    btnProcess.setOnClickListener {
                        updateStatusWithProof(ORDER_STATUS_PROCESSING)
                    }
                }
                ORDER_STATUS_PROCESSING -> {
                    actionContainer.visibility = VISIBLE
                    if (order.metodePengambilan == METODE_AMBIL_SENDIRI) {
                        tvActionTitle.text = "Tandai pesanan siap saat sudah bisa diambil pelanggan."
                        btnReady.visibility = VISIBLE
                        btnReady.text = "Siap Ambil"
                        bindStatusProofInput(order, ORDER_STATUS_READY_PICKUP)
                        btnReady.setOnClickListener {
                            updateStatusWithProof(ORDER_STATUS_READY_PICKUP)
                        }
                    } else {
                        tvActionTitle.text = "Tandai pesanan dikirim saat kurir mulai mengantar."
                        btnShipping.visibility = VISIBLE
                        btnShipping.text = "Kirim"
                        bindStatusProofInput(order, ORDER_STATUS_SHIPPING)
                        btnShipping.setOnClickListener {
                            updateStatusWithProof(ORDER_STATUS_SHIPPING)
                        }
                    }
                }
                ORDER_STATUS_DONE -> {
                    actionContainer.visibility = VISIBLE
                    tvActionTitle.text = "Pesanan selesai. Tambahkan atau ganti bukti pesanan diterima."
                    bindStatusProofInput(order, ORDER_STATUS_DONE)
                }
            }
        }
    }

    private fun bindStatusProofInput(order: Order, targetStatus: String) {
        statusProofTarget = targetStatus
        binding.statusProofInputPanel.visibility = VISIBLE
        binding.tvStatusProofTitle.text = statusProofTitle(targetStatus)

        val existingUrl = order.statusProofs[targetStatus].orEmpty()
        if (selectedStatusProofUri != null) {
            binding.tvStatusProofHint.text = "Foto baru siap diunggah saat status disimpan."
            binding.btnChooseStatusProof.text = "Ganti Foto Bukti"
        } else if (existingUrl.isNotBlank()) {
            binding.tvStatusProofHint.text = "Bukti status sudah tersedia. Pilih foto baru jika ingin menggantinya."
            binding.btnChooseStatusProof.text = "Ganti Foto Bukti"
            Glide.with(this).load(existingUrl).into(binding.ivStatusProofInput)
        } else {
            binding.tvStatusProofHint.text = "Pilih foto bukti sebelum memperbarui status."
            binding.btnChooseStatusProof.text = "Pilih Foto Bukti"
            binding.ivStatusProofInput.setImageResource(R.drawable.upload_bukti_pembayaran)
        }

        binding.btnUploadStatusProof.visibility = if (targetStatus == ORDER_STATUS_DONE) VISIBLE else GONE
        updateStatusProofActionAvailability()
    }

    private fun updateStatusProofActionAvailability() {
        val target = statusProofTarget ?: return
        val hasSelectedPhoto = selectedStatusProofUri != null
        val hasExistingPhoto = currentOrder?.statusProofs?.get(target).orEmpty().isNotBlank()
        val hasPhoto = hasSelectedPhoto || hasExistingPhoto

        when (target) {
            ORDER_STATUS_PROCESSING -> binding.btnProcess.isEnabled = hasPhoto
            ORDER_STATUS_SHIPPING -> binding.btnShipping.isEnabled = hasPhoto
            ORDER_STATUS_READY_PICKUP -> binding.btnReady.isEnabled = hasPhoto
            ORDER_STATUS_DONE -> binding.btnUploadStatusProof.isEnabled = hasSelectedPhoto
        }
    }

    private fun statusProofTitle(status: String): String {
        return when (status) {
            ORDER_STATUS_PROCESSING -> "Bukti Pesanan Diproses"
            ORDER_STATUS_SHIPPING -> "Bukti Pengiriman Gojek"
            ORDER_STATUS_READY_PICKUP -> "Bukti Pesanan Siap Diambil"
            ORDER_STATUS_DONE -> "Bukti Pesanan Diterima"
            else -> "Bukti Status Pesanan"
        }
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

        textView.setTextColor(Color.parseColor(textColor))
        textView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = textView.resources.displayMetrics.density * 20
            setColor(Color.parseColor(backgroundColor))
        }
    }

    private fun buildStatusDescription(status: String): String {
        return when (status) {
            ORDER_STATUS_PENDING_PAYMENT -> "Menunggu pembayaran dan bukti pembayaran dari pelanggan."
            ORDER_STATUS_PAYMENT_VERIFICATION -> "Bukti pembayaran sudah dikirim dan menunggu verifikasi admin."
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
            buildString {
                append(order.user.alamat?.ifBlank { "Belum ada alamat" } ?: "Belum ada alamat")
                if (order.patokanAlamat.isNotBlank()) {
                    append("\nPatokan: ")
                    append(order.patokanAlamat)
                }
            }
        }
    }

    private fun showPaymentProofDialog(url: String) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 10, dp(16), dp(4))
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
            contentDescription = "Bukti pembayaran pelanggan"
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
            val safeOrderId = orderId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Bukti Pembayaran $safeOrderId")
                .setDescription("Mengunduh bukti pembayaran pelanggan")
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

    private fun confirmOrder() {
        if (currentOrder?.paymentProofUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Bukti pembayaran belum tersedia", Toast.LENGTH_SHORT).show()
            return
        }

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

    private fun updateStatusWithProof(targetStatus: String) {
        val selectedUri = selectedStatusProofUri
        val existingUrl = currentOrder?.statusProofs?.get(targetStatus).orEmpty()
        if (selectedUri == null && existingUrl.isBlank()) {
            Toast.makeText(requireContext(), "Pilih foto bukti terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedUri == null) {
            saveStatusProofAndStatus(targetStatus, existingUrl)
            return
        }

        uploadStatusProof(selectedUri, targetStatus) { downloadUrl ->
            saveStatusProofAndStatus(targetStatus, downloadUrl)
        }
    }

    private fun saveCurrentStatusProof() {
        val targetStatus = statusProofTarget ?: return
        val selectedUri = selectedStatusProofUri
        if (selectedUri == null) {
            Toast.makeText(requireContext(), "Pilih foto bukti terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        uploadStatusProof(selectedUri, targetStatus) { downloadUrl ->
            saveStatusProofAndStatus(targetStatus, downloadUrl, updateStatus = false)
        }
    }

    private fun uploadStatusProof(
        uri: Uri,
        targetStatus: String,
        onUploaded: (String) -> Unit
    ) {
        setRequestLoading(true)
        val safeStatus = targetStatus.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val proofRef = storage.reference.child(
            "order_status_proofs/${orderId}_${safeStatus}_${System.currentTimeMillis()}.jpg"
        )
        proofRef.putFile(uri)
            .addOnSuccessListener {
                proofRef.downloadUrl
                    .addOnSuccessListener { downloadUrl -> onUploaded(downloadUrl.toString()) }
                    .addOnFailureListener { exception -> handleStatusProofFailure(exception) }
            }
            .addOnFailureListener { exception -> handleStatusProofFailure(exception) }
    }

    private fun saveStatusProofAndStatus(
        targetStatus: String,
        downloadUrl: String,
        updateStatus: Boolean = true
    ) {
        val statusProofs = currentOrder?.statusProofs?.toMutableMap() ?: mutableMapOf()
        statusProofs[targetStatus] = downloadUrl
        val updates = mutableMapOf<String, Any>("statusProofs" to statusProofs)
        if (updateStatus) updates["status"] = targetStatus

        updateOrderDocuments(updates) {
            selectedStatusProofUri = null
            val message = if (updateStatus) {
                "Status dan bukti foto berhasil disimpan"
            } else {
                "Bukti foto berhasil disimpan"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            fetchOrderDetails()
        }
    }

    private fun handleStatusProofFailure(exception: Exception) {
        if (_binding == null) return
        setRequestLoading(false)
        Toast.makeText(
            requireContext(),
            "Gagal mengunggah bukti foto: ${exception.message}",
            Toast.LENGTH_LONG
        ).show()
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
