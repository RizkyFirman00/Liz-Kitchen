package com.dissy.lizkitchen.ui.cart

import android.content.Context
import android.graphics.Rect
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.user.CheckoutUserAdapter
import com.dissy.lizkitchen.databinding.FragmentDetailCartBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.utility.LizKitchenBranch
import com.dissy.lizkitchen.utility.MAX_DELIVERY_RADIUS_METERS
import com.dissy.lizkitchen.utility.METODE_AMBIL_SENDIRI
import com.dissy.lizkitchen.utility.METODE_PESAN_ANTAR
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.PAYMENT_EXPIRY_DURATION_MILLIS
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.cartItemsFromAny
import com.dissy.lizkitchen.utility.deliveryDistanceMeters
import com.dissy.lizkitchen.utility.deliveryFeeForDistanceMeters
import com.dissy.lizkitchen.utility.deliveryFeeLabel
import com.dissy.lizkitchen.utility.isInvalidCheckoutAddress
import com.dissy.lizkitchen.utility.nearestBranchDistanceMeters
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.productPriceToLong
import com.dissy.lizkitchen.utility.pickupBranchForOrder
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DetailCartFragment : Fragment() {
    private var _binding: FragmentDetailCartBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private lateinit var checkoutAdapter: CheckoutUserAdapter
    private var orderId: String? = null
    private var selectedMetodePengambilan: String = ""
    private var selectedPickupBranch: LizKitchenBranch? = null
    private var hasSubmittedCheckout: Boolean = false
    private var validatedAddressText: String = ""
    private var deliveryCheckResult: DeliveryCheckResult? = null
    private var isUpdatingAddressText: Boolean = false
    private var orderSubtotalPrice: Long = 0L
    private var selectedDeliveryFee: Long = 0L
    private var selectedDeliveryDistanceMeters: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderId = arguments?.getString("orderId")
        checkoutAdapter = CheckoutUserAdapter()
        binding.rvCheckout.adapter = checkoutAdapter
        binding.rvCheckout.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCheckout.isNestedScrollingEnabled = false
        binding.tvCheckoutSummary.text = "Memuat detail pesanan..."
        setupAddressFocusDismiss()
        setupAddressValidation()
        fetchDataAndUpdateRecyclerView()

        val userId = Preferences.getUserId(requireContext())
        if (userId != null && orderId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener {
                    val alamat = it.getString("alamat") ?: "Belum ada alamat"
                    setInitialAddress(alamat)
                }

            db.collection("users").document(userId).collection("orders").document(orderId!!)
                .get()
                .addOnSuccessListener {
                    if (!it.exists()) return@addOnSuccessListener
                    val order = orderFromDocument(it)
                    orderSubtotalPrice = calculateCartSubtotal(order.cart)
                        .takeIf { subtotal -> subtotal > 0L }
                        ?: (order.totalPrice - order.deliveryFee).coerceAtLeast(0L)
                    selectedDeliveryFee = order.deliveryFee
                    selectedDeliveryDistanceMeters = order.deliveryDistanceMeters
                    binding.etPatokanAlamat.setText(order.patokanAlamat)
                    if (order.metodePengambilan.isNotBlank()) {
                        selectedMetodePengambilan = order.metodePengambilan
                        selectedPickupBranch = pickupBranchForOrder(order)
                        updateSelectedMethodView()
                    }
                    updatePriceSummary()
                }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    deleteDraftOrderAndNavigateUp()
                }
            }
        )

        binding.btnToHome.setOnClickListener { deleteDraftOrderAndNavigateUp() }

        binding.btnGantiMetodePengambilan.setOnClickListener {
            showMetodePengambilanSheet()
        }

        binding.btnCancel.setOnClickListener { deleteDraftOrderAndNavigateUp() }

        binding.btnCheckout.setOnClickListener {
            checkout()
        }
    }

    private fun setupAddressFocusDismiss() {
        attachAddressFocusDismissListener(binding.root)
    }

    private fun attachAddressFocusDismissListener(view: View) {
        if (view == binding.textInputLayout || view == binding.etAlamat) return

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                clearAddressFocusIfTappedOutside(event)
            }
            false
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                attachAddressFocusDismissListener(view.getChildAt(index))
            }
        }
    }

    private fun clearAddressFocusIfTappedOutside(event: MotionEvent) {
        if (!binding.etAlamat.hasFocus()) return
        if (isTouchInsideView(binding.textInputLayout, event.rawX.toInt(), event.rawY.toInt())) return

        binding.etAlamat.clearFocus()
        hideKeyboard(binding.etAlamat)
    }

    private fun isTouchInsideView(view: View, rawX: Int, rawY: Int): Boolean {
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)
        return bounds.contains(rawX, rawY)
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupAddressValidation() {
        binding.textInputLayout.setEndIconOnClickListener {
            validateTypedAddress()
        }
        binding.etAlamat.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingAddressText) invalidateAddressValidation()
            }
        })
        binding.textInputLayout.helperText = "Tekan ikon lokasi untuk memvalidasi alamat"
    }

    private fun validateTypedAddress() {
        val address = binding.etAlamat.text.toString().trim()
        if (isInvalidCheckoutAddress(address)) {
            binding.textInputLayout.error = "Masukkan alamat tujuan terlebih dahulu"
            return
        }

        hideKeyboard(binding.etAlamat)
        binding.textInputLayout.error = null
        binding.textInputLayout.helperText = "Memeriksa alamat..."
        binding.textInputLayout.isEndIconVisible = false

        checkDeliveryAvailability(address) { deliveryCheck ->
            if (_binding == null) return@checkDeliveryAvailability

            binding.textInputLayout.isEndIconVisible = true
            val resolvedAddress = deliveryCheck.resolvedAddress?.trim().orEmpty()
            if (resolvedAddress.isBlank()) {
                validatedAddressText = ""
                deliveryCheckResult = null
                binding.textInputLayout.helperText = "Alamat tidak ditemukan. Periksa kembali penulisannya."
                binding.textInputLayout.error = "Alamat belum dapat diverifikasi"
                return@checkDeliveryAvailability
            }

            setAddressTextWithoutInvalidating(resolvedAddress)
            validatedAddressText = resolvedAddress
            deliveryCheckResult = deliveryCheck
            selectedDeliveryDistanceMeters = deliveryDistanceMeters(deliveryCheck.distanceMeters)
            selectedDeliveryFee = deliveryFeeForDistanceMeters(deliveryCheck.distanceMeters) ?: 0L
            binding.textInputLayout.error = null
            binding.textInputLayout.helperText = when (deliveryCheck.status) {
                DeliveryStatus.AVAILABLE -> {
                    val distanceText = deliveryCheck.distanceMeters?.let { formatDistance(it) }
                    val fee = deliveryFeeForDistanceMeters(deliveryCheck.distanceMeters) ?: 0L
                    if (distanceText == null) {
                        "Alamat terverifikasi - biaya antar ${deliveryFeeLabel(fee)}"
                    } else {
                        "Alamat terverifikasi - delivery $distanceText dari cabang | biaya ${deliveryFeeLabel(fee)}"
                    }
                }
                DeliveryStatus.OUT_OF_RANGE -> {
                    val distanceText = deliveryCheck.distanceMeters?.let { formatDistance(it) } ?: "lebih dari 40 km"
                    "Di luar jangkauan delivery ($distanceText). Maksimal 40 km, pilih Ambil Sendiri."
                }
                DeliveryStatus.UNKNOWN -> "Alamat ditemukan, tetapi jangkauan delivery belum dapat dipastikan"
            }

            if (deliveryCheck.status != DeliveryStatus.AVAILABLE &&
                selectedMetodePengambilan == METODE_PESAN_ANTAR
            ) {
                selectedMetodePengambilan = ""
                selectedPickupBranch = null
                selectedDeliveryFee = 0L
                selectedDeliveryDistanceMeters = 0L
                updateSelectedMethodView()
            }
            updatePriceSummary()
        }
    }

    private fun invalidateAddressValidation() {
        validatedAddressText = ""
        deliveryCheckResult = null
        selectedDeliveryFee = 0L
        selectedDeliveryDistanceMeters = 0L
        binding.textInputLayout.error = null
        binding.textInputLayout.helperText = if (binding.etAlamat.text.isNullOrBlank()) {
            "Tekan ikon lokasi untuk memvalidasi alamat"
        } else {
            "Alamat berubah. Tekan ikon lokasi untuk memvalidasi ulang"
        }
        updatePriceSummary()
    }

    private fun setAddressTextWithoutInvalidating(address: String) {
        isUpdatingAddressText = true
        binding.etAlamat.setText(address)
        binding.etAlamat.setSelection(binding.etAlamat.text?.length ?: 0)
        isUpdatingAddressText = false
    }

    private fun setInitialAddress(address: String) {
        setAddressTextWithoutInvalidating(address)
        invalidateAddressValidation()
    }

    private fun showMetodePengambilanSheet() {
        val alamat = binding.etAlamat.text.toString().trim()
        val deliveryCheck = deliveryCheckResult?.takeIf {
            validatedAddressText.equals(alamat, ignoreCase = true)
        }
        val metodeAmbilFragment = MetodeAmbilFragment().apply {
            setDeliveryAvailability(
                isAvailable = deliveryCheck?.status == DeliveryStatus.AVAILABLE,
                notice = if (deliveryCheck == null) {
                    "Validasi alamat dengan ikon lokasi agar Pesan Antar tersedia."
                } else {
                    buildDeliveryNotice(deliveryCheck)
                }
            )
            setBranchRecommendation(
                branch = deliveryCheck?.nearestBranch,
                distanceMeters = deliveryCheck?.distanceMeters
            )
            setListener(object : MetodeAmbilFragment.MetodePengambilanListener {
                override fun onMetodePengambilanSelected(
                    metode: String,
                    pickupBranch: LizKitchenBranch?
                ) {
                    selectedMetodePengambilan = metode
                    selectedPickupBranch = pickupBranch
                    updateSelectedMethodView()
                }
            })
        }
        metodeAmbilFragment.show(childFragmentManager, metodeAmbilFragment.tag)
    }

    private fun updateSelectedMethodView() {
        val pickupBranch = selectedPickupBranch
        binding.tvMetodePengambilan.text = when {
            selectedMetodePengambilan == METODE_AMBIL_SENDIRI && pickupBranch != null ->
                "$METODE_AMBIL_SENDIRI - ${pickupBranch.name}"
            selectedMetodePengambilan.isNotBlank() -> selectedMetodePengambilan
            else -> "Pilih Metode Pengambilan"
        }

        if (selectedMetodePengambilan == METODE_AMBIL_SENDIRI && pickupBranch != null) {
            binding.pickupBranchContainer.visibility = View.VISIBLE
            binding.textInputLayout.visibility = View.GONE
            binding.textInputPatokan.visibility = View.GONE
            binding.tvPickupBranchName.text = pickupBranch.name
            binding.tvPickupBranchAddress.text = pickupBranch.address
        } else {
            binding.pickupBranchContainer.visibility = View.GONE
            binding.textInputLayout.visibility = View.VISIBLE
            binding.textInputPatokan.visibility = View.VISIBLE
            binding.tvPickupBranchName.text = ""
            binding.tvPickupBranchAddress.text = ""
        }

        if (selectedMetodePengambilan != METODE_PESAN_ANTAR) {
            selectedDeliveryFee = 0L
            selectedDeliveryDistanceMeters = 0L
        } else {
            selectedDeliveryFee = deliveryFeeForDistanceMeters(deliveryCheckResult?.distanceMeters)
                ?: selectedDeliveryFee
        }
        updatePriceSummary()
    }

    private fun checkDeliveryAvailability(
        address: String,
        onResult: (DeliveryCheckResult) -> Unit
    ) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val geocodedAddress = withContext(Dispatchers.IO) {
                geocodeAddress(appContext, address)
            }
            val nearestBranch = geocodedAddress?.location?.let { nearestBranchDistanceMeters(it) }
            val result = when {
                nearestBranch == null ->
                    DeliveryCheckResult(DeliveryStatus.UNKNOWN, null, null, geocodedAddress?.formattedAddress)
                nearestBranch.second <= MAX_DELIVERY_RADIUS_METERS ->
                    DeliveryCheckResult(
                        DeliveryStatus.AVAILABLE,
                        nearestBranch.first,
                        nearestBranch.second,
                        geocodedAddress.formattedAddress
                    )
                else -> DeliveryCheckResult(
                    DeliveryStatus.OUT_OF_RANGE,
                    nearestBranch.first,
                    nearestBranch.second,
                    geocodedAddress.formattedAddress
                )
            }
            onResult(result)
        }
    }

    @Suppress("DEPRECATION")
    private fun geocodeAddress(context: Context, address: String): GeocodedAddressResult? {
        if (!Geocoder.isPresent()) return null

        return try {
            val query = if (address.contains("Indonesia", ignoreCase = true)) {
                address
            } else {
                "$address, Indonesia"
            }
            val geocoder = Geocoder(context, Locale("id", "ID"))
            val geocodedAddress = geocoder.getFromLocationName(query, 1)
                ?.firstOrNull { it.hasLatitude() && it.hasLongitude() }

            geocodedAddress?.let {
                val location = Location("checkout_address").apply {
                    latitude = it.latitude
                    longitude = it.longitude
                }
                GeocodedAddressResult(location, it.getAddressLine(0).orEmpty())
            }
        } catch (exception: Exception) {
            null
        }
    }

    private fun buildDeliveryNotice(deliveryCheck: DeliveryCheckResult): String {
        return when (deliveryCheck.status) {
            DeliveryStatus.AVAILABLE -> {
                val distanceText = deliveryCheck.distanceMeters?.let { formatDistance(it) }
                val fee = deliveryFeeForDistanceMeters(deliveryCheck.distanceMeters) ?: 0L
                if (distanceText == null) {
                    "Delivery tersedia - Biaya antar ${deliveryFeeLabel(fee)}"
                } else {
                    "Delivery tersedia - $distanceText | Biaya antar ${deliveryFeeLabel(fee)}"
                }
            }
            DeliveryStatus.OUT_OF_RANGE -> {
                val distanceText = deliveryCheck.distanceMeters?.let { formatDistance(it) } ?: "lebih dari 40 km"
                "Pickup saja - $distanceText (maksimal 40 km)"
            }
            DeliveryStatus.UNKNOWN ->
                "Delivery belum bisa dicek"
        }
    }

    private fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1_000f) {
            String.format(Locale("id", "ID"), "%.1f km", distanceMeters / 1_000f)
        } else {
            "${distanceMeters.toInt()} m"
        }
    }

    private fun deleteDraftOrderAndNavigateUp() {
        if (hasSubmittedCheckout) {
            findNavController().navigateUp()
            return
        }

        val userId = Preferences.getUserId(requireContext())
        if (userId != null && orderId != null) {
            setRequestLoading(true)
            val globalOrderRef = db.collection("orders").document(orderId!!)
            val userOrderRef = db.collection("users").document(userId).collection("orders").document(orderId!!)

            db.runBatch { batch ->
                batch.delete(globalOrderRef)
                batch.delete(userOrderRef)
            }
                .addOnSuccessListener {
                    setRequestLoading(false)
                    findNavController().navigateUp()
                }
                .addOnFailureListener { exception ->
                    setRequestLoading(false)
                    Toast.makeText(
                        requireContext(),
                        "Gagal membatalkan checkout: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            findNavController().navigateUp()
        }
    }

    private fun checkout() {
        val alamat = binding.etAlamat.text.toString().trim()
        val patokanAlamat = binding.etPatokanAlamat.text.toString().trim()
        val metodePengambilan = selectedMetodePengambilan

        if (metodePengambilan.isBlank()) {
            Toast.makeText(requireContext(), "Silahkan pilih metode pengambilan", Toast.LENGTH_SHORT).show()
            return
        }

        if (metodePengambilan == METODE_AMBIL_SENDIRI && selectedPickupBranch == null) {
            Toast.makeText(requireContext(), "Silahkan pilih cabang pengambilan", Toast.LENGTH_SHORT).show()
            return
        }

        if (metodePengambilan == METODE_PESAN_ANTAR) {
            if (isInvalidCheckoutAddress(alamat)) {
                Toast.makeText(requireContext(), "Alamat delivery wajib diisi", Toast.LENGTH_SHORT).show()
                return
            }

            val deliveryCheck = deliveryCheckResult?.takeIf {
                validatedAddressText.equals(alamat, ignoreCase = true)
            }
            if (deliveryCheck == null) {
                binding.textInputLayout.error = "Tekan ikon lokasi untuk memvalidasi alamat"
                Toast.makeText(
                    requireContext(),
                    "Validasi alamat terlebih dahulu sebelum melanjutkan",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            when (deliveryCheck.status) {
                DeliveryStatus.AVAILABLE -> submitCheckout(alamat, patokanAlamat, metodePengambilan)
                DeliveryStatus.OUT_OF_RANGE -> Toast.makeText(
                    requireContext(),
                    "Alamat di luar jangkauan 40 km. Silahkan pilih Ambil Sendiri.",
                    Toast.LENGTH_LONG
                ).show()
                DeliveryStatus.UNKNOWN -> Toast.makeText(
                    requireContext(),
                    "Alamat belum bisa diverifikasi untuk delivery. Periksa alamat atau pilih Pickup.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        submitCheckout("", "", metodePengambilan)
    }

    private fun submitCheckout(alamat: String, patokanAlamat: String, metodePengambilan: String) {
        val userId = Preferences.getUserId(requireContext())
        if (userId != null && orderId != null) {
            setRequestLoading(true)
            val pickupBranch = if (metodePengambilan == METODE_AMBIL_SENDIRI) selectedPickupBranch else null
            val deliveryFee = if (metodePengambilan == METODE_PESAN_ANTAR) selectedDeliveryFee else 0L
            val deliveryDistance = if (metodePengambilan == METODE_PESAN_ANTAR) {
                selectedDeliveryDistanceMeters
            } else {
                0L
            }
            val paymentDeadlineMillis = System.currentTimeMillis() + PAYMENT_EXPIRY_DURATION_MILLIS
            val orderUpdates = mapOf(
                "user" to mapOf("alamat" to alamat),
                "patokanAlamat" to patokanAlamat,
                "metodePengambilan" to metodePengambilan,
                "pickupBranchId" to pickupBranch?.id.orEmpty(),
                "pickupBranchName" to pickupBranch?.name.orEmpty(),
                "pickupBranchAddress" to pickupBranch?.address.orEmpty(),
                "deliveryDistanceMeters" to deliveryDistance,
                "deliveryFee" to deliveryFee,
                "totalPrice" to (orderSubtotalPrice + deliveryFee),
                "status" to ORDER_STATUS_PENDING_PAYMENT,
                "paymentDeadlineMillis" to paymentDeadlineMillis
            )
            val userRef = db.collection("users").document(userId)
            val globalOrderRef = db.collection("orders").document(orderId!!)
            val userOrderRef = userRef.collection("orders").document(orderId!!)

            db.runBatch { batch ->
                if (alamat.isNotBlank()) {
                    batch.set(userRef, mapOf("alamat" to alamat), SetOptions.merge())
                }
                batch.set(globalOrderRef, orderUpdates, SetOptions.merge())
                batch.set(userOrderRef, orderUpdates, SetOptions.merge())
            }
                .addOnSuccessListener {
                    hasSubmittedCheckout = true
                    clearCartAfterCheckout(userId)
                }
                .addOnFailureListener { exception ->
                    setRequestLoading(false)
                    Toast.makeText(requireContext(), "Gagal membuat pesanan: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            setRequestLoading(false)
        }
    }

    private fun clearCartAfterCheckout(userId: String) {
        db.collection("users").document(userId).collection("cart").get()
            .addOnSuccessListener { cartSnapshot ->
                val batch = db.batch()
                cartSnapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit()
                    .addOnSuccessListener {
                        setRequestLoading(false)
                        val bundle = Bundle().apply { putString("orderId", orderId) }
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.navigation_cart, false)
                            .build()
                        findNavController().navigate(R.id.navigation_confirm, bundle, navOptions)
                        Toast.makeText(
                            requireContext(),
                            "Pesanan berhasil dibuat, Silahkan lakukan pembayaran",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { exception ->
                        setRequestLoading(false)
                        Toast.makeText(
                            requireContext(),
                            "Pesanan dibuat, tapi keranjang gagal dikosongkan: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { exception ->
                setRequestLoading(false)
                Toast.makeText(
                    requireContext(),
                    "Pesanan dibuat, tapi keranjang gagal diperbarui: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun fetchDataAndUpdateRecyclerView() {
        val userId = Preferences.getUserId(requireContext())
        if (userId != null && orderId != null) {
            setRequestLoading(true)
            db.collection("users").document(userId).collection("orders").document(orderId!!).get()
                .addOnSuccessListener { snapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    val cartItems = cartItemsFromAny(snapshot.get("cart"))
                    checkoutAdapter.submitList(cartItems)
                    orderSubtotalPrice = calculateCartSubtotal(cartItems)
                    binding.tvCheckoutSummary.text = buildCheckoutSummary(cartItems)
                    updatePriceSummary()
                    setRequestLoading(false)
                }
                .addOnFailureListener { exception ->
                    if (_binding == null) return@addOnFailureListener
                    setRequestLoading(false)
                    Toast.makeText(requireContext(), "Gagal memuat detail pesanan: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun buildCheckoutSummary(cartItems: List<Cart>): String {
        val productTypeCount = cartItems.size
        val quantityCount = cartItems.sumOf { it.jumlahPesanan }
        return "$productTypeCount jenis produk | $quantityCount item"
    }

    private fun calculateCartSubtotal(cartItems: List<Cart>): Long {
        return cartItems.sumOf { item ->
            productPriceToLong(item.cake.harga) * item.jumlahPesanan
        }
    }

    private fun updatePriceSummary() {
        if (_binding == null) return
        val deliverySelected = selectedMetodePengambilan == METODE_PESAN_ANTAR
        val fee = if (deliverySelected) selectedDeliveryFee else 0L
        val total = orderSubtotalPrice + fee
        binding.tvSubtotalPrice.text = formatAndDisplayCurrency(orderSubtotalPrice.toString())
        binding.tvDeliveryFeePrice.text = deliveryFeeLabel(fee)
        binding.tvDeliveryFeeDetail.text = if (deliverySelected && selectedDeliveryDistanceMeters > 0L) {
            "${formatDistance(selectedDeliveryDistanceMeters.toFloat())} dari cabang"
        } else {
            "Pilih Pesan Antar untuk menghitung biaya"
        }
        binding.tvPriceSum.text = formatAndDisplayCurrency(total.toString())
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

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.root.setFirebaseRequestLoading(isLoading, binding.progressBar2)
    }

    private enum class DeliveryStatus {
        AVAILABLE,
        OUT_OF_RANGE,
        UNKNOWN
    }

    private data class DeliveryCheckResult(
        val status: DeliveryStatus,
        val nearestBranch: LizKitchenBranch?,
        val distanceMeters: Float?,
        val resolvedAddress: String?
    )

    private data class GeocodedAddressResult(
        val location: Location,
        val formattedAddress: String
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
