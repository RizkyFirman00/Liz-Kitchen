package com.dissy.lizkitchen.ui.order

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.user.HomeOrderUserCakeAdapter
import com.dissy.lizkitchen.databinding.DialogPhotoSourceBinding
import com.dissy.lizkitchen.databinding.FragmentOrderDetailBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.utility.ORDER_STATUS_CANCELED
import com.dissy.lizkitchen.utility.ORDER_STATUS_AWAITING_ADMIN_COMPLETION
import com.dissy.lizkitchen.utility.ORDER_STATUS_CONFIRMED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DELIVERED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DONE
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.ORDER_STATUS_PAYMENT_VERIFICATION
import com.dissy.lizkitchen.utility.ORDER_STATUS_PROCESSING
import com.dissy.lizkitchen.utility.ORDER_STATUS_READY_PICKUP
import com.dissy.lizkitchen.utility.ORDER_STATUS_SHIPPING
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.deliveryDistanceLabel
import com.dissy.lizkitchen.utility.deliveryFeeLabel
import com.dissy.lizkitchen.utility.completionLabelForOrder
import com.dissy.lizkitchen.utility.cameraImageUri
import com.dissy.lizkitchen.utility.isUsableCameraImage
import com.dissy.lizkitchen.utility.metodePengambilanDisplayForOrder
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.orderProductSubtotal
import com.dissy.lizkitchen.utility.orderToFirestoreMap
import com.dissy.lizkitchen.utility.pickupBranchAddressForOrder
import com.dissy.lizkitchen.utility.pickupBranchNameForOrder
import com.dissy.lizkitchen.utility.printOrderInvoice
import com.dissy.lizkitchen.utility.prepareCameraImage
import com.dissy.lizkitchen.utility.receiptConfirmationRemainingLabel
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.validateOrderExpiryOnRead
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderDetailFragment : Fragment() {
    private var _binding: FragmentOrderDetailBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()
    private lateinit var orderDetailAdapter: HomeOrderUserCakeAdapter
    private var orderId: String? = null
    private var userId: String? = null
    private var currentOrder: Order? = null
    private var invoiceWebView: WebView? = null
    private var selectedReceiptProofUri: Uri? = null
    private var receiptCameraFile: File? = null

    private val receiptCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) openReceiptCamera()
        else Toast.makeText(requireContext(), "Izin kamera diperlukan untuk mengambil foto", Toast.LENGTH_SHORT).show()
    }

    private val receiptProofPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { selectedUri ->
        if (selectedUri == null || _binding == null) return@registerForActivityResult
        selectedReceiptProofUri = selectedUri
        uploadReceiptProof(selectedUri)
    }

    private val receiptCameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@registerForActivityResult
        val photoFile = receiptCameraFile?.takeIf(::isUsableCameraImage)
            ?: return@registerForActivityResult
        val currentContext = context
        if (currentContext == null) return@registerForActivityResult
        val photoUri = currentContext.cameraImageUri(photoFile)
        selectedReceiptProofUri = photoUri
        uploadReceiptProof(photoUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiptCameraFile = savedInstanceState?.getString(STATE_RECEIPT_CAMERA_FILE)?.let(::File)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        receiptCameraFile?.absolutePath?.let { outState.putString(STATE_RECEIPT_CAMERA_FILE, it) }
        super.onSaveInstanceState(outState)
    }

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
            val normalizedStatus = order.status.trim()
            val statusText = normalizedStatus.ifBlank { "Status belum tersedia" }
            tvOrderId.text = order.orderId
            tvStatus.text = statusText
            applyStatusStyle(tvStatus, statusText)
            tvStatusDescription.text = buildStatusDescription(statusText)
            tvCompletionLabel.text = completionLabelForOrder(order)
            tvCompletionLabel.visibility = if (tvCompletionLabel.text.isNullOrBlank()) View.GONE else View.VISIBLE
            tvItemCount.text = buildItemSummary(order)
            tvPriceSum.text = formatCurrency(order.totalPrice.toString())
            tvMetodePengambilan.text = metodePengambilanDisplayForOrder(order).ifBlank { "-" }
            tvDeliveryEstimate.visibility = if (order.metodePengambilan.contains("antar", ignoreCase = true)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            tvBranchName.text = pickupBranchNameForOrder(order)
            tvBranchAddress.text = pickupBranchAddressForOrder(order)
            llAlamat.visibility = if (order.metodePengambilan.contains("antar", ignoreCase = true)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            tvAlamat.text = buildAddressText(order)
            tvOrderDate.text = order.tanggalOrder.ifBlank { "-" }
            tvJamOrder.text = order.jamOrder.ifBlank { "-" }
            tvOrderSubtotal.text = formatCurrency(orderProductSubtotal(order).toString())
            deliveryFeeRow.visibility = if (order.metodePengambilan.contains("antar", ignoreCase = true)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            tvOrderDeliveryDistance.text = "${deliveryDistanceLabel(order.deliveryDistanceMeters)} dari cabang"
            tvOrderDeliveryFee.text = deliveryFeeLabel(order.deliveryFee)

            val statusProofEntries = listOf(
                ORDER_STATUS_PROCESSING,
                ORDER_STATUS_SHIPPING,
                ORDER_STATUS_DELIVERED,
                ORDER_STATUS_READY_PICKUP,
                ORDER_STATUS_DONE
            ).mapNotNull { status ->
                order.statusProofs[status]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { status to it }
            }
            val proofEntries = statusProofEntries.map { (status, url) ->
                statusProofTitle(status) to url
            }.toMutableList().apply {
                order.receiptProofUrl
                    .takeIf { it.isNotBlank() }
                    ?.let { add(receiptProofTitle(order) to it) }
            }
            val completionLabel = completionLabelForOrder(order)
            tvStatusProofHint.text = if (completionLabel.isBlank()) {
                "Bukti status dari admin. Ketuk foto untuk melihat lebih besar."
            } else {
                "Status penyelesaian: $completionLabel"
            }
            statusProofList.removeAllViews()
            if (proofEntries.isNotEmpty()) {
                statusProofPanel.visibility = View.VISIBLE
                tvStatusProofTitle.text = "Bukti Perjalanan Pesanan"
                tvStatusProofHint.text = "Bukti foto dari admin dan pelanggan. Ketuk foto untuk melihat lebih besar."
                proofEntries.forEach { (title, url) ->
                    addStatusProofPreview(title, url)
                }
            } else {
                statusProofPanel.visibility = View.GONE
            }

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
            tvActionTitle.visibility = View.VISIBLE
            receiptPromptContainer.visibility = View.GONE
            btnCancel.visibility = View.GONE
            btnConfirm.visibility = View.GONE
            btnReceive.visibility = View.GONE

            when {
                normalizedStatus.equals(ORDER_STATUS_PENDING_PAYMENT, ignoreCase = true) -> {
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
                normalizedStatus.equals(ORDER_STATUS_PAYMENT_VERIFICATION, ignoreCase = true) -> {
                    actionContainer.visibility = View.VISIBLE
                    tvActionTitle.text = "Bukti pembayaran sudah dikirim dan sedang menunggu verifikasi admin."
                    btnConfirm.visibility = View.VISIBLE
                    btnConfirm.text = "Ganti Bukti Pembayaran"
                    btnConfirm.setOnClickListener {
                        val bundle = Bundle().apply { putString("orderId", order.orderId) }
                        findNavController().navigate(R.id.navigation_confirm, bundle)
                    }
                }
                normalizedStatus.equals(ORDER_STATUS_DELIVERED, ignoreCase = true) ||
                    normalizedStatus.equals(ORDER_STATUS_READY_PICKUP, ignoreCase = true) -> {
                    actionContainer.visibility = View.VISIBLE
                    val isPickup = normalizedStatus.equals(ORDER_STATUS_READY_PICKUP, ignoreCase = true) ||
                        order.metodePengambilan.contains("ambil", ignoreCase = true)
                    tvActionTitle.visibility = View.GONE
                    receiptPromptContainer.visibility = View.VISIBLE
                    bindReceiptPrompt(order, isPickup)
                    btnReceive.visibility = View.VISIBLE
                    btnReceive.text = if (isPickup) {
                        "Upload Foto Pengambilan"
                    } else {
                        "Upload Foto Penerimaan"
                    }
                    btnReceive.setOnClickListener { requestReceiptProof(order) }
                }
            }
        }
    }

    private fun showPaymentProofDialog(url: String, title: String = "Bukti Pembayaran") {
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
            .setTitle(title)
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
            setOnClickListener { downloadPaymentProof(url, title) }
        }
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addStatusProofPreview(title: String, url: String) {
        val item = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = resources.getDrawable(R.drawable.bg_cart_panel, requireContext().theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (binding.statusProofList.childCount > 0) topMargin = dp(8)
            }
            isClickable = true
            setOnClickListener { showPaymentProofDialog(url, title) }
        }
        val title = TextView(requireContext()).apply {
            text = title
            setTextColor(Color.parseColor("#3A2A20"))
            textSize = 12f
            typeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_semibold)
        }
        val preview = AppCompatImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220)
            ).apply { topMargin = dp(6) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = title.text.toString()
        }
        item.addView(title)
        item.addView(preview)
        binding.statusProofList.addView(item)
        Glide.with(this).load(url).into(preview)
    }

    private fun downloadPaymentProof(url: String, title: String = "Bukti") {
        try {
            val safeOrderId = (orderId ?: "pesanan")
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("$title $safeOrderId")
                .setDescription("Mengunduh foto bukti pesanan")
                .setMimeType("image/*")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "liz_kitchen_bukti_$safeOrderId.jpg"
                )
            val manager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(requireContext(), "$title sedang diunduh", Toast.LENGTH_SHORT).show()
        } catch (exception: Exception) {
            Toast.makeText(
                requireContext(),
                "Gagal mengunduh bukti: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun statusProofTitle(status: String): String {
        return when (status) {
            ORDER_STATUS_PROCESSING -> "Bukti Pesanan Diproses"
            ORDER_STATUS_SHIPPING -> "Bukti Pesanan Dikirim"
            ORDER_STATUS_DELIVERED -> "Bukti Pesanan Sudah Diantar"
            ORDER_STATUS_READY_PICKUP -> "Bukti Pesanan Siap Diambil"
            ORDER_STATUS_DONE -> "Bukti Pesanan Diterima"
            else -> "Bukti Status Pesanan"
        }
    }

    private fun receiptProofTitle(order: Order): String {
        return if (order.metodePengambilan.contains("ambil", ignoreCase = true)) {
            "Bukti Pengambilan Pelanggan"
        } else {
            "Bukti Penerimaan Pelanggan"
        }
    }

    private fun requestReceiptProof(order: Order) {
        val isPickup = order.metodePengambilan.contains("ambil", ignoreCase = true)
        val action = if (isPickup) {
            "menyelesaikan pengambilan pesanan"
        } else {
            "mengonfirmasi penerimaan pesanan"
        }
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogPhotoSourceBinding.inflate(layoutInflater)
        dialogBinding.tvPhotoSourceTitle.text = if (isPickup) {
            "Upload Bukti Pengambilan"
        } else {
            "Upload Bukti Penerimaan"
        }
        dialogBinding.tvPhotoSourceDescription.text =
            "Pilih foto pesanan yang sudah kamu terima untuk $action."

        dialogBinding.btnPhotoGallery.setOnClickListener {
            dialog.dismiss()
            receiptProofPicker.launch("image/*")
        }
        dialogBinding.btnPhotoCamera.setOnClickListener {
            dialog.dismiss()
            startReceiptCameraWithPermissionCheck()
        }
        dialogBinding.btnPhotoCancel.setOnClickListener { dialog.dismiss() }

        dialog.setContentView(dialogBinding.root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.from(it).skipCollapsed = true
            }
        }
        dialog.show()
    }

    private fun startReceiptCameraWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openReceiptCamera()
        } else {
            receiptCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openReceiptCamera() {
        runCatching {
            val (photoFile, photoUri) = requireContext().prepareCameraImage()
            receiptCameraFile = photoFile
            receiptCameraLauncher.launch(photoUri)
        }.onFailure {
            receiptCameraFile = null
            Toast.makeText(requireContext(), "Kamera tidak dapat dibuka", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadReceiptProof(uri: Uri) {
        val order = currentOrder
        val currentUserId = userId
        val currentOrderId = orderId
        if (order == null || currentUserId.isNullOrBlank() || currentOrderId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Data pesanan tidak lengkap", Toast.LENGTH_SHORT).show()
            return
        }

        setRequestLoading(true)
        val proofRef = storage.reference.child(
            "receipt_proofs/${currentOrderId}_${System.currentTimeMillis()}.jpg"
        )
        proofRef.putFile(uri)
            .addOnSuccessListener {
                proofRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        val uploadedAt = System.currentTimeMillis()
                        val updates = mapOf(
                            "status" to ORDER_STATUS_AWAITING_ADMIN_COMPLETION,
                            "receiptProofUrl" to downloadUri.toString(),
                            "receiptProofUploadedAtMillis" to uploadedAt
                        )
                        val globalOrderRef = db.collection("orders").document(currentOrderId)
                        val userOrderRef = db.collection("users").document(currentUserId)
                            .collection("orders").document(currentOrderId)
                        db.runBatch { batch ->
                            batch.set(globalOrderRef, updates, SetOptions.merge())
                            batch.set(userOrderRef, updates, SetOptions.merge())
                        }.addOnSuccessListener {
                            if (_binding == null) return@addOnSuccessListener
                            currentOrder = order.copy(
                                status = ORDER_STATUS_AWAITING_ADMIN_COMPLETION,
                                receiptProofUrl = downloadUri.toString(),
                                receiptProofUploadedAtMillis = uploadedAt
                            )
                            selectedReceiptProofUri = null
                            setRequestLoading(false)
                            val isPickup = order.metodePengambilan.contains("ambil", ignoreCase = true)
                            Toast.makeText(
                                requireContext(),
                                if (isPickup) "Bukti pengambilan berhasil dikirim" else "Bukti penerimaan berhasil dikirim",
                                Toast.LENGTH_SHORT
                            ).show()
                            fetchOrderDetails()
                        }.addOnFailureListener { exception ->
                            handleReceiptProofFailure(exception)
                        }
                    }
                    .addOnFailureListener { exception ->
                        handleReceiptProofFailure(exception)
                    }
            }
            .addOnFailureListener { exception ->
                handleReceiptProofFailure(exception)
            }
    }

    private fun handleReceiptProofFailure(exception: Exception) {
        if (_binding == null) return
        setRequestLoading(false)
        Toast.makeText(
            requireContext(),
            "Gagal mengunggah bukti penerimaan: ${exception.message}",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun applyStatusStyle(textView: TextView, status: String) {
        val (textColor, backgroundColor) = when (status) {
            ORDER_STATUS_DONE -> "#128A35" to "#E8F7EC"
            ORDER_STATUS_CANCELED, ORDER_STATUS_EXPIRED -> "#C62828" to "#FDECEC"
            ORDER_STATUS_PENDING_PAYMENT, ORDER_STATUS_PAYMENT_VERIFICATION,
            ORDER_STATUS_AWAITING_ADMIN_COMPLETION -> "#C46A16" to "#FFF0DE"
            ORDER_STATUS_CONFIRMED, ORDER_STATUS_SHIPPING, ORDER_STATUS_DELIVERED,
            ORDER_STATUS_READY_PICKUP -> "#128A35" to "#E8F7EC"
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
            ORDER_STATUS_DELIVERED -> "Pesanan sudah sampai. Upload foto penerimaan agar admin dapat menyelesaikannya."
            ORDER_STATUS_READY_PICKUP -> "Pesanan sudah siap diambil di cabang Liz Kitchen."
            ORDER_STATUS_AWAITING_ADMIN_COMPLETION -> "Bukti sudah dikirim dan menunggu admin menyelesaikan pesanan."
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

    private fun bindReceiptPrompt(order: Order, isPickup: Boolean) = with(binding) {
        tvReceiptEyebrow.text = if (isPickup) "KONFIRMASI PENGAMBILAN" else "KONFIRMASI PENERIMAAN"
        tvReceiptTitle.text = if (isPickup) "Pesanan sudah diambil?" else "Pesanan sudah sampai?"
        tvReceiptDescription.text = if (isPickup) {
            "Upload foto pesanan yang kamu ambil agar admin dapat menyelesaikan pesanan."
        } else {
            "Upload foto pesanan yang kamu terima agar admin dapat menyelesaikan pesanan."
        }

        val deadline = order.autoCompletionDeadlineAtMillis
        if (deadline <= 0L) {
            tvReceiptDeadline.text = "Menunggu sinkronisasi"
            tvReceiptRemaining.text = "Sedang disiapkan"
            return@with
        }

        tvReceiptDeadline.text = SimpleDateFormat("dd-MM-yyyy, HH:mm", Locale("id", "ID"))
            .format(Date(deadline))
        val remaining = deadline - System.currentTimeMillis()
        tvReceiptRemaining.text = receiptConfirmationRemainingLabel(remaining)
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
        binding.root.setFirebaseRequestLoading(isLoading, binding.progressBar2)
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
            binding.statusProofList.removeAllViews()
        }
        invoiceWebView?.destroy()
        invoiceWebView = null
        _binding = null
    }

    private companion object {
        const val STATE_RECEIPT_CAMERA_FILE = "receipt_camera_file"
    }
}
