package com.dissy.lizkitchen.ui.admin.user

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.admin.HomeAdminUserAdapter
import com.dissy.lizkitchen.databinding.FragmentAdminUserOrderBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.Preferences
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AdminUserOrderFragment : Fragment() {
    private var _binding: FragmentAdminUserOrderBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val ordersCollection = db.collection("orders")
    private lateinit var adminUserAdapter: HomeAdminUserAdapter
    private var orderList = mutableListOf<Order>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUserOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnToLogout.setOnClickListener {
            Preferences.logout(requireContext())
            Intent(requireContext(), LoginActivity::class.java).also {
                startActivity(it)
                requireActivity().finish()
            }
        }

        binding.btnToHome.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnToMutasi.setOnClickListener {
            findNavController().navigate(R.id.navigation_admin_report)
        }

        adminUserAdapter = HomeAdminUserAdapter { orderId ->
            val bundle = Bundle().apply { putString("orderId", orderId) }
            findNavController().navigate(R.id.navigation_admin_user_order_detail, bundle)
        }
        binding.rvUser.adapter = adminUserAdapter
        binding.rvUser.layoutManager = LinearLayoutManager(requireContext())
        fetchDataAndUpdateRecyclerView()

        binding.searhView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    adminUserAdapter.filter.filter(newText)
                } else {
                    adminUserAdapter.submitList(orderList)
                }
                return true
            }
        })
    }

    private fun fetchDataAndUpdateRecyclerView() {
        ordersCollection.get().addOnSuccessListener { result ->
            if (_binding == null) return@addOnSuccessListener
            orderList.clear()
            for (document in result) {
                val order = document.toObject(Order::class.java)
                orderList.add(order)
            }
            adminUserAdapter.submitList(orderList)
        }.addOnFailureListener { exception ->
            Log.e("AdminUserOrder", "Error fetching data", exception)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}