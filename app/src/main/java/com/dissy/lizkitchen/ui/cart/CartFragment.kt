package com.dissy.lizkitchen.ui.cart

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.user.HomeCartUserAdapter
import com.dissy.lizkitchen.databinding.FragmentCartBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.ui.profile.ProfileActivity
import com.dissy.lizkitchen.utility.Preferences
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
        fetchDataAndUpdateRecyclerView()

        binding.btnToProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        binding.btnToLogout.setOnClickListener {
            Preferences.logout(requireContext())
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        binding.btnCheckout.setOnClickListener {
            if (userId != null) {
                binding.progressBar2.visibility = View.VISIBLE
                db.collection("users").document(userId).collection("cart").get()
                    .addOnSuccessListener { result ->
                        val cartList = mutableListOf<Cart>()
                        for (document in result) {
                            val cakeId = document.id
                            val jumlahPesanan = document.get("jumlahPesanan") as Long
                            val cakeDataMap = document.get("cake") as HashMap<String, Any>
                            val cakeData = Cake(
                                cakeId,
                                cakeDataMap["harga"] as String,
                                cakeDataMap["imageUrl"] as String,
                                cakeDataMap["namaKue"] as String,
                                cakeDataMap["stok"] as Long
                            )
                            cartList.add(Cart(cakeId, cakeData, jumlahPesanan))
                        }
                        val userInfo = Preferences.getUserInfo(requireContext())
                        val user = User(
                            userId = userId,
                            username = userInfo?.username ?: "User",
                            email = userInfo?.email ?: "Email",
                            phoneNumber = userInfo?.phoneNumber ?: "Phone",
                            alamat = userInfo?.alamat ?: "Address"
                        )
                        val orderId = "ORDER-${System.currentTimeMillis()}"
                        val order = hashMapOf(
                            "orderId" to orderId,
                            "cart" to cartList,
                            "status" to "Menunggu Pembayaran",
                            "totalPrice" to totalPrice,
                            "user" to user,
                            "tanggalOrder" to java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                        
                        db.collection("orders").document(orderId).set(order)
                            .addOnSuccessListener {
                                clearCart(userId)
                                binding.progressBar2.visibility = View.GONE
                                val intent = Intent(requireContext(), DetailCartActivity::class.java)
                                intent.putExtra("orderId", orderId)
                                startActivity(intent)
                            }
                    }
            }
        }
    }

    private fun fetchDataAndUpdateRecyclerView() {
        val userId = Preferences.getUserId(requireContext()) ?: return
        binding.progressBar2.visibility = View.VISIBLE
        db.collection("users").document(userId).collection("cart").get()
            .addOnSuccessListener { result ->
                val cartList = mutableListOf<Cart>()
                totalPrice = 0
                for (document in result) {
                    val cakeDataMap = document.get("cake") as HashMap<String, Any>
                    val jumlah = document.get("jumlahPesanan") as Long
                    val harga = (cakeDataMap["harga"] as String).replace(".", "").toLong()
                    val cake = Cake(document.id, cakeDataMap["harga"] as String, cakeDataMap["imageUrl"] as String, cakeDataMap["namaKue"] as String, cakeDataMap["stok"] as Long)
                    cartList.add(Cart(document.id, cake, jumlah))
                    totalPrice += harga * jumlah
                }
                binding.tvPriceSum.text = formatCurrency(totalPrice.toString())
                cartAdapter.submitList(cartList)
                binding.progressBar2.visibility = View.GONE
                binding.emptyCart.visibility = if (cartList.isEmpty()) View.VISIBLE else View.GONE
                binding.linearLayout1.visibility = if (cartList.isEmpty()) View.GONE else View.VISIBLE
            }
    }

    private fun clearCart(userId: String) {
        db.collection("users").document(userId).collection("cart").get().addOnSuccessListener { 
            for (doc in it) doc.reference.delete()
        }
    }

    private fun formatCurrency(value: String): String {
        val sb = StringBuilder(value)
        var i = sb.length - 3
        while (i > 0) { sb.insert(i, "."); i -= 3 }
        return sb.toString()
    }

    override fun onQuantityChanged(cart: Cart, newQuantity: Long) {
        val userId = Preferences.getUserId(requireContext()) ?: return
        db.collection("users").document(userId).collection("cart").document(cart.cakeId).update("jumlahPesanan", newQuantity)
            .addOnSuccessListener { fetchDataAndUpdateRecyclerView() }
    }

    override fun onCartItemDelete(cart: Cart) {
        val userId = Preferences.getUserId(requireContext()) ?: return
        db.collection("users").document(userId).collection("cart").document(cart.cakeId).delete()
            .addOnSuccessListener { fetchDataAndUpdateRecyclerView() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}