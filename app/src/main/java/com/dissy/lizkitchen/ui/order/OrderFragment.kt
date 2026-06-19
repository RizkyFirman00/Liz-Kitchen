package com.dissy.lizkitchen.ui.order

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.adapter.user.HomeOrderUserAdapter
import com.dissy.lizkitchen.databinding.FragmentOrderBinding
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.validateOrderExpiryOnRead
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

import androidx.navigation.fragment.findNavController
import com.dissy.lizkitchen.R

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
            val bundle = Bundle().apply {
                putString("orderId", orderId)
            }
            findNavController().navigate(R.id.navigation_order_detail, bundle)
        }
        binding.rvOrder.adapter = orderAdapter
        binding.rvOrder.layoutManager = LinearLayoutManager(requireContext())
        binding.tvOrderCount.text = "Memuat pesanan..."

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

        fetchOrders()
    }

    private fun fetchOrders() {
        val userId = Preferences.getUserId(requireContext())
        if (userId == null) {
            binding.tvOrderCount.text = "0 pesanan"
            binding.emptyCart.visibility = View.VISIBLE
            return
        }

        binding.root.setFirebaseRequestLoading(true, binding.progressBar2)
        db.collection("users").document(userId).collection("orders").get()
            .addOnSuccessListener { result ->
                if (_binding == null) return@addOnSuccessListener
                val orderList = result.map { validateOrderExpiryOnRead(db, orderFromDocument(it)) }
                    .sortedByDescending { order -> order.orderId.removePrefix("ORDER-").toLongOrNull() ?: 0 }
                orderAdapter.submitList(orderList)
                binding.tvOrderCount.text = buildOrderCountText(orderList.size)
                binding.root.setFirebaseRequestLoading(false, binding.progressBar2)
                binding.emptyCart.visibility = if (orderList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.tvOrderCount.text = "0 pesanan"
                binding.root.setFirebaseRequestLoading(false, binding.progressBar2)
                binding.emptyCart.visibility = View.VISIBLE
            }
    }

    private fun buildOrderCountText(orderCount: Int): String {
        return if (orderCount == 0) {
            "Belum ada pesanan aktif atau riwayat."
        } else {
            "$orderCount pesanan aktif dan riwayat."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
