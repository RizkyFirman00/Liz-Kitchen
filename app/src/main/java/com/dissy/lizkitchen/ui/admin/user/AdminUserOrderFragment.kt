package com.dissy.lizkitchen.ui.admin.user

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dissy.lizkitchen.R
import com.dissy.lizkitchen.adapter.admin.HomeAdminUserAdapter
import com.dissy.lizkitchen.databinding.FragmentAdminUserOrderBinding
import com.dissy.lizkitchen.model.Order
import com.dissy.lizkitchen.ui.login.LoginActivity
import com.dissy.lizkitchen.utility.ORDER_STATUS_CANCELED
import com.dissy.lizkitchen.utility.ORDER_STATUS_CONFIRMED
import com.dissy.lizkitchen.utility.ORDER_STATUS_DONE
import com.dissy.lizkitchen.utility.ORDER_STATUS_EXPIRED
import com.dissy.lizkitchen.utility.ORDER_STATUS_PENDING_PAYMENT
import com.dissy.lizkitchen.utility.ORDER_STATUS_PROCESSING
import com.dissy.lizkitchen.utility.ORDER_STATUS_READY_PICKUP
import com.dissy.lizkitchen.utility.ORDER_STATUS_SHIPPING
import com.dissy.lizkitchen.utility.Preferences
import com.dissy.lizkitchen.utility.orderFromDocument
import com.dissy.lizkitchen.utility.setFirebaseRequestLoading
import com.dissy.lizkitchen.utility.validateOrderExpiryOnRead
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.android.material.tabs.TabLayout

class AdminUserOrderFragment : Fragment() {
    private var _binding: FragmentAdminUserOrderBinding? = null
    private val binding get() = _binding!!
    private val db = Firebase.firestore
    private val ordersCollection = db.collection("orders")
    private lateinit var adminUserAdapter: HomeAdminUserAdapter
    private var orderList = mutableListOf<Order>()
    private var searchQuery = ""
    private var selectedStatus = STATUS_ALL
    private var selectedSort = SORT_NEWEST
    private var isLoading = true

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

        setupFilterAndSort()
        setupSearch()
        fetchDataAndUpdateRecyclerView()
    }

    private fun setupSearch() {
        binding.searhView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty()
                applyFilterAndSort()
                binding.searhView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                applyFilterAndSort()
                return true
            }
        })
    }

    private fun setupFilterAndSort() {
        setupStatusTabs()

        val sortAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            SORT_OPTIONS
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerSortOrder.adapter = sortAdapter

        binding.spinnerSortOrder.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSort = SORT_OPTIONS[position]
                applyFilterAndSort()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupStatusTabs() {
        binding.tabFilterStatus.removeAllTabs()
        STATUS_FILTERS.forEachIndexed { index, filter ->
            val tab = binding.tabFilterStatus.newTab().setText(filter.label)
            binding.tabFilterStatus.addTab(tab, index == 0)
        }

        binding.tabFilterStatus.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedStatus = STATUS_FILTERS.getOrNull(tab.position)?.status ?: STATUS_ALL
                applyFilterAndSort()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) {
                selectedStatus = STATUS_FILTERS.getOrNull(tab.position)?.status ?: STATUS_ALL
                applyFilterAndSort()
            }
        })
    }

    private fun applyFilterAndSort() {
        val query = searchQuery.trim()
        val filteredOrders = orderList.filter { order ->
            val matchesQuery = query.isEmpty() ||
                    order.orderId.contains(query, ignoreCase = true) ||
                    order.user.name.orEmpty().contains(query, ignoreCase = true) ||
                    order.user.phoneNumber.orEmpty().contains(query, ignoreCase = true)
            val matchesStatus = selectedStatus == STATUS_ALL || order.status == selectedStatus

            matchesQuery && matchesStatus
        }

        val sortedOrders = when (selectedSort) {
            SORT_OLDEST -> filteredOrders.sortedBy { orderTimestamp(it) }
            SORT_NAME_ASC -> filteredOrders.sortedBy { it.user.name.orEmpty().lowercase() }
            SORT_NAME_DESC -> filteredOrders.sortedByDescending { it.user.name.orEmpty().lowercase() }
            SORT_STATUS_ASC -> filteredOrders.sortedBy { it.status.lowercase() }
            else -> filteredOrders.sortedByDescending { orderTimestamp(it) }
        }

        adminUserAdapter.submitList(sortedOrders)
        binding.tvOrderSummary.text = buildOrderSummary(sortedOrders.size)
        binding.tvEmptyData.visibility = if (!isLoading && sortedOrders.isEmpty()) View.VISIBLE else View.GONE
        if (sortedOrders.isNotEmpty()) {
            binding.rvUser.scrollToPosition(0)
        }
    }

    private fun fetchDataAndUpdateRecyclerView() {
        isLoading = true
        binding.root.setFirebaseRequestLoading(true, binding.progressBar2)
        binding.tvEmptyData.visibility = View.GONE
        binding.tvOrderSummary.text = "Memuat pesanan..."

        ordersCollection.get().addOnSuccessListener { result ->
            if (_binding == null) return@addOnSuccessListener
            orderList.clear()
            for (document in result) {
                orderList.add(validateOrderExpiryOnRead(db, orderFromDocument(document)))
            }
            isLoading = false
            binding.root.setFirebaseRequestLoading(false, binding.progressBar2)
            applyFilterAndSort()
        }.addOnFailureListener { exception ->
            if (_binding != null) {
                isLoading = false
                binding.root.setFirebaseRequestLoading(false, binding.progressBar2)
                binding.tvOrderSummary.text = "Gagal memuat pesanan"
                binding.tvEmptyData.visibility = View.VISIBLE
            }
            Log.e("AdminUserOrder", "Error fetching data", exception)
        }
    }

    private fun buildOrderSummary(filteredCount: Int): String {
        if (isLoading) return "Memuat pesanan..."
        val totalCount = orderList.size
        return if (selectedStatus == STATUS_ALL && searchQuery.isBlank()) {
            "$totalCount pesanan masuk"
        } else {
            "$filteredCount dari $totalCount pesanan ditampilkan"
        }
    }

    private fun orderTimestamp(order: Order): Long {
        return order.orderId.removePrefix("ORDER-").toLongOrNull() ?: 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val STATUS_ALL = "Semua Status"
        private const val SORT_NEWEST = "Terbaru"
        private const val SORT_OLDEST = "Terlama"
        private const val SORT_NAME_ASC = "Nama A-Z"
        private const val SORT_NAME_DESC = "Nama Z-A"
        private const val SORT_STATUS_ASC = "Status A-Z"

        private data class StatusFilter(
            val status: String,
            val label: String
        )

        private val STATUS_FILTERS = listOf(
            StatusFilter(STATUS_ALL, "Semua"),
            StatusFilter(ORDER_STATUS_PENDING_PAYMENT, "Menunggu Bayar"),
            StatusFilter(ORDER_STATUS_CONFIRMED, "Dikonfirmasi"),
            StatusFilter(ORDER_STATUS_PROCESSING, "Diproses"),
            StatusFilter(ORDER_STATUS_SHIPPING, "Dikirim"),
            StatusFilter(ORDER_STATUS_READY_PICKUP, "Siap Diambil"),
            StatusFilter(ORDER_STATUS_DONE, "Selesai"),
            StatusFilter(ORDER_STATUS_CANCELED, "Dibatalkan"),
            StatusFilter(ORDER_STATUS_EXPIRED, "Expired")
        )

        private val SORT_OPTIONS = listOf(
            SORT_NEWEST,
            SORT_OLDEST,
            SORT_NAME_ASC,
            SORT_NAME_DESC,
            SORT_STATUS_ASC
        )
    }
}
