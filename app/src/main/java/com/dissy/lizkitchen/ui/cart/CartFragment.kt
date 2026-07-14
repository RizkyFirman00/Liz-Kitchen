package com.dissy.lizkitchen.ui.cart

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.user.HomeCartUserAdapter
import com.dissy.lizkitchen.databinding.FragmentCartBinding
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.PAYMENT_EXPIRY_DURATION_MILLIS
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.cakeFromMap
import com.dissy.lizkitchen.utility.isInvalidCheckoutAddress
import com.dissy.lizkitchen.utility.orderToFirestoreMap
import com.dissy.lizkitchen.utility.productPriceToLong
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class CartFragment : Fragment(), 
    HomeCartUserAdapter.CartInteractionListener, 
    HomeCartUserAdapter.CartDeleteListener {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private var totalPrice: Long = 0
    private lateinit var cartAdapter: HomeCartUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = Preferences.getUserId(requireContext())
        cartAdapter = HomeCartUserAdapter(this, this)
        binding.rvCart.adapter = cartAdapter
        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.tvCartSummary.text = "Memuat keranjang..."
        binding.tvCheckoutSummary.text = ""
        fetchDataAndUpdateRecyclerView()

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.btn_toProfile -> {
                    findNavController().navigate(R.id.navigation_profile)
                    true
                }
                R.id.btn_toLogout -> {
                    Preferences.logout(requireContext())
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                    true
                }
                else -> false
            }
        }

        binding.btnCheckout.setOnClickListener {
            if (userId != null) {
                validateAddressBeforeCheckout(userId)
            }
        }
    }

    private fun validateAddressBeforeCheckout(userId: String) {
        setRequestLoading(true)

        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDocument ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val address = userDocument.getString("alamat")
                    ?: Preferences.getUserInfo(requireContext())?.alamat

                val checkoutAddress = address
                    ?.takeUnless { value -> isInvalidCheckoutAddress(value) }
                    ?.trim()
                    .orEmpty()
                createOrder(userId, checkoutAddress)
            }
            .addOnFailureListener { exception ->
                if (!isAdded || _binding == null) return@addOnFailureListener
                val fallbackAddress = Preferences.getUserInfo(requireContext())?.alamat
                    ?.takeUnless { value -> isInvalidCheckoutAddress(value) }
                    ?.trim()
                    .orEmpty()
                createOrder(userId, fallbackAddress)
            }
    }

    private fun finishCheckoutPreparation() {
        setRequestLoading(false)
    }

    private fun createOrder(userId: String, address: String) {
        db.collection("users").document(userId).collection("cart").get()
            .addOnSuccessListener { result ->
                val cartList = mutableListOf<Cart>()
                for (document in result) {
                    val cartId = document.id
                    val jumlahPesanan = (document.get("jumlahPesanan") as? Number)?.toLong()
                        ?: document.get("jumlahPesanan")?.toString()?.toLongOrNull()
                        ?: 0
                    val cakeDataMap = document.get("cake") as? Map<*, *> ?: emptyMap<String, Any>()
                    val cakeData = cakeFromMap(cakeDataMap["documentId"]?.toString().orEmpty().ifBlank { cartId }, cakeDataMap)
                    cartList.add(Cart(cartId, cakeData, jumlahPesanan))
                }
                if (cartList.isEmpty()) {
                    finishCheckoutPreparation()
                    Toast.makeText(requireContext(), "Keranjang masih kosong", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val userInfo = Preferences.getUserInfo(requireContext())
                val user = User(
                    userId = userId,
                    name = userInfo?.name ?: "Pelanggan",
                    email = userInfo?.email ?: "Email",
                    phoneNumber = userInfo?.phoneNumber ?: "Phone",
                    alamat = address
                )
                val nowMillis = System.currentTimeMillis()
                val nowDate = java.util.Date(nowMillis)
                val orderId = "ORDER-$nowMillis"
                val order = Order(
                    cart = cartList,
                    orderId = orderId,
                    status = ORDER_STATUS_PENDING_PAYMENT,
                    totalPrice = totalPrice,
                    user = user,
                    tanggalOrder = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(nowDate),
                    jamOrder = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(nowDate),
                    createdAtMillis = nowMillis,
                    paymentDeadlineMillis = nowMillis + PAYMENT_EXPIRY_DURATION_MILLIS
                )
                val orderData = orderToFirestoreMap(order)

                val globalOrderRef = db.collection("orders").document(orderId)
                val userOrderRef = db.collection("users").document(userId)
                    .collection("orders").document(orderId)

                db.runBatch { batch ->
                    batch.set(globalOrderRef, orderData)
                    batch.set(userOrderRef, orderData)
                }.addOnSuccessListener {
                    finishCheckoutPreparation()
                    val bundle = Bundle().apply { putString("orderId", orderId) }
                    findNavController().navigate(R.id.navigation_detail_cart, bundle)
                }
                    .addOnFailureListener { exception ->
                        finishCheckoutPreparation()
                        Toast.makeText(requireContext(), "Error membuat pesanan: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { exception ->
                finishCheckoutPreparation()
                Toast.makeText(requireContext(), "Error mengambil data keranjang: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchDataAndUpdateRecyclerView() {
        val userId = Preferences.getUserId(requireContext())
        if (userId == null) {
            binding.tvCartSummary.text = "Keranjang belum tersedia."
            binding.emptyCart.visibility = View.VISIBLE
            binding.linearLayout1.visibility = View.GONE
            return
        }

        setRequestLoading(true)
        db.collection("users").document(userId).collection("cart").get()
            .addOnSuccessListener { result ->
                if (_binding == null) return@addOnSuccessListener
                val cartList = mutableListOf<Cart>()
                totalPrice = 0
                for (document in result) {
                    val cakeDataMap = document.get("cake") as? Map<*, *> ?: emptyMap<String, Any>()
                    val jumlah = (document.get("jumlahPesanan") as? Number)?.toLong()
                        ?: document.get("jumlahPesanan")?.toString()?.toLongOrNull()
                        ?: 0
                    val cake = cakeFromMap(cakeDataMap["documentId"]?.toString().orEmpty().ifBlank { document.id }, cakeDataMap)
                    val harga = productPriceToLong(cake.harga)
                    cartList.add(Cart(document.id, cake, jumlah))
                    totalPrice += harga * jumlah
                }
                binding.tvPriceSum.text = formatCurrency(totalPrice.toString())
                val summaryText = buildCartSummary(cartList)
                binding.tvCartSummary.text = if (cartList.isEmpty()) {
                    "Keranjang belum berisi produk."
                } else {
                    "$summaryText siap checkout."
                }
                binding.tvCheckoutSummary.text = summaryText
                cartAdapter.submitList(cartList)
                setRequestLoading(false)
                binding.emptyCart.visibility = if (cartList.isEmpty()) View.VISIBLE else View.GONE
                binding.linearLayout1.visibility = if (cartList.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener { exception ->
                if (_binding == null) return@addOnFailureListener
                binding.tvCartSummary.text = "Gagal memuat keranjang."
                setRequestLoading(false)
                binding.emptyCart.visibility = View.VISIBLE
                binding.linearLayout1.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat keranjang: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun buildCartSummary(cartList: List<Cart>): String {
        val productTypeCount = cartList.size
        val quantityCount = cartList.sumOf { it.jumlahPesanan }
        return "$productTypeCount jenis produk | $quantityCount item"
    }

    private fun formatCurrency(value: String): String {
        val sb = StringBuilder(value)
        var i = sb.length - 3
        while (i > 0) { sb.insert(i, "."); i -= 3 }
        return sb.toString()
    }

    override fun onQuantityChanged(cart: Cart, newQuantity: Long) {
        val userId = Preferences.getUserId(requireContext()) ?: return
        setRequestLoading(true)
        db.collection("users").document(userId).collection("cart").document(cart.cakeId).update("jumlahPesanan", newQuantity)
            .addOnSuccessListener { fetchDataAndUpdateRecyclerView() }
            .addOnFailureListener { exception ->
                setRequestLoading(false)
                Toast.makeText(requireContext(), "Gagal mengubah jumlah: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onCartItemDelete(cart: Cart) {
        val userId = Preferences.getUserId(requireContext()) ?: return
        setRequestLoading(true)
        db.collection("users").document(userId).collection("cart").document(cart.cakeId).delete()
            .addOnSuccessListener { fetchDataAndUpdateRecyclerView() }
            .addOnFailureListener { exception ->
                setRequestLoading(false)
                Toast.makeText(requireContext(), "Gagal menghapus item: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setRequestLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.root.setFirebaseRequestLoading(isLoading, binding.progressBar2)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
