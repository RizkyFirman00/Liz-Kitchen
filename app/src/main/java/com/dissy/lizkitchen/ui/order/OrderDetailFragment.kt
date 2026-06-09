package com.dissy.lizkitchen.ui.order

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.user.HomeOrderUserCakeAdapter
import com.dissy.lizkitchen.databinding.FragmentOrderDetailBinding
import com.dissy.lizkitchen.model.Cake
import com.dissy.lizkitchen.model.Cart
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.cakeFromMap
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class OrderDetailFragment : Fragment() {
    private var _binding: FragmentOrderDetailBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private lateinit var orderDetailAdapter: HomeOrderUserCakeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val orderId = arguments?.getString("orderId")
        val userId = Preferences.getUserId(requireContext())

        orderDetailAdapter = HomeOrderUserCakeAdapter()
        binding.rvDetailOrderItem.adapter = orderDetailAdapter
        binding.rvDetailOrderItem.layoutManager = LinearLayoutManager(requireContext())

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        if (userId != null && orderId != null) {
            db.collection("users").document(userId).collection("orders").document(orderId).get()
                .addOnSuccessListener { document ->
                    val cartItemsArray = document.get("cart") as? ArrayList<HashMap<String, Any>>
                    val cartItems = cartItemsArray?.map { map ->
                        val cakeMap = map["cake"] as? HashMap<*, *>
                        Cart(
                            cakeId = map["cakeId"] as? String ?: "",
                            cake = cakeFromMap(cakeMap?.get("documentId")?.toString().orEmpty(), cakeMap ?: emptyMap<String, Any>()),
                            jumlahPesanan = map["jumlahPesanan"] as? Long ?: 0
                        )
                    } ?: listOf()
                    
                    orderDetailAdapter.submitList(cartItems)
                    
                    binding.apply {
                        tvOrderId.text = orderId
                        tvStatus.text = document.getString("status")
                        tvPriceSum.text = formatCurrency(document.getLong("totalPrice")?.toString() ?: "0")
                        tvMetodePengambilan.text = document.getString("metodePengambilan")
                        tvAlamat.text = document.getString("user.alamat") ?: "Belum ada alamat"
                        tvOrderDate.text = document.getString("tanggalOrder") ?: "-"
                    }

                    if (document.getString("status") == "Menunggu Pembayaran") {
                        binding.btnConfirm.visibility = View.VISIBLE
                        binding.btnConfirm.setOnClickListener {
                            val bundle = Bundle().apply { putString("orderId", orderId) }
                            findNavController().navigate(R.id.navigation_confirm, bundle)
                        }
                    } else {
                        binding.btnConfirm.visibility = View.GONE
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
        return sb.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
