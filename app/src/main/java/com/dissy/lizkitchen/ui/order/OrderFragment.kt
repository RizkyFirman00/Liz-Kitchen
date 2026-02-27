package com.dissy.lizkitchen.ui.order

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.user.HomeOrderUserAdapter
import com.dissy.lizkitchen.databinding.FragmentOrderBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.model.User
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.ui.profile.ProfileActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class OrderFragment : Fragment() {
    private var _binding: FragmentOrderBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private lateinit var orderAdapter: HomeOrderUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderAdapter = HomeOrderUserAdapter { orderId ->
            val intent = Intent(requireContext(), OrderDetailActivity::class.java)
            intent.putExtra("orderId", orderId)
            startActivity(intent)
        }
        binding.rvOrder.adapter = orderAdapter
        binding.rvOrder.layoutManager = LinearLayoutManager(requireContext())

        binding.btnToProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        binding.btnToLogout.setOnClickListener {
            Preferences.logout(requireContext())
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        fetchOrders()
    }

    private fun fetchOrders() {
        val userId = Preferences.getUserId(requireContext()) ?: return
        binding.progressBar2.visibility = View.VISIBLE
        db.collection("users").document(userId).collection("orders").get()
            .addOnSuccessListener { result ->
                val orderList = mutableListOf<Order>()
                for (document in result) {
                    val cartItemsArray = document.get("cart") as? ArrayList<HashMap<String, Any>>
                    val cartItems = cartItemsArray?.map { map ->
                        val cakeMap = map["cake"] as? HashMap<*, *>
                        Cart(
                            cakeId = cakeMap?.get("documentId") as? String ?: "",
                            cake = Cake(
                                documentId = cakeMap?.get("documentId") as? String ?: "",
                                harga = cakeMap?.get("harga") as? String ?: "",
                                imageUrl = cakeMap?.get("imageUrl") as? String ?: "",
                                namaKue = cakeMap?.get("namaKue") as? String ?: "",
                                stok = (cakeMap?.get("stok") as? Long) ?: 0
                            ),
                            jumlahPesanan = map["jumlahPesanan"] as? Long ?: 0
                        )
                    } ?: listOf()
                    
                    val order = Order(
                        cart = cartItems,
                        orderId = document.getString("orderId") ?: "",
                        status = document.getString("status") ?: "",
                        totalPrice = document.getLong("totalPrice") ?: 0,
                        tanggalOrder = document.getString("tanggalOrder") ?: "",
                        metodePengambilan = document.getString("metodePengambilan") ?: "",
                        user = User() // Map basic user if needed
                    )
                    orderList.add(order)
                }
                orderAdapter.submitList(orderList)
                binding.progressBar2.visibility = View.GONE
                binding.emptyCart.visibility = if (orderList.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}